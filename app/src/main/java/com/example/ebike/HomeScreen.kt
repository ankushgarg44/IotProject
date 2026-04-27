package com.example.ebike

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.DrawerValue

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(viewModel: EbikeViewModel = viewModel()) {
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var destinationText by remember { mutableStateOf("") }
    var bottomSheetExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        locationPermissionState.launchPermissionRequest()
    }

    LaunchedEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            viewModel.startLocationUpdates()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                SideDrawer(viewModel = viewModel)
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            MapScreen(viewModel = viewModel)

            IconButton(
                onClick = { scope.launch { drawerState.open() } },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = 6.dp)
                    .align(Alignment.TopStart)
            ) {
                Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu", tint = Color.White)
            }

            AnimatedVisibility(
                visible = !viewModel.isNavigating,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            ) {
                SearchBar(
                    value = destinationText,
                    onValueChange = { destinationText = it },
                    onSearch = {
                        val parts = destinationText.split(",")
                        if (parts.size != 2) {
                            viewModel.stopNavigationMode()
                            return@SearchBar
                        }

                        val destinationLat = parts[0].trim().toDoubleOrNull()
                        val destinationLon = parts[1].trim().toDoubleOrNull()
                        if (destinationLat != null && destinationLon != null) {
                            viewModel.fetchRoute(destinationLat, destinationLon)
                        }
                    },
                    onClear = {
                        destinationText = ""
                    }
                )
            }

            AnimatedVisibility(
                visible = viewModel.isNavigating,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 14.dp, end = 14.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "${viewModel.distanceRemainingMeters}m",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF132238),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "ETA ${viewModel.etaMinutes} min",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF3A5577)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = viewModel.isNavigating,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 80.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.88f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Turn indicator",
                        tint = Color(0xFF0B57D0),
                        modifier = Modifier
                            .padding(22.dp)
                            .height(42.dp)
                            .rotate(turnRotation(viewModel.nextTurn))
                    )
                }
            }

            FloatingControls(
                routeLoaded = viewModel.routeLoaded,
                isNavigating = viewModel.isNavigating,
                onCenterClick = { viewModel.recenterOnUser() },
                onStartNavigationClick = { viewModel.startNavigationMode() },
                modifier = Modifier.align(Alignment.BottomEnd)
            )

            AnimatedVisibility(
                visible = viewModel.isNavigating,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                NavigationBottomSheet(
                    nextTurn = viewModel.nextTurn,
                    distanceToTurn = viewModel.distanceToTurn,
                    distanceRemainingMeters = viewModel.distanceRemainingMeters,
                    etaMinutes = viewModel.etaMinutes,
                    currentStep = viewModel.currentStepIndex + 1,
                    totalSteps = viewModel.totalSteps,
                    expanded = bottomSheetExpanded,
                    onToggleExpanded = { bottomSheetExpanded = !bottomSheetExpanded }
                )
            }

            if (!locationPermissionState.status.isGranted) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Location permission is required for navigation.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        TextButton(onClick = { locationPermissionState.launchPermissionRequest() }) {
                            Text("Grant permission")
                        }
                    }
                }
            }
        }
    }
}

private fun turnRotation(instruction: String): Float {
    val normalized = instruction.lowercase()
    return when {
        normalized.contains("left") -> -90f
        normalized.contains("right") -> 90f
        normalized.contains("uturn") -> 180f
        else -> 0f
    }
}
