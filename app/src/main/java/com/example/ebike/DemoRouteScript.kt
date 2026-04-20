package com.example.ebike

data class DemoRouteStep(
    val label: String,
    val command: String,
    val delayMillis: Long
)

object DemoRouteScript {
    val steps = listOf(
        DemoRouteStep("Start straight", "STRAIGHT", 1500),
        DemoRouteStep("First left turn", "LEFT", 2000),
        DemoRouteStep("Continue straight", "STRAIGHT", 1500),
        DemoRouteStep("Right turn", "RIGHT", 2000),
        DemoRouteStep("Short straight", "STRAIGHT", 1500),
        DemoRouteStep("U-turn checkpoint", "UTURN", 2500)
    )
}