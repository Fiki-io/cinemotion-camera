package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.core.CameraConstants
import com.example.ui.theme.AccentStabilizer
import com.example.ui.theme.CameraBorder
import com.example.ui.theme.CameraControlFill
import com.example.ui.theme.MatteBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.abs

@Composable
fun SmoothZoomBar(
    currentZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val zoomPresets = CameraConstants.DEFAULT_ZOOM_LEVELS
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(minZoom, maxZoom) {
                detectDragGestures(
                    onDragStart = { accumulatedDrag = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDrag += dragAmount.x
                        // Smooth scaling drag sensitivity
                        val delta = (dragAmount.x / 400f) * currentZoom
                        val newRatio = (currentZoom + delta).coerceIn(minZoom, maxZoom)
                        onZoomChange(newRatio)
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Quick Jump Presets (iPhone-style circular buttons)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(CameraControlFill)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            zoomPresets.forEach { preset ->
                val isSelected = abs(currentZoom - preset) < 0.15f
                val animatedScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.1f else 1.0f,
                    animationSpec = tween(durationMillis = 150),
                    label = "zoom_preset_anim"
                )

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) TextPrimary else CameraControlFill)
                        .clickable { onZoomChange(preset) }
                        .testTag("zoom_preset_${preset}"),
                    contentAlignment = Alignment.Center
                ) {
                    val labelText = if (preset < 1.0f) ".6" else "${preset.toInt()}"
                    Text(
                        text = if (isSelected) String.format(Locale.US, "%.1f", currentZoom) else labelText,
                        color = if (isSelected) MatteBlack else TextSecondary,
                        fontSize = if (isSelected) 11.sp else 12.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Precision Continuous Zoom Indicator
        Text(
            text = String.format(Locale.US, "%.1fx", currentZoom),
            color = if (currentZoom > 1.0f) AccentStabilizer else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
