package com.example.aifitnesscoach

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.aifitnesscoach.ml.BiometricsEstimatorOnDevice
import com.example.aifitnesscoach.network.RetrofitClient_func
import com.google.gson.Gson
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraCaptureActivity_ui : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isFrontalImage = mutableStateOf(true)
    private var frontalImageUri: Uri? = null
    private var sideImageUri: Uri? = null

    private var camera: Camera? = null
    private var cameraSelector = mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA)
    private var flashMode = mutableStateOf(ImageCapture.FLASH_MODE_OFF)
    private var isLoading = mutableStateOf(false)
    private var zoomProgress = mutableStateOf(0f)
    private var showBlink = mutableStateOf(false)
    private var blinkAlpha = mutableStateOf(0f)
    private var showSuccessOverlay = mutableStateOf(false)
    private var successMessage = mutableStateOf("")
    private var showCameraPreview = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            val context = LocalContext.current

            TrainiumTheme {
                val flipRotation = remember { androidx.compose.animation.core.Animatable(0f) }
                var isFirstRun by remember { mutableStateOf(true) }
                LaunchedEffect(cameraSelector.value) {
                    if (isFirstRun) {
                        isFirstRun = false
                        return@LaunchedEffect
                    }
                    val target = if (flipRotation.value == 0f) 180f else 0f
                    flipRotation.animateTo(
                        targetValue = target,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                    )
                }

                Box(modifier = Modifier.fillMaxSize().background(BackgroundBlack)) {
                    if (showCameraPreview.value) {
                        // Camera Viewport container with 3:4 aspect ratio centered on screen
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4f)
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    rotationY = flipRotation.value
                                    scaleX = if (flipRotation.value > 90f) -1f else 1f
                                }
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PreviewView(ctx).apply {
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { previewView ->
                                    // Read state values here so Compose triggers update on change
                                    val selector = cameraSelector.value
                                    val flash = flashMode.value

                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                                    cameraProviderFuture.addListener({
                                        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder()
                                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                                            .build()
                                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                                        imageCapture = ImageCapture.Builder()
                                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                                            .setFlashMode(flash)
                                            .build()

                                        try {
                                            cameraProvider.unbindAll()
                                            camera = cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                selector,
                                                preview,
                                                imageCapture
                                            )
                                            camera?.cameraControl?.setLinearZoom(zoomProgress.value)
                                            camera?.cameraControl?.enableTorch(flash == ImageCapture.FLASH_MODE_ON)
                                        } catch (exc: Exception) {
                                            Log.e("CameraCapture", "Use case binding failed", exc)
                                        }
                                    }, ContextCompat.getMainExecutor(context))
                                }
                            )
                        }
                    }

                    // Overlay UI controls
                    CameraCaptureOverlay(
                        isFrontal = isFrontalImage.value,
                        flashOn = flashMode.value == ImageCapture.FLASH_MODE_ON,
                        zoom = zoomProgress.value,
                        isLoading = isLoading.value,
                        onFlashToggle = { toggleFlash() },
                        onCameraSwitch = { switchCamera() },
                        onZoomChanged = {
                            zoomProgress.value = it
                            camera?.cameraControl?.setLinearZoom(it)
                        },
                        onCapture = { takePhoto() }
                    )

                    // White screen blink
                    if (showBlink.value) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CardOverlayColor.copy(alpha = blinkAlpha.value))
                        )
                    }

                    // Success animation overlay
                    if (showSuccessOverlay.value) {
                        SuccessOverlay(message = successMessage.value)
                    }
                }
            }
        }
    }

    private fun toggleFlash() {
        flashMode.value = if (flashMode.value == ImageCapture.FLASH_MODE_OFF) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
        val isFlashOn = flashMode.value == ImageCapture.FLASH_MODE_ON
        imageCapture?.flashMode = flashMode.value
        camera?.cameraControl?.enableTorch(isFlashOn)
    }

    private fun switchCamera() {
        cameraSelector.value = if (cameraSelector.value == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        capture.flashMode = flashMode.value
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraCapture", "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    lifecycleScope.launch {
                        if (isFrontalImage.value) {
                            frontalImageUri = savedUri

                            // Trigger blink
                            showBlink.value = true
                            blinkAlpha.value = 1f

                            // Show success message immediately
                            successMessage.value = "Frontal-view image clicked!"
                            showSuccessOverlay.value = true

                            // Animate blink fade out manually to avoid missing MonotonicFrameClock crash
                            val duration = 250L
                            val steps = 10
                            val delayTime = duration / steps
                            for (i in 0..steps) {
                                blinkAlpha.value = 1f - (i.toFloat() / steps)
                                kotlinx.coroutines.delay(delayTime)
                            }
                            showBlink.value = false

                            kotlinx.coroutines.delay(1500)
                            showSuccessOverlay.value = false
                            isFrontalImage.value = false
                        } else {
                            sideImageUri = savedUri

                            // Stop camera preview immediately before animation success
                            showCameraPreview.value = false

                            // Trigger blink
                            showBlink.value = true
                            blinkAlpha.value = 1f

                            // Show success message immediately
                            successMessage.value = "Side-view image clicked!"
                            showSuccessOverlay.value = true

                            // Animate blink fade out manually to avoid missing MonotonicFrameClock crash
                            val duration = 250L
                            val steps = 10
                            val delayTime = duration / steps
                            for (i in 0..steps) {
                                blinkAlpha.value = 1f - (i.toFloat() / steps)
                                kotlinx.coroutines.delay(delayTime)
                            }
                            showBlink.value = false

                            kotlinx.coroutines.delay(1500)
                            showSuccessOverlay.value = false

                            uploadImages()
                        }
                    }
                }
            })
    }

    private fun uploadImages() {
        if (frontalImageUri == null || sideImageUri == null) {
            Toast.makeText(this, "Please capture both images.", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading.value = true

        lifecycleScope.launch {
            try {
                val estimator = BiometricsEstimatorOnDevice.getInstance(this@CameraCaptureActivity_ui)
                val biometrics = estimator.predict(frontalImageUri!!, sideImageUri!!)
                val intent = Intent(this@CameraCaptureActivity_ui, OnboardingFormActivity_ui::class.java).apply {
                    putExtra("BIOMETRICS_DATA", Gson().toJson(biometrics))
                    putExtra("FRONTAL_IMAGE_URI", frontalImageUri.toString())
                    putExtra("SIDE_IMAGE_URI", sideImageUri.toString())
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.e("CameraCapture", "Error estimating biometrics locally", e)
                Toast.makeText(this@CameraCaptureActivity_ui, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                isLoading.value = false
                isFrontalImage.value = true
                showCameraPreview.value = true // restore preview!
                frontalImageUri = null
                sideImageUri = null
            }
        }
    }

    private val outputDirectory: File by lazy {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        mediaDir ?: filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}

@Composable
fun CameraCaptureOverlay(
    isFrontal: Boolean,
    flashOn: Boolean,
    zoom: Float,
    isLoading: Boolean,
    onFlashToggle: () -> Unit,
    onCameraSwitch: () -> Unit,
    onZoomChanged: (Float) -> Unit,
    onCapture: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundBlack),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    BiometricsScanningAnimation()

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "Estimating body metrics using local AI biometrics estimator...",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Depth estimation & skeletal matching models running on-device",
                        color = CardOverlayColor.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Header Instructions
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(BackgroundBlack.copy(alpha = 0.5f))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isFrontal) "Step 1/2" else "Step 2/2",
                    color = BrandLime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isFrontal) "Please take a frontal-view photo." else "Now, please take a side-view photo.",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(BackgroundBlack.copy(alpha = 0.6f))
                    .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
            ) {
                // Zoom Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1x", color = TextPrimary, fontSize = 12.sp)
                    Slider(
                        value = zoom,
                        onValueChange = onZoomChanged,
                        colors = SliderDefaults.colors(
                            activeTrackColor = BrandLime,
                            thumbColor = BrandLime
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                    Text("8x", color = TextPrimary, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flash toggle
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CardOverlayColor.copy(alpha = 0.1f))
                            .clickable { onFlashToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash Toggle",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Shutter button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(TextPrimary)
                            .padding(6.dp)
                            .clickable { onCapture() }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(BackgroundBlack)
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(BrandLime)
                            )
                        }
                    }

                    // Switch camera
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CardOverlayColor.copy(alpha = 0.1f))
                            .clickable { onCameraSwitch() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Camera Switch",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessOverlay(message: String) {
    var scale by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        androidx.compose.animation.core.animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            )
        ) { value, _ ->
            scale = value
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale
                )
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF121212))
                .border(1.dp, CardOverlayColor.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
                .padding(horizontal = 32.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Checkmark Circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(BrandLime.copy(alpha = 0.15f))
                    .border(2.dp, BrandLime, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    tint = BrandLime,
                    modifier = Modifier.size(48.dp)
                )
            }
            Text(
                text = message,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun BiometricsScanningAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_orbit")

    // Laser line scan offset (y-coordinate percentage)
    val scanOffset by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_scan"
    )

    // Inner circle scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Outer circle rotation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(150.dp)
    ) {
        // 1. Diagnostics outer dashed circle rotating
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(rotationZ = rotationAngle)
                .border(2.dp, Brush.sweepGradient(listOf(BrandLime, Color.Transparent, BrandLime.copy(alpha = 0.3f), BrandLime)), CircleShape)
        )

        // 2. Viewfinder corners/brackets (pulsing)
        Box(
            modifier = Modifier
                .size(110.dp)
                .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                .border(1.5.dp, CardOverlayColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
        )

        // 3. Central scan target circle
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(BrandLime.copy(alpha = 0.1f))
                .border(1.dp, BrandLime, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Little dot in center
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(BrandLime)
            )
        }

        // 4. Moving Laser Scanning Line
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(2.dp)
                .align(Alignment.Center)
                .graphicsLayer(translationY = scanOffset * 110.dp.value)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, BrandLime, BrandLime, Color.Transparent)
                    )
                )
        )
    }
}
