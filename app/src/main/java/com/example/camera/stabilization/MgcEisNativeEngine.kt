package com.example.camera.stabilization

import android.graphics.Rect

internal class MgcEisNativeEngine {

    companion object {
        const val STRIP_COUNT = 12
        const val LOOKAHEAD_FRAME_COUNT = 7
    }

    private var handle = 0L
    private val rowHomographiesBuffer = FloatArray(STRIP_COUNT * 9)
    private val stateBuffer = FloatArray(4)

    fun initialize(width: Int, height: Int, frontFacing: Boolean, strength: Float = 1.0f): Boolean {
        if (!MgcEisNativeBridge.isAvailable) return false
        release()
        handle = MgcEisNativeBridge.create(
            width = width,
            height = height,
            frontFacing = frontFacing,
            strength = strength.coerceIn(0f, 1f),
            lookaheadFrames = LOOKAHEAD_FRAME_COUNT
        )
        return handle != 0L
    }

    fun feedGyro(x: Float, y: Float, z: Float, timestampNs: Long): Boolean {
        if (handle == 0L) return false
        return MgcEisNativeBridge.processGyro(handle, x, y, z, timestampNs)
    }

    fun processFrame(
        sourceTimestampNs: Long,
        exposureTimeNs: Long,
        frameDurationNs: Long,
        rollingShutterSkewNs: Long,
        cropRegion: Rect,
        activeArray: Rect,
        focalLengthMm: Float
    ): FloatArray? {
        if (handle == 0L) return null

        val resultTimestamp = MgcEisNativeBridge.processFrame(
            handle = handle,
            sourceTimestampNs = sourceTimestampNs,
            firstRowCenterTimestampNs = sourceTimestampNs + (rollingShutterSkewNs / (2 * STRIP_COUNT)),
            exposureTimeNs = exposureTimeNs,
            frameDurationNs = frameDurationNs,
            rollingShutterSkewNs = rollingShutterSkewNs,
            inverseFocalLength = if (focalLengthMm > 0f) 1f / focalLengthMm else 0.25f,
            activeWidth = activeArray.width(),
            activeHeight = activeArray.height(),
            cropWidth = cropRegion.width(),
            cropHeight = cropRegion.height(),
            preCorrectionActiveWidth = activeArray.width(),
            preCorrectionActiveHeight = activeArray.height(),
            nominalLensIntrinsics = null,
            rowHomographies = rowHomographiesBuffer,
            state = stateBuffer
        )

        return if (resultTimestamp != -1L) {
            rowHomographiesBuffer.clone()
        } else {
            null
        }
    }

    fun release() {
        if (handle != 0L) {
            MgcEisNativeBridge.release(handle)
            handle = 0L
        }
    }
}
