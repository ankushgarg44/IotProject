import re

with open("app/src/main/java/com/example/ebike/HomeScreen.kt", "r") as f:
    code = f.read()

# 1. Imports
code = code.replace(
    "import org.osmdroid.views.overlay.Marker",
    "import com.google.accompanist.permissions.ExperimentalPermissionsApi\nimport com.google.accompanist.permissions.isGranted\nimport com.google.accompanist.permissions.rememberPermissionState\nimport org.osmdroid.views.overlay.Marker\nimport org.osmdroid.views.overlay.Polyline\nimport androidx.compose.material3.Button"
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

    // Initialize osmdroid configuration
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

# 3. Component pass
code = code.replace("MapScreen(location)", "MapScreen(viewModel)")

# 4. MapScreen signature & dependencies
old_map_start = """fun MapScreen(location: GeoPoint) {
    val context = LocalContext.current
    var drawerState by remember { mutableStateOf(false) }
    val mapViewState = remember { MapView(context) }
    val markerState = remember { Marker(mapViewState) }

    // Configure and manage MapView lifecycle
    DisposableEffect(Unit) {
        mapViewState.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            // Set initial position
            controller.setCenter(location)
        }
        
        // Configure marker
        markerState.apply {
            position = location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Ebike Location"
        }
        mapViewState.overlays.add(markerState)"""

new_map_start = """fun MapScreen(viewModel: EbikeViewModel) {
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
        mapViewState.overlays.add(markerState)"""

code = code.replace(old_map_start, new_map_start)

# 5. LaunchedEffect Polyline injection
old_map_launch = """    LaunchedEffect(location.latitude, location.longitude) {
        if (location.latitude != 0.0 || location.longitude != 0.0) {
            mapViewState.controller.animateTo(location)
            markerState.position = location
            markerState.title = "Ebike: ${String.format("%.4f, %.4f", location.latitude, location.longitude)}"
            mapViewState.invalidate()
        }
    }"""
new_map_launch = """    LaunchedEffect(location.latitude, location.longitude) {
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
    }"""
code = code.replace(old_map_launch, new_map_launch)

# 6. Map Screen Search Row Replacement
old_search_ui = """                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = { Text("Where to?", fontSize = 18.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),"""
                        
new_search_ui = """                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = destinationText,
                        onValueChange = { destinationText = it },
                        placeholder = { Text("lon,lat (e.g. 77.20,28.61)", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),"""
code = code.replace(old_search_ui, new_search_ui, 1) # Only first occurrence (MapScreen)

old_search_close = """                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF55A8FF)
                            )
                        }
                    )"""
                    
new_search_close = """                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF55A8FF)
                            )
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
                }"""
code = code.replace(old_search_close, new_search_close, 1)

# 7. Add Turn-By-Turn overlay inside MapScreen
old_map_box_end = """        // Overlay to close drawer when clicking outside"""

new_map_box_end = """        // Turn-by-Turn Instruction Overlay
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
        
        // Overlay to close drawer when clicking outside"""
code = code.replace(old_map_box_end, new_map_box_end)

with open("app/src/main/java/com/example/ebike/HomeScreen.kt", "w") as f:
    f.write(code)

print("Done")
