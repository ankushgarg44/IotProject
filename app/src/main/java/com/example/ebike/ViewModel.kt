package com.example.ebike

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.util.Log
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.ebike.FirebaseCommandSender

class EbikeViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "EbikeNavigation"
        private const val TURN_TRIGGER_METERS = 20f
        private const val OFF_ROUTE_THRESHOLD_METERS = 40.0
        private const val OFF_ROUTE_EXIT_THRESHOLD_METERS = 25.0
        private const val OFF_ROUTE_CONFIRM_TICKS = 3
        private const val REROUTE_COOLDOWN_MS = 10_000L
        private const val OFF_ROUTE_CHECK_INTERVAL_MS = 2_000L
        private const val MIN_MOVEMENT_METERS = 2f
        private const val MIN_PROCESS_INTERVAL_MS = 800L
        private const val STEP_RECOVERY_DISTANCE_METERS = 35f
        private const val MIN_REROUTE_ORIGIN_SHIFT_METERS = 15f
        private const val DISTANCE_SMOOTHING_ALPHA = 0.35f
    }

    private val firebaseCommandSender = FirebaseCommandSender()

    // Current State
    var latitude by mutableStateOf(0.0)
        private set
    var longitude by mutableStateOf(0.0)
        private set
    var speed by mutableStateOf(0.0)
        private set
    var heading by mutableStateOf(0f)
        private set
    var isConnected by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isFollowingUser by mutableStateOf(true)
        private set
    var isRotatingMap by mutableStateOf(true)
        private set

    // Navigation State
    var routePolyline by mutableStateOf<List<GeoPoint>>(emptyList())
        private set
    var nextInstruction by mutableStateOf("Ready to Navigate")
        private set
    var distanceToNextTurnMeters by mutableStateOf(0f)
        private set
    var currentStepIndex by mutableStateOf(0)
        private set
    var totalSteps by mutableStateOf(0)
        private set
    var upcomingManeuverPoint by mutableStateOf<GeoPoint?>(null)
        private set
    var routeDemoSteps by mutableStateOf<List<RouteDemoStep>>(emptyList())
        private set
    var routeDemoProgress by mutableStateOf(0)
        private set
    var isRouteDemoRunning by mutableStateOf(false)
        private set
    var routeDemoStatus by mutableStateOf("No route demo loaded")
        private set
    var isNavigating by mutableStateOf(false)
        private set
    var nextTurn by mutableStateOf("Ready")
        private set
    var distanceToTurn by mutableStateOf(0f)
        private set
    var routeLoaded by mutableStateOf(false)
        private set
    var distanceRemainingMeters by mutableStateOf(0)
        private set
    var etaMinutes by mutableStateOf(0)
        private set
    var recenterSignal by mutableStateOf(0)
        private set
    var isRerouting by mutableStateOf(false)
        private set
    var offRouteDistanceMeters by mutableStateOf(0f)
        private set

    private var currentSteps: List<OsrmStep> = emptyList()
    private var triggeredSteps: MutableSet<Int> = mutableSetOf()
    private var lockedCommandStep: Int = -1
    private var lastSentCommand: String? = null
    private var lastSentStepIndex: Int = -1
    private var currentRouteStartLat: Double? = null
    private var currentRouteStartLon: Double? = null
    private var currentRouteDestLat: Double? = null
    private var currentRouteDestLon: Double? = null
    private var rerouteInFlight: Boolean = false
    private var lastRerouteEpochMs: Long = 0L
    private var lastProcessedLocation: Location? = null
    private var lastProcessedAtMs: Long = 0L
    private var offRouteConsecutiveTicks: Int = 0
    private var smoothedDistanceToTurn: Float? = null
    private var smoothedOffRouteDistance: Float? = null
    private var lastOffRouteCheckAtMs: Long = 0L
    private var lastRerouteOriginLocation: Location? = null

    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(application)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation ?: return
            
            latitude = location.latitude
            longitude = location.longitude
            speed = (location.speed * 3.6).toDouble() // m/s to km/h
            heading = if (location.hasBearing()) location.bearing else heading

            Log.d(TAG, "GPS update lat=$latitude lon=$longitude speed=${"%.2f".format(speed)} heading=$heading")

            processNavigationTick(location)
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateDistanceMeters(1f)
            .build()
            
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        isConnected = true
        Log.d(TAG, "Location updates started (1s interval)")
    }

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    fun fetchRoute(destLat: Double, destLon: Double) {
        if (latitude == 0.0 || longitude == 0.0) {
            errorMessage = "Current location not available"
            return
        }

        requestRoute(
            originLat = latitude,
            originLon = longitude,
            destLat = destLat,
            destLon = destLon,
            isReroute = false
        )
    }

    private fun requestRoute(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        isReroute: Boolean
    ) {

        if (!isReroute) {
            currentStepIndex = 0
            totalSteps = 0
            nextInstruction = "Calculating route..."
            distanceToNextTurnMeters = 0f
            triggeredSteps.clear()
            lockedCommandStep = -1
            upcomingManeuverPoint = null
            routeDemoSteps = emptyList()
            routeDemoProgress = 0
            isRouteDemoRunning = false
            routeDemoStatus = "Calculating route demo..."
            routeLoaded = false
            distanceRemainingMeters = 0
            etaMinutes = 0
            nextTurn = nextInstruction
            distanceToTurn = 0f
            offRouteConsecutiveTicks = 0
            smoothedDistanceToTurn = null
            smoothedOffRouteDistance = null
        } else {
            routeDemoStatus = "Rerouting route demo..."
        }

        currentRouteStartLat = originLat
        currentRouteStartLon = originLon
        currentRouteDestLat = destLat
        currentRouteDestLon = destLon
        if (isReroute) {
            isRerouting = true
        } else {
            isNavigating = false
        }
        lastSentStepIndex = -1
        
        // Ensure format is longitude,latitude
        val coords = "$originLon,$originLat;$destLon,$destLat"
        
        RoutingClient.api.getRoute(coords).enqueue(object : Callback<OsrmResponse> {
            override fun onResponse(call: Call<OsrmResponse>, response: Response<OsrmResponse>) {
                if (response.isSuccessful && response.body()?.code == "Ok") {
                    val route = response.body()?.routes?.firstOrNull()
                    if (route != null) {
                        // 1. Plot the polyline
                        val geoPoints = route.geometry?.coordinates?.map { coord ->
                            // GeoJSON arrays are [longitude, latitude]
                            GeoPoint(coord[1], coord[0])
                        } ?: emptyList()
                        
                        routePolyline = geoPoints
                        
                        // 2. Extract steps for Turn-By-Turn
                        val allSteps = route.legs?.firstOrNull()?.steps ?: emptyList()
                        
                        // Filter out empty steps without a defined maneuver location
                        currentSteps = allSteps.filter { step -> 
                            step.maneuver?.location != null
                        }
                        totalSteps = currentSteps.size
                        
                        currentStepIndex = currentSteps.indexOfFirst {
                            it.maneuver?.type?.lowercase() != "depart"
                        }.let { if (it == -1) 0 else it }

                        updateUpcomingManeuverMarker()
                        routeDemoSteps = buildRouteDemoSteps(currentSteps)
                        routeDemoProgress = 0
                        routeDemoStatus = if (routeDemoSteps.isEmpty()) {
                            "No maneuver steps available for demo"
                        } else {
                            "Loaded ${routeDemoSteps.size} route directions for demo"
                        }
                        routeLoaded = routePolyline.isNotEmpty() && currentSteps.isNotEmpty()
                        if (isReroute) {
                            routeDemoStatus = "Reroute complete: ${routeDemoSteps.size} directions loaded"
                        }
                        offRouteConsecutiveTicks = 0
                        smoothedDistanceToTurn = null
                        smoothedOffRouteDistance = null
                        refreshCompactNavMetrics()
                        errorMessage = null
                        
                        updateNextInstruction(distance = 0f)
                        Log.d(TAG, "Route ready. steps=$totalSteps polylinePoints=${routePolyline.size}")
                    }
                } else {
                    errorMessage = "Failed to fetch route. Code: ${response.code()}"
                    routeDemoStatus = "Failed to load route demo"
                    if (!isReroute) {
                        routeLoaded = false
                    }
                }
                rerouteInFlight = false
                isRerouting = false
            }

            override fun onFailure(call: Call<OsrmResponse>, t: Throwable) {
                errorMessage = "API Error: ${t.message}"
                routeDemoStatus = "Failed to load route demo"
                if (!isReroute) {
                    routeLoaded = false
                }
                rerouteInFlight = false
                isRerouting = false
            }
        })
    }

    private fun buildRouteDemoSteps(steps: List<OsrmStep>): List<RouteDemoStep> {
        return steps.mapIndexed { index, step ->
            val type = step.maneuver?.type?.uppercase().orEmpty().ifBlank { "STEP" }
            val modifier = step.maneuver?.modifier?.uppercase().orEmpty()
            val roadName = step.name.orEmpty()
            val command = mapStepToCommand(step)
            val baseLabel = if (modifier.isNotBlank()) "$type $modifier" else type
            val label = if (roadName.isNotBlank()) {
                "${index + 1}. $baseLabel on $roadName"
            } else {
                "${index + 1}. $baseLabel"
            }

            val distanceMeters = step.distance ?: 20.0
            val delayMillis = ((distanceMeters / 6.0) * 1000).toLong().coerceIn(1200L, 4500L)

            RouteDemoStep(
                label = label,
                command = command,
                delayMillis = delayMillis,
                distanceMeters = distanceMeters
            )
        }
    }

    fun runLoadedRouteDemo() {
        if (isRouteDemoRunning) return
        if (routeDemoSteps.isEmpty()) {
            errorMessage = "No route demo loaded. Calculate a destination route first."
            routeDemoStatus = "No route demo loaded"
            return
        }

        viewModelScope.launch {
            isRouteDemoRunning = true
            routeDemoProgress = 0
            routeDemoStatus = "Running route demo..."

            try {
                routeDemoSteps.forEachIndexed { index, step ->
                    routeDemoProgress = index + 1
                    routeDemoStatus = "Sending ${index + 1}/${routeDemoSteps.size}: ${step.command}"
                    sendNavigationCommandToFirebase(step.command, stepIndex = index, allowDuplicate = true)
                    delay(step.delayMillis)
                }
                routeDemoStatus = "Route demo complete"
            } finally {
                isRouteDemoRunning = false
            }
        }
    }

    fun startNavigationMode() {
        if (!routeLoaded) {
            errorMessage = "Load a route first"
            return
        }
        isNavigating = true
        refreshCompactNavMetrics()
    }

    fun stopNavigationMode() {
        isNavigating = false
    }

    fun recenterOnUser() {
        recenterSignal++
    }

    private fun processNavigationTick(currentLocation: Location) {
        val now = System.currentTimeMillis()
        val previous = lastProcessedLocation
        if (previous != null) {
            val movement = previous.distanceTo(currentLocation)
            val elapsed = now - lastProcessedAtMs
            if (movement < MIN_MOVEMENT_METERS && elapsed < MIN_PROCESS_INTERVAL_MS) {
                return
            }
        }

        lastProcessedLocation = Location(currentLocation)
        lastProcessedAtMs = now

        if (!routeLoaded || currentSteps.isEmpty()) return

        recoverStepIfMissedTurn(currentLocation)
        checkManeuvers(currentLocation)

        if (isNavigating) {
            if ((now - lastOffRouteCheckAtMs) >= OFF_ROUTE_CHECK_INTERVAL_MS) {
                lastOffRouteCheckAtMs = now
                checkOffRouteAndRerouteIfNeeded(currentLocation, now)
            }
        }
    }

    private fun recoverStepIfMissedTurn(currentLocation: Location) {
        if (currentStepIndex !in currentSteps.indices) return

        val lookAheadEnd = (currentStepIndex + 3).coerceAtMost(currentSteps.lastIndex)
        var bestIndex = currentStepIndex
        var bestDistance = Float.MAX_VALUE

        for (i in currentStepIndex..lookAheadEnd) {
            val maneuverLoc = currentSteps[i].maneuver?.location ?: continue
            if (maneuverLoc.size < 2) continue

            val stepLocation = Location("").apply {
                longitude = maneuverLoc[0]
                latitude = maneuverLoc[1]
            }
            val d = currentLocation.distanceTo(stepLocation)
            if (d < bestDistance) {
                bestDistance = d
                bestIndex = i
            }
        }

        if (bestIndex > currentStepIndex && bestDistance < STEP_RECOVERY_DISTANCE_METERS) {
            currentStepIndex = bestIndex
            updateUpcomingManeuverMarker()
            Log.d(TAG, "Recovered missed turn. Jumped to step $currentStepIndex")
        }
    }

    private fun checkOffRouteAndRerouteIfNeeded(currentLocation: Location, nowMs: Long) {
        if (routePolyline.size < 2) return

        val rawDistance = distanceToPolylineMeters(currentLocation, routePolyline).toFloat()
        smoothedOffRouteDistance = if (smoothedOffRouteDistance == null) {
            rawDistance
        } else {
            smoothedOffRouteDistance!! + DISTANCE_SMOOTHING_ALPHA * (rawDistance - smoothedOffRouteDistance!!)
        }

        val distance = smoothedOffRouteDistance ?: rawDistance
        if (kotlin.math.abs(distance - offRouteDistanceMeters) >= 1f) {
            offRouteDistanceMeters = distance
        }

        offRouteConsecutiveTicks = when {
            distance > OFF_ROUTE_THRESHOLD_METERS -> (offRouteConsecutiveTicks + 1).coerceAtMost(OFF_ROUTE_CONFIRM_TICKS + 2)
            distance < OFF_ROUTE_EXIT_THRESHOLD_METERS -> 0
            else -> offRouteConsecutiveTicks
        }

        val canReroute = !rerouteInFlight && (nowMs - lastRerouteEpochMs) > REROUTE_COOLDOWN_MS
        val originShiftEnough = lastRerouteOriginLocation?.distanceTo(currentLocation)?.let {
            it >= MIN_REROUTE_ORIGIN_SHIFT_METERS
        } ?: true

        if (offRouteConsecutiveTicks >= OFF_ROUTE_CONFIRM_TICKS && canReroute && originShiftEnough) {
            val destLat = currentRouteDestLat
            val destLon = currentRouteDestLon
            if (destLat != null && destLon != null) {
                rerouteInFlight = true
                lastRerouteEpochMs = nowMs
                lastRerouteOriginLocation = Location(currentLocation)
                Log.w(TAG, "Off-route detected (${distance.toInt()}m). Triggering reroute...")
                requestRoute(
                    originLat = currentLocation.latitude,
                    originLon = currentLocation.longitude,
                    destLat = destLat,
                    destLon = destLon,
                    isReroute = true
                )
            }
        }
    }

    private fun distanceToPolylineMeters(currentLocation: Location, polyline: List<GeoPoint>): Double {
        if (polyline.size < 2) return Double.MAX_VALUE

        val lat0 = Math.toRadians(currentLocation.latitude)
        fun project(lat: Double, lon: Double): Pair<Double, Double> {
            val x = lon * 111320.0 * kotlin.math.cos(lat0)
            val y = lat * 110540.0
            return x to y
        }

        val (px, py) = project(currentLocation.latitude, currentLocation.longitude)
        var minDistance = Double.MAX_VALUE

        for (i in 0 until polyline.lastIndex) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val (ax, ay) = project(a.latitude, a.longitude)
            val (bx, by) = project(b.latitude, b.longitude)

            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)

            val projX = ax + t * dx
            val projY = ay + t * dy
            val dist = kotlin.math.hypot(px - projX, py - projY)
            if (dist < minDistance) {
                minDistance = dist
            }
        }

        return minDistance
    }

    fun getRouteDemoJson(): String {
        val stepsArray = JSONArray()
        routeDemoSteps.forEachIndexed { index, step ->
            stepsArray.put(
                JSONObject()
                    .put("index", index + 1)
                    .put("label", step.label)
                    .put("command", step.command)
                    .put("delayMillis", step.delayMillis)
                    .put("distanceMeters", step.distanceMeters)
            )
        }

        val payload = JSONObject()
            .put("generatedAtEpochMs", System.currentTimeMillis())
            .put("source", "Ebike route demo")
            .put("start", JSONObject()
                .put("lat", currentRouteStartLat)
                .put("lon", currentRouteStartLon)
            )
            .put("destination", JSONObject()
                .put("lat", currentRouteDestLat)
                .put("lon", currentRouteDestLon)
            )
            .put("stepCount", routeDemoSteps.size)
            .put("steps", stepsArray)

        return payload.toString(2)
    }

    private fun checkManeuvers(currentLocation: Location) {
        if (currentStepIndex < 0 || currentStepIndex >= currentSteps.size) return
        
        val nextStep = currentSteps[currentStepIndex]
        val maneuverLoc = nextStep.maneuver?.location ?: return
        
        // OSRM Location: [longitude, latitude]
        val maneuverLocation = Location("").apply {
            longitude = maneuverLoc[0]
            latitude = maneuverLoc[1]
        }
        
        // Calculate dynamic distance to the turn maneuver
        val rawDistanceToTurn = currentLocation.distanceTo(maneuverLocation)
        smoothedDistanceToTurn = if (smoothedDistanceToTurn == null) {
            rawDistanceToTurn
        } else {
            smoothedDistanceToTurn!! + DISTANCE_SMOOTHING_ALPHA * (rawDistanceToTurn - smoothedDistanceToTurn!!)
        }

        val distanceToTurn = smoothedDistanceToTurn ?: rawDistanceToTurn
        if (kotlin.math.abs(distanceToTurn - distanceToNextTurnMeters) >= 1f) {
            distanceToNextTurnMeters = distanceToTurn
            updateNextInstruction(distanceToTurn)
            refreshCompactNavMetrics()
        }
        Log.d(TAG, "Distance to step[$currentStepIndex]: ${"%.2f".format(rawDistanceToTurn)}m (smoothed=${"%.2f".format(distanceToTurn)})")

        val triggerDistance = kotlin.math.min(rawDistanceToTurn, distanceToTurn)

        if (isNavigating &&
            triggerDistance < TURN_TRIGGER_METERS &&
            currentStepIndex !in triggeredSteps &&
            currentStepIndex != lockedCommandStep
        ) {
            val command = mapStepToCommand(nextStep)
            triggeredSteps.add(currentStepIndex)
            lockedCommandStep = currentStepIndex
            sendNavigationCommandToFirebase(command, stepIndex = currentStepIndex)
            
            advanceStep()
        }
    }

    private fun advanceStep() {
        currentStepIndex++
        updateUpcomingManeuverMarker()
        if (currentStepIndex < currentSteps.size) {
            updateNextInstruction(distance = 0f)
            refreshCompactNavMetrics()
            Log.d(TAG, "Moved to step $currentStepIndex")
        } else {
            nextInstruction = "Arrived at destination"
            distanceToNextTurnMeters = 0f
            refreshCompactNavMetrics()
            Log.d(TAG, "Navigation complete")
        }
    }

    private fun updateNextInstruction(distance: Float) {
        val computedInstruction = when {
            currentStepIndex >= 0 && currentStepIndex < currentSteps.size -> {
                val step = currentSteps[currentStepIndex]
                val mod = mapStepToCommand(step)
                if (distance > 0) {
                    "$mod in ${distance.toInt()}m"
                } else {
                    "$mod ahead"
                }
            }
            currentStepIndex >= currentSteps.size -> "Arrived at destination"
            else -> nextInstruction
        }

        if (computedInstruction != nextInstruction) {
            nextInstruction = computedInstruction
        }
        if (nextTurn != nextInstruction) {
            nextTurn = nextInstruction
        }
        distanceToTurn = distanceToNextTurnMeters
    }

    private fun refreshCompactNavMetrics() {
        val remaining = if (currentStepIndex in currentSteps.indices) {
            currentSteps.subList(currentStepIndex, currentSteps.size).sumOf { (it.distance ?: 0.0) }
        } else {
            0.0
        }

        val remainingInt = remaining.toInt()
        if (remainingInt != distanceRemainingMeters) {
            distanceRemainingMeters = remainingInt
        }

        val eta = (remaining / 8.33 / 60.0).toInt().coerceAtLeast(0) // ~30 km/h average moving speed
        if (eta != etaMinutes) {
            etaMinutes = eta
        }

        distanceToTurn = distanceToNextTurnMeters
        if (nextTurn != nextInstruction) {
            nextTurn = nextInstruction
        }
    }

    private fun updateUpcomingManeuverMarker() {
        if (currentStepIndex in currentSteps.indices) {
            val maneuverLoc = currentSteps[currentStepIndex].maneuver?.location
            upcomingManeuverPoint = if (maneuverLoc != null && maneuverLoc.size >= 2) {
                GeoPoint(maneuverLoc[1], maneuverLoc[0])
            } else {
                null
            }
        } else {
            upcomingManeuverPoint = null
        }
    }

    private fun mapStepToCommand(step: OsrmStep): String {
        val type = step.maneuver?.type?.lowercase().orEmpty()
        val modifier = step.maneuver?.modifier?.lowercase().orEmpty()
        val normalized = "$type-$modifier"

        return when {
            normalized.contains("turn-left") || modifier == "left" -> "LEFT"
            normalized.contains("turn-right") || modifier == "right" -> "RIGHT"
            normalized.contains("uturn") || modifier.contains("uturn") -> "UTURN"
            else -> "STRAIGHT"
        }
    }

    private fun sendNavigationCommandToFirebase(
        command: String,
        stepIndex: Int,
        allowDuplicate: Boolean = false
    ) {
        if (!allowDuplicate && command == lastSentCommand && stepIndex == lastSentStepIndex) {
            Log.d("NAVIGATION", "Skipping duplicate command=$command step=$stepIndex")
            return
        }

        lastSentCommand = command
        lastSentStepIndex = stepIndex
        Log.d("NAVIGATION", "Sending direction to Firebase: $command")

        firebaseCommandSender.sendCommand(command) { success, errorMessage ->
            if (success) {
                Log.d("NAVIGATION", "Firebase command success: $command")
            } else {
                Log.e("NAVIGATION", "Firebase command failed: $errorMessage")
                viewModelScope.launch {
                    this@EbikeViewModel.errorMessage = "Failed to send $command to Firebase: $errorMessage"
                }
            }
        }
    }

    fun setFollowUser(enabled: Boolean) {
        isFollowingUser = enabled
    }

    fun setRotateMap(enabled: Boolean) {
        isRotatingMap = enabled
    }
}

data class RouteDemoStep(
    val label: String,
    val command: String,
    val delayMillis: Long,
    val distanceMeters: Double
)