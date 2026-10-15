package com.example.camera.stabilization

internal object MgcEisNativeBridge {
    val isAvailable: Boolean = try {
        System.loadLibrary("cinemotion-native")
        true
    } catch (_: Throwable) {
        false
    }

    external fun create(
        width: Int,
        height: Int,
        frontFacing: Boolean,
        strength: Float,
        lookaheadFrames: Int,
    ): Long

    external fun release(handle: Long)

    external fun processGyro(
        handle: Long,
        x: Float,
        y: Float,
        z: Float,
        timestampNs: Long,
    ): Boolean

    external fun processLensOffset(
        handle: Long,
        xShiftPixels: Float,
        yShiftPixels: Float,
        timestampNs: Long,
        cameraType: Int,
    ): Boolean

    external fun processLensIntrinsics(
        handle: Long,
        fx: Float,
        fy: Float,
        cx: Float,
        cy: Float,
        skew: Float,
        timestampNs: Long,
        cameraType: Int,
    ): Boolean

    external fun processFrame(
        handle: Long,
        sourceTimestampNs: Long,
        firstRowCenterTimestampNs: Long,
        exposureTimeNs: Long,
        frameDurationNs: Long,
        rollingShutterSkewNs: Long,
        inverseFocalLength: Float,
        activeWidth: Int,
        activeHeight: Int,
        cropWidth: Int,
        cropHeight: Int,
        preCorrectionActiveWidth: Int,
        preCorrectionActiveHeight: Int,
        nominalLensIntrinsics: FloatArray?,
        rowHomographies: FloatArray,
        state: FloatArray,
    ): Long
}
