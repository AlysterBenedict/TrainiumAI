package com.example.aifitnesscoach.ml

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred

class BiometricsEstimatorOnDevice private constructor(private val context: Context) {

    private var module: Module? = null
    private val modelDeferred = CompletableDeferred<Module>()
    private val targetColumns = listOf(
        "height_cm", "weight_kg", "ankle", "arm-length", "bicep", "calf", "chest",
        "forearm", "hip", "leg-length", "shoulder-breadth", "shoulder-to-crotch",
        "thigh", "waist", "wrist"
    )

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val modelPath = assetFilePath(context, "best_bodym_model.pt")
                val loadedModule = Module.load(modelPath)
                module = loadedModule
                modelDeferred.complete(loadedModule)
                Log.i(TAG, "Biometrics PyTorch model loaded successfully from assets in background.")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading Biometrics PyTorch model in background", e)
                modelDeferred.completeExceptionally(e)
            }
        }
    }

    /**
     * Estimates body metrics from frontal and side images by segmenting them,
     * building binary silhouettes, and running on-device PyTorch model inference.
     */
    suspend fun predict(frontalImageUri: Uri, sideImageUri: Uri): Map<String, Float> {
        val model = module ?: modelDeferred.await()

        Log.d(TAG, "Starting segmentation for frontal image...")
        val frontalSilhouette = getSilhouette(context, frontalImageUri)
        Log.d(TAG, "Starting segmentation for side image...")
        val sideSilhouette = getSilhouette(context, sideImageUri)

        val frontalTensor = bitmapToNormalizedTensor(frontalSilhouette)
        val sideTensor = bitmapToNormalizedTensor(sideSilhouette)

        Log.d(TAG, "Running model forward pass...")
        val outputTensor = model.forward(
            IValue.from(frontalTensor),
            IValue.from(sideTensor)
        ).toTensor()

        val outputArray = outputTensor.dataAsFloatArray
        val results = mutableMapOf<String, Float>()
        for (i in targetColumns.indices) {
            if (i < outputArray.size) {
                results[targetColumns[i]] = outputArray[i]
            }
        }
        
        Log.d(TAG, "Inference completed successfully: $results")
        return results
    }

    private suspend fun getSilhouette(context: Context, uri: Uri): Bitmap = suspendCancellableCoroutine { continuation ->
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = Segmentation.getClient(options)

        val inputImage = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            continuation.resumeWithException(e)
            return@suspendCancellableCoroutine
        }

        segmenter.process(inputImage)
            .addOnSuccessListener { segmentationMask ->
                try {
                    val maskBuffer = segmentationMask.buffer
                    val maskWidth = segmentationMask.width
                    val maskHeight = segmentationMask.height

                    val outputBitmap = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
                    val pixels = IntArray(maskWidth * maskHeight)

                    maskBuffer.rewind()
                    for (i in 0 until maskWidth * maskHeight) {
                        val confidence = maskBuffer.float
                        // If segmenter confidence > 0.5f, it's person (white), else background (black)
                        pixels[i] = if (confidence > 0.5f) {
                            0xFFFFFFFF.toInt()
                        } else {
                            0xFF000000.toInt()
                        }
                    }
                    outputBitmap.setPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)

                    // Resize to model expected input dimension (224x224)
                    val silhouetteResized = Bitmap.createScaledBitmap(outputBitmap, 224, 224, true)
                    continuation.resume(silhouetteResized)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                } finally {
                    segmenter.close()
                }
            }
            .addOnFailureListener { e ->
                segmenter.close()
                continuation.resumeWithException(e)
            }
    }

    private fun bitmapToNormalizedTensor(bitmap: Bitmap): Tensor {
        val size = 224
        val floatArray = FloatArray(3 * size * size)
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        for (i in 0 until size * size) {
            val pixel = pixels[i]
            // Extract the green channel to verify if it's white (foreground)
            val isForeground = ((pixel shr 8) and 0xFF) > 128
            val value = if (isForeground) 1.0f else -1.0f

            // Set CHW (Channel, Height, Width) tensor layout
            floatArray[i] = value                  // R
            floatArray[size * size + i] = value     // G
            floatArray[2 * size * size + i] = value // B
        }
        return Tensor.fromBlob(floatArray, longArrayOf(1, 3, size.toLong(), size.toLong()))
    }

    companion object {
        private const val TAG = "BiometricsEstimatorOD"

        @Volatile
        private var INSTANCE: BiometricsEstimatorOnDevice? = null

        fun getInstance(context: Context): BiometricsEstimatorOnDevice {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BiometricsEstimatorOnDevice(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun assetFilePath(context: Context, assetName: String): String {
            val file = File(context.filesDir, assetName)
            if (file.exists() && file.length() > 0) {
                return file.absolutePath
            }
            context.assets.open(assetName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    outputStream.flush()
                }
            }
            return file.absolutePath
        }
    }
}
