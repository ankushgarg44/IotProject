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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.ebike.FirebaseCommandSender

class EbikeViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "EbikeNavigation"
        private const val TURN_TRIGGER_METERS = 25f
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

    private var currentSteps: List<OsrmStep> = emptyList()
    private var triggeredSteps: MutableSet<Int> = mutableSetOf()
    private var lockedCommandStep: Int = -1

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

            checkManeuvers(location)
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
        
        // Ensure format is longitude,latitude
        val coords = "$longitude,$latitude;$destLon,$destLat"
        
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
                        errorMessage = null
                        
                        updateNextInstruction(distance = 0f)
                        Log.d(TAG, "Route ready. steps=$totalSteps polylinePoints=${routePolyline.size}")
                    }
                } else {
                    errorMessage = "Failed to fetch route. Code: ${response.code()}"
                    routeDemoStatus = "Failed to load route demo"
                }
            }

            override fun onFailure(call: Call<OsrmResponse>, t: Throwable) {
                errorMessage = "API Error: ${t.message}"
                routeDemoStatus = "Failed to load route demo"
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
                    sendNavigationCommandToFirebase(step.command)
                    delay(step.delayMillis)
                }
                routeDemoStatus = "Route demo complete"
            } finally {
                isRouteDemoRunning = false
            }
        }
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
        val distanceToTurn = currentLocation.distanceTo(maneuverLocation)
        distanceToNextTurnMeters = distanceToTurn
        Log.d(TAG, "Distance to step[$currentStepIndex]: ${"%.2f".format(distanceToTurn)}m")
        
        updateNextInstruction(distanceToTurn)

        if (distanceToTurn < TURN_TRIGGER_METERS &&
            currentStepIndex !in triggeredSteps &&
            currentStepIndex != lockedCommandStep
        ) {
            val command = mapStepToCommand(nextStep)
            triggeredSteps.add(currentStepIndex)
            lockedCommandStep = currentStepIndex
            sendNavigationCommandToFirebase(command)
            
            advanceStep()
        }
    }

    private fun advanceStep() {
        currentStepIndex++
        updateUpcomingManeuverMarker()
        if (currentStepIndex < currentSteps.size) {
            updateNextInstruction(distance = 0f)
            Log.d(TAG, "Moved to step $currentStepIndex")
        } else {
            nextInstruction = "Arrived at destination"
            distanceToNextTurnMeters = 0f
            Log.d(TAG, "Navigation complete")
        }
    }

    private fun updateNextInstruction(distance: Float) {
        if (currentStepIndex >= 0 && currentStepIndex < currentSteps.size) {
            val step = currentSteps[currentStepIndex]
            val mod = mapStepToCommand(step)
            nextInstruction = if (distance > 0) {
                "$mod in ${distance.toInt()}m"
            } else {
                "$mod ahead"
            }
        } else if (currentStepIndex >= currentSteps.size) {
            nextInstruction = "Arrived at destination"
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

    private fun sendNavigationCommandToFirebase(command: String) {
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