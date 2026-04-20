package com.example.ebike

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ============================================================
// OSRM API Response Models
// ============================================================

data class OsrmResponse(
    val code: String,
    val routes: List<OsrmRoute>?
)

data class OsrmRoute(
    val geometry: OsrmGeoJson?,
    val legs: List<OsrmLeg>?
)

data class OsrmGeoJson(
    val type: String,
    val coordinates: List<List<Double>> // Array of [longitude, latitude]
)

data class OsrmLeg(
    val steps: List<OsrmStep>?,
    val distance: Double?,
    val summary: String?
)

data class OsrmStep(
    val distance: Double?,
    val maneuver: OsrmManeuver?,
    val name: String?
)

data class OsrmManeuver(
    val type: String?, // e.g., "turn", "roundabout", "depart", "arrive"
    val modifier: String?, // "left", "right", "straight", "uturn"
    val location: List<Double>? // [longitude, latitude]
)

// ============================================================
// Retrofit Client
// ============================================================

interface OsrmApi {
    @GET("route/v1/driving/{coordinates}")
    fun getRoute(
        @Path("coordinates", encoded = true) coordinates: String, // format: lon1,lat1;lon2,lat2
        @Query("steps") steps: Boolean = true,
        @Query("geometries") geometries: String = "geojson",
        @Query("overview") overview: String = "full"
    ): Call<OsrmResponse>
}

object RoutingClient {
    private const val BASE_URL = "http://router.project-osrm.org/"

    private val client = OkHttpClient.Builder().build()

    val api: OsrmApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OsrmApi::class.java)
    }
}
