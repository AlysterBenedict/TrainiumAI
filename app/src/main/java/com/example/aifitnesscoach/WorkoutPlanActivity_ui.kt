package com.example.aifitnesscoach

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkoutPlanActivity_ui : AppCompatActivity() {

    private lateinit var workoutPlan: Map<String, List<String>>
    private val resumeTrigger = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val workoutPlanJson = intent.getStringExtra("WORKOUT_PLAN")
        val parsedPlan = if (workoutPlanJson != null) {
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            Gson().fromJson<Map<String, List<String>>>(workoutPlanJson, type)
        } else {
            emptyMap()
        }

        // Ensure all 30 days are populated to guarantee a complete timeline
        val mutablePlan = parsedPlan.toMutableMap()
        val workoutExercises = listOf(
            "SQUAT", "PUSH-UP", "PLANK", "JUMPING JACKS", "GLUTE BRIDGE",
            "LEG RAISES", "MOUNTAIN CLIMBER", "LUNGE", "SUPERMAN", "BURPEES",
            "BICEP CURL", "OVERHEAD PRESS", "TRICEP DIPS", "CALF RAISES",
            "WALL SIT", "HIGH KNEES", "RUSSIAN TWIST", "CRUNCHES"
        )
        val random = java.util.Random(42) // Consistent seed for default generation
        for (day in 1..30) {
            val key = "Day_$day"
            if (!mutablePlan.containsKey(key)) {
                if (day % 4 == 0) {
                    mutablePlan[key] = listOf("Rest Day")
                } else {
                    val count = 8 + random.nextInt(5)
                    mutablePlan[key] = workoutExercises.shuffled(random).take(count)
                }
            }
        }
        workoutPlan = mutablePlan

        val days = workoutPlan.keys.sortedBy { WorkoutProgressHelper.getDayNumber(it) }

        setContent {
            val trigger = resumeTrigger.value
            TrainiumTheme {
                WorkoutPlanScreen(
                    days = days,
                    workoutPlan = workoutPlan,
                    resumeTrigger = trigger,
                    onDaySelected = { day ->
                        val exercises = workoutPlan[day]
                        val intent = Intent(this, DayExercisesActivity_ui::class.java).apply {
                            putExtra("DAY_TITLE", day)
                            putStringArrayListExtra("EXERCISES_LIST", ArrayList(exercises ?: emptyList()))
                        }
                        startActivity(intent)
                    },
                    onResetPlan = {
                        WorkoutProgressHelper.resetAllPlanProgress(this, workoutPlan.keys)
                        com.example.aifitnesscoach.network.FirebaseSyncHelper.clearAll30DayWorkoutLogs(this)
                        Toast.makeText(this, "Plan progress reset successful!", Toast.LENGTH_SHORT).show()
                        resumeTrigger.value++
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
}

@Composable
fun WorkoutPlanScreen(
    days: List<String>,
    workoutPlan: Map<String, List<String>>,
    resumeTrigger: Int,
    onDaySelected: (String) -> Unit,
    onResetPlan: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = Color(0xFF111111),
            title = {
                Text(
                    text = "Reset Workout Plan?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to reset all progress of the 30-day workout plan? This will clear all completed days and stats for this plan. This action cannot be undone.",
                    color = CardOverlayColor.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onResetPlan()
                    }
                ) {
                    Text("Reset", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false }
                ) {
                    Text("Cancel", color = TextPrimary)
                }
            }
        )
    }


    var completedDays by remember { mutableStateOf(emptySet<String>()) }
    var activeDay by remember { mutableStateOf("Day_1") }
    var activeDayNum by remember { mutableStateOf(1) }

    var progressStage1 by remember { mutableStateOf(0f) }
    var progressStage2 by remember { mutableStateOf(0f) }
    var progressStage3 by remember { mutableStateOf(0f) }
    var progressStage4 by remember { mutableStateOf(0f) }

    LaunchedEffect(resumeTrigger) {
        withContext(Dispatchers.IO) {
            val compDays = mutableSetOf<String>()
            for (day in workoutPlan.keys) {
                val exercises = workoutPlan[day] ?: emptyList()
                if (WorkoutProgressHelper.isDayCompleted(context, day, exercises.size)) {
                    compDays.add(day)
                }
            }

            val actDay = WorkoutProgressHelper.getActiveDay(context, workoutPlan)
            val actDayNum = WorkoutProgressHelper.getDayNumber(actDay)

            val p1 = getStageProgressWithCache(1..4, workoutPlan, compDays)
            val p2 = getStageProgressWithCache(5..10, workoutPlan, compDays)
            val p3 = getStageProgressWithCache(11..20, workoutPlan, compDays)
            val p4 = getStageProgressWithCache(21..30, workoutPlan, compDays)

            completedDays = compDays
            activeDay = actDay
            activeDayNum = actDayNum
            progressStage1 = p1
            progressStage2 = p2
            progressStage3 = p3
            progressStage4 = p4
        }
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
                .padding(bottom = 110.dp) // space for bottom dock
        ) {
            Spacer(modifier = Modifier.height(72.dp)) // Space for TopAppBar

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // STAGE 1
                item {
                    StageHeader(title = "Stage 1: Start Strong", progress = progressStage1)
                }
                items(days.filter { WorkoutProgressHelper.getDayNumber(it) in 1..4 }) { day ->
                    DayTimelineRow(
                        day = day,
                        workoutPlan = workoutPlan,
                        activeDayNum = activeDayNum,
                        completedDays = completedDays,
                        onDaySelected = onDaySelected
                    )
                }

                // STAGE 2
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    StageHeader(title = "Stage 2: Muscle Up Body", progress = progressStage2)
                }
                items(days.filter { WorkoutProgressHelper.getDayNumber(it) in 5..10 }) { day ->
                    DayTimelineRow(
                        day = day,
                        workoutPlan = workoutPlan,
                        activeDayNum = activeDayNum,
                        completedDays = completedDays,
                        onDaySelected = onDaySelected
                    )
                }

                // STAGE 3
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    StageHeader(title = "Stage 3: Reach Your Potential", progress = progressStage3)
                }
                items(days.filter { WorkoutProgressHelper.getDayNumber(it) in 11..20 }) { day ->
                    DayTimelineRow(
                        day = day,
                        workoutPlan = workoutPlan,
                        activeDayNum = activeDayNum,
                        completedDays = completedDays,
                        onDaySelected = onDaySelected
                    )
                }

                // STAGE 4
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    StageHeader(title = "Stage 4: Reach Goal Limits", progress = progressStage4)
                }
                items(days.filter { WorkoutProgressHelper.getDayNumber(it) in 21..30 }) { day ->
                    DayTimelineRow(
                        day = day,
                        workoutPlan = workoutPlan,
                        activeDayNum = activeDayNum,
                        completedDays = completedDays,
                        onDaySelected = onDaySelected
                    )
                }

                // Reset Progress Button at the end of the plan page
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(240.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            SurfaceLow.copy(alpha = 0.6f),
                                            SurfaceLow.copy(alpha = 0.3f)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            CardOverlayColor.copy(alpha = 0.18f),
                                            CardOverlayColor.copy(alpha = 0.05f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .clickable {
                                    showResetDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Reset Plan Progress",
                                    tint = AccentRed.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Reset Plan Progress",
                                    color = AccentRed.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Top AppBar Header
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
                    text = "My plan",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Floating Glass Bottom Navigation Dock
        TrainiumBottomDock(
            activeTab = "", // Subpage
            onTabSelected = { tab ->
                navigateToTab(context, tab)
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun StageHeader(title: String, progress: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(20.dp),
                color = BrandLime,
                strokeWidth = 2.dp,
                trackColor = CardOverlayColor.copy(alpha = 0.1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${(progress * 100).toInt()}%",
                color = BrandLime,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DayTimelineRow(
    day: String,
    workoutPlan: Map<String, List<String>>,
    activeDayNum: Int,
    completedDays: Set<String>,
    onDaySelected: (String) -> Unit
) {
    val context = LocalContext.current
    val dayNum = WorkoutProgressHelper.getDayNumber(day)
    val isRestDay = dayNum % 4 == 0
    val exercises = workoutPlan[day] ?: emptyList()
    
    val isCompleted = completedDays.contains(day)
    val isActive = dayNum == activeDayNum
    val isLocked = dayNum > activeDayNum

    val dayTitleFormatted = String.format("Day %d", dayNum)
    val (estimatedDuration, estimatedCalories) = remember(day, exercises) {
        WorkoutProgressHelper.getEstimatedStatsForDay(context, exercises)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min) // Intrinsic size allows left line to fill exact height of card
    ) {
        // Left timeline column
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            // Continuous connection line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(CardOverlayColor.copy(alpha = 0.15f))
            )

            // Timeline Circle Node
            if (isCompleted) {
                // Completed: Green circle with checkmark
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(BrandLime),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = BackgroundBlack,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else if (isActive) {
                // Active: Green border with center dot
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(BackgroundBlack)
                        .border(2.dp, BrandLime, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(BrandLime)
                    )
                }
            } else {
                // Locked / Upcoming: Grey border circle
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(BackgroundBlack)
                        .border(2.dp, CardOverlayColor.copy(alpha = 0.2f), CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Day card display
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
        ) {
            if (isRestDay) {
                // Rest Day card layout
                TrainiumGlassCard(
                    isActive = false,
                    onClick = {
                        val prevDayKey = if (day.contains("_")) "Day_${dayNum - 1}" else "Day ${dayNum - 1}"
                        val isPrevCompleted = dayNum == 1 || completedDays.contains(prevDayKey)
                        if (!isCompleted && isPrevCompleted) {
                            WorkoutProgressHelper.markExerciseCompleted(context, day, 0, 0, 0f)
                            Toast.makeText(context, "Enjoy your Rest Day! ☕ Progress updated.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Rest Day! Keep recovering ☕", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dayTitleFormatted,
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Rest Day!",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "☕",
                            fontSize = 28.sp
                        )
                    }
                }
            } else if (isActive) {
                // Active Day card: glassmorphic highlighted card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    PrimaryFixed.copy(alpha = 0.24f),
                                    PrimaryFixed.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    PrimaryFixed.copy(alpha = 0.7f),
                                    PrimaryFixed.copy(alpha = 0.15f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onDaySelected(day) }
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = dayTitleFormatted,
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$estimatedDuration min • ${String.format(java.util.Locale.US, "%.1f", estimatedCalories)} kcal",
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            // Glow indicator dot on the right
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(BrandLime)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "START WORKOUT",
                                color = BrandLime,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = BrandLime,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else {
                // Completed or Locked card (lower opacity if locked)
                val cardAlpha = if (isLocked) 0.5f else 1.0f
                val cardDuration = if (isCompleted) {
                    val actualDur = WorkoutProgressHelper.getDayDuration(context, day)
                    if (actualDur > 0) actualDur / 60 else estimatedDuration
                } else {
                    estimatedDuration
                }
                val cardCalories = if (isCompleted) {
                    val actualCals = WorkoutProgressHelper.getDayCalories(context, day)
                    if (actualCals > 0f) actualCals else estimatedCalories
                } else {
                    estimatedCalories
                }

                Box(modifier = Modifier.fillMaxWidth().alpha(cardAlpha)) {
                    TrainiumGlassCard(
                        isActive = false,
                        onClick = {
                            if (isLocked) {
                                Toast.makeText(context, "Complete previous days first!", Toast.LENGTH_SHORT).show()
                            } else {
                                onDaySelected(day)
                            }
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dayTitleFormatted,
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$cardDuration min - ${String.format(java.util.Locale.US, "%.1f", cardCalories)} kcal",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                            if (isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Completed",
                                    tint = BrandLime
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getStageProgressWithCache(range: IntRange, plan: Map<String, List<String>>, completedDays: Set<String>): Float {
    var completed = 0
    var total = 0
    for (day in range) {
        val key = "Day_$day"
        val exercises = plan[key] ?: continue
        total++
        if (completedDays.contains(key)) {
            completed++
        }
    }
    return if (total > 0) completed.toFloat() / total.toFloat() else 0f
}
