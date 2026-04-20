with open("app/src/main/java/com/example/ebike/HomeScreen.kt", "r") as f:
    code = f.read()

# 1. Imports
imports = """package com.example.ebike

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlinx.coroutines.delay

enum class Screen {
    Home, Map, Favorites, Profile
}
"""
home_idx = code.find("@Composable\nfun HomeScreen()")
code = imports + "\n" + code[home_idx:]

# 2. HomeScreen Signature & Logic
old_home = """@Composable
fun HomeScreen() {
    var location by remember { mutableStateOf(LatLng(0.0, 0.0)) }
    var speed by remember { mutableStateOf(0.0) }
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var drawerState by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
            while (true) {
                // getLocation and speed in ViewModel
                var  result : LocationData? = getLocation()
                if (result != null) {
                    location = LatLng(result.latitude, result.longitude)
                    speed = result.speed  // ✅ Set speed

                    println("Location: $location | Speed: $speed")
                    println("Location: $location")
                }else {
                    println("Failed to get location")
                }
                delay (1000)
            }
    }"""
new_home = """@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(viewModel: EbikeViewModel = viewModel()) {
    val location = GeoPoint(viewModel.latitude, viewModel.longitude)
    val speed = viewModel.speed
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var drawerState by remember { mutableStateOf(false) }

    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        locationPermissionState.launchPermissionRequest()
    }
    
    LaunchedEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            viewModel.startLocationUpdates()
        }
    }"""
code = code.replace(old_home, new_home)

code = code.replace("MapScreen(location)", "MapScreen(viewModel)")
code = code.replace("SimplifiedMapScreen(location)", "SimplifiedMapScreen(location)")

code = code.replace("""if (location.latitude == 0.0 && location.longitude == 0.0) "Set Location" else String.format("%.4f, %.4f", location.latitude, location.longitude)""", """String.format("%.4f, %.4f", location.latitude, location.longitude)""")
code = code.replace("""if (location.latitude == 0.0 && location.longitude == 0.0) "Not available" else String.format("%f, %f", location.latitude, location.longitude)""", """String.format("%.4f, %.4f", location.latitude, location.longitude)""")


# 3. MapScreen Replacement
start_map = code.find("@Composable\nfun MapScreen(location: LatLng)")
end_map = code.find("@Composable\nfun FavoritesContent")
old_map = code[start_map:end_map]

new_map = """@Composable
fun MapScreen(viewModel: EbikeViewModel) {
    val location = GeoPoint(viewModel.latitude, viewModel.longitude)
    val context = LocalContext.current
    var drawerState by remember { mutableStateOf(false) }
    val mapViewState = remember { MapView(context) }
    val markerState = remember { Marker(mapViewState) }
    val polylineState = remember { Polyline(mapViewState) }
    var destinationText by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        mapViewState.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(location)
        }
        
        markerState.apply {
            position = location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Current Location"
        }
        
        polylineState.apply {
            outlinePaint.color = android.graphics.Color.BLUE
            outlinePaint.strokeWidth = 10f
        }
        
        mapViewState.overlays.add(polylineState)
        mapViewState.overlays.add(markerState)
        
        onDispose { mapViewState.onDetach() }
    }

    LaunchedEffect(location.latitude, location.longitude) {
        if (location.latitude != 0.0 || location.longitude != 0.0) {
            mapViewState.controller.animateTo(location)
            markerState.position = location
            markerState.title = "Ebike: ${String.format("%.4f, %.4f", location.latitude, location.longitude)}"
            mapViewState.invalidate()
        }
    }
    
    LaunchedEffect(viewModel.routePolyline) {
        polylineState.setPoints(viewModel.routePolyline)
        mapViewState.invalidate()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapViewState },
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.onResume() }
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = destinationText,
                        onValueChange = { destinationText = it },
                        placeholder = { Text("lon,lat (e.g. -122.4,37.7)", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFFF8F9FA),
                            focusedContainerColor = Color(0xFFF8F9FA),
                            unfocusedBorderColor = Color(0xFFE9ECEF),
                            focusedBorderColor = Color(0xFFE9ECEF),
                            cursorColor = Color(0xFF55A8FF)
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF55A8FF))
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            val parts = destinationText.split(",")
                            if (parts.size == 2) {
                                val dLon = parts[0].trim().toDoubleOrNull()
                                val dLat = parts[1].trim().toDoubleOrNull()
                                if (dLon != null && dLat != null) {
                                    viewModel.fetchRoute(dLat, dLon)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Navigation")
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = { drawerState = true },
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFF8F9FA))
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.Black)
                }
            }
        }

        AnimatedVisibility(
            visible = drawerState,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(400)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(400)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(250.dp)
                    .background(Color.White, shape = MaterialTheme.shapes.large)
                    .border(1.dp, Color(0xFFE9ECEF), MaterialTheme.shapes.large)
                    .padding(top = 24.dp, bottom = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(50.dp).clip(CircleShape).background(Color(0xFF0D47A1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ev_avatar),
                                contentDescription = "Profile Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Rishi Bhardwaj", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                            Text("Premium Member", color = Color(0xFF6C757D), fontSize = 13.sp)
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE9ECEF))
                    
                    DrawerMenuItem(iconRes = R.drawable.ic_routes, label = "Routes", selected = true, onClick = { drawerState = false })
                    DrawerMenuItem(iconRes = R.drawable.ic_home, label = "Dashboard", onClick = { drawerState = false })
                    DrawerMenuItem(iconRes = R.drawable.ic_stats, label = "Stats", onClick = { drawerState = false })
                    DrawerMenuItem(iconRes = R.drawable.ic_firmware, label = "Firmware Update", onClick = { drawerState = false })
                    DrawerMenuItem(iconRes = R.drawable.ic_settings, label = "Settings", onClick = { drawerState = false })
                }
            }
        }

        if (viewModel.routePolyline.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
                    .background(Color(0xFF0D47A1).copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = viewModel.nextInstruction,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }
        
        if (drawerState) {
            Box(
                modifier = Modifier.fillMaxSize().clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { drawerState = false }
            )
        }
    }
}
"""
code = code.replace(old_map, new_map)

# 4. Replace SimplifiedMapScreen at the bottom
start_simp = code.find("@Composable\nfun SimplifiedMapScreen(location: LatLng)")
old_simp = code[start_map:] if start_simp == -1 else code[start_simp:]

new_simp = """@Composable
fun SimplifiedMapScreen(location: GeoPoint) {
    val context = LocalContext.current
    val mapViewState = remember { MapView(context) }
    val markerState = remember { Marker(mapViewState) }

    DisposableEffect(Unit) {
        mapViewState.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            controller.setZoom(15.0)
            controller.setCenter(location)
        }
        markerState.apply {
            position = location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Ebike Location"
        }
        mapViewState.overlays.add(markerState)
        onDispose { mapViewState.onDetach() }
    }

    LaunchedEffect(location.latitude, location.longitude) {
        if (location.latitude != 0.0 || location.longitude != 0.0) {
            mapViewState.controller.animateTo(location)
            markerState.position = location
            mapViewState.invalidate()
        }
    }

    AndroidView(
        factory = { mapViewState },
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
        update = { view -> view.onResume() }
    )
}
"""
code = code[:start_simp] + new_simp

with open("app/src/main/java/com/example/ebike/HomeScreen.kt", "w") as f:
    f.write(code)

print("Rewrite Complete!")
