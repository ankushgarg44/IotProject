package com.example.ebike

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
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
fun TurnInstructionCard(viewModel: EbikeViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101820))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Next Turn: ${viewModel.nextInstruction}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Distance: ${viewModel.distanceToNextTurnMeters.toInt()} meters",
                    color = Color(0xFFB6D3FF),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Step ${viewModel.currentStepIndex + 1}/${viewModel.totalSteps.coerceAtLeast(1)}",
                    color = Color(0xFF7A93B7),
                    fontSize = 12.sp
                )
            }
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = "Direction",
                tint = Color.White,
                modifier = Modifier
                    .size(36.dp)
                    .rotate(rotationForInstruction(viewModel.nextInstruction))
            )
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