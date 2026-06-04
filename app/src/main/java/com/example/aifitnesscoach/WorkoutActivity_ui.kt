package com.example.aifitnesscoach

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2

class WorkoutActivity_ui : AppCompatActivity(), PoseLandmarkerHelper_func.ResultListener {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper_func

    private lateinit var workoutPlan: ArrayList<String>
    private var exerciseDuration: Long = 0
    private var restDuration: Long = 0
    private var currentExerciseIndex = 0
    private var countDownTimer: CountDownTimer? = null

    private lateinit var currentExerciseConfig: ExerciseConfig_func
    private var internalRepCounter = 0
    private var exerciseStage = ""
    private var internalFeedbackText = "Get Ready"
    private var jointColor = Color.GREEN
    private var overlayView: OverlayView_ui? = null

    private var burpeeStage = "start"
    private var twistStage = "center"
    private var hasTwistedLeft = false
    private var hasTwistedRight = false

    // State variables for Compose
    private var exerciseNameState = mutableStateOf("")
    private var repsState = mutableStateOf(0)
    private var feedbackState = mutableStateOf("Get Ready")
    private var timerSecondsState = mutableStateOf(30L)
    private var feedColorState = mutableStateOf(ComposeColor.Green)

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        const val LEFT_HIP = 23; const val LEFT_KNEE = 25; const val LEFT_ANKLE = 27
        const val RIGHT_HIP = 24; const val RIGHT_KNEE = 26; const val RIGHT_ANKLE = 28
        const val LEFT_SHOULDER = 11; const val LEFT_ELBOW = 13; const val LEFT_WRIST = 15
        const val RIGHT_SHOULDER = 12; const val RIGHT_ELBOW = 14; const val RIGHT_WRIST = 16
        const val LEFT_HEEL = 29; const val RIGHT_HEEL = 30; const val NOSE = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        workoutPlan = intent.getStringArrayListExtra(Constants_func.EXTRA_WORKOUT_PLAN) ?: ArrayList()
        currentExerciseIndex = intent.getIntExtra(Constants_func.EXTRA_CURRENT_INDEX, 0)
        exerciseDuration = intent.getLongExtra(Constants_func.EXTRA_EXERCISE_DURATION, 30000)
        restDuration = intent.getLongExtra(Constants_func.EXTRA_REST_DURATION, 15000)

        if (currentExerciseIndex >= workoutPlan.size) {
            workoutComplete()
            return
        }

        val currentExerciseName = workoutPlan[currentExerciseIndex]
        currentExerciseConfig = Exercises_func.list.find { it.name == currentExerciseName }!!

        timerSecondsState.value = exerciseDuration / 1000

        resetExerciseState()
        updateUI()

        setContent {
            TrainiumTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. Camera View Finder
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { previewView ->
                            if (allPermissionsGranted()) {
                                setupPoseLandmarker()
                                startCamera(previewView)
                            } else {
                                ActivityCompat.requestPermissions(
                                    this@WorkoutActivity_ui,
                                    REQUIRED_PERMISSIONS,
                                    REQUEST_CODE_PERMISSIONS
                                )
                            }
                        }
                    )

                    // 2. Custom drawing overlay
                    AndroidView(
                        factory = { ctx ->
                            OverlayView_ui(ctx, null).also {
                                overlayView = it
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 3. Compose overlay info
                    WorkoutOverlay(
                        exerciseName = exerciseNameState.value,
                        reps = repsState.value,
                        feedback = feedbackState.value,
                        secondsLeft = timerSecondsState.value,
                        maxSeconds = exerciseDuration / 1000,
                        feedbackColor = feedColorState.value,
                        onSkip = { goToRest() }
                    )
                }
            }
        }

        startTimer(exerciseDuration)
    }

    private fun resetExerciseState() {
        internalRepCounter = 0
        exerciseStage = if (currentExerciseConfig.invertStages) "down" else "up"
        internalFeedbackText = "Ready"
        burpeeStage = "start"
        twistStage = "center"
        hasTwistedLeft = false
        hasTwistedRight = false
    }

    private fun startTimer(duration: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timerSecondsState.value = millisUntilFinished / 1000 + 1
            }
            override fun onFinish() { goToRest() }
        }.start()
    }

    private fun goToRest() {
        countDownTimer?.cancel()
        val nextIndex = currentExerciseIndex + 1

        if (nextIndex >= workoutPlan.size) {
            workoutComplete()
        } else {
            val nextExerciseName = workoutPlan.getOrNull(nextIndex)
            val intent = Intent(this, RestActivity_ui::class.java).apply {
                putExtra(Constants_func.EXTRA_REST_DURATION, restDuration)
                putExtra(Constants_func.EXTRA_NEXT_EXERCISE_NAME, nextExerciseName)
                putStringArrayListExtra(Constants_func.EXTRA_WORKOUT_PLAN, workoutPlan)
                putExtra(Constants_func.EXTRA_CURRENT_INDEX, nextIndex)
                putExtra(Constants_func.EXTRA_EXERCISE_DURATION, exerciseDuration)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun workoutComplete() {
        startActivity(Intent(this, WorkoutCompleteActivity_ui::class.java))
        finish()
    }

    private fun updateUI() {
        exerciseNameState.value = currentExerciseConfig.name
        repsState.value = internalRepCounter
        feedbackState.value = internalFeedbackText
        feedColorState.value = if (jointColor == Color.GREEN) ComposeColor.Green else if (jointColor == Color.RED) ComposeColor.Red else ComposeColor(0xFFF57542)
    }

    private fun processExercise(poseLandmarks: List<NormalizedLandmark>) {
        currentExerciseConfig.postureValidation?.let { pvConfig ->
            if (pvConfig.landmarksToTrack.size >= 3 && poseLandmarks.size > maxOf(pvConfig.landmarksToTrack[0], pvConfig.landmarksToTrack[1], pvConfig.landmarksToTrack[2])) {
                val p1 = Landmark_func(poseLandmarks[pvConfig.landmarksToTrack[0]].x(), poseLandmarks[pvConfig.landmarksToTrack[0]].y())
                val p2 = Landmark_func(poseLandmarks[pvConfig.landmarksToTrack[1]].x(), poseLandmarks[pvConfig.landmarksToTrack[1]].y())
                val p3 = Landmark_func(poseLandmarks[pvConfig.landmarksToTrack[2]].x(), poseLandmarks[pvConfig.landmarksToTrack[2]].y())
                val postureAngle = calculateAngle(p1, p2, p3)

                if (postureAngle > pvConfig.angleThreshold) {
                    internalFeedbackText = pvConfig.feedbackIncorrect
                    jointColor = Color.RED
                    updateUI()
                    return
                }
            }
        }

        when (currentExerciseConfig.exerciseType) {
            "rep_based" -> {
                if (currentExerciseConfig.landmarksToTrack.size >= 3 && poseLandmarks.size > currentExerciseConfig.landmarksToTrack.maxOrNull() ?: 0) {
                    val landmarks = currentExerciseConfig.landmarksToTrack.map { Landmark_func(poseLandmarks[it].x(), poseLandmarks[it].y()) }
                    val angle1 = calculateAngle(landmarks[0], landmarks[1], landmarks[2])
                    val angle2 = if (landmarks.size > 5) calculateAngle(landmarks[3], landmarks[4], landmarks[5]) else angle1

                    val angleToUse = when (currentExerciseConfig.angleLogic) {
                        "min" -> minOf(angle1, angle2)
                        "max" -> maxOf(angle1, angle2)
                        else -> (angle1 + angle2) / 2
                    }

                    val upThreshold = currentExerciseConfig.upThreshold!!
                    val downThreshold = currentExerciseConfig.downThreshold!!
                    val feedbackMap = currentExerciseConfig.feedbackMap

                    if (!currentExerciseConfig.invertStages) {
                        if (angleToUse > upThreshold) {
                            if (exerciseStage == "down") {
                                // Transition complete
                            }
                            exerciseStage = "up"
                            internalFeedbackText = feedbackMap["up"] ?: "Ready"
                            jointColor = Color.rgb(245, 117, 66)
                        } else if (angleToUse < downThreshold && exerciseStage == "up") {
                            exerciseStage = "down"
                            internalRepCounter++
                            internalFeedbackText = feedbackMap["down"] ?: "Good Rep"
                            jointColor = Color.GREEN
                        } else {
                            internalFeedbackText = if (exerciseStage == "up") feedbackMap["transition_down"] ?: "Go Down"
                            else feedbackMap["transition_up"] ?: "Go Up"
                            jointColor = Color.RED
                        }
                    } else {
                        if (angleToUse < downThreshold) {
                            exerciseStage = "down"
                            internalFeedbackText = feedbackMap["down"] ?: "Ready"
                            jointColor = Color.rgb(245, 117, 66)
                        } else if (angleToUse > upThreshold && exerciseStage == "down") {
                            exerciseStage = "up"
                            internalRepCounter++
                            internalFeedbackText = feedbackMap["up"] ?: "Good Rep"
                            jointColor = Color.GREEN
                        } else {
                            internalFeedbackText = if (exerciseStage == "down") feedbackMap["transition_up"] ?: "Go Up"
                            else feedbackMap["transition_down"] ?: "Go Down"
                            jointColor = Color.RED
                        }
                    }
                }
            }
            "timed" -> {
                if (currentExerciseConfig.landmarksToTrack.size >= 3 && poseLandmarks.size > currentExerciseConfig.landmarksToTrack.maxOrNull() ?: 0) {
                    val landmarks = currentExerciseConfig.landmarksToTrack.map { Landmark_func(poseLandmarks[it].x(), poseLandmarks[it].y()) }
                    val angle = calculateAngle(landmarks[0], landmarks[1], landmarks[2])
                    val correctFormAngle = currentExerciseConfig.correctFormAngle!!
                    val isCorrect = if (currentExerciseConfig.invertStages) angle < correctFormAngle else angle > correctFormAngle

                    if (isCorrect) {
                        internalFeedbackText = currentExerciseConfig.feedbackMap["correct"] ?: "Hold Position"
                        jointColor = Color.GREEN
                    } else {
                        internalFeedbackText = currentExerciseConfig.feedbackMap["incorrect"] ?: "Incorrect Form"
                        jointColor = Color.RED
                    }
                }
            }
            "knee_height" -> {
                if (poseLandmarks.size > maxOf(LEFT_KNEE, LEFT_HIP, RIGHT_KNEE, RIGHT_HIP)) {
                    val leftKneeY = poseLandmarks[LEFT_KNEE].y()
                    val leftHipY = poseLandmarks[LEFT_HIP].y()
                    val rightKneeY = poseLandmarks[RIGHT_KNEE].y()
                    val rightHipY = poseLandmarks[RIGHT_HIP].y()

                    if ((leftKneeY < leftHipY || rightKneeY < rightHipY) && exerciseStage == "down") {
                        exerciseStage = "up"
                        internalFeedbackText = currentExerciseConfig.feedbackMap["up"] ?: "Good!"
                        jointColor = Color.GREEN
                        internalRepCounter++
                    } else if (leftKneeY > leftHipY && rightKneeY > rightHipY) {
                        exerciseStage = "down"
                        internalFeedbackText = currentExerciseConfig.feedbackMap["down"] ?: "Drive Knee Up!"
                        jointColor = Color.rgb(245, 117, 66)
                    }
                }
            }
            "pull_up" -> {
                if (poseLandmarks.size > maxOf(NOSE, LEFT_WRIST, RIGHT_WRIST)) {
                    val noseY = poseLandmarks[NOSE].y()
                    val barY = minOf(poseLandmarks[LEFT_WRIST].y(), poseLandmarks[RIGHT_WRIST].y())

                    if (noseY < barY && exerciseStage == "down") {
                        exerciseStage = "up"
                        internalFeedbackText = currentExerciseConfig.feedbackMap["up"] ?: "Good Rep!"
                        jointColor = Color.GREEN
                        internalRepCounter++
                    } else if (noseY > barY) {
                        exerciseStage = "down"
                        internalFeedbackText = currentExerciseConfig.feedbackMap["down"] ?: "Pull Up!"
                        jointColor = Color.rgb(245, 117, 66)
                    }
                }
            }
            "bird_dog" -> {
                if (poseLandmarks.size > maxOf(LEFT_WRIST, RIGHT_KNEE)) {
                    val leftWrist = Landmark_func(poseLandmarks[LEFT_WRIST].x(), poseLandmarks[LEFT_WRIST].y())
                    val rightKnee = Landmark_func(poseLandmarks[RIGHT_KNEE].x(), poseLandmarks[RIGHT_KNEE].y())
                    val dist = Math.sqrt(Math.pow((leftWrist.x - rightKnee.x).toDouble(), 2.0) + Math.pow((leftWrist.y - rightKnee.y).toDouble(), 2.0))

                    if (dist > currentExerciseConfig.thresholds["extended"]!! && exerciseStage == "in") {
                        exerciseStage = "out"
                        internalFeedbackText = currentExerciseConfig.feedbackMap["out"] ?: "Extend!"
                        jointColor = Color.GREEN
                        internalRepCounter++
                    } else if (dist < currentExerciseConfig.thresholds["contracted"]!!) {
                        exerciseStage = "in"
                        internalFeedbackText = currentExerciseConfig.feedbackMap["in"] ?: "Return"
                        jointColor = Color.rgb(245, 117, 66)
                    }
                }
            }
            "russian_twist" -> {
                if (poseLandmarks.size > maxOf(LEFT_SHOULDER, RIGHT_SHOULDER)) {
                    val leftShoulder = Landmark_func(poseLandmarks[LEFT_SHOULDER].x(), poseLandmarks[LEFT_SHOULDER].y())
                    val rightShoulder = Landmark_func(poseLandmarks[RIGHT_SHOULDER].x(), poseLandmarks[RIGHT_SHOULDER].y())

                    val shouldersVecX = rightShoulder.x - leftShoulder.x
                    val shouldersVecY = rightShoulder.y - leftShoulder.y
                    val angleShoulders = Math.toDegrees(atan2(shouldersVecY.toDouble(), shouldersVecX.toDouble()))

                    val leftThresh = currentExerciseConfig.thresholds["left"]!!
                    val rightThresh = currentExerciseConfig.thresholds["right"]!!

                    if (angleShoulders < leftThresh && twistStage == "center") {
                        twistStage = "left"
                        internalFeedbackText = currentExerciseConfig.feedbackMap["left"] ?: "Twist Left"
                        jointColor = Color.YELLOW
                    } else if (angleShoulders > rightThresh && twistStage == "center") {
                        twistStage = "right"
                        internalFeedbackText = currentExerciseConfig.feedbackMap["right"] ?: "Twist Right"
                        jointColor = Color.YELLOW
                    } else if (angleShoulders in (leftThresh + 1)..(rightThresh - 1)) {
                        if (twistStage == "left") hasTwistedLeft = true
                        else if (twistStage == "right") hasTwistedRight = true
                        twistStage = "center"
                        internalFeedbackText = "Center"
                        jointColor = Color.rgb(245, 117, 66)
                    }

                    if (hasTwistedLeft && hasTwistedRight) {
                        internalRepCounter++
                        internalFeedbackText = "Good Rep!"
                        jointColor = Color.GREEN
                        hasTwistedLeft = false
                        hasTwistedRight = false
                    }
                }
            }
            "mountain_climber" -> {
                if (poseLandmarks.size > maxOf(LEFT_KNEE, LEFT_ELBOW)) {
                    val leftKnee = Landmark_func(poseLandmarks[LEFT_KNEE].x(), poseLandmarks[LEFT_KNEE].y())
                    val leftElbow = Landmark_func(poseLandmarks[LEFT_ELBOW].x(), poseLandmarks[LEFT_ELBOW].y())
                    val dist = Math.sqrt(Math.pow((leftKnee.x - leftElbow.x).toDouble(), 2.0) + Math.pow((leftKnee.y - leftElbow.y).toDouble(), 2.0))

                    if (dist < currentExerciseConfig.thresholds["close"]!! && exerciseStage == "back") {
                        exerciseStage = "forward"
                        internalFeedbackText = currentExerciseConfig.feedbackMap["forward"] ?: "Knee to Elbow!"
                        jointColor = Color.GREEN
                        internalRepCounter++
                    } else if (dist > currentExerciseConfig.thresholds["far"]!!) {
                        exerciseStage = "back"
                        internalFeedbackText = currentExerciseConfig.feedbackMap["back"] ?: "Switch"
                        jointColor = Color.rgb(245, 117, 66)
                    }
                }
            }
            "burpee" -> {
                if (poseLandmarks.size > maxOf(LEFT_HIP, LEFT_KNEE, LEFT_ANKLE, LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST)) {
                    val squatAngle = calculateAngle(
                        Landmark_func(poseLandmarks[LEFT_HIP].x(), poseLandmarks[LEFT_HIP].y()),
                        Landmark_func(poseLandmarks[LEFT_KNEE].x(), poseLandmarks[LEFT_KNEE].y()),
                        Landmark_func(poseLandmarks[LEFT_ANKLE].x(), poseLandmarks[LEFT_ANKLE].y())
                    )
                    val plankAngle = calculateAngle(
                        Landmark_func(poseLandmarks[LEFT_SHOULDER].x(), poseLandmarks[LEFT_SHOULDER].y()),
                        Landmark_func(poseLandmarks[LEFT_HIP].x(), poseLandmarks[LEFT_HIP].y()),
                        Landmark_func(poseLandmarks[LEFT_ANKLE].x(), poseLandmarks[LEFT_ANKLE].y())
                    )
                    val pushupAngle = calculateAngle(
                        Landmark_func(poseLandmarks[LEFT_SHOULDER].x(), poseLandmarks[LEFT_SHOULDER].y()),
                        Landmark_func(poseLandmarks[LEFT_ELBOW].x(), poseLandmarks[LEFT_ELBOW].y()),
                        Landmark_func(poseLandmarks[LEFT_WRIST].x(), poseLandmarks[LEFT_WRIST].y())
                    )

                    if (burpeeStage == "start" && squatAngle < 100) { burpeeStage = "squat"; internalFeedbackText = "Down to Plank" }
                    else if (burpeeStage == "squat" && plankAngle > 160) { burpeeStage = "plank"; internalFeedbackText = "Push-up" }
                    else if (burpeeStage == "plank" && pushupAngle < 90) { burpeeStage = "pushup"; internalFeedbackText = "Back to Squat" }
                    else if (burpeeStage == "pushup" && squatAngle < 100 && plankAngle < 150) { burpeeStage = "return_squat"; internalFeedbackText = "Jump Up!" }
                    else if (burpeeStage == "return_squat" && squatAngle > 165) { burpeeStage = "start"; internalRepCounter++; internalFeedbackText = "Good Rep!" }
                }
            }
            "plank_jacks" -> {
                if (poseLandmarks.size > maxOf(LEFT_ANKLE, RIGHT_ANKLE)) {
                    val leftAnkleX = poseLandmarks[LEFT_ANKLE].x()
                    val rightAnkleX = poseLandmarks[RIGHT_ANKLE].x()
                    val ankleDist = abs(leftAnkleX - rightAnkleX)

                    if (ankleDist > currentExerciseConfig.thresholds["out"]!! && exerciseStage == "in") {
                        exerciseStage = "out"
                        internalFeedbackText = currentExerciseConfig.feedbackMap["out"] ?: "Legs Out!"
                        jointColor = Color.GREEN
                        internalRepCounter++
                    } else if (ankleDist < currentExerciseConfig.thresholds["in"]!!) {
                        exerciseStage = "in"
                        internalFeedbackText = currentExerciseConfig.feedbackMap["in"] ?: "Legs In!"
                        jointColor = Color.rgb(245, 117, 66)
                    }
                }
            }
            "shoulder_taps" -> {
                if (poseLandmarks.size > maxOf(LEFT_WRIST, RIGHT_SHOULDER)) {
                    val leftWrist = Landmark_func(poseLandmarks[LEFT_WRIST].x(), poseLandmarks[LEFT_WRIST].y())
                    val rightShoulder = Landmark_func(poseLandmarks[RIGHT_SHOULDER].x(), poseLandmarks[RIGHT_SHOULDER].y())
                    val dist = Math.sqrt(Math.pow((leftWrist.x - rightShoulder.x).toDouble(), 2.0) + Math.pow((leftWrist.y - rightShoulder.y).toDouble(), 2.0))

                    if (dist < currentExerciseConfig.thresholds["tap"]!! && exerciseStage == "down") {
                        exerciseStage = "up"
                        internalFeedbackText = currentExerciseConfig.feedbackMap["tap"] ?: "Tap!"
                        jointColor = Color.GREEN
                        internalRepCounter++
                    } else if (dist > currentExerciseConfig.thresholds["release"]!!) {
                        exerciseStage = "down"
                        internalFeedbackText = currentExerciseConfig.feedbackMap["release"] ?: "Return Hand"
                        jointColor = Color.rgb(245, 117, 66)
                    }
                }
            }
        }
        updateUI()
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper_func.ResultBundle) {
        runOnUiThread {
            if (resultBundle.results.isEmpty() || resultBundle.results.first().landmarks().isEmpty()) {
                overlayView?.clear()
                internalFeedbackText = "No pose detected"
                updateUI()
                return@runOnUiThread
            }
            val poseLandmarks = resultBundle.results.first().landmarks().first()
            processExercise(poseLandmarks)
            overlayView?.setResults(
                resultBundle.results.first(),
                resultBundle.inputImageHeight,
                resultBundle.inputImageWidth,
                RunningMode.LIVE_STREAM,
                jointColor
            )
        }
    }

    override fun onError(error: String, errorCode: Int) {
        runOnUiThread { Toast.makeText(this, error, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        cameraExecutor.shutdown()
    }

    private fun setupPoseLandmarker() {
        poseLandmarkerHelper = PoseLandmarkerHelper_func(
            context = this,
            runningMode = RunningMode.LIVE_STREAM,
            poseLandmarkerHelperListener = this
        )
    }

    private fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        poseLandmarkerHelper.detectLiveStream(image)
                    }
                }
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e("WorkoutActivity_ui", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                setupPoseLandmarker()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}

@Composable
fun WorkoutOverlay(
    exerciseName: String,
    reps: Int,
    feedback: String,
    secondsLeft: Long,
    maxSeconds: Long,
    feedbackColor: ComposeColor,
    onSkip: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Floating Top HUD Panel
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ComposeColor.Black.copy(alpha = 0.65f))
                .border(1.dp, ComposeColor.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ComposeColor.White.copy(alpha = 0.05f))
                        .border(1.dp, ComposeColor.White.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getExerciseIcon(exerciseName),
                        contentDescription = exerciseName,
                        tint = BrandLime,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("EXERCISE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(exerciseName, color = ComposeColor.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(ComposeColor.White.copy(alpha = 0.05f))
                    .border(1.dp, ComposeColor.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { secondsLeft.toFloat() / maxSeconds.toFloat() },
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    color = BrandLime,
                    strokeWidth = 3.dp,
                    trackColor = ComposeColor.White.copy(alpha = 0.1f)
                )
                Text("${secondsLeft}s", color = ComposeColor.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Floating Bottom HUD Panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(ComposeColor.Black.copy(alpha = 0.7f))
                .border(1.dp, ComposeColor.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("REPS", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("$reps", color = ComposeColor.White, fontSize = 48.sp, fontWeight = FontWeight.Black)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("FEEDBACK", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(feedbackColor.copy(alpha = 0.15f))
                            .border(1.dp, feedbackColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = feedback,
                            color = feedbackColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            TrainiumButton(
                text = "SKIP EXERCISE",
                onClick = onSkip,
                icon = {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip",
                        tint = ComposeColor.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}
