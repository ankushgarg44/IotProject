package com.example.ebike

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FloatingControls(
    routeLoaded: Boolean,
    isNavigating: Boolean,
    onCenterClick: () -> Unit,
    onStartNavigationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FloatingActionButton(onClick = onCenterClick) {
            Icon(imageVector = Icons.Filled.MyLocation, contentDescription = "Center location")
        }

        AnimatedVisibility(visible = routeLoaded && !isNavigating) {
            ExtendedFloatingActionButton(
                onClick = onStartNavigationClick,
                text = { Text("Start") },
                icon = { Icon(imageVector = Icons.Filled.Navigation, contentDescription = "Start navigation") }
            )
        }
    }
}
