package com.example.aifitnesscoach

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class RestActivity_ui : AppCompatActivity() {

    private var dayTitle: String = "Day_1"
    private var countDownTimer: CountDownTimer? = null
    private var timeRemaining = mutableStateOf(15L)
    private var isCustomWorkout = false
    private var accumulatedDurationSeconds = 0
    private var accumulatedCalories = 0f
    private var workoutMode: String = WorkoutMode.TRAINER.name

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        workoutMode = intent.getStringExtra("WORKOUT_MODE") ?: WorkoutMode.TRAINER.name
        val restDuration = intent.getLongExtra(Constants_func.EXTRA_REST_DURATION, 15000)
        val nextExerciseName = intent.getStringExtra(Constants_func.EXTRA_NEXT_EXERCISE_NAME) ?: "Finish"
        val workoutPlan = intent.getStringArrayListExtra(Constants_func.EXTRA_WORKOUT_PLAN)
        val nextExerciseIndex = intent.getIntExtra(Constants_func.EXTRA_CURRENT_INDEX, 0)
        val exerciseDuration = intent.getLongExtra(Constants_func.EXTRA_EXERCISE_DURATION, 30000)
        dayTitle = intent.getStringExtra(Constants_func.EXTRA_DAY_TITLE) ?: "Day_1"
        isCustomWorkout = intent.getBooleanExtra("IS_CUSTOM_WORKOUT", false)
        accumulatedDurationSeconds = intent.getIntExtra("ACCUMULATED_DURATION_SECONDS", 0)
        accumulatedCalories = intent.getFloatExtra("ACCUMULATED_CALORIES", 0f)

        timeRemaining.value = restDuration / 1000

        setContent {
            TrainiumTheme {
                RestScreen(
                    nextExerciseName = nextExerciseName,
                    secondsRemaining = timeRemaining.value,
                    maxSeconds = restDuration / 1000,
                    onSkip = {
                        goToNextExercise(workoutPlan, nextExerciseIndex, exerciseDuration, restDuration, true)
                    }
                )
            }
        }

        countDownTimer = object : CountDownTimer(restDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining.value = millisUntilFinished / 1000 + 1
            }

            override fun onFinish() {
                goToNextExercise(workoutPlan, nextExerciseIndex, exerciseDuration, restDuration, false)
            }
        }.start()
    }

    private fun goToNextExercise(
        plan: ArrayList<String>?,
        index: Int,
        exDuration: Long,
        restDuration: Long,
        isSkipped: Boolean
    ) {
        countDownTimer?.cancel()
        val intent = Intent(this, WorkoutActivity_ui::class.java).apply {
            putStringArrayListExtra(Constants_func.EXTRA_WORKOUT_PLAN, plan)
            putExtra(Constants_func.EXTRA_CURRENT_INDEX, index)
            putExtra(Constants_func.EXTRA_EXERCISE_DURATION, exDuration)
            putExtra(Constants_func.EXTRA_REST_DURATION, restDuration)
            putExtra(Constants_func.EXTRA_DAY_TITLE, dayTitle) // Pass dayTitle forward
            putExtra("IS_CUSTOM_WORKOUT", isCustomWorkout)
            putExtra("ACCUMULATED_DURATION_SECONDS", accumulatedDurationSeconds)
            putExtra("ACCUMULATED_CALORIES", accumulatedCalories)
            putExtra("WORKOUT_MODE", workoutMode)
            putExtra("IS_REST_SKIPPED", isSkipped)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}

@Composable
fun RestScreen(
    nextExerciseName: String,
    secondsRemaining: Long,
    maxSeconds: Long,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack),
        contentAlignment = Alignment.Center
    ) {
        // Ambient glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandLime.copy(alpha = 0.1f), Color.Transparent),
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "REST",
                color = BrandLime,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Large circular countdown timer
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F0F0F))
                    .border(2.dp, CardOverlayColor.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { secondsRemaining.toFloat() / maxSeconds.toFloat() },
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    color = BrandLime,
                    strokeWidth = 5.dp,
                    trackColor = CardOverlayColor.copy(alpha = 0.05f)
                )

                Text(
                    text = "${secondsRemaining}s",
                    color = TextPrimary,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Next exercise glassmorphic card
            if (nextExerciseName != "Finish") {
                TrainiumGlassCard(
                    isActive = true,
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(CardOverlayColor.copy(alpha = 0.04f))
                                .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getExerciseIcon(nextExerciseName),
                                contentDescription = nextExerciseName,
                                tint = BrandLime,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "NEXT EXERCISE",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = nextExerciseName,
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "LAST SET COMPLETE!",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            TrainiumButton(
                text = "SKIP REST",
                onClick = onSkip,
                icon = {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip Rest",
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}
