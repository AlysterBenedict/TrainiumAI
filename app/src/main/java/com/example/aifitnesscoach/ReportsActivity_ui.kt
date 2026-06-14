package com.example.aifitnesscoach

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aifitnesscoach.network.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportsActivity_ui : AppCompatActivity() {

    private val resumeTrigger = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val trigger = resumeTrigger.value
            TrainiumTheme {
                ReportsScreen(
                    resumeTrigger = trigger,
                    onNavigateTab = { tab ->
                        navigateToTab(this, tab)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTrigger.value++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    resumeTrigger: Int,
    onNavigateTab: (String) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Tab state: "Report", "Today", "Weight"
    var selectedTab by remember { mutableStateOf("Report") }

    // Core data states loaded from cache (initialized with safe defaults, loaded asynchronously)
    var userStats by remember { mutableStateOf(UserStats()) }
    var workouts by remember { mutableStateOf(emptyList<WorkoutLog>()) }
    var weights by remember { mutableStateOf(emptyList<WeightLog>()) }
    var userData by remember { mutableStateOf(UserData()) }
    var goalWeight by remember { mutableStateOf(0f) }
    var workoutPlanJson by remember { mutableStateOf<String?>(null) }

    // Helper to reload all states from database & cache asynchronously on Dispatchers.IO
    val reloadData = {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val stats = FirebaseSyncHelper.getUserStats(context)
                val wrk = FirebaseSyncHelper.getWorkouts(context)
                val wgt = FirebaseSyncHelper.getWeights(context)
                val uData = FirebaseSyncHelper.getGlobalUserData(context)
                val gWeight = FirebaseSyncHelper.getGoalWeight(context)
                val appPrefs = context.getTrainiumPrefs("app_prefs")
                val planJson = appPrefs.getString("SAVED_WORKOUT_PLAN", null)

                withContext(Dispatchers.Main) {
                    userStats = stats
                    workouts = wrk
                    weights = wgt
                    userData = uData
                    goalWeight = gWeight
                    workoutPlanJson = planJson
                }
            }
        }
    }

    // Load values on initialization and every time activity is resumed from local cache
    LaunchedEffect(resumeTrigger) {
        withContext(Dispatchers.IO) {
            FirebaseSyncHelper.syncSharedPreferencesToDatabase(context)
        }
        reloadData()
    }

    // Map workout plan JSON to structured object
    val workoutPlan = remember(workoutPlanJson) {
        if (workoutPlanJson != null) {
            try {
                val type = object : TypeToken<Map<String, List<String>>>() {}.type
                val parsed = Gson().fromJson<Map<String, List<String>>>(workoutPlanJson, type)
                val mutableMap = parsed.toMutableMap()
                val day1Keys = listOf("Day_1", "Day_01", "Day 1", "Day 01")
                var day1Exercises: List<String>? = null
                for (key in day1Keys) {
                    val list = mutableMap[key]
                    if (list != null && list.isNotEmpty() && list.any { it.isNotBlank() }) {
                        day1Exercises = list
                        break
                    }
                }
                for (key in day1Keys) {
                    mutableMap.remove(key)
                }
                if (day1Exercises == null || day1Exercises.size < 12) {
                    day1Exercises = listOf(
                        "CAT-COW STRETCH", "SQUAT", "PUSH-UP", "PLANK",
                        "JUMPING JACKS", "GLUTE BRIDGE", "LEG RAISES", "MOUNTAIN CLIMBER",
                        "LUNGE", "SUPERMAN", "BURPEES", "CHILD'S POSE"
                    )
                }
                mutableMap["Day_1"] = day1Exercises
                mutableMap
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    // Dialog trigger states
    var showGoalsDialog by remember { mutableStateOf(false) }
    var showBmiDialog by remember { mutableStateOf(false) }
    var showAddWorkoutDialog by remember { mutableStateOf(false) }
    var showAddWeightDialog by remember { mutableStateOf(false) }
    var showGoalWeightDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Top ambient radial glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandLime.copy(alpha = 0.12f), Color.Transparent),
                    )
                )
        )

        // Main content column
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Top Tab Header
            ReportsHeader(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            // Dynamic Views based on tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 140.dp) // space for floating bottom dock
            ) {
                when (selectedTab) {
                    "Report" -> {
                        ReportTabContent(
                            userStats = userStats,
                            workouts = workouts,
                            userData = userData,
                            workoutPlan = workoutPlan,
                            onEditGoalsClick = { showGoalsDialog = true },
                            onEditBmiClick = { showBmiDialog = true }
                        )
                    }
                    "Today" -> {
                        TodayTabContent(
                            workouts = workouts,
                            workoutPlan = workoutPlan,
                            onAddWorkoutClick = { showAddWorkoutDialog = true }
                        )
                    }
                    "Weight" -> {
                        WeightTabContent(
                            weights = weights,
                            goalWeight = goalWeight,
                            onQuickAddWeight = { showAddWeightDialog = true },
                            onEditGoalWeight = { showGoalWeightDialog = true }
                        )
                    }
                }
            }
        }

        // Floating Glass Bottom Navigation Dock
        TrainiumBottomDock(
            activeTab = "reports",
            onTabSelected = onNavigateTab,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // --- ALL DIALOGS ---

        // Edit Goals Dialog (Calories & Duration)
        if (showGoalsDialog) {
            var inputCalorieGoal by remember { mutableStateOf(userStats.dailyCalorieGoal.toString()) }
            var inputTimeGoal by remember { mutableStateOf(userStats.dailyTimeGoalMinutes.toString()) }

            AlertDialog(
                onDismissRequest = { showGoalsDialog = false },
                containerColor = Color(0xFF111111),
                title = { Text("Edit Daily Goals", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = inputCalorieGoal,
                            onValueChange = { inputCalorieGoal = it },
                            label = { Text("Daily Calorie Goal (kcal)", color = TextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = BrandLime
                            )
                        )
                        OutlinedTextField(
                            value = inputTimeGoal,
                            onValueChange = { inputTimeGoal = it },
                            label = { Text("Daily Duration Goal (min)", color = TextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = BrandLime
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val cal = inputCalorieGoal.toFloatOrNull()
                            val time = inputTimeGoal.toIntOrNull()
                            if (cal != null && time != null) {
                                val updated = userStats.copy(
                                    dailyCalorieGoal = cal,
                                    dailyTimeGoalMinutes = time
                                )
                                FirebaseSyncHelper.saveUserStats(context, updated)
                                reloadData()
                                showGoalsDialog = false
                            } else {
                                Toast.makeText(context, "Please enter valid numeric values.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Save", color = BrandLime, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGoalsDialog = false }) {
                        Text("Cancel", color = TextPrimary)
                    }
                }
            )
        }

        // Edit BMI Height & Weight Dialog
        if (showBmiDialog) {
            val currentHeight = userData?.heightCm ?: 0f
            val currentWeight = userData?.weightKg ?: 0f

            var inputHeight by remember { mutableStateOf(currentHeight.toString()) }
            var inputWeight by remember { mutableStateOf(currentWeight.toString()) }

            AlertDialog(
                onDismissRequest = { showBmiDialog = false },
                containerColor = Color(0xFF111111),
                title = { Text("Edit Height & Weight", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = inputHeight,
                            onValueChange = { inputHeight = it },
                            label = { Text("Height (cm)", color = TextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = BrandLime
                            )
                        )
                        OutlinedTextField(
                            value = inputWeight,
                            onValueChange = { inputWeight = it },
                            label = { Text("Weight (kg)", color = TextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = BrandLime
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val ht = inputHeight.toFloatOrNull()
                            val wt = inputWeight.toFloatOrNull()
                            if (ht != null && wt != null) {
                                FirebaseSyncHelper.updateHeightAndWeight(context, ht, wt)
                                reloadData()
                                showBmiDialog = false
                                Toast.makeText(context, "Metrics updated globally!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Please enter valid numeric values.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Update", color = BrandLime, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBmiDialog = false }) {
                        Text("Cancel", color = TextPrimary)
                    }
                }
            )
        }

        // Add Manual Workout Activity Dialog
        if (showAddWorkoutDialog) {
            var workoutName by remember { mutableStateOf("") }
            var durationMins by remember { mutableStateOf("") }
            var caloriesBurned by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddWorkoutDialog = false },
                containerColor = Color(0xFF111111),
                title = { Text("Log Workout", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = workoutName,
                            onValueChange = { workoutName = it },
                            label = { Text("Activity / Workout Name", color = TextSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = BrandLime
                            )
                        )
                        OutlinedTextField(
                            value = durationMins,
                            onValueChange = { durationMins = it },
                            label = { Text("Duration (minutes)", color = TextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = BrandLime
                            )
                        )
                        OutlinedTextField(
                            value = caloriesBurned,
                            onValueChange = { caloriesBurned = it },
                            label = { Text("Calories Burned (kcal)", color = TextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = BrandLime
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val dur = durationMins.toIntOrNull()
                            val cals = caloriesBurned.toFloatOrNull()
                            if (workoutName.trim().isNotEmpty() && dur != null && cals != null) {
                                val log = WorkoutLog(
                                    workoutName = workoutName.trim(),
                                    timestamp = System.currentTimeMillis(),
                                    durationSeconds = dur * 60,
                                    caloriesBurned = cals,
                                    accuracy = 95 // default simulated high accuracy for manual entry
                                )
                                FirebaseSyncHelper.addWorkout(context, log)
                                reloadData()
                                showAddWorkoutDialog = false
                                Toast.makeText(context, "Workout logged successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Please fill in all fields with valid values.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Add", color = BrandLime, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddWorkoutDialog = false }) {
                        Text("Cancel", color = TextPrimary)
                    }
                }
            )
        }

        // Add Quick Weight Dialog
        if (showAddWeightDialog) {
            var inputWeight by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddWeightDialog = false },
                containerColor = Color(0xFF111111),
                title = { Text("Log Current Weight", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = inputWeight,
                            onValueChange = { inputWeight = it },
                            label = { Text("Weight (kg)", color = TextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = BrandLime
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val wt = inputWeight.toFloatOrNull()
                            if (wt != null) {
                                // Also update the global height and weight to keep it synchronized
                                val currentHeight = userData?.heightCm ?: 178.0f
                                FirebaseSyncHelper.updateHeightAndWeight(context, currentHeight, wt)
                                reloadData()
                                showAddWeightDialog = false
                                Toast.makeText(context, "Weight logged globally!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Please enter a valid numeric value.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Add", color = BrandLime, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddWeightDialog = false }) {
                        Text("Cancel", color = TextPrimary)
                    }
                }
            )
        }

        // Edit Goal Weight Dialog
        if (showGoalWeightDialog) {
            var inputGoalWeight by remember { mutableStateOf(goalWeight.toString()) }

            AlertDialog(
                onDismissRequest = { showGoalWeightDialog = false },
                containerColor = Color(0xFF111111),
                title = { Text("Edit Goal Weight", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = inputGoalWeight,
                            onValueChange = { inputGoalWeight = it },
                            label = { Text("Goal Weight (kg)", color = TextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = BrandLime
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val goalWt = inputGoalWeight.toFloatOrNull()
                            if (goalWt != null) {
                                FirebaseSyncHelper.updateGoalWeight(context, goalWt)
                                reloadData()
                                showGoalWeightDialog = false
                                Toast.makeText(context, "Goal weight updated!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Please enter a valid numeric value.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Save", color = BrandLime, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGoalWeightDialog = false }) {
                        Text("Cancel", color = TextPrimary)
                    }
                }
            )
        }
    }
}

// --- VIEW HEADER & NAVIGATION TABS ---

@Composable
fun ReportsHeader(
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundBlack.copy(alpha = 0.85f))
            .border(width = 1.dp, color = CardOverlayColor.copy(alpha = 0.05f))
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Reports",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
        }

        // Custom Tab Selection row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf("Report", "Today", "Weight")
            tabs.forEach { tabName ->
                val isActive = selectedTab == tabName
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isActive) BrandLime else CardOverlayColor.copy(alpha = 0.05f))
                        .clickable { onTabSelected(tabName) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabName,
                        color = if (isActive) BackgroundBlack else TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

// --- REPORT TAB CONTENT ---

@Composable
fun ReportTabContent(
    userStats: UserStats,
    workouts: List<WorkoutLog>,
    userData: UserData?,
    workoutPlan: Map<String, List<String>>?,
    onEditGoalsClick: () -> Unit,
    onEditBmiClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
    ) {
        // Section: Lifetime Stats
        Text(
            text = "Lifetime Progress",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        val totalWorkoutsCount = workouts.size
        val totalCaloriesCount = workouts.sumOf { it.caloriesBurned.toDouble() }.toFloat()
        val totalDurationMinutes = workouts.sumOf { it.durationSeconds } / 60

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCard(
                title = "Workouts",
                value = "$totalWorkoutsCount",
                description = "Total completed",
                icon = Icons.Default.FitnessCenter,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Energy",
                value = String.format(Locale.US, "%.1f", totalCaloriesCount),
                description = "Total burned kcal",
                icon = Icons.Default.OfflineBolt,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Time Spent",
                value = "$totalDurationMinutes",
                description = "Total active mins",
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f)
            )
        }

        // Section: Consistency Grid & Streak
        TrainiumGlassCard(isActive = false) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Weekly Consistency",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = "Streak",
                            tint = BrandLime,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${userStats.currentStreak} Day Streak",
                            color = BrandLime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Grid list Monday to Sunday
                val daysOfWeek = getDaysOfWeek()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    daysOfWeek.forEach { date ->
                        val dayLabel = SimpleDateFormat("E", Locale.US).format(date).substring(0, 1)
                        val isWorkedOut = workouts.any { isDateMatching(date, it.timestamp) }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = dayLabel,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isWorkedOut) BrandLime else CardOverlayColor.copy(alpha = 0.05f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isWorkedOut) BrandLime else CardOverlayColor.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isWorkedOut) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Completed",
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

        // Section: Daily Goals circular progress rings
        TrainiumGlassCard(isActive = false) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily Goals Progress",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onEditGoalsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Edit Goals",
                            tint = TextSecondary
                        )
                    }
                }

                // Calculate today's totals
                val todayWorkouts = workouts.filter {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    sdf.format(Date(it.timestamp)) == sdf.format(Date())
                }
                val todayCalories = todayWorkouts.sumOf { it.caloriesBurned.toDouble() }.toFloat()
                val todayMinutes = todayWorkouts.sumOf { (it.durationSeconds / 60).toDouble() }.toInt()

                val calorieProgress = (todayCalories / userStats.dailyCalorieGoal.coerceAtLeast(1f)).coerceIn(0f, 1f)
                val durationProgress = (todayMinutes.toFloat() / userStats.dailyTimeGoalMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Detailed progress strings
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(BrandLime))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Active Calories", color = TextSecondary, fontSize = 11.sp)
                                Text(
                                    text = String.format(Locale.US, "%.1f / %.1f kcal", todayCalories, userStats.dailyCalorieGoal),
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GradientEnd))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Duration Target", color = TextSecondary, fontSize = 11.sp)
                                Text(
                                    text = "$todayMinutes / ${userStats.dailyTimeGoalMinutes} mins",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Right: Custom double-layer rings
                    DailyGoalsProgressRing(
                        calorieProgress = calorieProgress,
                        durationProgress = durationProgress,
                        modifier = Modifier
                            .size(130.dp)
                            .padding(8.dp)
                    )
                }
            }
        }

        // Section: BMI Spectrum Calculator (recalculated globally)
        val height = userData?.heightCm ?: 0f
        val weight = userData?.weightKg ?: 0f
        val bmi = if (height > 0f) weight / ((height / 100f) * (height / 100f)) else 0f

        val bmiStatus = when {
            bmi < 18.5f -> "Underweight"
            bmi < 25f -> "Normal"
            bmi < 30f -> "Overweight"
            else -> "Obese"
        }
        val bmiColor = when {
            bmi < 18.5f -> Color(0xFF3B82F6)
            bmi < 25f -> Color(0xFF10B981)
            bmi < 30f -> Color(0xFFF59E0B)
            else -> Color(0xFFEF4444)
        }

        TrainiumGlassCard(isActive = false) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BMI Index Spectrum",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onEditBmiClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile Metrics",
                            tint = TextSecondary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left parameters & calculated value
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = String.format(Locale.US, "Height: %.1f cm", height),
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = String.format(Locale.US, "Weight: %.1f kg", weight),
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = String.format(Locale.US, "%.1f", bmi),
                                color = TextPrimary,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bmiColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = bmiStatus,
                                    color = bmiColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Right Spectrum Bar
                    BmiSpectrumBar(
                        bmi = bmi,
                        modifier = Modifier
                            .width(20.dp)
                            .height(120.dp)
                    )
                }
            }
        }


        // Section: Export 30-Day Training Plan to CSV
        ExportPlanCsvCard(
            workoutPlan = workoutPlan
        )
    }
}


// --- EXPORT 30-DAY WORKOUT PLAN TO CSV COMPOSABLE ---

private fun generateWorkoutPlanCsv(workoutPlan: Map<String, List<String>>?): String {
    if (workoutPlan == null) return ""
    val sb = java.lang.StringBuilder()
    sb.append("Day,Workout\n")
    val sortedDays = workoutPlan.keys.sortedBy { com.example.aifitnesscoach.WorkoutProgressHelper.getDayNumber(it) }
    for (dayKey in sortedDays) {
        val dayNum = com.example.aifitnesscoach.WorkoutProgressHelper.getDayNumber(dayKey)
        val exercises = workoutPlan[dayKey] ?: emptyList()
        val exercisesJoined = exercises.joinToString(", ")
        val escapedExercises = if (exercisesJoined.contains(",") || exercisesJoined.contains("\"") || exercisesJoined.contains("\n")) {
            "\"" + exercisesJoined.replace("\"", "\"\"") + "\""
        } else {
            exercisesJoined
        }
        sb.append("Day $dayNum,$escapedExercises\n")
    }
    return sb.toString()
}

@Composable
fun ExportPlanCsvCard(
    workoutPlan: Map<String, List<String>>?
) {
    val context = LocalContext.current
    var csvDataToExport by remember { mutableStateOf("") }
    
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(csvDataToExport.toByteArray(Charsets.UTF_8))
                    Toast.makeText(context, "Workout plan exported successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    TrainiumGlassCard(isActive = false) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = BrandLime,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "30-Day Training Plan",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Export your personalized 30-day training program as a structured CSV spreadsheet to save, view, or print.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = {
                    if (workoutPlan != null) {
                        csvDataToExport = generateWorkoutPlanCsv(workoutPlan)
                        createDocumentLauncher.launch("my_30day_workout_plan.csv")
                    } else {
                        Toast.makeText(context, "No workout plan found to export.", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandLime),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Export plan to CSV",
                    tint = BackgroundBlack,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "EXPORT WORKOUT PLAN TO CSV",
                    color = BackgroundBlack,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --- TODAY TAB CONTENT ---

private fun getDayKeyFromWorkoutLog(log: WorkoutLog, plan: Map<String, List<String>>?): String? {
    if (plan == null) return null
    if (log.id.startsWith("30day_", ignoreCase = true)) {
        val dayPart = log.id.substring("30day_".length)
        if (plan.containsKey(dayPart)) return dayPart
        val normalizedDayPart = dayPart.replace("_", "").replace(" ", "").lowercase(Locale.US)
        for (key in plan.keys) {
            if (key.replace("_", "").replace(" ", "").lowercase(Locale.US) == normalizedDayPart) {
                return key
            }
        }
    }
    val name = log.workoutName.lowercase(Locale.US)
    if (name.contains("day") && name.contains("workout")) {
        val regex = Regex("day[\\s_]*(\\d+)")
        val match = regex.find(name)
        if (match != null) {
            val dayNumStr = match.groupValues[1]
            val dayNum = dayNumStr.toIntOrNull()
            if (dayNum != null) {
                val target = "day$dayNum"
                for (key in plan.keys) {
                    if (key.replace("_", "").replace(" ", "").lowercase(Locale.US) == target) {
                        return key
                    }
                }
            }
        }
    }
    return null
}

@Composable
fun TodayTabContent(
    workouts: List<WorkoutLog>,
    workoutPlan: Map<String, List<String>>?,
    onAddWorkoutClick: () -> Unit
) {
    val context = LocalContext.current
    val todayWorkouts = workouts.filter {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.format(Date(it.timestamp)) == sdf.format(Date())
    }.sortedByDescending { it.timestamp }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Today's Activities",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = SimpleDateFormat("EEEE, MMMM d", Locale.US).format(Date()),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            IconButton(
                onClick = onAddWorkoutClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(BrandLime)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add activity",
                    tint = BackgroundBlack
                )
            }
        }

        if (todayWorkouts.isEmpty()) {
            TrainiumGlassCard(isActive = false) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No activities logged today.\nTap the + button to add one manually.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                todayWorkouts.forEach { item ->
                    val mins = item.durationSeconds / 60
                    val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(item.timestamp))

                    val dayKey = getDayKeyFromWorkoutLog(item, workoutPlan)
                    val progressStr = if (dayKey != null && workoutPlan != null) {
                        val exercises = workoutPlan[dayKey] ?: emptyList()
                        val total = exercises.size
                        val dayNum = WorkoutProgressHelper.getDayNumber(dayKey)
                        
                        val activeDayKey = WorkoutProgressHelper.getActiveDay(context, workoutPlan)
                        val activeDayNum = WorkoutProgressHelper.getDayNumber(activeDayKey)
                        
                        if (dayNum < activeDayNum) {
                            "100% Progress"
                        } else {
                            val completedSet = context.getTrainiumPrefs("app_prefs")
                                .getStringSet("COMPLETED_EXERCISES_$dayKey", emptySet()) ?: emptySet()
                            val completed = completedSet.size
                            val percent = if (total > 0) (completed * 100) / total else 0
                            "$percent% Progress"
                        }
                    } else {
                        "${item.accuracy}% Progress"
                    }

                    HistoryItem(
                        name = item.workoutName.replace("_", " "),
                        date = "$timeStr  •  $mins mins",
                        reps = "${item.caloriesBurned} kcal",
                        accuracy = progressStr
                    )
                }
            }
        }
    }
}

// --- WEIGHT TAB CONTENT ---

@Composable
fun WeightTabContent(
    weights: List<WeightLog>,
    goalWeight: Float,
    onQuickAddWeight: () -> Unit,
    onEditGoalWeight: () -> Unit
) {
    val sortedWeights = weights.sortedBy { it.timestamp }
    val currentWeight = sortedWeights.lastOrNull()?.weight ?: 0f
    val deltaStr = calculate30DayDelta(sortedWeights)

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Weight Analytics",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onQuickAddWeight,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(BrandLime)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Weight Entry",
                    tint = BackgroundBlack
                )
            }
        }

        // Stats Cards Highlight
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "Current",
                value = String.format(Locale.US, "%.1f kg", currentWeight),
                description = "Last logged weight",
                icon = Icons.Default.Scale,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Goal Target",
                value = String.format(Locale.US, "%.1f kg", goalWeight),
                description = "Tap to edit goal",
                icon = Icons.Default.Flag,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEditGoalWeight() }
            )
            StatCard(
                title = "30-Day Delta",
                value = deltaStr,
                description = "Monthly weight change",
                icon = Icons.Default.TrendingFlat,
                modifier = Modifier.weight(1f)
            )
        }

        // Weight chart card
        Text(
            text = "Weight Trend History",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        TrainiumGlassCard(isActive = false) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Log weight changes to track your trajectory over time. Tap on chart nodes to view details.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                WeightTrendLineChart(
                    weights = sortedWeights,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }
        }
    }
}

// --- DOUBLE-LAYER CIRCULAR PROGRESS RINGS COMPOSABLE ---

@Composable
fun DailyGoalsProgressRing(
    calorieProgress: Float,
    durationProgress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val center = Offset(width / 2f, height / 2f)

        // Outer calorie progress ring parameters
        val outerRadius = (Math.min(width, height) / 2f) - 10.dp.toPx()
        val innerRadius = outerRadius - 18.dp.toPx()
        val strokeWidth = 8.dp.toPx()

        // 1. Draw Outer Ring Background
        drawCircle(
            color = CardOverlayColor.copy(alpha = 0.05f),
            radius = outerRadius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // 2. Draw Outer Active Sweep
        val outerSweepAngle = calorieProgress * 360f
        drawArc(
            color = BrandLime,
            startAngle = -90f,
            sweepAngle = outerSweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2, outerRadius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Draw Dot on Outer Leading Edge
        if (outerSweepAngle > 0f) {
            val rad = Math.toRadians((outerSweepAngle - 90f).toDouble())
            val dotX = center.x + outerRadius * Math.cos(rad).toFloat()
            val dotY = center.y + outerRadius * Math.sin(rad).toFloat()
            drawCircle(
                color = BrandLime.copy(alpha = 0.4f),
                radius = 7.dp.toPx(),
                center = Offset(dotX, dotY)
            )
            drawCircle(
                color = TextPrimary,
                radius = 3.dp.toPx(),
                center = Offset(dotX, dotY)
            )
        }

        // 3. Draw Inner Ring Background
        drawCircle(
            color = CardOverlayColor.copy(alpha = 0.05f),
            radius = innerRadius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // 4. Draw Inner Active Sweep
        val innerSweepAngle = durationProgress * 360f
        drawArc(
            color = GradientEnd,
            startAngle = -90f,
            sweepAngle = innerSweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
            size = Size(innerRadius * 2, innerRadius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Draw Dot on Inner Leading Edge
        if (innerSweepAngle > 0f) {
            val rad = Math.toRadians((innerSweepAngle - 90f).toDouble())
            val dotX = center.x + innerRadius * Math.cos(rad).toFloat()
            val dotY = center.y + innerRadius * Math.sin(rad).toFloat()
            drawCircle(
                color = GradientEnd.copy(alpha = 0.4f),
                radius = 7.dp.toPx(),
                center = Offset(dotX, dotY)
            )
            drawCircle(
                color = TextPrimary,
                radius = 3.dp.toPx(),
                center = Offset(dotX, dotY)
            )
        }
    }
}

// --- VERTICAL COLOR-CODED BMI SPECTRUM BAR ---

@Composable
fun BmiSpectrumBar(
    bmi: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val colors = listOf(
            Color(0xFFEF4444), // Obese (Red) - top
            Color(0xFFF59E0B), // Overweight (Yellow)
            Color(0xFF10B981), // Normal (Green)
            Color(0xFF3B82F6)  // Underweight (Blue) - bottom
        )

        drawRoundRect(
            brush = Brush.verticalGradient(colors = colors),
            size = Size(width, height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2, width / 2)
        )

        val minBmi = 15f
        val maxBmi = 35f
        val clamped = bmi.coerceIn(minBmi, maxBmi)

        val fraction = (clamped - minBmi) / (maxBmi - minBmi)
        val ptrY = height - (fraction * height)

        // White border indicator dot
        drawCircle(
            color = TextPrimary,
            radius = 6.dp.toPx(),
            center = Offset(width / 2f, ptrY)
        )
        // Black core indicator dot
        drawCircle(
            color = BackgroundBlack,
            radius = 3.dp.toPx(),
            center = Offset(width / 2f, ptrY)
        )
    }
}

// --- WEIGHT TREND LINE CHART WITH INTERACTIVE TOOLTIPS ---

@Composable
fun WeightTrendLineChart(
    weights: List<WeightLog>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    
    // Sort and use all points to allow horizontal scrolling
    val chartData = weights

    if (chartData.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No weight records recorded.", color = TextSecondary, fontSize = 13.sp)
        }
        return
    }

    // Min and Max logic for Y axis
    val minW = chartData.minOf { it.weight }
    val maxW = chartData.maxOf { it.weight }
    val yMin = if (maxW == minW) minW - 5f else minW - 1f
    val yMax = if (maxW == minW) maxW + 5f else maxW + 1f
    val yRange = yMax - yMin

    // State parameters for node selection interaction
    var selectedIndex by remember { mutableStateOf(-1) }
    var tooltipText by remember { mutableStateOf<String?>(null) }
    var tooltipOffset by remember { mutableStateOf<Offset?>(null) }

    // Map offset coordinates list dynamically
    val points = remember(chartData, yMin, yMax) { mutableListOf<Offset>() }

    // Spacing between points
    val spacing = 65.dp
    // Calculate total width of chart (parent list padding is 20.dp on each side, so subtract 40.dp)
    val chartWidth = maxOf(configuration.screenWidthDp.dp - 40.dp, spacing * chartData.size)
    val scrollState = rememberScrollState()

    // Auto-scroll to the end (latest dates) when chartData changes
    LaunchedEffect(chartData.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        Box(
            modifier = Modifier
                .width(chartWidth)
                .fillMaxHeight()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(chartData) {
                        detectTapGestures { offset ->
                            var clickedIdx = -1
                            var bestDist = Float.MAX_VALUE
                            for (i in points.indices) {
                                val pt = points[i]
                                val dist = Math.hypot((offset.x - pt.x).toDouble(), (offset.y - pt.y).toDouble()).toFloat()
                                if (dist < 32.dp.toPx() && dist < bestDist) {
                                    bestDist = dist
                                    clickedIdx = i
                                }
                            }

                            if (clickedIdx != -1) {
                                selectedIndex = clickedIdx
                                val dateStr = SimpleDateFormat("MMM d", Locale.US).format(Date(chartData[clickedIdx].timestamp))
                                tooltipText = String.format(Locale.US, "%s\n%.1f kg", dateStr, chartData[clickedIdx].weight)
                                tooltipOffset = points[clickedIdx]
                            } else {
                                selectedIndex = -1
                                tooltipText = null
                                tooltipOffset = null
                            }
                        }
                    }
            ) {
                val width = size.width
                val height = size.height

                val padLeft = 24.dp.toPx()
                val padRight = 24.dp.toPx()
                val padTop = 32.dp.toPx()
                val padBottom = 32.dp.toPx() // Increased to prevent date text clipping

                val graphW = width - padLeft - padRight
                val graphH = height - padTop - padBottom

                // Compute actual locations
                points.clear()
                for (i in chartData.indices) {
                    val fractionX = if (chartData.size > 1) i.toFloat() / (chartData.size - 1) else 0.5f
                    val fractionY = (chartData[i].weight - yMin) / yRange

                    val x = padLeft + fractionX * graphW
                    val y = padTop + graphH - (fractionY * graphH)
                    points.add(Offset(x, y))
                }

                // Draw horizontal reference helper lines
                val dashedStroke = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
                for (i in 0..2) {
                    val refY = padTop + (i.toFloat() / 2f) * graphH
                    drawLine(
                        color = CardOverlayColor.copy(alpha = 0.08f),
                        start = Offset(padLeft, refY),
                        end = Offset(width - padRight, refY),
                        strokeWidth = dashedStroke.width,
                        pathEffect = dashedStroke.pathEffect
                    )
                }

                // Draw area gradient path under the line
                if (points.size > 1) {
                    val fillPath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                        lineTo(points.last().x, padTop + graphH)
                        lineTo(points.first().x, padTop + graphH)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(BrandLime.copy(alpha = 0.2f), Color.Transparent),
                            startY = padTop,
                            endY = padTop + graphH
                        )
                    )

                    // Draw actual trend connecting line
                    val strokePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    drawPath(
                        path = strokePath,
                        color = BrandLime,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Draw individual nodes / dots
                points.forEachIndexed { index, pt ->
                    val isSelected = index == selectedIndex
                    // Outer ring for selected
                    if (isSelected) {
                        drawCircle(
                            color = CardOverlayColor.copy(alpha = 0.3f),
                            radius = 8.dp.toPx(),
                            center = pt
                        )
                    }
                    // Core dot
                    drawCircle(
                        color = if (isSelected) TextPrimary else BrandLime,
                        radius = 4.dp.toPx(),
                        center = pt
                    )
                }

                // Draw X-axis date labels under the nodes
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb((0.5f * 255).toInt(), 255, 255, 255)
                    textSize = density.run { 10.sp.toPx() }
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                for (i in chartData.indices) {
                    val dateStr = SimpleDateFormat("MMM d", Locale.US).format(Date(chartData[i].timestamp))
                    drawContext.canvas.nativeCanvas.drawText(
                        dateStr,
                        points[i].x,
                        padTop + graphH + 18.dp.toPx(),
                        paint
                    )
                }
            }

            // Overlay Interactive Tooltip Card
            AnimatedVisibility(
                visible = tooltipText != null && tooltipOffset != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                tooltipOffset?.let { offset ->
                    val xDp = with(density) { offset.x.toDp() }
                    val yDp = with(density) { offset.y.toDp() }

                    Box(
                        modifier = Modifier
                            .offset(x = xDp - 45.dp, y = yDp - 60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF222222))
                            .border(1.dp, CardOverlayColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tooltipText ?: "",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// --- INTERNAL HELPERS ---

private fun getDaysOfWeek(): List<Date> {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)

    val currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val daysToSubtract = if (currentDayOfWeek == Calendar.SUNDAY) {
        6
    } else {
        currentDayOfWeek - Calendar.MONDAY
    }
    cal.add(Calendar.DAY_OF_YEAR, -daysToSubtract)

    val list = mutableListOf<Date>()
    for (i in 0 until 7) {
        list.add(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return list
}

private fun isDateMatching(date: Date, timestamp: Long): Boolean {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return sdf.format(date) == sdf.format(Date(timestamp))
}

private fun calculate30DayDelta(weights: List<WeightLog>): String {
    if (weights.isEmpty()) return "0.0 kg"
    val latest = weights.last()
    val targetTime = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)

    // Find entry closest to 30 days ago
    val pastWeight = weights.minByOrNull { Math.abs(it.timestamp - targetTime) } ?: weights.first()
    val delta = latest.weight - pastWeight.weight
    val prefix = if (delta > 0) "+" else ""
    return String.format(Locale.US, "%s%.1f kg", prefix, delta)
}

@Composable
fun StatCard(
    title: String,
    value: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    TrainiumGlassCard(
        isActive = false,
        modifier = modifier
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = BrandLime,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = CardOverlayColor.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun HistoryItem(
    name: String,
    date: String,
    reps: String,
    accuracy: String
) {
    TrainiumGlassCard(isActive = false) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$date  •  $reps",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(BrandLime.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = accuracy,
                    color = BrandLime,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
