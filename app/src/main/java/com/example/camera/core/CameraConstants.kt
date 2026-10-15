package com.example.camera.core

object CameraConstants {
    const val BITRATE_4K_BPS = 50_000_000 // 50 Mbps high fidelity
    const val BITRATE_FHD_BPS = 28_000_000 // 28 Mbps high fidelity
    const val AUDIO_BITRATE_BPS = 256_000 // 256 kbps AAC

    const val LIVE_PHOTO_RECORD_DURATION_MS = 1500L
    const val LIVE_PHOTO_FPS = 30

    val DEFAULT_ZOOM_LEVELS = listOf(0.6f, 1.0f, 2.0f, 5.0f)
    const val MIN_ZOOM_FALLBACK = 0.6f
    const val MAX_ZOOM_FALLBACK = 10.0f

    const val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000"
    const val XMP_MOTION_PHOTO_TAG = "GCamera:MotionPhoto=\"1\""
    const val XMP_MICRO_VIDEO_TAG = "GCamera:MicroVideo=\"1\""
    const val XMP_OFFSET_TAG_PREFIX = "GCamera:MicroVideoOffset=\""

    const val MEDIA_FOLDER_NAME = "CineMotion"
}
