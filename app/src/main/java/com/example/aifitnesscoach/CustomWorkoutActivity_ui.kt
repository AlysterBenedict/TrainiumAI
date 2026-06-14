package com.example.aifitnesscoach

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class CustomWorkoutActivity_ui : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var selectedExercises by remember { mutableStateOf(setOf<ExerciseConfig_func>()) }

            TrainiumTheme {
                CustomWorkoutScreen(
                    exerciseList = Exercises_func.list,
                    selectedExercises = selectedExercises,
                    onExerciseToggled = { exercise ->
                        selectedExercises = if (selectedExercises.contains(exercise)) {
                            selectedExercises - exercise
                        } else {
                            selectedExercises + exercise
                        }
                    },
                    onBack = { finish() },
                    onStartWorkout = {
                        startWorkout(selectedExercises.toList())
                    }
                )
            }
        }
    }

    private fun startWorkout(selected: List<ExerciseConfig_func>) {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        if (selected.isEmpty()) {
            Toast.makeText(this, "Please select at least one exercise.", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getTrainiumPrefs("app_prefs")
        val exerciseDuration = prefs.getInt("pref_exercise_duration_seconds", 30).toLong()
        val restDuration = prefs.getInt("pref_rest_duration_seconds", 15).toLong()

        val intent = Intent(this, WorkoutActivity_ui::class.java).apply {
            putStringArrayListExtra(Constants_func.EXTRA_WORKOUT_PLAN, ArrayList(selected.map { it.name }))
            putExtra(Constants_func.EXTRA_EXERCISE_DURATION, exerciseDuration * 1000)
            putExtra(Constants_func.EXTRA_REST_DURATION, restDuration * 1000)
            putExtra(Constants_func.EXTRA_CURRENT_INDEX, 0)
            putExtra("IS_CUSTOM_WORKOUT", true)
        }
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomWorkoutScreen(
    exerciseList: List<ExerciseConfig_func>,
    selectedExercises: Set<ExerciseConfig_func>,
    onExerciseToggled: (ExerciseConfig_func) -> Unit,
    onBack: () -> Unit,
    onStartWorkout: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Main list
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp) // space for floating bottom button
        ) {
            Spacer(modifier = Modifier.height(72.dp)) // space for TopBar

            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(exerciseList) { exercise ->
                    val isSelected = selectedExercises.contains(exercise)

                    TrainiumGlassCard(
                        isActive = isSelected,
                        onClick = { onExerciseToggled(exercise) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) BrandLime.copy(alpha = 0.15f) else CardOverlayColor.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) BrandLime.copy(alpha = 0.4f) else CardOverlayColor.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getExerciseIcon(exercise.name),
                                    contentDescription = exercise.name,
                                    tint = if (isSelected) BrandLime else CardOverlayColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = exercise.name,
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (exercise.exerciseType == "timed") "Timed • Core" else "Rep Based • Compound",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            // Checkbox circle
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) BrandLime else Color.Transparent)
                                    .border(
                                        width = 2.dp,
                                        color = if (isSelected) BrandLime else CardOverlayColor.copy(alpha = 0.2f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = BackgroundBlack,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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

                Text(
                    text = "Select Exercises",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(40.dp)) // spacer for centering
            }
        }

        // Bottom Action Area containing Start Button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(BackgroundBlack.copy(alpha = 0.85f))
                .border(width = 1.dp, color = CardOverlayColor.copy(alpha = 0.05f))
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TrainiumButton(
                text = "START WORKOUT",
                onClick = onStartWorkout,
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
