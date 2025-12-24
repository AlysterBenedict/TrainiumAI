package com.example.aifitnesscoach

// --- REQUIRED IMPORTS ---
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
// UPDATED: Import the new binding class
import com.example.aifitnesscoach.databinding.ActivityWorkoutBinding
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2

class WorkoutActivity : AppCompatActivity(), PoseLandmarkerHelper.ResultListener {

    // UPDATED: Use the new ActivityWorkoutBinding
    private lateinit var binding: ActivityWorkoutBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper

    // --- Workout State Variables ---
    private lateinit var workoutPlan: ArrayList<String>
    private var exerciseDuration: Long = 0
    private var restDuration: Long = 0
    private var currentExerciseIndex = 0
    private var countDownTimer: CountDownTimer? = null

    // --- Rep Counting State Variables (RESTORED) ---
    private lateinit var currentExerciseConfig: ExerciseConfig
    private var repCounter = 0
    private var exerciseStage = "" // e.g., "up" or "down"
    private var feedbackText = "Get Ready"
    private var jointColor = Color.GREEN

    // --- Custom exercise state variables ---
    private var burpeeStage = "start"
    private var twistStage = "center"
    private var hasTwistedLeft = false
    private var hasTwistedRight = false

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        const val LEFT_HIP = 23; const val LEFT_KNEE = 25; const val LEFT_ANKLE = 27
        const val RIGHT_HIP = 24; const val RIGHT_KNEE = 26; const val RIGHT_ANKLE = 28
        const val LEFT_SHOULDER = 11; const val LEFT_ELBOW = 13; const val LEFT_WRIST = 15
        const val RIGHT_SHOULDER = 12; const val RIGHT_ELBOW = 14; const val RIGHT_WRIST = 16
        const val LEFT_HEEL = 29; const val RIGHT_HEEL = 30; const val NOSE = 0;
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // UPDATED: Inflate the new binding class
        binding = ActivityWorkoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // --- Retrieve workout plan from Intent ---
        workoutPlan = intent.getStringArrayListExtra(Constants.EXTRA_WORKOUT_PLAN) ?: ArrayList()
        currentExerciseIndex = intent.getIntExtra(Constants.EXTRA_CURRENT_INDEX, 0)
        exerciseDuration = intent.getLongExtra(Constants.EXTRA_EXERCISE_DURATION, 30000)
        restDuration = intent.getLongExtra(Constants.EXTRA_REST_DURATION, 15000)

        if (currentExerciseIndex >= workoutPlan.size) {
            workoutComplete()
            return
        }

        val currentExerciseName = workoutPlan[currentExerciseIndex]
        currentExerciseConfig = Exercises.list.find { it.name == currentExerciseName }!!

        binding.exerciseTimerText.text = "${exerciseDuration / 1000}s"

        // --- Reset State for the new exercise ---
        resetExerciseState()
        updateUI()

        binding.skipExerciseButton.setOnClickListener { goToRest() }
        binding.viewFinder.post {
            if (allPermissionsGranted()) {
                setupPoseLandmarker()
                startCamera()
                startTimer(exerciseDuration)
            } else {
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                )
            }
        }
    }

    private fun resetExerciseState() {
        repCounter = 0
        exerciseStage = if (currentExerciseConfig.invertStages) "down" else "up"
        feedbackText = "Ready"
        burpeeStage = "start"
        twistStage = "center"
        hasTwistedLeft = false
        hasTwistedRight = false
    }

    private fun startTimer(duration: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000 + 1
                binding.exerciseTimerText.text = "${secondsLeft}s"
            }
            override fun onFinish() {
                goToRest()
            }
        }.start()
    }

    private fun goToRest() {
        countDownTimer?.cancel()
        val nextIndex = currentExerciseIndex + 1

        if (nextIndex >= workoutPlan.size) {
            workoutComplete()
        } else {
            val nextExerciseName = workoutPlan.getOrNull(nextIndex)
            val intent = Intent(this, RestActivity::class.java).apply {
                putExtra(Constants.EXTRA_REST_DURATION, restDuration)
                putExtra(Constants.EXTRA_NEXT_EXERCISE_NAME, nextExerciseName)
                putStringArrayListExtra(Constants.EXTRA_WORKOUT_PLAN, workoutPlan)
                putExtra(Constants.EXTRA_CURRENT_INDEX, nextIndex)
                putExtra(Constants.EXTRA_EXERCISE_DURATION, exerciseDuration)
            }
            startActivity(intent)
            finish()
        }
    }
    private fun workoutComplete() {
        startActivity(Intent(this, WorkoutCompleteActivity::class.java))
        finish()
    }

    private fun updateUI() {
        binding.exerciseNameText.text = currentExerciseConfig.name
        binding.repsCounterText.text = repCounter.toString()
        binding.feedbackText.text = feedbackText
    }

    private fun processExercise(poseLandmarks: List<NormalizedLandmark>) {
        currentExerciseConfig.postureValidation?.let { pvConfig ->
            val p1 = Landmark(poseLandmarks[pvConfig.landmarksToTrack[0]].x(), poseLandmarks[pvConfig.landmarksToTrack[0]].y())
            val p2 = Landmark(poseLandmarks[pvConfig.landmarksToTrack[1]].x(), poseLandmarks[pvConfig.landmarksToTrack[1]].y())
            val p3 = Landmark(poseLandmarks[pvConfig.landmarksToTrack[2]].x(), poseLandmarks[pvConfig.landmarksToTrack[2]].y())
            val postureAngle = calculateAngle(p1, p2, p3)

            if (postureAngle > pvConfig.angleThreshold) {
                feedbackText = pvConfig.feedbackIncorrect
                jointColor = Color.RED
                updateUI()
                return
            }
        }

        when (currentExerciseConfig.exerciseType) {
            "rep_based" -> {
                val landmarks = currentExerciseConfig.landmarksToTrack.map { Landmark(poseLandmarks[it].x(), poseLandmarks[it].y()) }
                val angle1 = calculateAngle(landmarks[0], landmarks[1], landmarks[2])
                val angle2 = if (landmarks.size > 3) calculateAngle(landmarks[3], landmarks[4], landmarks[5]) else angle1

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
                        exerciseStage = "up"
                        feedbackText = feedbackMap["up"] ?: "Ready"
                        jointColor = Color.rgb(245, 117, 66)
                    } else if (angleToUse < downThreshold && exerciseStage == "up") {
                        exerciseStage = "down"
                        repCounter++
                        feedbackText = feedbackMap["down"] ?: "Good Rep"
                        jointColor = Color.GREEN
                    } else {
                        if (exerciseStage == "up") feedbackText = feedbackMap["transition_down"] ?: "Go Down"
                        else feedbackText = feedbackMap["transition_up"] ?: "Go Up"
                        jointColor = Color.RED
                    }
                } else {
                    if (angleToUse < downThreshold) {
                        exerciseStage = "down"
                        feedbackText = feedbackMap["down"] ?: "Ready"
                        jointColor = Color.rgb(245, 117, 66)
                    } else if (angleToUse > upThreshold && exerciseStage == "down") {
                        exerciseStage = "up"
                        repCounter++
                        feedbackText = feedbackMap["up"] ?: "Good Rep"
                        jointColor = Color.GREEN
                    } else {
                        if (exerciseStage == "down") feedbackText = feedbackMap["transition_up"] ?: "Go Up"
                        else feedbackText = feedbackMap["transition_down"] ?: "Go Down"
                        jointColor = Color.RED
                    }
                }
            }
            "timed" -> {
                val landmarks = currentExerciseConfig.landmarksToTrack.map { Landmark(poseLandmarks[it].x(), poseLandmarks[it].y()) }
                val angle = calculateAngle(landmarks[0], landmarks[1], landmarks[2])
                val correctFormAngle = currentExerciseConfig.correctFormAngle!!
                val isCorrect = if (currentExerciseConfig.invertStages) angle < correctFormAngle else angle > correctFormAngle

                if (isCorrect) {
                    feedbackText = currentExerciseConfig.feedbackMap["correct"] ?: "Hold Position"
                    jointColor = Color.GREEN
                } else {
                    feedbackText = currentExerciseConfig.feedbackMap["incorrect"] ?: "Incorrect Form"
                    jointColor = Color.RED
                }
            }
            "knee_height" -> {
                val leftKneeY = poseLandmarks[LEFT_KNEE].y()
                val leftHipY = poseLandmarks[LEFT_HIP].y()
                val rightKneeY = poseLandmarks[RIGHT_KNEE].y()
                val rightHipY = poseLandmarks[RIGHT_HIP].y()

                if ((leftKneeY < leftHipY || rightKneeY < rightHipY) && exerciseStage == "down") {
                    exerciseStage = "up"
                    feedbackText = currentExerciseConfig.feedbackMap["up"] ?: "Good!"
                    jointColor = Color.GREEN
                    repCounter++
                } else if (leftKneeY > leftHipY && rightKneeY > rightHipY) {
                    exerciseStage = "down"
                    feedbackText = currentExerciseConfig.feedbackMap["down"] ?: "Drive Knee Up!"
                    jointColor = Color.rgb(245, 117, 66)
                }
            }
            "pull_up" -> {
                val noseY = poseLandmarks[NOSE].y()
                val barY = minOf(poseLandmarks[LEFT_WRIST].y(), poseLandmarks[RIGHT_WRIST].y())

                if (noseY < barY && exerciseStage == "down") {
                    exerciseStage = "up"
                    feedbackText = currentExerciseConfig.feedbackMap["up"] ?: "Good Rep!"
                    jointColor = Color.GREEN
                    repCounter++
                } else if (noseY > barY) {
                    exerciseStage = "down"
                    feedbackText = currentExerciseConfig.feedbackMap["down"] ?: "Pull Up!"
                    jointColor = Color.rgb(245, 117, 66)
                }
            }
            "bird_dog" -> {
                val leftWrist = Landmark(poseLandmarks[LEFT_WRIST].x(), poseLandmarks[LEFT_WRIST].y())
                val rightKnee = Landmark(poseLandmarks[RIGHT_KNEE].x(), poseLandmarks[RIGHT_KNEE].y())
                val dist = Math.sqrt(Math.pow((leftWrist.x - rightKnee.x).toDouble(), 2.0) + Math.pow((leftWrist.y - rightKnee.y).toDouble(), 2.0))

                if (dist > currentExerciseConfig.thresholds["extended"]!! && exerciseStage == "in") {
                    exerciseStage = "out"
                    feedbackText = currentExerciseConfig.feedbackMap["out"] ?: "Extend!"
                    jointColor = Color.GREEN
                    repCounter++
                } else if (dist < currentExerciseConfig.thresholds["contracted"]!!) {
                    exerciseStage = "in"
                    feedbackText = currentExerciseConfig.feedbackMap["in"] ?: "Return"
                    jointColor = Color.rgb(245, 117, 66)
                }
            }
            "russian_twist" -> {
                val leftShoulder = Landmark(poseLandmarks[LEFT_SHOULDER].x(), poseLandmarks[LEFT_SHOULDER].y())
                val rightShoulder = Landmark(poseLandmarks[RIGHT_SHOULDER].x(), poseLandmarks[RIGHT_SHOULDER].y())

                val shouldersVecX = rightShoulder.x - leftShoulder.x
                val shouldersVecY = rightShoulder.y - leftShoulder.y
                val angleShoulders = Math.toDegrees(atan2(shouldersVecY.toDouble(), shouldersVecX.toDouble()))

                val leftThresh = currentExerciseConfig.thresholds["left"]!!
                val rightThresh = currentExerciseConfig.thresholds["right"]!!

                if (angleShoulders < leftThresh && twistStage == "center") {
                    twistStage = "left"
                    feedbackText = currentExerciseConfig.feedbackMap["left"] ?: "Twist Left"
                    jointColor = Color.YELLOW
                } else if (angleShoulders > rightThresh && twistStage == "center") {
                    twistStage = "right"
                    feedbackText = currentExerciseConfig.feedbackMap["right"] ?: "Twist Right"
                    jointColor = Color.YELLOW
                } else if (angleShoulders in (leftThresh + 1)..(rightThresh - 1)) {
                    if (twistStage == "left") hasTwistedLeft = true
                    else if (twistStage == "right") hasTwistedRight = true
                    twistStage = "center"
                    feedbackText = "Center"
                    jointColor = Color.rgb(245, 117, 66)
                }

                if (hasTwistedLeft && hasTwistedRight) {
                    repCounter++
                    feedbackText = "Good Rep!"
                    jointColor = Color.GREEN
                    hasTwistedLeft = false
                    hasTwistedRight = false
                }
            }
            "mountain_climber" -> {
                val leftKnee = Landmark(poseLandmarks[LEFT_KNEE].x(), poseLandmarks[LEFT_KNEE].y())
                val leftElbow = Landmark(poseLandmarks[LEFT_ELBOW].x(), poseLandmarks[LEFT_ELBOW].y())
                val dist = Math.sqrt(Math.pow((leftKnee.x - leftElbow.x).toDouble(), 2.0) + Math.pow((leftKnee.y - leftElbow.y).toDouble(), 2.0))

                if (dist < currentExerciseConfig.thresholds["close"]!! && exerciseStage == "back") {
                    exerciseStage = "forward"
                    feedbackText = currentExerciseConfig.feedbackMap["forward"] ?: "Knee to Elbow!"
                    jointColor = Color.GREEN
                    repCounter++
                } else if (dist > currentExerciseConfig.thresholds["far"]!!) {
                    exerciseStage = "back"
                    feedbackText = currentExerciseConfig.feedbackMap["back"] ?: "Switch"
                    jointColor = Color.rgb(245, 117, 66)
                }
            }
            "burpee" -> {
                val squatAngle = calculateAngle(
                    Landmark(poseLandmarks[LEFT_HIP].x(), poseLandmarks[LEFT_HIP].y()),
                    Landmark(poseLandmarks[LEFT_KNEE].x(), poseLandmarks[LEFT_KNEE].y()),
                    Landmark(poseLandmarks[LEFT_ANKLE].x(), poseLandmarks[LEFT_ANKLE].y())
                )
                val plankAngle = calculateAngle(
                    Landmark(poseLandmarks[LEFT_SHOULDER].x(), poseLandmarks[LEFT_SHOULDER].y()),
                    Landmark(poseLandmarks[LEFT_HIP].x(), poseLandmarks[LEFT_HIP].y()),
                    Landmark(poseLandmarks[LEFT_ANKLE].x(), poseLandmarks[LEFT_ANKLE].y())
                )
                val pushupAngle = calculateAngle(
                    Landmark(poseLandmarks[LEFT_SHOULDER].x(), poseLandmarks[LEFT_SHOULDER].y()),
                    Landmark(poseLandmarks[LEFT_ELBOW].x(), poseLandmarks[LEFT_ELBOW].y()),
                    Landmark(poseLandmarks[LEFT_WRIST].x(), poseLandmarks[LEFT_WRIST].y())
                )

                if (burpeeStage == "start" && squatAngle < 100) {
                    burpeeStage = "squat"
                    feedbackText = "Down to Plank"
                } else if (burpeeStage == "squat" && plankAngle > 160) {
                    burpeeStage = "plank"
                    feedbackText = "Push-up"
                } else if (burpeeStage == "plank" && pushupAngle < 90) {
                    burpeeStage = "pushup"
                    feedbackText = "Back to Squat"
                } else if (burpeeStage == "pushup" && squatAngle < 100 && plankAngle < 150) {
                    burpeeStage = "return_squat"
                    feedbackText = "Jump Up!"
                } else if (burpeeStage == "return_squat" && squatAngle > 165) {
                    burpeeStage = "start"
                    repCounter++
                    feedbackText = "Good Rep!"
                }
            }
            "plank_jacks" -> {
                val leftAnkleX = poseLandmarks[LEFT_ANKLE].x()
                val rightAnkleX = poseLandmarks[RIGHT_ANKLE].x()
                val ankleDist = abs(leftAnkleX - rightAnkleX)

                if (ankleDist > currentExerciseConfig.thresholds["out"]!! && exerciseStage == "in") {
                    exerciseStage = "out"
                    feedbackText = currentExerciseConfig.feedbackMap["out"] ?: "Legs Out!"
                    jointColor = Color.GREEN
                    repCounter++
                } else if (ankleDist < currentExerciseConfig.thresholds["in"]!!) {
                    exerciseStage = "in"
                    feedbackText = currentExerciseConfig.feedbackMap["in"] ?: "Legs In!"
                    jointColor = Color.rgb(245, 117, 66)
                }
            }
            "shoulder_taps" -> {
                val leftWrist = Landmark(poseLandmarks[LEFT_WRIST].x(), poseLandmarks[LEFT_WRIST].y())
                val rightShoulder = Landmark(poseLandmarks[RIGHT_SHOULDER].x(), poseLandmarks[RIGHT_SHOULDER].y())
                val dist = Math.sqrt(Math.pow((leftWrist.x - rightShoulder.x).toDouble(), 2.0) + Math.pow((leftWrist.y - rightShoulder.y).toDouble(), 2.0))

                if (dist < currentExerciseConfig.thresholds["tap"]!! && exerciseStage == "down") {
                    exerciseStage = "up"
                    feedbackText = currentExerciseConfig.feedbackMap["tap"] ?: "Tap!"
                    jointColor = Color.GREEN
                    repCounter++
                } else if (dist > currentExerciseConfig.thresholds["release"]!!) {
                    exerciseStage = "down"
                    feedbackText = currentExerciseConfig.feedbackMap["release"] ?: "Return Hand"
                    jointColor = Color.rgb(245, 117, 66)
                }
            }
        }
        updateUI()
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        runOnUiThread {
            if (resultBundle.results.isEmpty() || resultBundle.results.first().landmarks().isEmpty()) {
                binding.overlay.clear()
                feedbackText = "No pose detected"
                updateUI()
                return@runOnUiThread
            }
            val poseLandmarks = resultBundle.results.first().landmarks().first()
            processExercise(poseLandmarks)
            binding.overlay.setResults(
                resultBundle.results.first(),
                resultBundle.inputImageHeight,
                resultBundle.inputImageWidth,
                RunningMode.LIVE_STREAM,
                jointColor
            )
        }
    }

    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        cameraExecutor.shutdown()
    }

    private fun setupPoseLandmarker() {
        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = this,
            runningMode = RunningMode.LIVE_STREAM,
            poseLandmarkerHelperListener = this
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
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
            } catch(exc: Exception) {
                Log.e("WorkoutActivity", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                setupPoseLandmarker()
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
