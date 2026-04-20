with open("app/src/main/java/com/example/ebike/HomeScreen.kt", "r") as f:
    code = f.read()

# 1. Imports
code = code.replace(
    "import org.osmdroid.views.overlay.Marker",
    "import org.osmdroid.views.overlay.Marker\nimport org.osmdroid.views.overlay.Polyline\nimport com.google.accompanist.permissions.ExperimentalPermissionsApi\nimport com.google.accompanist.permissions.isGranted\nimport com.google.accompanist.permissions.rememberPermissionState\nimport androidx.compose.material3.Button"
)

# 2. HomeScreen Signature & Permissions
old_home = """@Composable
fun HomeScreen(viewModel: EbikeViewModel = viewModel()) {
    val location = GeoPoint(viewModel.latitude, viewModel.longitude)
    val speed = viewModel.speed
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var drawerState by remember { mutableStateOf(false) }

    // Initialize osmdroid configuration
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }"""
new_home = """@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(viewModel: EbikeViewModel = viewModel()) {
    val location = GeoPoint(viewModel.latitude, viewModel.longitude)
    val speed = viewModel.speed
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var drawerState by remember { mutableStateOf(false) }

    val locationPermissionState = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)

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

# 3. MapScreen invocation in HomeScreen
code = code.replace("MapScreen(location)", "MapScreen(viewModel)")

# 4. MapScreen replacement
# Find "fun MapScreen(location: GeoPoint) {"
start_map = code.find("fun MapScreen(location: GeoPoint) {")
# Find the next fun declarations to bound the replacement
end_map = code.find("fun SimplifiedMapScreen(location: GeoPoint)")

old_map_full = code[start_map:end_map]

new_map_full = """fun MapScreen(viewModel: EbikeViewModel) {
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
                        placeholder = { Text("lon,lat (-122.4,37.7)", fontSize = 14.sp) },
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

code = code.replace(old_map_full, new_map_full)

with open("app/src/main/java/com/example/ebike/HomeScreen.kt", "w") as f:
    f.write(code)

print("Patched!")
