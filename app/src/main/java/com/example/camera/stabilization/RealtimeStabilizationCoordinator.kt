package com.example.camera.stabilization

import android.content.Context
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RealtimeStabilizationCoordinator(
    private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val nativeEngine = MgcEisNativeEngine()

    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.Default)

    fun start(width: Int, height: Int, frontFacing: Boolean) {
        if (isRunning) return
        nativeEngine.initialize(width, height, frontFacing)
        gyroscope?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
        isRunning = true
    }

    fun stop() {
        if (!isRunning) return
        sensorManager?.unregisterListener(this)
        nativeEngine.release()
        isRunning = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            nativeEngine.feedGyro(
                x = event.values[0],
                y = event.values[1],
                z = event.values[2],
                timestampNs = event.timestamp
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun processFrame(
        sensorTimestampNs: Long,
        exposureTimeNs: Long,
        frameDurationNs: Long,
        rollingShutterSkewNs: Long,
        cropRegion: Rect,
        activeArray: Rect,
        focalLengthMm: Float
    ): FloatArray? {
        return nativeEngine.processFrame(
            sourceTimestampNs = sensorTimestampNs,
            exposureTimeNs = exposureTimeNs,
            frameDurationNs = frameDurationNs,
            rollingShutterSkewNs = rollingShutterSkewNs,
            cropRegion = cropRegion,
            activeArray = activeArray,
            focalLengthMm = focalLengthMm
        )
    }
}
