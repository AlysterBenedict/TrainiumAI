package com.example.aifitnesscoach

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import com.example.aifitnesscoach.network.WorkoutLog
import com.example.aifitnesscoach.network.FirebaseSyncHelper
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient

enum class WorkoutMode {
    TRAINER,
    SELF,
    TUTORIAL
}

class WorkoutActivity_ui : AppCompatActivity(), PoseLandmarkerHelper_func.ResultListener {

    private var dayTitle: String = "Day_1"
    private var totalWorkoutDurationSeconds = 0
    private var totalCaloriesBurned = 0f
    private var accumulatedDurationSeconds = 0
    private var accumulatedCalories = 0f
    private var userWeightKg = 70f

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
    private var currentModeState = mutableStateOf(WorkoutMode.TRAINER)
    private var showGetReadyPopupState = mutableStateOf(false)
    private var getReadySecondsLeftState = mutableStateOf(3)
    private var getReadyTimer: CountDownTimer? = null
    private var getReadyOnFinishedCallback: (() -> Unit)? = null
    private var wasRunningBeforeLifecyclePause = false
    private var wasGetReadyActiveBeforeLifecyclePause = false
    private var toneGenerator: ToneGenerator? = null
    private var hasBeepedStart = false

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
        dayTitle = intent.getStringExtra(Constants_func.EXTRA_DAY_TITLE) ?: "Day_1"
        accumulatedDurationSeconds = intent.getIntExtra("ACCUMULATED_DURATION_SECONDS", 0)
        accumulatedCalories = intent.getFloatExtra("ACCUMULATED_CALORIES", 0f)
        userWeightKg = com.example.aifitnesscoach.network.FirebaseSyncHelper.getGlobalUserData(this).weightKg.let { if (it > 0f) it else 70f }
 
        if (currentExerciseIndex >= workoutPlan.size) {
            val isCustomWorkout = intent.getBooleanExtra("IS_CUSTOM_WORKOUT", false)
            workoutCompleteCustomOrNormal(isCustomWorkout, accumulatedDurationSeconds, accumulatedCalories)
            return
        }

        val currentExerciseName = workoutPlan[currentExerciseIndex]
        var foundConfig = Exercises_func.list.find { it.name.equals(currentExerciseName, ignoreCase = true) }
        if (foundConfig == null) {
            val mappedName = when (currentExerciseName.trim().lowercase()) {
                "deep squats", "deep squat" -> "SQUAT"
                "push-ups", "pushups", "pushup", "push-up" -> "PUSH-UP"
                "plank hold", "plank" -> "PLANK"
                "jumping jacks" -> "JUMPING JACKS"
                "glute bridges", "glute bridge" -> "GLUTE BRIDGE"
                "lying leg raises", "leg raises", "leg raise" -> "LEG RAISES"
                "mountain climbers", "mountain climber" -> "MOUNTAIN CLIMBER"
                "bodyweight lunges", "lunges", "lunge" -> "LUNGE"
                "superman hold", "superman" -> "SUPERMAN"
                "burpees", "burpee" -> "BURPEES"
                else -> null
            }
            if (mappedName != null) {
                foundConfig = Exercises_func.list.find { it.name == mappedName }
            }
        }
        if (foundConfig == null) {
            foundConfig = Exercises_func.list.find {
                val cleanName = currentExerciseName.uppercase().replace("-", " ").replace(" ", "")
                val cleanList = it.name.uppercase().replace("-", " ").replace(" ", "")
                cleanName.contains(cleanList) || cleanList.contains(cleanName)
            }
        }
        currentExerciseConfig = foundConfig ?: ExerciseConfig_func(
            name = currentExerciseName,
            landmarksToTrack = emptyList(),
            exerciseType = "timed",
            correctFormAngle = 0.0,
            feedbackMap = mapOf(
                "correct" to "Keep going!",
                "incorrect" to "Keep going!"
            )
        )

        val isTimed = currentExerciseConfig.exerciseType == "timed"
        if (isTimed) {
            val durationStr = WorkoutProgressHelper.getExerciseDurationOrReps(this, currentExerciseName)
            val parts = durationStr.split(":")
            if (parts.size == 2) {
                val minutes = parts[0].toLongOrNull() ?: 0L
                val seconds = parts[1].toLongOrNull() ?: 0L
                exerciseDuration = (minutes * 60 + seconds) * 1000L
            }
        }

        timerSecondsState.value = exerciseDuration / 1000
        remainingTimeMillis = exerciseDuration

        val initialModeName = intent.getStringExtra("WORKOUT_MODE") ?: WorkoutMode.TRAINER.name
        try {
            currentModeState.value = WorkoutMode.valueOf(initialModeName)
        } catch (e: Exception) {
            currentModeState.value = WorkoutMode.TRAINER
        }

        resetExerciseState()
        updateUI()

        setContent {
            TrainiumTheme {
                val currentMode = currentModeState.value

                var previousMode by remember { mutableStateOf(currentMode) }

                LaunchedEffect(currentMode) {
                    if (currentMode == WorkoutMode.TUTORIAL) {
                        getReadyTimer?.cancel()
                        showGetReadyPopupState.value = false
                        pauseTimer()
                    } else {
                        if (previousMode == WorkoutMode.TUTORIAL) {
                            startGetReadyCountdown {
                                resumeTimer()
                            }
                        }
                    }
                    previousMode = currentMode
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundBlack)
                ) {
                    if (currentMode != WorkoutMode.TUTORIAL) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Spacer above camera preview / content
                            Spacer(modifier = Modifier.height(112.dp))

                            // Camera container resized to 3:4 aspect ratio just below the exercise card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(3f / 4f)
                                    .clipToBounds()
                            ) {
                                when (currentMode) {
                                    WorkoutMode.TRAINER -> {
                                        // 1. Camera View Finder
                                        AndroidView(
                                            factory = { ctx ->
                                                PreviewView(ctx).apply {
                                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize().clipToBounds(),
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
                                            modifier = Modifier.fillMaxSize().clipToBounds()
                                        )
                                    }
                                    WorkoutMode.SELF -> {
                                        SelfModeContent(
                                            exerciseName = exerciseNameState.value,
                                            details = getExerciseDetails(exerciseNameState.value)
                                        )
                                    }
                                    WorkoutMode.TUTORIAL -> {
                                        // Unreachable but required by Kotlin when statement
                                    }
                                }

                                // Mode selection pill overlayed on the bottom center of the camera viewfinder (just on top of bottom block)
                                WorkoutModeSelector(
                                    currentMode = currentMode,
                                    onModeSelected = { currentModeState.value = it },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 16.dp)
                                )
                            }

                            // 3. Bottom HUD Panel (Reps, feedback, button) - starts exactly below camera preview
                            WorkoutBottomPanel(
                                reps = repsState.value,
                                feedback = if (currentMode == WorkoutMode.SELF) "Perform at your own pace" else feedbackState.value,
                                feedbackColor = if (currentMode == WorkoutMode.SELF) BrandLime else feedColorState.value,
                                isTimed = currentExerciseConfig.exerciseType == "timed",
                                showReps = currentMode != WorkoutMode.SELF,
                                onCompleted = { goToRest() },
                                onSkip = { goToRest() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }

                        // Floating Top HUD Panel
                        WorkoutTopPanel(
                            exerciseName = exerciseNameState.value,
                            secondsLeft = timerSecondsState.value,
                            maxSeconds = exerciseDuration / 1000,
                            isTimed = currentExerciseConfig.exerciseType == "timed",
                            isPaused = isTimerPausedState.value,
                            onPauseToggle = {
                                if (isTimerPausedState.value) {
                                    resumeTimer()
                                } else {
                                    pauseTimer()
                                }
                            },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    } else {
                        // Tutorial Screen Content (scrollable, features YouTube video and steps)
                        TutorialScreenContent(
                            exerciseName = exerciseNameState.value,
                            currentMode = currentMode,
                            onModeSelected = { currentModeState.value = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (showGetReadyPopupState.value) {
                        GetReadyCountdownOverlay(
                            secondsLeft = getReadySecondsLeftState.value
                        )
                    }
                }
            }
        }

        val startMode = currentModeState.value
        if (startMode != WorkoutMode.TUTORIAL) {
            val isFirstExercise = currentExerciseIndex == 0
            val isRestSkipped = intent.getBooleanExtra("IS_REST_SKIPPED", false)
            if (isFirstExercise || isRestSkipped) {
                startGetReadyCountdown {
                    startTimer(exerciseDuration)
                }
            } else {
                showGetReadyPopupState.value = false
                isTimerPaused = false
                startTimer(exerciseDuration)
            }
        } else {
            isTimerPaused = true
        }
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

    private fun saveProgress() {
        val isCustomWorkout = intent.getBooleanExtra("IS_CUSTOM_WORKOUT", false)
        if (isCustomWorkout) {
            totalWorkoutDurationSeconds = 0
            totalCaloriesBurned = 0f
            return
        }
        val rate = WorkoutProgressHelper.getCalorieBurnRate(currentExerciseConfig.name, userWeightKg)
        val isTimed = currentExerciseConfig.exerciseType == "timed"
        val exerciseCalories = if (isTimed) {
            totalWorkoutDurationSeconds * rate
        } else {
            val repsToUse = if (internalRepCounter > 0) internalRepCounter else 16
            val pace = totalWorkoutDurationSeconds.toFloat() / repsToUse.toFloat()
            16f * pace * rate
        }

        val dateString = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val durKey = "DURATION_${dayTitle}_${dateString}"
        val calKey = "CALORIES_${dayTitle}_${dateString}"
        
        val sharedPrefs = getTrainiumPrefs("app_prefs")
        val todayDur = sharedPrefs.getInt(durKey, 0) + totalWorkoutDurationSeconds
        val todayCal = sharedPrefs.getFloat(calKey, 0f) + exerciseCalories
        
        sharedPrefs.edit()
            .putInt(durKey, todayDur)
            .putFloat(calKey, todayCal)
            .apply()

        WorkoutProgressHelper.markExerciseCompleted(
            this,
            dayTitle,
            currentExerciseIndex,
            totalWorkoutDurationSeconds,
            exerciseCalories
        )
        
        // Log this day to the database/Firebase incrementally
        val log = WorkoutLog(
            id = "30day_${dayTitle}_${dateString}",
            workoutName = "${dayTitle.replace("_", " ")} Workout",
            timestamp = System.currentTimeMillis(),
            durationSeconds = todayDur,
            caloriesBurned = todayCal,
            accuracy = 90
        )
        FirebaseSyncHelper.addOrUpdateWorkout(this, log)

        totalWorkoutDurationSeconds = 0
        totalCaloriesBurned = 0f
    }

    private var isTimerPausedState = mutableStateOf(false)
    private var isTimerPaused: Boolean
        get() = isTimerPausedState.value
        set(value) {
            isTimerPausedState.value = value
        }
    private var remainingTimeMillis: Long = 0

    private fun pauseTimer() {
        if (isTimerPaused) return
        isTimerPaused = true
        countDownTimer?.cancel()
        val isTimed = currentExerciseConfig.exerciseType == "timed"
        if (isTimed) {
            remainingTimeMillis = timerSecondsState.value * 1000
        }
    }

    private fun resumeTimer() {
        if (!isTimerPaused) return
        val isTimed = currentExerciseConfig.exerciseType == "timed"
        if (isTimed) {
            startTimer(remainingTimeMillis)
        } else {
            startTimer(86400000L)
        }
    }

    private fun startGetReadyCountdown(seconds: Int = 3, onFinished: () -> Unit) {
        getReadyOnFinishedCallback = onFinished
        getReadyTimer?.cancel()
        getReadySecondsLeftState.value = seconds
        showGetReadyPopupState.value = true
        isTimerPaused = true

        getReadyTimer = object : CountDownTimer(seconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                getReadySecondsLeftState.value = (millisUntilFinished / 1000 + 1).toInt()
            }
            override fun onFinish() {
                showGetReadyPopupState.value = false
                getReadyOnFinishedCallback = null
                onFinished()
            }
        }.start()
    }

    private fun playHighPitchBeep(frequency: Double, durationMs: Int) {
        try {
            val sampleRate = 44100
            val numSamples = (durationMs * sampleRate / 1000)
            val buffer = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val sample = Math.sin(2.0 * Math.PI * i.toDouble() / (sampleRate / frequency))
                buffer[i] = (sample * 32767).toInt().toShort()
            }

            // Envelope (fade-in & fade-out 5% of samples to prevent clicking/popping)
            val fadeSamples = (numSamples * 0.05).toInt()
            for (i in 0 until fadeSamples) {
                val ramp = i.toDouble() / fadeSamples
                buffer[i] = (buffer[i] * ramp).toInt().toShort()
                buffer[numSamples - 1 - i] = (buffer[numSamples - 1 - i] * ramp).toInt().toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()

            // Block current thread for the duration of the beep plus a small buffer
            Thread.sleep(durationMs.toLong() + 50)
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            Log.e("WorkoutActivity_ui", "Failed to play high pitch beep: ${e.message}", e)
        }
    }

    private fun playStartBeep() {
        Thread {
            playHighPitchBeep(2500.0, 500)
        }.start()
    }

    private fun playEndBeep() {
        Thread {
            playHighPitchBeep(2500.0, 250)
            Thread.sleep(100) // gap between tones
            playHighPitchBeep(2500.0, 400)
        }.start()
    }

    private fun startTimer(duration: Long) {
        if (!hasBeepedStart) {
            playStartBeep()
            hasBeepedStart = true
        }
        isTimerPaused = false
        countDownTimer?.cancel()
        val isTimed = currentExerciseConfig.exerciseType == "timed"
        if (isTimed) {
            countDownTimer = object : CountDownTimer(duration, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    timerSecondsState.value = millisUntilFinished / 1000 + 1
                    totalWorkoutDurationSeconds++
                }
                override fun onFinish() { goToRest() }
            }.start()
        } else {
            // Non-timed: count up seconds in background without auto-finishing
            countDownTimer = object : CountDownTimer(86400000L, 1000) { // 24 hours
                override fun onTick(millisUntilFinished: Long) {
                    totalWorkoutDurationSeconds++
                }
                override fun onFinish() {}
            }.start()
        }
    }

    private fun goToRest() {
        playEndBeep()
        countDownTimer?.cancel()
        
        val isTimed = currentExerciseConfig.exerciseType == "timed"
        val rate = WorkoutProgressHelper.getCalorieBurnRate(currentExerciseConfig.name, userWeightKg)
        totalCaloriesBurned = if (isTimed) {
            totalWorkoutDurationSeconds * rate
        } else {
            val repsToUse = if (internalRepCounter > 0) internalRepCounter else 16
            val pace = totalWorkoutDurationSeconds.toFloat() / repsToUse.toFloat()
            16f * pace * rate
        }

        val isCustomWorkout = intent.getBooleanExtra("IS_CUSTOM_WORKOUT", false)
        val nextDuration = accumulatedDurationSeconds + totalWorkoutDurationSeconds
        val nextCalories = accumulatedCalories + totalCaloriesBurned
        
        if (!isCustomWorkout) {
            saveProgress()
        } else {
            totalWorkoutDurationSeconds = 0
            totalCaloriesBurned = 0f
        }
        
        val nextIndex = if (isCustomWorkout) {
            currentExerciseIndex + 1
        } else {
            val sharedPrefs = getTrainiumPrefs("app_prefs")
            val completedSet = sharedPrefs.getStringSet("COMPLETED_EXERCISES_$dayTitle", emptySet()) ?: emptySet()
            
            var found = -1
            // Look forward first
            for (i in (currentExerciseIndex + 1) until workoutPlan.size) {
                if (!completedSet.contains(i.toString())) {
                    found = i
                    break
                }
            }
            // Wrap around check
            if (found == -1) {
                for (i in 0..currentExerciseIndex) {
                    if (!completedSet.contains(i.toString())) {
                        found = i
                        break
                    }
                }
            }
            found
        }
 
        if (nextIndex == -1 || (isCustomWorkout && nextIndex >= workoutPlan.size)) {
            workoutCompleteCustomOrNormal(isCustomWorkout, nextDuration, nextCalories)
        } else {
            val nextExerciseName = workoutPlan.getOrNull(nextIndex)
            val intent = Intent(this, RestActivity_ui::class.java).apply {
                putExtra(Constants_func.EXTRA_REST_DURATION, restDuration)
                putExtra(Constants_func.EXTRA_NEXT_EXERCISE_NAME, nextExerciseName)
                putStringArrayListExtra(Constants_func.EXTRA_WORKOUT_PLAN, workoutPlan)
                putExtra(Constants_func.EXTRA_CURRENT_INDEX, nextIndex)
                putExtra(Constants_func.EXTRA_EXERCISE_DURATION, exerciseDuration)
                putExtra(Constants_func.EXTRA_DAY_TITLE, dayTitle) // Propagate dayTitle
                putExtra("IS_CUSTOM_WORKOUT", isCustomWorkout)
                putExtra("ACCUMULATED_DURATION_SECONDS", nextDuration)
                putExtra("ACCUMULATED_CALORIES", nextCalories)
                putExtra("WORKOUT_MODE", currentModeState.value.name)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun workoutCompleteCustomOrNormal(isCustom: Boolean, finalDur: Int, finalCal: Float) {
        if (isCustom) {
            val log = com.example.aifitnesscoach.network.WorkoutLog(
                id = "custom_${System.currentTimeMillis()}",
                workoutName = "Custom Workout",
                timestamp = System.currentTimeMillis(),
                durationSeconds = finalDur,
                caloriesBurned = finalCal,
                accuracy = 90
            )
            com.example.aifitnesscoach.network.FirebaseSyncHelper.addWorkout(this, log)

            val intent = Intent(this, WorkoutCompleteActivity_ui::class.java).apply {
                putExtra("EXTRA_DURATION_MINUTES", Math.max(1, finalDur / 60))
                putExtra("EXTRA_CALORIES_BURNED", finalCal)
                putExtra("IS_CUSTOM_WORKOUT", true)
            }
            startActivity(intent)
            finish()
            return
        }

        val sharedPrefs = getTrainiumPrefs("app_prefs")
        val finalDurSeconds = sharedPrefs.getInt("DURATION_$dayTitle", 0)
        val finalCalories = sharedPrefs.getFloat("CALORIES_$dayTitle", 0f)
  
        // Mark all exercises of this day fully completed
        val exercisesCount = workoutPlan.size
        val allCheckedSet = (0 until exercisesCount).map { it.toString() }.toSet()
        sharedPrefs.edit().putStringSet("COMPLETED_EXERCISES_$dayTitle", allCheckedSet).apply()
  
        // Create/Update final log entry
        val dateString = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val durKey = "DURATION_${dayTitle}_${dateString}"
        val calKey = "CALORIES_${dayTitle}_${dateString}"
        val todayDur = sharedPrefs.getInt(durKey, 0)
        val todayCal = sharedPrefs.getFloat(calKey, 0f)

        val log = com.example.aifitnesscoach.network.WorkoutLog(
            id = "30day_${dayTitle}_${dateString}",
            workoutName = "${dayTitle.replace("_", " ")} Workout",
            timestamp = System.currentTimeMillis(),
            durationSeconds = todayDur,
            caloriesBurned = todayCal,
            accuracy = 90
        )
        com.example.aifitnesscoach.network.FirebaseSyncHelper.addOrUpdateWorkout(this, log)
  
        val intent = Intent(this, WorkoutCompleteActivity_ui::class.java).apply {
            putExtra("EXTRA_DURATION_MINUTES", Math.max(1, finalDurSeconds / 60))
            putExtra("EXTRA_CALORIES_BURNED", finalCalories)
        }
        startActivity(intent)
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
                } else {
                    internalFeedbackText = currentExerciseConfig.feedbackMap["correct"] ?: "Keep going!"
                    jointColor = Color.GREEN
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
            if (isTimerPaused) {
                overlayView?.clear()
                return@runOnUiThread
            }
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

    override fun onPause() {
        super.onPause()
        if (showGetReadyPopupState.value) {
            getReadyTimer?.cancel()
            wasGetReadyActiveBeforeLifecyclePause = true
        } else {
            wasGetReadyActiveBeforeLifecyclePause = false
        }

        if (currentModeState.value != WorkoutMode.TUTORIAL && !isTimerPausedState.value) {
            wasRunningBeforeLifecyclePause = true
            pauseTimer()
        } else {
            wasRunningBeforeLifecyclePause = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (wasGetReadyActiveBeforeLifecyclePause) {
            val remainingSeconds = getReadySecondsLeftState.value
            val callback = getReadyOnFinishedCallback ?: {
                if (currentExerciseIndex == 0) {
                    startTimer(exerciseDuration)
                } else {
                    resumeTimer()
                }
            }
            startGetReadyCountdown(remainingSeconds, callback)
        } else if (wasRunningBeforeLifecyclePause) {
            resumeTimer()
        }
        wasGetReadyActiveBeforeLifecyclePause = false
        wasRunningBeforeLifecyclePause = false
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        getReadyTimer?.cancel()
        cameraExecutor.shutdown()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // ignore
        }
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
fun WorkoutTopPanel(
    exerciseName: String,
    secondsLeft: Long,
    maxSeconds: Long,
    isTimed: Boolean,
    isPaused: Boolean,
    onPauseToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BackgroundBlack.copy(alpha = 0.65f))
            .border(1.dp, CardOverlayColor.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardOverlayColor.copy(alpha = 0.05f))
                    .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), CircleShape),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(exerciseName, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = WorkoutProgressHelper.getExerciseDurationOrReps(LocalContext.current, exerciseName),
                        color = BrandLime,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (isTimed) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onPauseToggle,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CardOverlayColor.copy(alpha = 0.05f))
                        .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        tint = BrandLime,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(CardOverlayColor.copy(alpha = 0.05f))
                        .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { secondsLeft.toFloat() / maxSeconds.toFloat() },
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        color = BrandLime,
                        strokeWidth = 3.dp,
                        trackColor = CardOverlayColor.copy(alpha = 0.1f)
                    )
                    Text("${secondsLeft}s", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WorkoutBottomPanel(
    reps: Int,
    feedback: String,
    feedbackColor: ComposeColor,
    isTimed: Boolean,
    showReps: Boolean = true,
    onCompleted: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(BackgroundBlack.copy(alpha = 0.7f))
            .border(1.dp, CardOverlayColor.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (showReps) Arrangement.SpaceAround else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showReps) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("REPS", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("$reps", color = TextPrimary, fontSize = 48.sp, fontWeight = FontWeight.Black)
                }
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

        Spacer(modifier = Modifier.height(16.dp))

        if (isTimed) {
            TrainiumButton(
                text = "SKIP EXERCISE",
                onClick = onSkip,
                icon = {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip",
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        } else {
            TrainiumButton(
                text = "COMPLETED",
                onClick = onCompleted,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}

private fun getExerciseDetails(name: String): String {
    return when (name.uppercase()) {
        "SQUAT" -> "Keep your feet shoulder-width apart. Lower your hips as if sitting in a chair, keeping your chest up and knees behind your toes. Press through your heels to stand back up."
        "BICEP CURL" -> "Stand tall with a dumbbell in each hand. Keep elbows close to your torso. Curl the weights while contracting your biceps. Lower back down slowly."
        "PUSH-UP" -> "Place hands slightly wider than shoulder-width. Keep your body in a straight line from head to heels. Lower your chest to the floor, then push back up."
        "LUNGE" -> "Step forward with one leg and lower your hips until both knees are bent at a 90-degree angle. Keep your front knee directly above your ankle."
        "PLANK" -> "Place forearms on the floor, elbows under shoulders. Keep body straight and core engaged. Do not let your hips sag or hike up."
        "OVERHEAD PRESS" -> "Press weights upward from shoulder level until arms are fully extended. Lower back down to shoulder height with control."
        "JUMPING JACKS" -> "Start with feet together and arms at sides. Jump feet out while raising arms overhead. Jump back to starting position."
        "GLUTE BRIDGE" -> "Lie on your back, knees bent, feet flat on the floor. Lift your hips toward the ceiling, squeezing your glutes. Lower slowly."
        "BENT OVER ROW" -> "Hinge forward at the hips, keeping your back flat. Pull weights toward your lower ribs, squeezing shoulder blades together."
        "TRICEP DIPS" -> "Support body on bench/chair. Lower hips by bending elbows to 90 degrees. Push back up to extend arms."
        "CALF RAISES" -> "Stand tall on a flat surface. Raise heels as high as possible, standing on your toes. Hold briefly and lower slowly."
        "WALL SIT" -> "Lean against a wall, lower hips until thighs are parallel to the floor (90-degree bend). Hold position, keeping core tight."
        "DEADLIFT" -> "Hinge at your hips and bend knees slightly. Keep back straight and bar close to legs as you lift and lower."
        "HIGH KNEES" -> "Run in place, driving your knees up toward your chest as high as possible. Engage core and pump arms."
        "PULL-UPS" -> "Hang from a bar, pull your chest up to the bar. Lower back down with control."
        "BIRD-DOG" -> "Start on hands and knees. Extend opposite arm and leg straight out. Return to start and switch sides."
        "RUSSIAN TWIST" -> "Sit with knees bent, feet slightly off floor. Lean back slightly and twist torso side to side."
        "CRUNCHES" -> "Lie on back, knees bent. Lift shoulders off floor using abdominal muscles, keeping lower back pressed down."
        "LEG RAISES" -> "Lie on back with legs straight. Lift legs to 90 degrees, then lower them slowly without touching the floor."
        "MOUNTAIN CLIMBER" -> "Start in high plank. Drive knees toward chest one at a time in a running motion."
        "SIDE LUNGES" -> "Step out wide to the side, bending that knee while keeping the other leg straight. Push back to start."
        "SUPERMAN" -> "Lie face down, extend arms and legs. Lift chest, arms, and legs off floor, holding contraction."
        "BURPEES" -> "From standing, squat down, jump feet back to plank, do a push-up, jump feet forward, and jump up explosively."
        "SIDE PLANK" -> "Lie on side, support body on forearm. Lift hips to form straight line. Hold position."
        "LATERAL RAISES" -> "Raise weights out to the sides until arms are parallel to floor. Keep slight bend in elbows."
        "SUMO SQUAT" -> "Take a wide stance with toes pointed outwards. Lower hips deeply, keeping chest high."
        "PIKE PUSH-UP" -> "Start in downward dog/V-shape. Lower head toward floor by bending elbows, then press back up."
        "REVERSE CRUNCHES" -> "Lie on back, lift hips and knees toward chest, curling lower back off floor."
        "PLANK JACKS" -> "Hold high plank position. Jump feet out wide, then jump them back together."
        "GOOD MORNINGS" -> "Place hands behind head, hinge at hips while keeping back straight. Return to upright stance."
        "DONKEY KICKS" -> "On hands and knees, kick one leg up and back, keeping a 90-degree knee bend. Squeeze glute."
        "FIRE HYDRANTS" -> "On hands and knees, lift one leg out to the side, keeping knee bent. Return and repeat."
        "SHOULDER TAPS" -> "In high plank, tap opposite shoulder with hand, alternating sides while keeping hips stable."
        "WALL PUSH-UPS" -> "Lean against wall with hands at shoulder width. Lower chest to wall, then push away."
        "ARM CIRCLES" -> "Extend arms straight out to sides. Make small, controlled circular motions forward/backward."
        "TORSO TWISTS" -> "Stand tall, twist torso from side to side, keeping hips facing forward."
        "REVERSE LUNGES" -> "Step backward with one leg and lower hips until knees are bent. Push back up to start."
        "FORWARD FOLD" -> "Stand tall, bend forward at hips, letting head and arms hang toward floor. Keep knees slightly bent."
        "CAT-COW STRETCH" -> "Alternate between arching back down (Cow) and rounding spine up (Cat) on hands and knees."
        "CHILD'S POSE" -> "Kneel, sit back on heels, fold forward stretching arms out front. Rest forehead on floor."
        "COBRA POSE" -> "Lie face down, place hands under shoulders, press chest up, arching back gently."
        "DOWNWARD DOG" -> "Form inverted V-shape with body, hands and feet on floor, pushing hips high and back."
        "DIAMOND PUSH-UP" -> "Place hands close together under chest forming diamond shape with index fingers/thumbs. Push-up."
        "FLUTTER KICKS" -> "Lie on back, hands under hips. Lift legs slightly and kick them up and down alternately."
        "SCISSOR KICKS" -> "Lie on back, cross legs over and under each other horizontally slightly off the floor."
        "INCHWORM" -> "Bend forward, walk hands out to high plank, then walk feet in toward hands."
        "HIGH PLANK TO LOW PLANK" -> "Alternate between supporting body on hands (high plank) and forearms (low plank)."
        "BOXER SHUFFLE" -> "Lightly shift weight from one foot to the other in a quick, relaxed boxer stance."
        "SIDE BEND" -> "Stand tall, slide hand down thigh bending torso sideways, alternate sides."
        "T-POSE HOLD" -> "Hold arms straight out to sides at shoulder height, keeping posture tall and strong."
        else -> "Perform this exercise with controlled movements. Follow safety guidelines, keep your core engaged, and maintain consistent breathing."
    }
}

@Composable
fun ModernAestheticAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "aesthetic_anim")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(200.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = rotationAngle
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            BrandLime.copy(alpha = glowAlpha),
                            ComposeColor.Transparent,
                            PrimaryFixedDim.copy(alpha = glowAlpha),
                            BrandLime.copy(alpha = glowAlpha)
                        )
                    ),
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(150.dp)
                .graphicsLayer {
                    rotationZ = -rotationAngle
                }
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            CardOverlayColor.copy(alpha = 0.3f),
                            CardOverlayColor.copy(alpha = 0.05f)
                        )
                    ),
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BrandLime.copy(alpha = 0.3f),
                            ComposeColor.Transparent
                        )
                    )
                )
                .graphicsLayer {
                    scaleX = 1f / pulseScale
                    scaleY = 1f / pulseScale
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(BrandLime)
                    .border(2.dp, TextPrimary, CircleShape)
            )
        }
    }
}

@Composable
fun SelfModeContent(
    exerciseName: String,
    details: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor(0xFF0D0D0D))
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "SELF WORKOUT MODE",
                color = BrandLime,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = exerciseName,
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }

        ModernAestheticAnimation(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 16.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardOverlayColor.copy(alpha = 0.03f))
                .border(1.dp, CardOverlayColor.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "FORM INSTRUCTIONS",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = details,
                color = CardOverlayColor.copy(alpha = 0.85f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun WorkoutModeSelector(
    currentMode: WorkoutMode,
    onModeSelected: (WorkoutMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ComposeColor(0xFF1E1E1E).copy(alpha = 0.85f))
            .border(1.dp, CardOverlayColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val modes = listOf(
            WorkoutMode.TRAINER to "Trainer",
            WorkoutMode.SELF to "Self",
            WorkoutMode.TUTORIAL to "Tutorial"
        )
        
        modes.forEach { (mode, label) ->
            val isActive = mode == currentMode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isActive) BrandLime else ComposeColor.Transparent
                    )
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isActive) BackgroundBlack else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun GetReadyCountdownOverlay(
    secondsLeft: Int,
    modifier: Modifier = Modifier
) {
    val scale = remember { androidx.compose.animation.core.Animatable(0.5f) }
    LaunchedEffect(secondsLeft) {
        scale.snapTo(0.5f)
        scale.animateTo(
            targetValue = 1.2f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack.copy(alpha = 0.85f))
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(ComposeColor(0xFF161616).copy(alpha = 0.95f))
                .border(1.dp, CardOverlayColor.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                .padding(horizontal = 40.dp, vertical = 48.dp)
        ) {
            Text(
                text = "GET READY",
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (secondsLeft > 0) "$secondsLeft" else "GO!",
                color = BrandLime,
                fontSize = 80.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Starting exercise soon...",
                color = CardOverlayColor.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ==========================================
// Exercise Video & Tutorial Screen Extension
// ==========================================

private fun getYouTubeVideoId(exerciseName: String): String? {
    val name = exerciseName.uppercase().trim()
    return when {
        name.contains("JUMPING JACK") -> "2W4ZNSwoW_4"
        name.contains("KNEE PUSH-UP") || name.contains("KNEE PUSHUP") -> "jWxvty2KROs"
        name.contains("RUSSIAN TWIST") -> "DJQGX2J4IVw"
        name.contains("CHEST STRETCH") -> "NS64IgKUyeY"
        name.contains("HEEL TOUCH") -> "9bR-elyolBQ"
        name.contains("MOUNTAIN CLIMBER") || name.contains("MOUNTAIN CLIMBERS") -> "wQq3ybaLZeA"
        name.contains("SIT-UP TWIST") -> "_xzyH6NP_9k"
        name.contains("SIT-UP") || name.contains("SITUP") -> "swOyWKk7Oko"
        name.contains("ABDOMINAL CRUNCH") || name.contains("CRUNCH") -> {
            if (name.contains("SIDE")) "w0OWFjfI3zM"
            else if (name.contains("BICYCLE")) "-nJkAJpQemI"
            else if (name.contains("REVERSE")) "UwRfRN5fYRg"
            else if (name.contains("CROSS")) "Qz3ylqqJ90M"
            else if (name.contains("LONG")) "GxKoSEkmRC8"
            else if (name.contains("V ")) "AkHgaJiwtFE"
            else if (name.contains("CLAPPING")) "LUQt2wSOFNM"
            else if (name.contains("ARM CURL")) "pxsOe8MJq68"
            else "RUNrHkbP4Pc"
        }
        name.contains("COBRA STRETCH") || name.contains("COBRA POSE") || name.contains("COBRA") -> "z21McHHOpAg"
        name.contains("HIGH STEPPING") || name.contains("HIGH KNEE") || name.contains("HIGH KNEES") -> "Cmxr9xcNhgU"
        name.contains("BURPEE") || name.contains("BURPEES") -> "818SkLAPyKY"
        name.contains("DYNAMIC CHEST") -> "kLmWN3Qsj0A"
        name.contains("PLANK JACK") || name.contains("PLANK JACKS") -> "yCVyaX-RjLM"
        name.contains("PLANK") -> {
            if (name.contains("LATERAL")) "yCVyaX-RjLM"
            else if (name.contains("SIDE")) "7ytbYd4CK3o"
            else "Fcbw82ykBvY"
        }
        name.contains("FLUTTER KICK") || name.contains("FLUTTER KICKS") -> "K5wuM_gNWyw"
        name.contains("WALL PUSH-UP") || name.contains("WALL PUSHUP") -> "EOf3cGIQpA4"
        name.contains("LEG RAISE") || name.contains("LEG RAISES") -> "dGKbTKLnym4"
        name.contains("TRICEP DIP") || name.contains("TRICEPS DIP") || name.contains("TRICEP DIPS") -> "JhX1nBnirNw"
        name.contains("HINDU PUSH-UP") || name.contains("HINDU PUSHUP") -> "HE0ijmUc6Og"
        name.contains("REVERSE SNOW ANGEL") -> "0qLP2RNKX4A"
        name.contains("LYING TWIST") -> "ZI-j_POtzlU"
        name.contains("HOVER PUSH UP") -> "6wdVoBSkU0Y"
        name.contains("INCLINE PUSH-UP") || name.contains("INCLINE PUSHUP") -> "3WUUeM07i_Q"
        name.contains("INCHWORM") || name.contains("INCHWORMS") -> "ZY2ji_Ho0dA"
        name.contains("MILITARY PUSH-UP") || name.contains("MILITARY PUSHUP") || name.contains("MILITARY PUSH UPS") -> "H8LoGZ-ZN48"
        name.contains("LEG IN") || name.contains("LEG IN & OUT") || name.contains("LEG IN \u0026 OUTS") -> "V1wZc9RwPW8"
        name.contains("STAR CRAWL") -> "M_uNXxdI018"
        name.contains("WIDE ARM PUSH-UP") || name.contains("WIDE ARM PUSHUPS") -> "pQUsUHvyoI0"
        name.contains("OBLIQUE V-UP") -> "iFaZ095MMGg"
        name.contains("SIDE BRIDGE") || name.contains("SIDE BRIDGES") -> "7ytbYd4CK3o"
        name.contains("BENT LEG TWIST") -> "chWR8vsuamo"
        name.contains("SPINE LUMBAR") -> "ryNlb_0GmAw"
        name.contains("SIDE-LYING FLOOR") || name.contains("SIDE LYING FLOOR") -> "DMlSdmsHEeI"
        name.contains("PRONE TRICEPS") || name.contains("PRONE TRICEP") -> "Rr43jMaoJ9g"
        name.contains("SIDE LUNGE") || name.contains("SIDE LUNGES") -> "tlUg1DXhHm8"
        name.contains("LEG BARBELL") -> "3kZS8HVFquk"
        name.contains("RHOMBOID PULL") || name.contains("RHOMBOID PULLS") -> "DEyDbzSudEU"
        name.contains("BUTT BRIDGE") || name.contains("GLUTE BRIDGE") || name.contains("GLUTE BRIDGES") -> "9qo48CYN06w"
        name.contains("DIAMOND PUSH-UP") || name.contains("DIAMOND PUSHUPS") -> "UCmqw3kKZ38"
        name.contains("FLOOR TRICEP") || name.contains("FLOOR TRICEPS") -> "geNkbcZ6qDo"
        name.contains("V-UP") || name.contains("V UP") -> "5kvKmRGADlQ"
        name.contains("SUPINE PUSH") -> "WwbgPb9Gb48"
        name.contains("SKIPPING") -> "CYGeazlNbU4"
        name.contains("SPIDERMAN PUSH-UP") || name.contains("SPIDERMAN PUSHUP") -> "YmonBKorAIw"
        name.contains("ALTERNATING HOOK") || name.contains("ALTERNATING HOOKS") -> "wiyvVpEKOsc"
        name.contains("FROGGY GLUTE") -> "wl10q6aqy-4"
        name.contains("CROSSOVER CRUNCH") -> "q2_KHKE5CDE"
        name.contains("STANDING BICEPS") -> "jw8EXo5h0ec"
        name.contains("PLIE SQUAT") || name.contains("PLIE SQUATS") -> "XEKiRnwBfYA"
        name.contains("STAGGERED PUSH-UP") || name.contains("STAGGERED PUSHUPS") -> "JWNTTiAQMhc"
        name.contains("ELBOWS BACK") -> "rhtadqkrWo0"
        name.contains("TRICEPS KICKBACK") || name.contains("TRICEPS KICKBACKS") -> "f3E7eEq2c6c"
        name.contains("ARM RAISES") || name.contains("ARM RAISE") -> "Bqvmyni_sKQ"
        name.contains("FLOOR Y RAISES") || name.contains("FLOOR Y RAISE") -> "lUGi7NilqWA"
        name.contains("SQUAT PULSE") || name.contains("SQUAT PULSES") -> "7HarjcM6b10"
        name.contains("CURTSY LUNGE") || name.contains("CURTSY LUNGES") -> "-rTyKlHjYT8"
        name.contains("CHEST PRESS") -> "Fz4oo1vFo9M"
        name.contains("CRUNCH KICK") || name.contains("CRUNCH KICKS") -> "z0zwPZrPpXc"
        name.contains("PIKE PUSH") -> "Q2koXI9jphI"
        name.contains("REVERSE PUSH") -> "XRpbVcpx-Yc"
        name.contains("SUMO SQUAT") -> {
            if (name.contains("WALL")) "Hcy81KUTIZ8"
            else "42bFodPahBU"
        }
        name.contains("DONKEY KICK") || name.contains("DONKEY KICKS") -> "4ranVQDqlaU"
        name.contains("SHOULDER GATOR") || name.contains("SHOULDER GATORS") -> "JWp8_LGkTR8"
        name.contains("ARM CIRCLES") -> "Lha66p0ZXUc"
        name.contains("BACKWARD LUNGE") || name.contains("BACKWARD LUNGES") -> "_LGpDtENZ5U"
        name.contains("PUSH-UP & ROTATION") || name.contains("PUSH-UP \u0026 ROTATION") -> "Plv5CIclPtQ"
        name.contains("TRICEPS STRETCH") || name.contains("TRICEP STRETCH") -> "L9IGOcrdcFk"
        name.contains("LYING SWING LEGS") -> "hIoFHFyZJnE"
        name.contains("RECLINED RHOMBOID") -> "olv2Sv9DwmA"
        name.contains("STAR JUMP") || name.contains("STAR JUMPS") -> "VVEO_J1tIXU"
        name.contains("SIDE-LYING LEG LIFT") || name.contains("SIDE LYING LEG LIFT") -> "VlwBJE1WtOQ"
        name.contains("BOX PUSH-UP") || name.contains("BOX PUSHUPS") -> "dcJVA2sBPqw"
        name.contains("HEELS TO THE HEAVENS") -> "wdS2U6z0JGY"
        name.contains("SIDE HOP") -> "nYmUEJIBj3c"
        name.contains("SMILING FISH") -> "mLYm4ItAuro"
        name.contains("CHILD POSE") || name.contains("CHILD'S POSE") -> "DMwRPGMPB10"
        name.contains("BUTT KICK") || name.contains("BUTT KICKS") -> "vXVPvY1UbJI"
        name.contains("DUMBBELL RUSSIAN TWIST") -> "FShbaqrGGu4"
        name.contains("X MAN CRUNCH") -> "f_ZsJgaqFNE"
        name.contains("HYPEREXTENSION") -> "W9y8xq4Ya_E"
        name.contains("ARM SCISSORS") -> "pFrJQ-MyL10"
        name.contains("FIRE HYDRANT") || name.contains("FIRE HYDRANTS") -> "7LnuhLi-78I"
        name.contains("SIDE ARM RAISE") -> "YslHgg2E-Ro"
        name.contains("WALL STANDING") -> "qzqDHSDTc0U"
        name.contains("SQUAT") || name.contains("SQUATS") -> "42bFodPahBU"
        name.contains("LUNGE") || name.contains("LUNGES") -> "tlUg1DXhHm8"
        name.contains("CALF RAISES") || name.contains("CALF RAISE") -> "Hcy81KUTIZ8"
        else -> null
    }
}

private fun getExerciseFocusAreas(exerciseName: String): List<String> {
    val upper = exerciseName.uppercase()
    return when {
        upper.contains("SQUAT") -> listOf("Quadriceps", "Gluteus Maximus", "Hamstrings", "Calves", "Core")
        upper.contains("CURL") -> listOf("Biceps Brachii", "Brachialis", "Forearms", "Grip Muscles")
        upper.contains("PUSH-UP") || upper.contains("PUSHUP") || upper.contains("PRESS") -> listOf("Chest (Pectorals)", "Triceps Brachii", "Anterior Deltoids", "Core")
        upper.contains("LUNGE") -> listOf("Quadriceps", "Glutes", "Hamstrings", "Calves", "Hip Flexors")
        upper.contains("PLANK") || upper.contains("HOLD") -> listOf("Rectus Abdominis", "Transverse Abdominis", "Obliques", "Shoulders", "Lower Back")
        upper.contains("JACK") || upper.contains("JUMP") || upper.contains("HOP") || upper.contains("SKIPPING") || upper.contains("STEPPING") || upper.contains("KNEES") -> listOf("Cardiovascular System", "Calves", "Quadriceps", "Glutes", "Shoulders")
        upper.contains("BRIDGE") -> listOf("Glutes", "Hamstrings", "Lower Back", "Core", "Hip Stabilizers")
        upper.contains("ROW") || upper.contains("PULL") || upper.contains("CHIN") -> listOf("Latissimus Dorsi", "Rhomboids", "Rear Deltoids", "Biceps", "Core")
        upper.contains("DIP") || upper.contains("DIPS") || upper.contains("KICKBACK") -> listOf("Triceps Brachii", "Pectorals", "Anterior Deltoids", "Core")
        upper.contains("RAISE") -> {
            if (upper.contains("LEG")) listOf("Lower Abdominis", "Hip Flexors", "Rectus Abdominis")
            else if (upper.contains("CALF")) listOf("Gastrocnemius", "Soleus", "Ankle Stabilizers")
            else listOf("Lateral Deltoids", "Trapezius", "Upper Back")
        }
        upper.contains("TWIST") || upper.contains("HEEL") || upper.contains("CRUNCH") || upper.contains("SIT-UP") || upper.contains("SITUP") || upper.contains("V-UP") -> listOf("Obliques", "Rectus Abdominis", "Transverse Abdominis", "Hip Flexors")
        upper.contains("BURPEE") || upper.contains("BURPEES") -> listOf("Full Body", "Cardiovascular", "Chest", "Quadriceps", "Core")
        upper.contains("STRETCH") || upper.contains("POSE") || upper.contains("FOLD") -> listOf("Joint Flexibility", "Hamstrings", "Spine", "Shoulders", "Lower Back")
        upper.contains("DONKEY") || upper.contains("HYDRANT") -> listOf("Gluteus Medius", "Gluteus Maximus", "Outer Thighs", "Core")
        else -> listOf("Primary Target Muscle", "Stabilizing Joints", "Core Muscles", "Secondary Muscles")
    }
}

private fun getExercisePerks(exerciseName: String): List<String> {
    val upper = exerciseName.uppercase()
    return when {
        upper.contains("SQUAT") -> listOf("Increases lower body power", "Enhances hip & ankle mobility", "Improves functional strength", "Boosts natural hormone release")
        upper.contains("CURL") -> listOf("Builds defined upper arms", "Improves pulling strength", "Strengthens wrist & grip stability", "Reduces elbow strain risks")
        upper.contains("PUSH-UP") || upper.contains("PUSHUP") || upper.contains("PRESS") -> listOf("Develops chest and arm push power", "Improves shoulder stability", "Strengthens core locking strength", "Promotes upper body bone density")
        upper.contains("LUNGE") -> listOf("Corrects muscle imbalances", "Enhances single-leg stability", "Increases hip flexor flexibility", "Tones legs and lifts glutes")
        upper.contains("PLANK") || upper.contains("HOLD") -> listOf("Builds rock-solid core endurance", "Relieves chronic lower back strain", "Improves full-body posture alignment", "Enhances deep breathing control")
        upper.contains("JACK") || upper.contains("JUMP") || upper.contains("HOP") || upper.contains("SKIPPING") || upper.contains("STEPPING") || upper.contains("KNEES") -> listOf("Elevates heart rate & fat burn", "Improves athletic agility & speed", "Strengthens calf and ankle joints", "Boosts stamina & endurance")
        upper.contains("BRIDGE") -> listOf("Wakes up inactive glute muscles", "Protects lower back from injury", "Strengthens hamstrings & hips", "Improves posture & pelvic alignment")
        upper.contains("ROW") || upper.contains("PULL") || upper.contains("CHIN") -> listOf("Develops strong, V-shaped back", "Counteracts hunchback posture", "Improves shoulder blade health", "Enhances overall pulling power")
        upper.contains("DIP") || upper.contains("DIPS") || upper.contains("KICKBACK") -> listOf("Targets stubborn triceps fat", "Increases elbow extension force", "Strengthens shoulder joints", "Improves bench press performance")
        upper.contains("RAISE") -> {
            if (upper.contains("LEG")) listOf("Strengthens deep lower abs", "Improves hip flexor mobility", "Tightens core cylinder", "Reduces lower back pain")
            else if (upper.contains("CALF")) listOf("Sculpts lower leg definition", "Enhances vertical jump power", "Reduces Achilles tendonitis risk", "Improves ankle balance")
            else listOf("Broadens shoulder silhouette", "Strengthens shoulder cuff", "Improves overhead mobility", "Protects neck muscles")
        }
        upper.contains("TWIST") || upper.contains("HEEL") || upper.contains("CRUNCH") || upper.contains("SIT-UP") || upper.contains("SITUP") || upper.contains("V-UP") -> listOf("Defines waistline and side abs", "Improves rotational core power", "Stimulates digestion & organs", "Builds abdominal endurance")
        upper.contains("BURPEE") || upper.contains("BURPEES") -> listOf("High-calorie fat burning engine", "Increases explosive power", "Tests mental grit & resilience", "No-equipment total-body conditioning")
        upper.contains("STRETCH") || upper.contains("POSE") || upper.contains("FOLD") -> listOf("Lengthens tight muscle fibers", "Improves blood circulation", "Calms mind & reduces stress", "Prevents workout injuries")
        upper.contains("DONKEY") || upper.contains("HYDRANT") -> listOf("Isolates glutes without knee stress", "Improves outer hip stability", "Tones glute profile", "Strengthens pelvic floor")
        else -> listOf("Boosts physical fitness", "Improves form coordination", "Enhances muscle awareness", "Supports active lifestyle longevity")
    }
}

@Composable
fun YouTubePlayer(
    videoId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                private var customView: android.view.View? = null
                private var customViewCallback: CustomViewCallback? = null

                override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    customView = view
                    customViewCallback = callback
                    val activity = context as? android.app.Activity
                    val decor = activity?.window?.decorView as? android.view.ViewGroup
                    decor?.addView(view, android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                }

                override fun onHideCustomView() {
                    if (customView == null) return
                    val activity = context as? android.app.Activity
                    val decor = activity?.window?.decorView as? android.view.ViewGroup
                    decor?.removeView(customView)
                    customView = null
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                }
            }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }
            setBackgroundColor(android.graphics.Color.parseColor("#0D0D0D"))
        }
    }

    LaunchedEffect(videoId) {
        val embedUrl = "https://www.youtube.com/embed/$videoId?autoplay=0&rel=0&showinfo=0&fs=1&origin=https://www.example.com"
        val html = """
            <html>
            <head>
                <meta name="referrer" content="strict-origin-when-cross-origin">
            </head>
            <body style="margin:0;padding:0;background-color:#0D0D0D;">
                <iframe width="100%" height="100%" src="$embedUrl" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" referrerpolicy="strict-origin-when-cross-origin" allowfullscreen style="position:fixed; top:0; left:0; bottom:0; right:0; width:100%; height:100%; border:none; margin:0; padding:0; overflow:hidden;"></iframe>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://www.example.com", html, "text/html", "utf-8", null)
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}

@Composable
fun TutorialScreenContent(
    exerciseName: String,
    currentMode: WorkoutMode,
    onModeSelected: (WorkoutMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val details = getExerciseDetails(exerciseName)
    val steps = details.split(". ")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { if (it.endsWith(".")) it else "$it." }
    
    val videoId = getYouTubeVideoId(exerciseName)
    val focusAreas = getExerciseFocusAreas(exerciseName)
    val perks = getExercisePerks(exerciseName)

    Box(
        modifier = modifier
            .background(ComposeColor(0xFF0D0D0D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header Title & Category
            Text(
                text = "EXERCISE TUTORIAL",
                color = BrandLime,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = exerciseName,
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(20.dp))

            // 1. YouTube Video Box (16:9 ratio) - only displayed if videoId is not null
            if (videoId != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, CardOverlayColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = BackgroundBlack)
                ) {
                    YouTubePlayer(
                        videoId = videoId,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // 2. Focus Areas (Left) & Perks (Right) Card Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Focus Areas Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardOverlayColor.copy(alpha = 0.08f)),
                    colors = CardDefaults.cardColors(containerColor = CardOverlayColor.copy(alpha = 0.03f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(BrandLime)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FOCUS AREAS",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            focusAreas.forEach { area ->
                                Text(
                                    text = "• $area",
                                    color = CardOverlayColor.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Perks Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardOverlayColor.copy(alpha = 0.08f)),
                    colors = CardDefaults.cardColors(containerColor = CardOverlayColor.copy(alpha = 0.03f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(BrandLime)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BENEFITS",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            perks.forEach { perk ->
                                Text(
                                    text = "✓ $perk",
                                    color = CardOverlayColor.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // 3. Detailed Steps Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardOverlayColor.copy(alpha = 0.08f)),
                colors = CardDefaults.cardColors(containerColor = CardOverlayColor.copy(alpha = 0.03f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "INSTRUCTIONS & FORM STEPS",
                        color = BrandLime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        steps.forEachIndexed { index, step ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(BrandLime.copy(alpha = 0.1f))
                                        .border(1.dp, BrandLime, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = BrandLime,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = step,
                                    color = CardOverlayColor.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
            
            // Padding to ensure no overlap with floating mode selector pill
            Spacer(modifier = Modifier.height(100.dp))
        }

        // Floating Workout Mode Selector Pill at bottom center
        WorkoutModeSelector(
            currentMode = currentMode,
            onModeSelected = onModeSelected,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}
