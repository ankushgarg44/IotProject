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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class EbikeViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "EbikeNavigation"
        private const val TURN_TRIGGER_METERS = 25f
    }

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
                        errorMessage = null
                        
                        updateNextInstruction(distance = 0f)
                        Log.d(TAG, "Route ready. steps=$totalSteps polylinePoints=${routePolyline.size}")
                    }
                } else {
                    errorMessage = "Failed to fetch route. Code: ${response.code()}"
                }
            }

            override fun onFailure(call: Call<OsrmResponse>, t: Throwable) {
                errorMessage = "API Error: ${t.message}"
            }
        })
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
            sendEsp32CommandWithRetry(command)
            
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

    private fun sendEsp32CommandWithRetry(command: String) {
        Log.d(TAG, "Sending command to ESP32: $command")

        viewModelScope.launch(Dispatchers.IO) {
            var sent = false
            for (attempt in 1..3) {
                try {
                    val response = Esp32Client.api.sendDirectionCommand(
                        DirectionCommandRequest(direction = command)
                    ).execute()
                    if (response.isSuccessful) {
                        Log.d(TAG, "ESP32 command success: $command (attempt $attempt)")
                        sent = true
                        break
                    }
                    Log.w(TAG, "ESP32 command failed HTTP ${response.code()} (attempt $attempt)")
                } catch (e: Exception) {
                    Log.e(TAG, "ESP32 command error (attempt $attempt): ${e.message}")
                }
            }

            if (!sent) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Failed to send command $command to ESP32"
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