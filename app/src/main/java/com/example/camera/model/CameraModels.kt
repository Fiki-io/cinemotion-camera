package com.example.camera.model

import android.net.Uri

enum class CameraMode {
    VIDEO,
    CINEMATIC,
    PHOTO,
    LIVE_PHOTO
}

enum class VideoResolution(val label: String, val width: Int, val height: Int) {
    UHD_4K("4K", 3840, 2160),
    FHD_1080P("1080P", 1920, 1080)
}

enum class FrameRate(val fps: Int) {
    FPS_24(24),
    FPS_30(30),
    FPS_60(60)
}

enum class FlashMode {
    OFF,
    ON,
    AUTO
}

enum class MediaType {
    PHOTO,
    LIVE_PHOTO,
    VIDEO
}

enum class StabilizationMode(val label: String) {
    OIS("OIS"),
    EIS("EIS"),
    OFF("OFF")
}

data class CapturedMedia(
    val id: String,
    val uri: Uri,
    val filePath: String,
    val mediaType: MediaType,
    val timestamp: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val motionVideoPath: String? = null
)

data class CameraUiState(
    val currentMode: CameraMode = CameraMode.VIDEO,
    val isRecording: Boolean = false,
    val recordingDurationSeconds: Int = 0,
    val zoomRatio: Float = 1.0f,
    val minZoomRatio: Float = 0.6f,
    val maxZoomRatio: Float = 10.0f,
    val stabilizationMode: StabilizationMode = StabilizationMode.OIS,
    val resolution: VideoResolution = VideoResolution.UHD_4K,
    val frameRate: FrameRate = FrameRate.FPS_30,
    val isGridEnabled: Boolean = true,
    val flashMode: FlashMode = FlashMode.OFF,
    val isFrontCamera: Boolean = false,
    val isCapturing: Boolean = false,
    val capturedMediaList: List<CapturedMedia> = emptyList(),
    val selectedMedia: CapturedMedia? = null,
    val isSettingsOpen: Boolean = false,
    val isGalleryOpen: Boolean = false,
    val statusMessage: String? = null
)
