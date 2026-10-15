package com.example.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.camera.model.CameraMode
import com.example.camera.model.CameraUiState
import com.example.ui.theme.AccentLevelValid
import com.example.ui.theme.AccentLivePhoto
import com.example.ui.theme.MatteBlack
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

@Composable
fun CameraViewfinder(
    uiState: CameraUiState,
    onSurfaceReady: (androidx.camera.core.Preview.SurfaceProvider) -> Unit,
    onFocusPoint: (x: Float, y: Float, width: Float, height: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var focusOffset by remember { mutableStateOf<Offset?>(null) }
    val focusScale = remember { Animatable(1.5f) }
    val focusAlpha = remember { Animatable(1f) }

    // Accelerometer Sensor for Level Indicator
    var rollAngle by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val angle = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
                    rollAngle = angle
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        accelerometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MatteBlack)
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    focusOffset = tapOffset
                    onFocusPoint(tapOffset.x, tapOffset.y, size.width.toFloat(), size.height.toFloat())
                    coroutineScope.launch {
                        focusScale.snapTo(1.5f)
                        focusAlpha.snapTo(1f)
                        focusScale.animateTo(1.0f, tween(200))
                        kotlinx.coroutines.delay(600)
                        focusAlpha.animateTo(0f, tween(300))
                        focusOffset = null
                    }
                }
            }
    ) {
        val width = maxWidth
        val height = maxHeight

        // SurfaceView CameraX Viewfinder
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    onSurfaceReady(this.surfaceProvider)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Rule-of-Thirds Grid 3x3 Overlay
        if (uiState.isGridEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val gridColor = Color.White.copy(alpha = 0.22f)
                val strokeWidth = 1.dp.toPx()

                // Vertical lines
                drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth)
                drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth)

                // Horizontal lines
                drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth)
                drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth)
            }
        }

        // Horizon Level Line Indicator
        if (uiState.isGridEnabled) {
            val isLevel = abs(rollAngle) < 1.5f || abs(abs(rollAngle) - 90f) < 1.5f
            val levelColor = if (isLevel) AccentLevelValid else Color.White.copy(alpha = 0.45f)

            Canvas(
                modifier = Modifier
                    .size(120.dp, 24.dp)
                    .align(Alignment.Center)
            ) {
                val lineLength = 40.dp.toPx()
                val centerY = size.height / 2f
                val centerX = size.width / 2f

                // Left wing
                drawLine(
                    color = levelColor,
                    start = Offset(centerX - lineLength - 10.dp.toPx(), centerY),
                    end = Offset(centerX - 10.dp.toPx(), centerY),
                    strokeWidth = 2.dp.toPx()
                )
                // Right wing
                drawLine(
                    color = levelColor,
                    start = Offset(centerX + 10.dp.toPx(), centerY),
                    end = Offset(centerX + lineLength + 10.dp.toPx(), centerY),
                    strokeWidth = 2.dp.toPx()
                )
                // Center target notch
                drawCircle(
                    color = levelColor,
                    radius = 3.dp.toPx(),
                    center = Offset(centerX, centerY)
                )
            }
        }

        // Cinematic 2.39:1 Letterbox Mattes
        if (uiState.currentMode == CameraMode.CINEMATIC) {
            val screenAspect = width / height
            val targetAspect = 2.39f
            if (screenAspect < targetAspect) {
                val contentHeight = width / targetAspect
                val letterboxHeight = (height - contentHeight) / 2f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(letterboxHeight)
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.85f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(letterboxHeight)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.85f))
                )
            }
        }

        // Interactive Tap Focus Square
        focusOffset?.let { offset ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (offset.x - 30.dp.toPx()).roundToInt(),
                            (offset.y - 30.dp.toPx()).roundToInt()
                        )
                    }
                    .size((60 * focusScale.value).dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        color = AccentLivePhoto.copy(alpha = focusAlpha.value),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }
    }
}
