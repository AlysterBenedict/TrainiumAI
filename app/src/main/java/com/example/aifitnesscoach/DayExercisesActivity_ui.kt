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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class DayExercisesActivity_ui : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dayTitle = intent.getStringExtra("DAY_TITLE") ?: "Day_1"
        val exercises = intent.getStringArrayListExtra("EXERCISES_LIST") ?: arrayListOf()

        val dayNumber = dayTitle.substringAfter('_').toIntOrNull() ?: 1
        val formattedTitle = String.format(Locale.US, "Day %02d", dayNumber)

        setContent {
            TrainiumTheme {
                DayExercisesScreen(
                    dayTitle = formattedTitle,
                    exercises = exercises,
                    onExerciseSelected = { index ->
                        startWorkoutSequence(exercises, index)
                    },
                    onStartWorkout = {
                        startWorkoutSequence(exercises, 0)
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun startWorkoutSequence(exercises: ArrayList<String>, startIndex: Int) {
        val intent = Intent(this, WorkoutActivity_ui::class.java).apply {
            putStringArrayListExtra(Constants_func.EXTRA_WORKOUT_PLAN, exercises)
            putExtra(Constants_func.EXTRA_CURRENT_INDEX, startIndex)
            putExtra(Constants_func.EXTRA_EXERCISE_DURATION, 30000L)
            putExtra(Constants_func.EXTRA_REST_DURATION, 15000L)
        }
        startActivity(intent)
    }
}

@Composable
fun DayExercisesScreen(
    dayTitle: String,
    exercises: List<String>,
    onExerciseSelected: (Int) -> Unit,
    onStartWorkout: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(exercises) { index, exerciseName ->
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
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
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
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Target: Full Body",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
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
                verticalAlignment = Alignment.CenterVertically
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

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = dayTitle,
                    color = Color.White,
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
                .background(Color.Black.copy(alpha = 0.85f))
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
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
