package com.example.ebike

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Sends HTTP requests to the ESP32 IP address.
 */
interface Esp32Api {
    @POST("command")
    fun sendDirectionCommand(@Body command: DirectionCommandRequest): Call<ResponseBody>
}

data class DirectionCommandRequest(
    val direction: String
)

object Esp32Client {
    // Default ESP32 SoftAP IP.
    private const val BASE_URL = "http://192.168.4.1/"

    val api: Esp32Api by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Esp32Api::class.java)
    }
}
