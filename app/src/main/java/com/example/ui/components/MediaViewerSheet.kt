package com.example.ui.components

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.R
import com.example.camera.core.MotionPhotoMuxer
import com.example.camera.model.CameraUiState
import com.example.camera.model.CapturedMedia
import com.example.camera.model.MediaType
import com.example.ui.theme.AccentLivePhoto
import com.example.ui.theme.AccentStabilizer
import com.example.ui.theme.CameraBorder
import com.example.ui.theme.CameraSurface
import com.example.ui.theme.CameraSurfaceVariant
import com.example.ui.theme.MatteBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerSheet(
    uiState: CameraUiState,
    onSelectMedia: (CapturedMedia?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = uiState.selectedMedia

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MatteBlack,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selected != null) selected.id else stringResource(R.string.gallery_title),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = {
                        if (selected != null) {
                            onSelectMedia(null)
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier.testTag("close_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selected == null) {
                // Grid of all captures
                if (uiState.capturedMediaList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.gallery_empty),
                            color = TextTertiary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.capturedMediaList) { media ->
                            MediaGridItem(
                                media = media,
                                onClick = { onSelectMedia(media) }
                            )
                        }
                    }
                }
            } else {
                // Detail view for selected item
                MediaDetailView(
                    media = selected,
                    onBackToGrid = { onSelectMedia(null) }
                )
            }
        }
    }
}

@Composable
private fun MediaGridItem(
    media: CapturedMedia,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(CameraSurfaceVariant)
            .clickable { onClick() }
            .testTag("grid_item_${media.id}")
    ) {
        AsyncImage(
            model = media.uri,
            contentDescription = media.id,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Badge
        when (media.mediaType) {
            MediaType.LIVE_PHOTO -> {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentLivePhoto.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.live_photo_badge),
                        color = MatteBlack,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            MediaType.VIDEO -> {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MatteBlack.copy(alpha = 0.7f))
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.metadata_type),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            MediaType.PHOTO -> { /* No badge */ }
        }
    }
}

@Composable
private fun MediaDetailView(
    media: CapturedMedia,
    onBackToGrid: () -> Unit
) {
    val context = LocalContext.current
    var isHoldingToPlay by remember { mutableStateOf(false) }
    var extractedMotionVideoFile by remember { mutableStateOf<File?>(null) }

    // Pre-extract embedded video if it's a Live Photo
    DisposableEffect(media) {
        if (media.mediaType == MediaType.LIVE_PHOTO) {
            if (media.motionVideoPath != null && File(media.motionVideoPath).exists()) {
                extractedMotionVideoFile = File(media.motionVideoPath)
            } else {
                val cacheVideo = File(context.cacheDir, "preview_motion_${media.id}.mp4")
                val success = MotionPhotoMuxer.extractEmbeddedVideo(File(media.filePath), cacheVideo)
                if (success && cacheVideo.exists()) {
                    extractedMotionVideoFile = cacheVideo
                }
            }
        }
        onDispose {
            isHoldingToPlay = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Viewer Frame
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CameraSurface)
                .pointerInput(media) {
                    if (media.mediaType == MediaType.LIVE_PHOTO) {
                        detectTapGestures(
                            onPress = {
                                isHoldingToPlay = true
                                tryAwaitRelease()
                                isHoldingToPlay = false
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (media.mediaType == MediaType.VIDEO) {
                // Video Player
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(media.uri)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (media.mediaType == MediaType.LIVE_PHOTO && isHoldingToPlay && extractedMotionVideoFile != null) {
                // Live Photo Motion Video Playing on Hold
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(extractedMotionVideoFile!!.absolutePath)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Still Photo Display
                AsyncImage(
                    model = media.uri,
                    contentDescription = media.id,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // Live Photo Badge & Tap-and-Hold Hint
            if (media.mediaType == MediaType.LIVE_PHOTO) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isHoldingToPlay) AccentLivePhoto else MatteBlack.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isHoldingToPlay) "LIVE PLAYING" else "LIVE PHOTO",
                        color = if (isHoldingToPlay) MatteBlack else AccentLivePhoto,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isHoldingToPlay) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MatteBlack.copy(alpha = 0.7f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.live_photo_hold_hint),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Metadata Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CameraSurfaceVariant)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.gallery_details),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(media.timestamp))
                Text(
                    text = dateStr,
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = stringResource(R.string.metadata_type), color = TextSecondary, fontSize = 11.sp)
                    Text(text = media.mediaType.name, color = AccentStabilizer, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text(text = stringResource(R.string.metadata_resolution), color = TextSecondary, fontSize = 11.sp)
                    Text(text = "${media.width} x ${media.height}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    val sizeMb = media.fileSizeBytes / (1024.0 * 1024.0)
                    Text(text = stringResource(R.string.metadata_size), color = TextSecondary, fontSize = 11.sp)
                    Text(text = String.format(Locale.US, "%.1f MB", sizeMb), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
