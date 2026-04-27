package com.example.ebike

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun MapScreen(viewModel: EbikeViewModel) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val userMarker = remember { Marker(mapView) }
    val upcomingManeuverMarker = remember { Marker(mapView) }
    val routePolyline = remember { Polyline(mapView) }

    DisposableEffect(Unit) {
        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(18.0)
        }

        userMarker.apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "You"
        }

        upcomingManeuverMarker.apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Upcoming Turn"
        }

        routePolyline.outlinePaint.color = android.graphics.Color.BLUE
        routePolyline.outlinePaint.strokeWidth = 10f

        mapView.overlays.add(routePolyline)
        mapView.overlays.add(upcomingManeuverMarker)
        mapView.overlays.add(userMarker)

        onDispose {
            mapView.onDetach()
        }
    }

    LaunchedEffect(viewModel.latitude, viewModel.longitude, viewModel.heading, viewModel.isFollowingUser, viewModel.isRotatingMap) {
        val current = GeoPoint(viewModel.latitude, viewModel.longitude)
        userMarker.position = current

        if (viewModel.latitude != 0.0 || viewModel.longitude != 0.0) {
            if (viewModel.isFollowingUser) {
                mapView.controller.animateTo(current)
            }
            if (viewModel.isRotatingMap && viewModel.heading != 0f) {
                mapView.mapOrientation = viewModel.heading
            } else {
                mapView.mapOrientation = 0f
            }
        }
        mapView.invalidate()
    }

    LaunchedEffect(viewModel.routePolyline) {
        routePolyline.setPoints(viewModel.routePolyline)
        mapView.invalidate()
    }

    LaunchedEffect(viewModel.upcomingManeuverPoint) {
        val maneuverPoint = viewModel.upcomingManeuverPoint
        if (maneuverPoint == null) {
            mapView.overlays.remove(upcomingManeuverMarker)
        } else {
            upcomingManeuverMarker.position = maneuverPoint
            if (!mapView.overlays.contains(upcomingManeuverMarker)) {
                mapView.overlays.add(upcomingManeuverMarker)
            }
        }
        mapView.invalidate()
    }

    LaunchedEffect(viewModel.recenterSignal) {
        if (viewModel.latitude != 0.0 || viewModel.longitude != 0.0) {
            mapView.controller.animateTo(GeoPoint(viewModel.latitude, viewModel.longitude))
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize(),
        update = { it.onResume() }
    )
}