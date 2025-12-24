package com.example.aifitnesscoach

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.aifitnesscoach.databinding.ActivityCameraCaptureBinding
import com.example.aifitnesscoach.network.RetrofitClient
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

class CameraCaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraCaptureBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isFrontalImage = true // To track which image we are capturing

    private var frontalImageUri: Uri? = null
    private var sideImageUri: Uri? = null

    private var camera: Camera? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var flashMode = ImageCapture.FLASH_MODE_OFF

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        startCamera()

        binding.captureButton.setOnClickListener {
            takePhoto()
        }

        binding.switchCameraButton.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }

        binding.flashButton.setOnClickListener {
            toggleFlash()
        }

        binding.zoomSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                camera?.cameraControl?.setLinearZoom(progress / 100f)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setFlashMode(flashMode)
                .build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleFlash() {
        flashMode = if (flashMode == ImageCapture.FLASH_MODE_OFF) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }

        // Update UI
        val iconRes = if (flashMode == ImageCapture.FLASH_MODE_ON) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        binding.flashButton.setImageResource(iconRes)

        // Update ImageCapture use case directly
        imageCapture?.flashMode = flashMode
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    if (isFrontalImage) {
                        frontalImageUri = savedUri
                        isFrontalImage = false
                        binding.instructionTextView.text = "Now, please take a side-view photo."
                        binding.stepIndicator.text = "Step 2/2"
                        // You can also show the captured image preview here if you want
                    } else {
                        sideImageUri = savedUri
                        // Now we have both images, let's upload them
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

        // Show loading animation
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.captureButton.isEnabled = false
        binding.switchCameraButton.isEnabled = false
        binding.zoomSlider.isEnabled = false

        lifecycleScope.launch {
            try {
                val frontalFile = File(frontalImageUri!!.path!!)
                val sideFile = File(sideImageUri!!.path!!)

                val frontalRequestBody = frontalFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val sideRequestBody = sideFile.asRequestBody("image/jpeg".toMediaTypeOrNull())

                val frontalPart = MultipartBody.Part.createFormData("frontal_image", frontalFile.name, frontalRequestBody)
                val sidePart = MultipartBody.Part.createFormData("side_image", sideFile.name, sideRequestBody)

                val response = RetrofitClient.instance.predictBiometrics(frontalPart, sidePart)

                // On success, navigate to the OnboardingFormActivity
                val intent = Intent(this@CameraCaptureActivity, OnboardingFormActivity::class.java).apply {
                    // Pass the biometrics data as a JSON string
                    putExtra("BIOMETRICS_DATA", Gson().toJson(response.biometrics))
                    putExtra("FRONTAL_IMAGE_URI", frontalImageUri.toString())
                    putExtra("SIDE_IMAGE_URI", sideImageUri.toString())
                }
                startActivity(intent)
                finish() // Finish this activity

            } catch (e: Exception) {
                Log.e(TAG, "Error uploading images", e)
                Toast.makeText(this@CameraCaptureActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                
                // Hide loading animation and reset
                binding.loadingOverlay.visibility = View.GONE
                binding.captureButton.isEnabled = true
                binding.switchCameraButton.isEnabled = true
                binding.zoomSlider.isEnabled = true
                
                // Reset for re-capture
                isFrontalImage = true
                frontalImageUri = null
                sideImageUri = null
                binding.instructionTextView.text = "Please take a frontal-view photo."
                binding.stepIndicator.text = "Step 1/2"
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
        private const val TAG = "CameraCaptureActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}