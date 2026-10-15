package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.camera.model.CameraMode
import com.example.camera.model.FrameRate
import com.example.camera.model.VideoResolution
import com.example.camera.viewmodel.CameraViewModel
import com.example.ui.components.CameraControls
import com.example.ui.components.CameraViewfinder
import com.example.ui.components.MediaViewerSheet
import com.example.ui.components.SettingsSheet
import com.example.ui.components.SmoothZoomBar
import com.example.ui.components.TopBarControls
import com.example.ui.theme.AccentStabilizer
import com.example.ui.theme.CameraSurface
import com.example.ui.theme.CineMotionTheme
import com.example.ui.theme.MatteBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private val cameraViewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CineMotionTheme {
                CameraScreen(viewModel = cameraViewModel)
            }
        }
    }
}

@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MatteBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MatteBlack)
            ) {
                // 1. Hardware Viewfinder
                CameraViewfinder(
                    uiState = uiState,
                    onSurfaceReady = { surfaceProvider ->
                        viewModel.initController(lifecycleOwner, surfaceProvider)
                    },
                    onFocusPoint = { x, y, width, height ->
                        viewModel.focusAtPoint(x, y, width, height)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 2. Top Controls Bar
                TopBarControls(
                    uiState = uiState,
                    onToggleStabilizer = { viewModel.toggleStabilizer() },
                    onToggleFlash = { viewModel.toggleFlash() },
                    onCycleResolutionFps = {
                        // Quick toggle between 4K30 and FHD60
                        if (uiState.resolution == VideoResolution.UHD_4K) {
                            viewModel.setResolution(VideoResolution.FHD_1080P)
                            viewModel.setFrameRate(FrameRate.FPS_60)
                        } else {
                            viewModel.setResolution(VideoResolution.UHD_4K)
                            viewModel.setFrameRate(FrameRate.FPS_30)
                        }
                    },
                    onOpenSettings = { viewModel.setSettingsOpen(true) },
                    onToggleGrid = { viewModel.toggleGrid() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                )

                // 3. Bottom Camera Controls & Smooth Zoom Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    if (!uiState.isRecording) {
                        SmoothZoomBar(
                            currentZoom = uiState.zoomRatio,
                            minZoom = uiState.minZoomRatio,
                            maxZoom = uiState.maxZoomRatio,
                            onZoomChange = { newZoom ->
                                viewModel.setSmoothZoom(newZoom)
                            },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    CameraControls(
                        uiState = uiState,
                        onModeSelected = { mode -> viewModel.switchMode(mode) },
                        onShutterClick = { viewModel.onShutterClicked() },
                        onSwitchCamera = { viewModel.toggleCameraFacing() },
                        onOpenGallery = { viewModel.setGalleryOpen(true) },
                        modifier = Modifier.navigationBarsPadding()
                    )
                }

                // 4. Modal Settings Sheet
                if (uiState.isSettingsOpen) {
                    SettingsSheet(
                        uiState = uiState,
                        onStabilizationSelected = { mode -> viewModel.setStabilizationMode(mode) },
                        onResolutionSelected = { res -> viewModel.setResolution(res) },
                        onFrameRateSelected = { fps -> viewModel.setFrameRate(fps) },
                        onToggleGrid = { viewModel.toggleGrid() },
                        onDismiss = { viewModel.setSettingsOpen(false) }
                    )
                }

                // 5. Gallery & Interactive Live Photo Viewer Sheet
                if (uiState.isGalleryOpen) {
                    MediaViewerSheet(
                        uiState = uiState,
                        onSelectMedia = { item -> viewModel.selectMedia(item) },
                        onDismiss = {
                            viewModel.selectMedia(null)
                            viewModel.setGalleryOpen(false)
                        }
                    )
                }
            }
        } else {
            // Permission Request State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MatteBlack),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(28.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CameraSurface)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = stringResource(R.string.camera_permission_required),
                        tint = AccentStabilizer,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.camera_permission_required),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.camera_permission_desc),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.RECORD_AUDIO
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentStabilizer,
                            contentColor = MatteBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("request_permission_button")
                    ) {
                        Text(
                            text = stringResource(R.string.grant_permission),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
