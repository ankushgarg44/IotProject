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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun HardwareTestControls(viewModel: EbikeViewModel) {
    val context = LocalContext.current
    val firebaseCommandSender = remember { FirebaseCommandSender() }
    var expanded by remember { mutableStateOf(false) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Hardware Test Controls", color = Color(0xFF1A1A1A))
                Button(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Show")
                }
            }

            if (expanded) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = viewModel.routeDemoSteps.isNotEmpty() && !viewModel.isRouteDemoRunning,
                    onClick = {
                        viewModel.runLoadedRouteDemo()
                    }
                ) {
                    Text(if (viewModel.isRouteDemoRunning) "RUNNING LOADED ROUTE DEMO" else "RUN LOADED ROUTE DEMO")
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = viewModel.routeDemoSteps.isNotEmpty() && !viewModel.isRouteDemoRunning,
                    onClick = {
                        if (viewModel.routeDemoSteps.isEmpty()) {
                            Toast.makeText(context, "No route demo loaded", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val json = viewModel.getRouteDemoJson()
                        val uri = RouteDemoExporter.exportJsonToDownloads(context, json)
                        if (uri == null) {
                            Toast.makeText(context, "Failed to export JSON", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Route demo JSON exported", Toast.LENGTH_SHORT).show()
                            RouteDemoExporter.shareJson(context, uri)
                        }
                    }
                ) {
                    Text("EXPORT ROUTE DEMO JSON")
                }

                Text(
                    text = "${viewModel.routeDemoStatus} (${viewModel.routeDemoProgress}/${viewModel.routeDemoSteps.size})",
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
}
