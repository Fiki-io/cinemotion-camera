package com.example.camera.manager

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.camera.core.CameraConstants
import com.example.camera.core.MotionPhotoMuxer
import com.example.camera.model.CameraMode
import com.example.camera.model.CapturedMedia
import com.example.camera.model.FlashMode
import com.example.camera.model.FrameRate
import com.example.camera.model.MediaType
import com.example.camera.model.StabilizationMode
import com.example.camera.model.VideoResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraHardwareController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(Dispatchers.Main)
    private val stabilizationCoordinator = com.example.camera.stabilization.RealtimeStabilizationCoordinator(context)

    private val _zoomRatio = MutableStateFlow(1.0f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private val _minZoom = MutableStateFlow(CameraConstants.MIN_ZOOM_FALLBACK)
    val minZoom: StateFlow<Float> = _minZoom.asStateFlow()

    private val _maxZoom = MutableStateFlow(CameraConstants.MAX_ZOOM_FALLBACK)
    val maxZoom: StateFlow<Float> = _maxZoom.asStateFlow()

    private var currentMode = CameraMode.VIDEO
    private var isFront = false
    private var currentStabilizationMode = StabilizationMode.OIS
    private var currentResolution = VideoResolution.UHD_4K
    private var currentFrameRate = FrameRate.FPS_30
    private var currentFlashMode = FlashMode.OFF

    private var recordingTimerJob: kotlinx.coroutines.Job? = null

    fun initialize(surfaceProvider: Preview.SurfaceProvider, onReady: () -> Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            bindCameraUseCases(surfaceProvider)
            onReady()
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun bindCameraUseCases(surfaceProvider: Preview.SurfaceProvider) {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val cameraSelector = if (isFront) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Configure Preview with Hardware ISP Stabilization & Edge Enhancement
        val previewBuilder = Preview.Builder()
        val previewExtender = Camera2Interop.Extender(previewBuilder)

        configureHardwareIsp(previewExtender)

        preview = previewBuilder.build().apply {
            setSurfaceProvider(surfaceProvider)
        }

        // Configure ImageCapture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(
                when (currentFlashMode) {
                    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                }
            )
            .build()

        // Configure VideoCapture with Quality Profile
        val quality = when (currentResolution) {
            VideoResolution.UHD_4K -> QualitySelector.from(
                Quality.UHD,
                FallbackStrategy.higherQualityOrLowerThan(Quality.FHD)
            )
            VideoResolution.FHD_1080P -> QualitySelector.from(
                Quality.FHD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
            )
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(quality)
            .setExecutor(cameraExecutor)
            .build()

        val videoCaptureBuilder = VideoCapture.Builder(recorder)
        val videoExtender = Camera2Interop.Extender(videoCaptureBuilder)
        configureHardwareIsp(videoExtender)

        videoCapture = videoCaptureBuilder.build()

        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                videoCapture
            )
            cameraControl = camera?.cameraControl
            cameraInfo = camera?.cameraInfo

            cameraInfo?.zoomState?.observe(lifecycleOwner) { state ->
                _zoomRatio.value = state.zoomRatio
                _minZoom.value = state.minZoomRatio
                _maxZoom.value = state.maxZoomRatio.coerceAtMost(CameraConstants.MAX_ZOOM_FALLBACK)
            }
            stabilizationCoordinator.start(currentResolution.width, currentResolution.height, isFront)
        } catch (_: Exception) {
            // Fallback: Bind without videoCapture if simultaneous binding fails on specific vendor HAL
            try {
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                cameraControl = camera?.cameraControl
                cameraInfo = camera?.cameraInfo
            } catch (_: Exception) {
                // Keep resilient
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun <T> configureHardwareIsp(extender: Camera2Interop.Extender<T>) {
        when (currentStabilizationMode) {
            StabilizationMode.OIS -> {
                // Pure Optical Hardware Stabilization (Physical Lens shift via magnet coil)
                // Disables EIS to completely prevent the dual-stabilization jitter conflict found in PhotonCamera
                extender.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
            }
            StabilizationMode.EIS -> {
                // Electronic ISP Preview Stabilization (Gyro-based crop)
                // Disables physical OIS so the lens and sensor algorithm do not fight each other
                extender.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extender.setCaptureRequestOption(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                    )
                } else {
                    extender.setCaptureRequestOption(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                    )
                }
            }
            StabilizationMode.OFF -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
                )
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
            }
        }

        // Enable hardware ISP edge sharpening without artifact halos
        extender.setCaptureRequestOption(
            CaptureRequest.EDGE_MODE,
            CaptureRequest.EDGE_MODE_HIGH_QUALITY
        )
        // Enable hardware noise reduction in low light
        extender.setCaptureRequestOption(
            CaptureRequest.NOISE_REDUCTION_MODE,
            CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
        )

        // Lock target FPS range to avoid frame dropping
        val targetFps = currentFrameRate.fps
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
            Range(targetFps, targetFps)
        )
    }

    /**
     * Smooth interpolated zoom matching iPhone spring easing.
     */
    fun setSmoothZoom(targetRatio: Float) {
        val clampedTarget = targetRatio.coerceIn(_minZoom.value, _maxZoom.value)
        val startRatio = _zoomRatio.value
        val steps = 12
        val stepDelayMs = 16L // ~60fps smooth ramp

        scope.launch {
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                // Cubic ease-out curve
                val eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t)
                val currentStep = startRatio + (clampedTarget - startRatio) * eased
                cameraControl?.setZoomRatio(currentStep)
                _zoomRatio.value = currentStep
                delay(stepDelayMs)
            }
            cameraControl?.setZoomRatio(clampedTarget)
            _zoomRatio.value = clampedTarget
        }
    }

    fun focusAtPoint(x: Float, y: Float, width: Float, height: Float) {
        val factory = SurfaceOrientedMeteringPointFactory(width, height)
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        cameraControl?.startFocusAndMetering(action)
    }

    fun updateConfiguration(
        mode: CameraMode,
        isFrontCamera: Boolean,
        stabilization: StabilizationMode,
        resolution: VideoResolution,
        frameRate: FrameRate,
        flash: FlashMode,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        currentMode = mode
        isFront = isFrontCamera
        currentStabilizationMode = stabilization
        currentResolution = resolution
        currentFrameRate = frameRate
        currentFlashMode = flash
        bindCameraUseCases(surfaceProvider)
    }

    fun takeStillPhoto(
        onSuccess: (CapturedMedia) -> Unit,
        onError: (String) -> Unit
    ) {
        val capture = imageCapture ?: run {
            onError("ImageCapture belum siap")
            return
        }

        val mediaDir = getMediaOutputDirectory()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val photoFile = File(mediaDir, "IMG_$timeStamp.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val media = CapturedMedia(
                        id = photoFile.name,
                        uri = android.net.Uri.fromFile(photoFile),
                        filePath = photoFile.absolutePath,
                        mediaType = MediaType.PHOTO,
                        timestamp = System.currentTimeMillis(),
                        width = 4000,
                        height = 3000,
                        fileSizeBytes = photoFile.length()
                    )
                    scope.launch { onSuccess(media) }
                }

                override fun onError(exc: ImageCaptureException) {
                    scope.launch { onError(exc.message ?: "Gagal mengambil foto") }
                }
            }
        )
    }

    fun takeLivePhoto(
        onSuccess: (CapturedMedia) -> Unit,
        onError: (String) -> Unit
    ) {
        val capture = imageCapture ?: run {
            onError("Kamera belum siap")
            return
        }
        val video = videoCapture ?: run {
            onError("Video recorder belum siap")
            return
        }

        val mediaDir = getMediaOutputDirectory()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempJpgFile = File(mediaDir, "TEMP_STILL_$timeStamp.jpg")
        val tempMp4File = File(mediaDir, "TEMP_CLIP_$timeStamp.mp4")
        val finalLivePhotoFile = File(mediaDir, "LIVE_$timeStamp.jpg")

        val videoOutputOptions = FileOutputOptions.Builder(tempMp4File).build()

        try {
            var recordingRef: Recording? = null
            recordingRef = video.output.prepareRecording(context, videoOutputOptions)
                .start(cameraExecutor) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        if (!event.hasError() && tempJpgFile.exists()) {
                            // Merge into Motion Photo container
                            val muxed = MotionPhotoMuxer.createMotionPhoto(
                                jpegFile = tempJpgFile,
                                mp4ClipFile = tempMp4File,
                                outputFile = finalLivePhotoFile
                            )
                            // Clean temp files
                            tempJpgFile.delete()
                            val motionVideoPath = tempMp4File.absolutePath

                            if (muxed && finalLivePhotoFile.exists()) {
                                val media = CapturedMedia(
                                    id = finalLivePhotoFile.name,
                                    uri = android.net.Uri.fromFile(finalLivePhotoFile),
                                    filePath = finalLivePhotoFile.absolutePath,
                                    mediaType = MediaType.LIVE_PHOTO,
                                    timestamp = System.currentTimeMillis(),
                                    width = 4000,
                                    height = 3000,
                                    durationMs = CameraConstants.LIVE_PHOTO_RECORD_DURATION_MS,
                                    fileSizeBytes = finalLivePhotoFile.length(),
                                    motionVideoPath = motionVideoPath
                                )
                                scope.launch { onSuccess(media) }
                            } else {
                                scope.launch { onError("Gagal memproses Live Photo") }
                            }
                        }
                    }
                }

            // Capture still picture during the motion recording
            val photoOptions = ImageCapture.OutputFileOptions.Builder(tempJpgFile).build()
            capture.takePicture(
                photoOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        // After duration, stop the video recording clip
                        scope.launch {
                            delay(CameraConstants.LIVE_PHOTO_RECORD_DURATION_MS)
                            recordingRef.stop()
                        }
                    }

                    override fun onError(exc: ImageCaptureException) {
                        recordingRef.stop()
                        tempJpgFile.delete()
                        tempMp4File.delete()
                        scope.launch { onError("Gagal memotret Live Photo") }
                    }
                }
            )
        } catch (e: Exception) {
            onError(e.message ?: "Gagal memulai Live Photo")
        }
    }

    @SuppressLint("MissingPermission")
    fun startVideoRecording(
        onSuccess: (CapturedMedia) -> Unit,
        onError: (String) -> Unit
    ) {
        val video = videoCapture ?: run {
            onError("Perekam video belum siap")
            return
        }

        val mediaDir = getMediaOutputDirectory()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val prefix = if (currentMode == CameraMode.CINEMATIC) "CINE_" else "VID_"
        val videoFile = File(mediaDir, "$prefix$timeStamp.mp4")

        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        try {
            var recordingBuilder = video.output.prepareRecording(context, outputOptions)

            // Check if record audio permission is granted
            val audioPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            )
            if (audioPermission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                recordingBuilder = recordingBuilder.withAudioEnabled()
            }

            activeRecording = recordingBuilder.start(cameraExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        scope.launch {
                            _isRecording.value = true
                            _recordingSeconds.value = 0
                            startRecordingTimer()
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        scope.launch {
                            _isRecording.value = false
                            stopRecordingTimer()
                        }
                        if (!event.hasError() && videoFile.exists()) {
                            val duration = event.recordingStats.recordedDurationNanos / 1_000_000L
                            val media = CapturedMedia(
                                id = videoFile.name,
                                uri = android.net.Uri.fromFile(videoFile),
                                filePath = videoFile.absolutePath,
                                mediaType = MediaType.VIDEO,
                                timestamp = System.currentTimeMillis(),
                                width = currentResolution.width,
                                height = currentResolution.height,
                                durationMs = duration,
                                fileSizeBytes = videoFile.length()
                            )
                            scope.launch { onSuccess(media) }
                        } else {
                            scope.launch {
                                onError(event.cause?.message ?: "Gagal menyelesaikan rekaman")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _isRecording.value = false
            stopRecordingTimer()
            onError(e.message ?: "Gagal memulai rekaman")
        }
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
        activeRecording = null
        _isRecording.value = false
        stopRecordingTimer()
    }

    private fun startRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = scope.launch {
            while (_isRecording.value) {
                delay(1000L)
                _recordingSeconds.value += 1
            }
        }
    }

    private fun stopRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
    }

    private fun getMediaOutputDirectory(): File {
        val mediaDir = File(context.filesDir, CameraConstants.MEDIA_FOLDER_NAME).apply {
            if (!exists()) mkdirs()
        }
        return mediaDir
    }

    fun shutdown() {
        stopVideoRecording()
        stabilizationCoordinator.stop()
        cameraExecutor.shutdown()
    }
}
