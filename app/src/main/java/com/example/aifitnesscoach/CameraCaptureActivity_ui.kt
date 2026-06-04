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
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            val context = LocalContext.current

            TrainiumTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Camera Viewport
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { previewView ->
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                            cameraProviderFuture.addListener({
                                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder()
                                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                                    .build()
                                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                                imageCapture = ImageCapture.Builder()
                                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                                    .setFlashMode(flashMode.value)
                                    .build()

                                try {
                                    cameraProvider.unbindAll()
                                    camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector.value,
                                        preview,
                                        imageCapture
                                    )
                                    camera?.cameraControl?.setLinearZoom(zoomProgress.value)
                                } catch (exc: Exception) {
                                    Log.e("CameraCapture", "Use case binding failed", exc)
                                }
                            }, ContextCompat.getMainExecutor(context))
                        }
                    )

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
        imageCapture?.flashMode = flashMode.value
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
                    if (isFrontalImage.value) {
                        frontalImageUri = savedUri
                        isFrontalImage.value = false
                    } else {
                        sideImageUri = savedUri
                        uploadImages()
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
                val frontalFile = File(frontalImageUri!!.path!!)
                val sideFile = File(sideImageUri!!.path!!)
                val frontalPart = MultipartBody.Part.createFormData(
                    "frontal_image", frontalFile.name,
                    frontalFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                val sidePart = MultipartBody.Part.createFormData(
                    "side_image", sideFile.name,
                    sideFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                val response = RetrofitClient_func.fitnessApi.predictBiometrics(frontalPart, sidePart)
                val intent = Intent(this@CameraCaptureActivity_ui, OnboardingFormActivity_ui::class.java).apply {
                    putExtra("BIOMETRICS_DATA", Gson().toJson(response.biometrics))
                    putExtra("FRONTAL_IMAGE_URI", frontalImageUri.toString())
                    putExtra("SIDE_IMAGE_URI", sideImageUri.toString())
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.e("CameraCapture", "Error uploading images", e)
                Toast.makeText(this@CameraCaptureActivity_ui, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                isLoading.value = false
                isFrontalImage.value = true
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
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = BrandLime)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Uploading biometrics analysis...", color = Color.White)
                }
            }
        } else {
            // Header Instructions
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
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
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
            ) {
                // Zoom Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1x", color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = zoom,
                        onValueChange = onZoomChanged,
                        colors = SliderDefaults.colors(
                            activeTrackColor = BrandLime,
                            thumbColor = BrandLime
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                    Text("8x", color = Color.White, fontSize = 12.sp)
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
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { onFlashToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash Toggle",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Shutter button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(6.dp)
                            .clickable { onCapture() }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color.Black)
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
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { onCameraSwitch() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Camera Switch",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
