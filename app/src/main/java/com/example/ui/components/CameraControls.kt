package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.camera.model.CameraMode
import com.example.camera.model.CameraUiState
import com.example.camera.model.CapturedMedia
import com.example.ui.theme.AccentLivePhoto
import com.example.ui.theme.AccentRecording
import com.example.ui.theme.CameraBorder
import com.example.ui.theme.CameraControlFill
import com.example.ui.theme.MatteBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@Composable
fun CameraControls(
    uiState: CameraUiState,
    onModeSelected: (CameraMode) -> Unit,
    onShutterClick: () -> Unit,
    onSwitchCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(
        CameraMode.VIDEO to stringResource(R.string.mode_video),
        CameraMode.CINEMATIC to stringResource(R.string.mode_cinematic),
        CameraMode.PHOTO to stringResource(R.string.mode_photo),
        CameraMode.LIVE_PHOTO to stringResource(R.string.mode_live)
    )

    val listState = rememberLazyListState()

    LaunchedEffect(uiState.currentMode) {
        val index = modes.indexOfFirst { it.first == uiState.currentMode }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MatteBlack.copy(alpha = 0.85f))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode Selector Carousel
        if (!uiState.isRecording) {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
            ) {
                items(modes) { (mode, label) ->
                    val isSelected = uiState.currentMode == mode
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 4.dp)
                            .testTag("mode_${mode.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) AccentLivePhoto else TextSecondary,
                            fontSize = if (isSelected) 13.sp else 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(36.dp))
        }

        // Shutter Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery Thumbnail Button
            val recentMedia: CapturedMedia? = uiState.capturedMediaList.firstOrNull()
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CameraControlFill)
                    .border(1.5.dp, CameraBorder, RoundedCornerShape(12.dp))
                    .clickable { onOpenGallery() }
                    .testTag("gallery_thumbnail_button"),
                contentAlignment = Alignment.Center
            ) {
                if (recentMedia != null && File(recentMedia.filePath).exists()) {
                    AsyncImage(
                        model = recentMedia.uri,
                        contentDescription = stringResource(R.string.action_gallery),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = stringResource(R.string.action_gallery),
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Primary Shutter Button
            ShutterButton(
                uiState = uiState,
                onClick = onShutterClick
            )

            // Camera Lens Switcher Button
            val switchRotation by animateFloatAsState(
                targetValue = if (uiState.isFrontCamera) 180f else 0f,
                animationSpec = tween(durationMillis = 300),
                label = "camera_switch_rotation"
            )
            IconButton(
                onClick = onSwitchCamera,
                enabled = !uiState.isRecording,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(CameraControlFill)
                    .testTag("switch_camera_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = stringResource(R.string.action_switch_camera),
                    tint = TextPrimary,
                    modifier = Modifier
                        .size(26.dp)
                        .rotate(switchRotation)
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(
    uiState: CameraUiState,
    onClick: () -> Unit
) {
    val isRecording = uiState.isRecording
    val isVideo = uiState.currentMode == CameraMode.VIDEO || uiState.currentMode == CameraMode.CINEMATIC
    val isLivePhoto = uiState.currentMode == CameraMode.LIVE_PHOTO

    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(3.5.dp, Color.White, CircleShape)
            .clickable { onClick() }
            .padding(6.dp)
            .testTag("shutter_button"),
        contentAlignment = Alignment.Center
    ) {
        if (uiState.isCapturing) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = if (isLivePhoto) AccentLivePhoto else Color.White,
                strokeWidth = 3.dp
            )
        } else if (isVideo) {
            if (isRecording) {
                // Morph to red stop square
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentRecording)
                )
            } else {
                // Video record red disc
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(AccentRecording)
                )
            }
        } else if (isLivePhoto) {
            // Live Photo concentric circle disc
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(2.dp, AccentLivePhoto, CircleShape)
                )
            }
        } else {
            // Still Photo solid white disc
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
