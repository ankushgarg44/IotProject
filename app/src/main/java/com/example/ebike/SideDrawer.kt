package com.example.ebike

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SideDrawer(viewModel: EbikeViewModel) {
    val context = LocalContext.current
    val firebaseCommandSender = remember { FirebaseCommandSender() }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Menu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        HorizontalDivider()

        Text("Map Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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

        HorizontalDivider()
        Text("Hardware Test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(modifier = Modifier.weight(1f), onClick = {
                firebaseCommandSender.sendCommand("LEFT") { success, errorMessage ->
                    Toast.makeText(
                        context,
                        if (success) "LEFT sent" else "Failed: ${errorMessage ?: "network error"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }) { Text("TEST LEFT") }

            Button(modifier = Modifier.weight(1f), onClick = {
                firebaseCommandSender.sendCommand("RIGHT") { success, errorMessage ->
                    Toast.makeText(
                        context,
                        if (success) "RIGHT sent" else "Failed: ${errorMessage ?: "network error"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }) { Text("TEST RIGHT") }
        }

        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            firebaseCommandSender.sendCommand("UTURN") { success, errorMessage ->
                Toast.makeText(
                    context,
                    if (success) "UTURN sent" else "Failed: ${errorMessage ?: "network error"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }) { Text("TEST UTURN") }

        HorizontalDivider()
        Text("Firebase Route Demo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = viewModel.routeDemoSteps.isNotEmpty() && !viewModel.isRouteDemoRunning,
            onClick = { viewModel.runLoadedRouteDemo() }
        ) {
            Text(if (viewModel.isRouteDemoRunning) "RUNNING ROUTE DEMO" else "RUN ROUTE DEMO")
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
                    RouteDemoExporter.shareJson(context, uri)
                }
            }
        ) { Text("EXPORT ROUTE DEMO JSON") }

        HorizontalDivider()
        Text("Debug Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("GPS: ${viewModel.latitude}, ${viewModel.longitude}")
        Text("Route loaded: ${viewModel.routeLoaded}")
        Text("Next turn: ${viewModel.nextTurn}")
        Text("Distance to turn: ${viewModel.distanceToTurn.toInt()}m")
        Text("Demo status: ${viewModel.routeDemoStatus}")
    }
}
