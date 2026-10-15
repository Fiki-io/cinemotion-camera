package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.camera.model.CameraMode
import com.example.camera.model.CameraUiState
import com.example.camera.model.FlashMode
import com.example.camera.model.FrameRate
import com.example.camera.model.StabilizationMode
import com.example.camera.model.VideoResolution
import com.example.ui.theme.AccentRecording
import com.example.ui.theme.AccentStabilizer
import com.example.ui.theme.CameraControlFill
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TopBarControls(
    uiState: CameraUiState,
    onToggleStabilizer: () -> Unit,
    onToggleFlash: () -> Unit,
    onCycleResolutionFps: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleGrid: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (uiState.isRecording) {
            // Recording Active Status Bar
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CameraControlFill)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(AccentRecording)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val minutes = uiState.recordingDurationSeconds / 60
                val seconds = uiState.recordingDurationSeconds % 60
                val formattedTime = String.format("%02d:%02d", minutes, seconds)
                Text(
                    text = "${stringResource(R.string.recording_indicator)} $formattedTime",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            // Normal Standby Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flash Toggle
                IconButton(
                    onClick = onToggleFlash,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CameraControlFill)
                        .testTag("flash_toggle_button")
                ) {
                    val icon = when (uiState.flashMode) {
                        FlashMode.ON -> Icons.Default.FlashOn
                        FlashMode.AUTO -> Icons.Default.FlashAuto
                        FlashMode.OFF -> Icons.Default.FlashOff
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(R.string.action_flash),
                        tint = if (uiState.flashMode != FlashMode.OFF) AccentStabilizer else TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Stabilizer Mode Badge (OIS / EIS / OFF)
                val isStabActive = uiState.stabilizationMode != StabilizationMode.OFF
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isStabActive) AccentStabilizer.copy(alpha = 0.2f) else CameraControlFill)
                        .clickable { onToggleStabilizer() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("stabilizer_toggle_badge"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = stringResource(R.string.settings_stabilization),
                            tint = if (isStabActive) AccentStabilizer else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val stabLabel = when (uiState.stabilizationMode) {
                            StabilizationMode.OIS -> "OIS (OPTIK)"
                            StabilizationMode.EIS -> "EIS (GYRO)"
                            StabilizationMode.OFF -> "STAB OFF"
                        }
                        Text(
                            text = stabLabel,
                            color = if (isStabActive) AccentStabilizer else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Quick Resolution / FPS Pill
                if (uiState.currentMode == CameraMode.VIDEO || uiState.currentMode == CameraMode.CINEMATIC) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(CameraControlFill)
                            .clickable { onCycleResolutionFps() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("res_fps_toggle_pill"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${uiState.resolution.label} • ${uiState.frameRate.fps}",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Grid Toggle
                IconButton(
                    onClick = onToggleGrid,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CameraControlFill)
                        .testTag("grid_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = stringResource(R.string.settings_grid),
                        tint = if (uiState.isGridEnabled) AccentStabilizer else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Settings Gear
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CameraControlFill)
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.action_settings),
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
