package com.example.ebike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun NavigationControls(viewModel: EbikeViewModel) {
    var destinationText by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(text = "Destination", color = Color(0xFF1A1A1A))
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = destinationText,
                onValueChange = {
                    destinationText = it
                    parseError = null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("lat,lon e.g. 28.6139,77.2090") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val parts = destinationText.split(",")
                    if (parts.size != 2) {
                        parseError = "Use format: lat,lon"
                        return@Button
                    }

                    val destinationLat = parts[0].trim().toDoubleOrNull()
                    val destinationLon = parts[1].trim().toDoubleOrNull()
                    if (destinationLat == null || destinationLon == null) {
                        parseError = "Invalid coordinates"
                        return@Button
                    }

                    viewModel.fetchRoute(destinationLat, destinationLon)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Navigation")
            }

            if (parseError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = parseError ?: "", color = Color(0xFFD32F2F))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Follow camera")
                Switch(
                    checked = viewModel.isFollowingUser,
                    onCheckedChange = { viewModel.setFollowUser(it) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Rotate map")
                Switch(
                    checked = viewModel.isRotatingMap,
                    onCheckedChange = { viewModel.setRotateMap(it) }
                )
            }
        }
    }
}