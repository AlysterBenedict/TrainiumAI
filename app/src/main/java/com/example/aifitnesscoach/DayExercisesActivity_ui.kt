package com.example.aifitnesscoach

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

class DayExercisesActivity_ui : AppCompatActivity() {

    private lateinit var dayTitle: String
    private val resumeTrigger = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dayTitle = intent.getStringExtra("DAY_TITLE") ?: "Day_1"
        val exercises = intent.getStringArrayListExtra("EXERCISES_LIST") ?: arrayListOf()

        val dayNumber = WorkoutProgressHelper.getDayNumber(dayTitle)
        val formattedTitle = String.format(Locale.US, "Day %d", dayNumber)

        setContent {
            val trigger = resumeTrigger.value
            TrainiumTheme {
                DayExercisesScreen(
                    dayKey = dayTitle,
                    dayTitle = formattedTitle,
                    dayNumber = dayNumber,
                    exercises = exercises,
                    resumeTrigger = trigger,
                    onExerciseSelected = { index ->
                        startWorkoutSequence(dayTitle, exercises, index)
                    },
                    onStartWorkout = { startIndex ->
                        startWorkoutSequence(dayTitle, exercises, startIndex)
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.aifitnesscoach.network.FirebaseSyncHelper.syncSharedPreferencesToDatabase(this)
        resumeTrigger.value++
    }

    private fun startWorkoutSequence(dayTitle: String, exercises: ArrayList<String>, startIndex: Int) {
        val prefs = getTrainiumPrefs("app_prefs")
        val exerciseDuration = prefs.getInt("pref_exercise_duration_seconds", 30).toLong()
        val restDuration = prefs.getInt("pref_rest_duration_seconds", 15).toLong()

        val intent = Intent(this, WorkoutActivity_ui::class.java).apply {
            putStringArrayListExtra(Constants_func.EXTRA_WORKOUT_PLAN, exercises)
            putExtra(Constants_func.EXTRA_CURRENT_INDEX, startIndex)
            putExtra(Constants_func.EXTRA_EXERCISE_DURATION, exerciseDuration * 1000L)
            putExtra(Constants_func.EXTRA_REST_DURATION, restDuration * 1000L)
            putExtra(Constants_func.EXTRA_DAY_TITLE, dayTitle) // Pass dayTitle to WorkoutActivity
        }
        startActivity(intent)
    }
}

@Composable
fun DayExercisesScreen(
    dayKey: String,
    dayTitle: String,
    dayNumber: Int,
    exercises: List<String>,
    resumeTrigger: Int,
    onExerciseSelected: (Int) -> Unit,
    onStartWorkout: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val completedExercises = remember(dayKey, resumeTrigger) {
        exercises.indices.filter { WorkoutProgressHelper.isExerciseCompleted(context, dayKey, it) }.toSet()
    }
    
    val allCompleted = completedExercises.size == exercises.size && exercises.isNotEmpty()
    val buttonText = when {
        allCompleted -> "DO IT AGAIN"
        completedExercises.isNotEmpty() -> "Continue Workout"
        else -> "START WORKOUT"
    }

    val (estimatedDuration, estimatedCalories) = remember(exercises) {
        WorkoutProgressHelper.getEstimatedStatsForDay(context, exercises)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Ambient glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandLime.copy(alpha = 0.08f), Color.Transparent),
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp) // space for bottom start button
        ) {
            Spacer(modifier = Modifier.height(72.dp)) // space for TopBar

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Header displaying title, subtitle, stats and focus areas
                item {
                    DayExercisesHeader(
                        dayTitle = dayTitle,
                        estimatedDuration = estimatedDuration,
                        estimatedCalories = estimatedCalories
                    )
                }

                // Section title for exercises
                item {
                    Text(
                        text = "Exercises (${exercises.size})",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                itemsIndexed(exercises) { index, exerciseName ->
                    val isCompleted = completedExercises.contains(index)
                    TrainiumGlassCard(
                        isActive = false,
                        onClick = { onExerciseSelected(index) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(CardOverlayColor.copy(alpha = 0.04f))
                                    .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getExerciseIcon(exerciseName),
                                    contentDescription = exerciseName,
                                    tint = BrandLime,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = exerciseName,
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = WorkoutProgressHelper.getExerciseDurationOrReps(LocalContext.current, exerciseName),
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                            
                            if (isCompleted) {
                                // Green check icon on the right if exercise is completed (Image 2 style)
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Completed",
                                    tint = BrandLime
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Start",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Subpage Header TopAppBar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(72.dp)
                .background(BackgroundBlack.copy(alpha = 0.85f))
                .border(width = 1.dp, color = CardOverlayColor.copy(alpha = 0.05f))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CardOverlayColor.copy(alpha = 0.05f))
                        .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = dayTitle,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom Action Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(BackgroundBlack.copy(alpha = 0.85f))
                .border(width = 1.dp, color = CardOverlayColor.copy(alpha = 0.05f))
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            TrainiumButton(
                text = buttonText,
                onClick = {
                    val startIndex = if (allCompleted) {
                        WorkoutProgressHelper.resetDayProgress(context, dayKey)
                        0
                    } else {
                        exercises.indices.firstOrNull { !completedExercises.contains(it) } ?: 0
                    }
                    onStartWorkout(startIndex)
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Start",
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun DayExercisesHeader(
    dayTitle: String,
    estimatedDuration: Int,
    estimatedCalories: Float
) {
    val context = LocalContext.current
    val userData = remember {
        com.example.aifitnesscoach.network.FirebaseSyncHelper.getGlobalUserData(context)
    }
    val userGoal = remember(userData) {
        userData.goal.ifBlank { "Lose Weight" }
    }
    val userLevel = remember(userData) {
        userData.level.ifBlank { "Beginner" }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Text(
            text = dayTitle.uppercase(),
            color = TextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "${userGoal.uppercase(Locale.US)} IN 30 DAYS",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // Basic Info Cards Row (Image 2 style)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardOverlayColor.copy(alpha = 0.04f))
                    .border(1.dp, CardOverlayColor.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        text = String.format(Locale.US, "%.1f kcal", estimatedCalories),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text("Basic", color = TextSecondary, fontSize = 11.sp)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardOverlayColor.copy(alpha = 0.04f))
                    .border(1.dp, CardOverlayColor.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        text = "$estimatedDuration min",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text("Time", color = TextSecondary, fontSize = 11.sp)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardOverlayColor.copy(alpha = 0.04f))
                    .border(1.dp, CardOverlayColor.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        text = userLevel,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text("Level", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Warm-up",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))
        Divider(color = CardOverlayColor.copy(alpha = 0.08f))
    }
}
