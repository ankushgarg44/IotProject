package com.example.ebike

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HardwareTestControls() {
    val context = LocalContext.current
    val firebaseCommandSender = remember { FirebaseCommandSender() }
    val coroutineScope = rememberCoroutineScope()
    var isRunningDemo by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Hardware Test Controls", color = Color(0xFF1A1A1A))

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunningDemo,
                onClick = {
                    coroutineScope.launch {
                        isRunningDemo = true
                        try {
                            DemoRouteScript.steps.forEachIndexed { index, step ->
                                firebaseCommandSender.sendCommand(step.command) { success, errorMessage ->
                                    val message = if (success) {
                                        "Demo step ${index + 1}: ${step.label} sent"
                                    } else {
                                        "Demo step ${index + 1} failed: ${errorMessage ?: "network error"}"
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                                delay(step.delayMillis)
                            }
                        } finally {
                            isRunningDemo = false
                        }
                    }
                }
            ) {
                Text(if (isRunningDemo) "RUNNING DEMO ROUTE" else "RUN DEMO ROUTE")
            }

            Text(
                text = "Uses Firebase path: bike_commands/direction",
                color = Color(0xFF5A5A5A)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        firebaseCommandSender.sendCommand("LEFT") { success, errorMessage ->
                            if (success) {
                                Toast.makeText(context, "Command sent to ESP32", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Failed to send command: ${errorMessage ?: "network error"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) {
                    Text("TEST LEFT")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        firebaseCommandSender.sendCommand("RIGHT") { success, errorMessage ->
                            if (success) {
                                Toast.makeText(context, "Command sent to ESP32", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Failed to send command: ${errorMessage ?: "network error"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) {
                    Text("TEST RIGHT")
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    firebaseCommandSender.sendCommand("UTURN") { success, errorMessage ->
                        if (success) {
                            Toast.makeText(context, "Command sent to ESP32", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Failed to send command: ${errorMessage ?: "network error"}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            ) {
                Text("TEST UTURN")
            }
        }
    }
}
