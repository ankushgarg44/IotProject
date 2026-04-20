package com.example.ebike

import kotlinx.serialization.Serializable

@Serializable
data class LocationData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speed: Double = 0.0
)