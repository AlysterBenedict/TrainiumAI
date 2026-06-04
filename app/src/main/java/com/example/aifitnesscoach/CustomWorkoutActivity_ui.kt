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
            var exerciseDurationStr by remember { mutableStateOf("30") }
            var restDurationStr by remember { mutableStateOf("15") }

            TrainiumTheme {
                CustomWorkoutScreen(
                    exerciseList = Exercises_func.list,
                    selectedExercises = selectedExercises,
                    exerciseDurationStr = exerciseDurationStr,
                    restDurationStr = restDurationStr,
                    onExerciseToggled = { exercise ->
                        selectedExercises = if (selectedExercises.contains(exercise)) {
                            selectedExercises - exercise
                        } else {
                            selectedExercises + exercise
                        }
                    },
                    onExerciseDurationChanged = { exerciseDurationStr = it },
                    onRestDurationChanged = { restDurationStr = it },
                    onBack = { finish() },
                    onStartWorkout = {
                        startWorkout(selectedExercises.toList(), exerciseDurationStr, restDurationStr)
                    }
                )
            }
        }
    }

    private fun startWorkout(selected: List<ExerciseConfig_func>, exerciseDurationStr: String, restDurationStr: String) {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        if (selected.isEmpty()) {
            Toast.makeText(this, "Please select at least one exercise.", Toast.LENGTH_SHORT).show()
            return
        }
        if (exerciseDurationStr.isBlank() || restDurationStr.isBlank()) {
            Toast.makeText(this, "Please enter both durations.", Toast.LENGTH_SHORT).show()
            return
        }

        val exerciseDuration = exerciseDurationStr.toLongOrNull()
        val restDuration = restDurationStr.toLongOrNull()

        if (exerciseDuration == null || restDuration == null) {
            Toast.makeText(this, "Please enter valid durations.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, WorkoutActivity_ui::class.java).apply {
            putStringArrayListExtra(Constants_func.EXTRA_WORKOUT_PLAN, ArrayList(selected.map { it.name }))
            putExtra(Constants_func.EXTRA_EXERCISE_DURATION, exerciseDuration * 1000)
            putExtra(Constants_func.EXTRA_REST_DURATION, restDuration * 1000)
            putExtra(Constants_func.EXTRA_CURRENT_INDEX, 0)
        }
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomWorkoutScreen(
    exerciseList: List<ExerciseConfig_func>,
    selectedExercises: Set<ExerciseConfig_func>,
    exerciseDurationStr: String,
    restDurationStr: String,
    onExerciseToggled: (ExerciseConfig_func) -> Unit,
    onExerciseDurationChanged: (String) -> Unit,
    onRestDurationChanged: (String) -> Unit,
    onBack: () -> Unit,
    onStartWorkout: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Main list
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 190.dp) // space for floating bottom input & button
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
                                        if (isSelected) BrandLime.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) BrandLime.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getExerciseIcon(exercise.name),
                                    contentDescription = exercise.name,
                                    tint = if (isSelected) BrandLime else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = exercise.name,
                                    color = Color.White,
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
                                        color = if (isSelected) BrandLime else Color.White.copy(alpha = 0.2f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.Black,
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
                .background(Color.Black.copy(alpha = 0.85f))
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f))
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
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Select Exercises",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(40.dp)) // spacer for centering
            }
        }

        // Bottom Action Area containing duration fields and Start Button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.85f))
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = exerciseDurationStr,
                    onValueChange = onExerciseDurationChanged,
                    label = { Text("Duration (s)", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandLime,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color(0xFF111111),
                        unfocusedContainerColor = Color(0xFF111111)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = restDurationStr,
                    onValueChange = onRestDurationChanged,
                    label = { Text("Rest (s)", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandLime,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color(0xFF111111),
                        unfocusedContainerColor = Color(0xFF111111)
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            TrainiumButton(
                text = "START WORKOUT",
                onClick = onStartWorkout,
                icon = {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Start",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}
