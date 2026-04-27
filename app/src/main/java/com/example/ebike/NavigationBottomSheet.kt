package com.example.ebike

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NavigationBottomSheet(
    nextTurn: String,
    distanceToTurn: Float,
    distanceRemainingMeters: Int,
    etaMinutes: Int,
    currentStep: Int,
    totalSteps: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleExpanded() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next turn",
                        modifier = Modifier
                            .size(34.dp)
                            .rotate(rotationForInstruction(nextTurn)),
                        tint = Color(0xFF0B57D0)
                    )
                    Text(
                        text = if (distanceToTurn > 0f) {
                            "$nextTurn in ${distanceToTurn.toInt()}m"
                        } else {
                            nextTurn
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color(0xFF132238)
                    )
                }
                Text(
                    text = if (expanded) "Less" else "More",
                    color = Color(0xFF3C5A80)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Distance remaining: ${distanceRemainingMeters.coerceAtLeast(0)}m",
                        color = Color(0xFF3C5A80)
                    )
                    Text(
                        text = "ETA: ${etaMinutes.coerceAtLeast(0)} min",
                        color = Color(0xFF3C5A80)
                    )
                    Text(
                        text = "Step ${currentStep.coerceAtLeast(1)}/${totalSteps.coerceAtLeast(1)}",
                        color = Color(0xFF3C5A80)
                    )
                }
            }
        }
    }
}

private fun rotationForInstruction(instruction: String): Float {
    val normalized = instruction.lowercase()
    return when {
        normalized.contains("left") -> -90f
        normalized.contains("right") -> 90f
        normalized.contains("uturn") -> 180f
        else -> 0f
    }
}
