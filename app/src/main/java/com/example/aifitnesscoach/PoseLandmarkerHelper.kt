package com.example.aifitnesscoach

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import com.example.aifitnesscoach.R
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class PoseLandmarkerHelper(
    var minPoseDetectionConfidence: Float = 0.5f,
    var minPosePresenceConfidence: Float = 0.5f,
    var minTrackingConfidence: Float = 0.5f,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    val poseLandmarkerHelperListener: ResultListener? = null
) {

    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    fun isClosed(): Boolean {
        return poseLandmarker == null
    }

    fun setupPoseLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder()

        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionsBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionsBuilder.setDelegate(Delegate.GPU)
        }

        val modelName = "pose_landmarker.task"
        val file = File(context.filesDir, modelName)
        
        // Always copy the file to ensure we have the latest version and it exists
        try {
            val inputStream = context.resources.openRawResource(R.raw.pose_landmarker_lite_task)
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            
            Log.d(TAG, "Model copied to: ${file.absolutePath}, size: ${file.length()}")
            
            if (file.length() == 0L) {
                 throw Exception("Model file is empty after copy.")
            }
            
            baseOptionsBuilder.setModelAssetPath(file.absolutePath)
        } catch (e: Exception) {
            poseLandmarkerHelperListener?.onError("Failed to copy model file: ${e.message}")
            Log.e(TAG, "Error copying model: ${e.message}")
            return
        }

        if (runningMode == RunningMode.LIVE_STREAM && poseLandmarkerHelperListener == null) {
            throw IllegalStateException("Listener must be set when runningMode is LIVE_STREAM.")
        }

        try {
            val optionsBuilder =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                    .setMinTrackingConfidence(minTrackingConfidence)
                    .setMinPosePresenceConfidence(minPosePresenceConfidence)
                    .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder.setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            poseLandmarkerHelperListener?.onError("Pose Landmarker failed to initialize. See error logs for details")
            Log.e(TAG, "MediaPipe failed to load the task with error: " + e.message)
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            return
        }
        val frameTime = SystemClock.uptimeMillis()
        // Create a blank bitmap using KTX version
        val bitmapBuffer = createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
        }

        // Create a new bitmap with transformation using the static Bitmap.createBitmap method
        val rotatedBitmap = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true)
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    private fun returnLivestreamResult(result: PoseLandmarkerResult, input: MPImage) {
        poseLandmarkerHelperListener?.onResults(
            ResultBundle(
                listOf(result),
                input.height,
                input.width
            )
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        poseLandmarkerHelperListener?.onError(error.message ?: "An unknown error has occurred")
    }

    companion object {
        const val TAG = "PoseLandmarkerHelper"
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
    }

    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface ResultListener {
        fun onError(error: String, errorCode: Int = 0)
        fun onResults(resultBundle: ResultBundle)
    }
}
