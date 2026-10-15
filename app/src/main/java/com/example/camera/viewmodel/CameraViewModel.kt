package com.example.camera.viewmodel

import android.app.Application
import android.net.Uri
import androidx.camera.core.Preview
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.camera.core.CameraConstants
import com.example.camera.manager.CameraHardwareController
import com.example.camera.model.CameraMode
import com.example.camera.model.CameraUiState
import com.example.camera.model.CapturedMedia
import com.example.camera.model.FlashMode
import com.example.camera.model.FrameRate
import com.example.camera.model.MediaType
import com.example.camera.model.StabilizationMode
import com.example.camera.model.VideoResolution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var hardwareController: CameraHardwareController? = null
    private var cachedSurfaceProvider: Preview.SurfaceProvider? = null

    init {
        loadExistingMedia()
    }

    fun initController(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        cachedSurfaceProvider = surfaceProvider
        if (hardwareController == null) {
            hardwareController = CameraHardwareController(getApplication(), lifecycleOwner).apply {
                initialize(
                    surfaceProvider = surfaceProvider,
                    onError = { err -> showStatus(err) }
                ) {
                    viewModelScope.launch {
                        zoomRatio.collect { currentZoom ->
                            _uiState.update { it.copy(zoomRatio = currentZoom) }
                        }
                    }
                    viewModelScope.launch {
                        minZoom.collect { minZ ->
                            _uiState.update { it.copy(minZoomRatio = minZ) }
                        }
                    }
                    viewModelScope.launch {
                        maxZoom.collect { maxZ ->
                            _uiState.update { it.copy(maxZoomRatio = maxZ) }
                        }
                    }
                    viewModelScope.launch {
                        isRecording.collect { rec ->
                            _uiState.update { it.copy(isRecording = rec) }
                        }
                    }
                    viewModelScope.launch {
                        recordingSeconds.collect { secs ->
                            _uiState.update { it.copy(recordingDurationSeconds = secs) }
                        }
                    }
                }
            }
        } else {
            cachedSurfaceProvider?.let { provider ->
                reapplyConfiguration(provider)
            }
        }
    }

    fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider) {
        cachedSurfaceProvider = surfaceProvider
        reapplyConfiguration(surfaceProvider)
    }

    private fun reapplyConfiguration(surfaceProvider: Preview.SurfaceProvider) {
        val s = _uiState.value
        hardwareController?.updateConfiguration(
            mode = s.currentMode,
            isFrontCamera = s.isFrontCamera,
            stabilization = s.stabilizationMode,
            resolution = s.resolution,
            frameRate = s.frameRate,
            flash = s.flashMode,
            surfaceProvider = surfaceProvider
        )
    }

    fun switchMode(newMode: CameraMode) {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(currentMode = newMode) }
        cachedSurfaceProvider?.let { reapplyConfiguration(it) }
    }

    fun onShutterClicked() {
        val s = _uiState.value
        if (s.isCapturing) return

        when (s.currentMode) {
            CameraMode.VIDEO, CameraMode.CINEMATIC -> {
                if (s.isRecording) {
                    hardwareController?.stopVideoRecording()
                } else {
                    hardwareController?.startVideoRecording(
                        onSuccess = { media ->
                            onMediaCaptured(media)
                        },
                        onError = { error ->
                            showStatus(error)
                        }
                    )
                }
            }
            CameraMode.PHOTO -> {
                _uiState.update { it.copy(isCapturing = true) }
                hardwareController?.takeStillPhoto(
                    onSuccess = { media ->
                        _uiState.update { it.copy(isCapturing = false) }
                        onMediaCaptured(media)
                    },
                    onError = { error ->
                        _uiState.update { it.copy(isCapturing = false) }
                        showStatus(error)
                    }
                )
            }
            CameraMode.LIVE_PHOTO -> {
                _uiState.update { it.copy(isCapturing = true) }
                hardwareController?.takeLivePhoto(
                    onSuccess = { media ->
                        _uiState.update { it.copy(isCapturing = false) }
                        onMediaCaptured(media)
                    },
                    onError = { error ->
                        _uiState.update { it.copy(isCapturing = false) }
                        showStatus(error)
                    }
                )
            }
        }
    }

    fun setSmoothZoom(targetRatio: Float) {
        hardwareController?.setSmoothZoom(targetRatio)
    }

    fun focusAtPoint(x: Float, y: Float, width: Float, height: Float) {
        hardwareController?.focusAtPoint(x, y, width, height)
    }

    fun toggleStabilizer() {
        val next = when (_uiState.value.stabilizationMode) {
            StabilizationMode.OIS -> StabilizationMode.EIS
            StabilizationMode.EIS -> StabilizationMode.OFF
            StabilizationMode.OFF -> StabilizationMode.OIS
        }
        _uiState.update { it.copy(stabilizationMode = next) }
        cachedSurfaceProvider?.let { reapplyConfiguration(it) }
    }

    fun setStabilizationMode(mode: StabilizationMode) {
        _uiState.update { it.copy(stabilizationMode = mode) }
        cachedSurfaceProvider?.let { reapplyConfiguration(it) }
    }

    fun setResolution(resolution: VideoResolution) {
        if (_uiState.value.isRecording) return
        // Safeguard: on 4K, clamp framerate to 30 FPS for MediaTek ISP stability
        val safeFps = if (resolution == VideoResolution.UHD_4K && _uiState.value.frameRate == FrameRate.FPS_60) {
            FrameRate.FPS_30
        } else {
            _uiState.value.frameRate
        }
        _uiState.update { it.copy(resolution = resolution, frameRate = safeFps) }
        cachedSurfaceProvider?.let { reapplyConfiguration(it) }
    }

    fun setFrameRate(frameRate: FrameRate) {
        if (_uiState.value.isRecording) return
        // Safeguard: 60 FPS is supported on 1080P FHD
        val safeResolution = if (frameRate == FrameRate.FPS_60 && _uiState.value.resolution == VideoResolution.UHD_4K) {
            VideoResolution.FHD_1080P
        } else {
            _uiState.value.resolution
        }
        _uiState.update { it.copy(frameRate = frameRate, resolution = safeResolution) }
        cachedSurfaceProvider?.let { reapplyConfiguration(it) }
    }

    fun toggleCameraFacing() {
        if (_uiState.value.isRecording) return
        val nextFacing = !_uiState.value.isFrontCamera
        _uiState.update { it.copy(isFrontCamera = nextFacing) }
        cachedSurfaceProvider?.let { reapplyConfiguration(it) }
    }

    fun toggleFlash() {
        val next = when (_uiState.value.flashMode) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.OFF
        }
        _uiState.update { it.copy(flashMode = next) }
        cachedSurfaceProvider?.let { reapplyConfiguration(it) }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(isGridEnabled = !it.isGridEnabled) }
    }

    fun setSettingsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = isOpen) }
    }

    fun setGalleryOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isGalleryOpen = isOpen) }
    }

    fun selectMedia(item: CapturedMedia?) {
        _uiState.update { it.copy(selectedMedia = item) }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun showStatus(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    private fun onMediaCaptured(media: CapturedMedia) {
        _uiState.update {
            it.copy(
                capturedMediaList = listOf(media) + it.capturedMediaList
            )
        }
    }

    private fun loadExistingMedia() {
        viewModelScope.launch {
            val mediaDir = File(getApplication<Application>().filesDir, CameraConstants.MEDIA_FOLDER_NAME)
            if (!mediaDir.exists()) return@launch

            val files = mediaDir.listFiles() ?: return@launch
            val mediaItems = files
                .filter { it.isFile && (it.name.endsWith(".jpg") || it.name.endsWith(".mp4")) }
                .sortedByDescending { it.lastModified() }
                .map { file ->
                    val type = when {
                        file.name.startsWith("LIVE_") -> MediaType.LIVE_PHOTO
                        file.name.endsWith(".mp4") -> MediaType.VIDEO
                        else -> MediaType.PHOTO
                    }
                    val motionVideo = if (type == MediaType.LIVE_PHOTO) {
                        val baseName = file.name.removePrefix("LIVE_").removeSuffix(".jpg")
                        val companion = File(mediaDir, "TEMP_CLIP_$baseName.mp4")
                        if (companion.exists()) companion.absolutePath else null
                    } else null

                    CapturedMedia(
                        id = file.name,
                        uri = Uri.fromFile(file),
                        filePath = file.absolutePath,
                        mediaType = type,
                        timestamp = file.lastModified(),
                        width = 1920,
                        height = 1080,
                        fileSizeBytes = file.length(),
                        motionVideoPath = motionVideo
                    )
                }

            _uiState.update { it.copy(capturedMediaList = mediaItems) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        hardwareController?.shutdown()
    }
}
