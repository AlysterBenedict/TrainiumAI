package com.example.aifitnesscoach

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var pointPaint = Paint()
    private var linePaint = Paint()
    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    // NEW: Variable to hold the current color
    private var jointColor = Color.GREEN

    init {
        initPaints()
    }

    fun clear() {
        results = null
        pointPaint.reset()
        linePaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        // We will set the color dynamically, so we just set style and width here
        linePaint.strokeWidth = 8F
        linePaint.style = Paint.Style.STROKE

        pointPaint.strokeWidth = 8F
        pointPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { poseLandmarkerResult ->
            // NEW: Set the paint colors before drawing
            linePaint.color = jointColor
            pointPaint.color = jointColor // You can change this to Color.YELLOW if you want static points

            for (landmark in poseLandmarkerResult.landmarks()) {
                for (normalizedLandmark in landmark) {
                    canvas.drawPoint(
                        normalizedLandmark.x() * imageWidth * scaleFactor + offsetX,
                        normalizedLandmark.y() * imageHeight * scaleFactor + offsetY,
                        pointPaint
                    )
                }

                PoseLandmarker.POSE_LANDMARKS.forEach { connection ->
                    canvas.drawLine(
                        poseLandmarkerResult.landmarks()[0][connection.start()].x() * imageWidth * scaleFactor + offsetX,
                        poseLandmarkerResult.landmarks()[0][connection.start()].y() * imageHeight * scaleFactor + offsetY,
                        poseLandmarkerResult.landmarks()[0][connection.end()].x() * imageWidth * scaleFactor + offsetX,
                        poseLandmarkerResult.landmarks()[0][connection.end()].y() * imageHeight * scaleFactor + offsetY,
                        linePaint
                    )
                }
            }
        }
    }

    // NEW: Updated function to accept a color
    fun setResults(
        poseLandmarkerResult: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE,
        color: Int // The new color parameter
    ) {
        results = poseLandmarkerResult
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth
        this.jointColor = color // Store the new color

        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight
        scaleFactor = max(scaleX, scaleY)

        val scaledImageWidth = imageWidth * scaleFactor
        val scaledImageHeight = imageHeight * scaleFactor

        offsetX = (width - scaledImageWidth) / 2
        offsetY = (height - scaledImageHeight) / 2

        invalidate()
    }
}

