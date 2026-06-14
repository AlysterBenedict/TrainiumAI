package com.example.aifitnesscoach

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.res.vectorResource
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.aifitnesscoach.network.UserData
import com.example.aifitnesscoach.network.FirebaseSyncHelper
import com.example.aifitnesscoach.network.WorkoutLog
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Timer
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.aifitnesscoach.WorkoutProgressHelper
import java.util.Locale
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.PlayArrow

class HomeActivity_ui : AppCompatActivity() {

    private var userName = "User"
    private var profilePhotoUrl: String? = null
    private val resumeTrigger = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            userName = currentUser.displayName?.split(" ")?.firstOrNull() ?: "User"
            profilePhotoUrl = getHighResProfilePhotoUrl(currentUser.photoUrl?.toString())
            com.example.aifitnesscoach.network.FirebaseSyncHelper.performFullSync(this) {
                runOnUiThread {
                    resumeTrigger.value++
                }
            }
        } else {
            val globalPrefs = getSharedPreferences("global_prefs", MODE_PRIVATE)
            userName = globalPrefs.getString("local_user_name", "Guest")?.split(" ")?.firstOrNull() ?: "Guest"
            profilePhotoUrl = null
        }



        setContent {
            val trigger = resumeTrigger.value
            TrainiumTheme {
                HomeScreen(
                    userName = userName,
                    profilePhotoUrl = profilePhotoUrl,
                    resumeTrigger = trigger,
                    onNavigateChat = { navigateToTab(this, "coach") },
                    onNavigateCustomWorkout = { startActivity(Intent(this, CustomWorkoutActivity_ui::class.java)) },
                    onNavigatePersonalizedWorkout = { startActivity(Intent(this, PhotoInstructionsActivity_ui::class.java)) },
                    onNavigateProfile = { navigateToTab(this, "profile") },
                    onContinueProgress = { continueProgress() },
                    onResetPlan = {
                        com.example.aifitnesscoach.network.FirebaseSyncHelper.deleteWorkoutPlan(this)
                        resumeTrigger.value++
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.aifitnesscoach.network.FirebaseSyncHelper.syncSharedPreferencesToDatabase(this)
        resumeTrigger.value++
    }

    private fun continueProgress() {
        val sharedPrefs = getTrainiumPrefs("app_prefs")
        val savedPlanJson = sharedPrefs.getString("SAVED_WORKOUT_PLAN", null)

        if (savedPlanJson != null) {
            val intent = Intent(this, WorkoutPlanActivity_ui::class.java).apply {
                putExtra("WORKOUT_PLAN", savedPlanJson)
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "No saved workout plan found.", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userName: String,
    profilePhotoUrl: String?,
    resumeTrigger: Int,
    onNavigateChat: () -> Unit,
    onNavigateCustomWorkout: () -> Unit,
    onNavigatePersonalizedWorkout: () -> Unit,
    onNavigateProfile: () -> Unit,
    onContinueProgress: () -> Unit,
    onResetPlan: () -> Unit
) {
    val scrollState = rememberScrollState()
    var showResetPlanDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val sharedPrefs = remember { context.getTrainiumPrefs("app_prefs") }
    var showBmiDialog by remember { mutableStateOf(false) }

    var localTrigger by remember { mutableStateOf(0) }
    val combinedTrigger = resumeTrigger + localTrigger

    val user = remember(combinedTrigger) { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser }
    val isLocalAccount = user == null

    val pullToRefreshState = rememberPullToRefreshState()
    var isFirstRefresh by remember { mutableStateOf(true) }

    if (!isLocalAccount && pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            if (isFirstRefresh) {
                FirebaseSyncHelper.performFullSync(context) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        localTrigger++
                        isFirstRefresh = false
                        pullToRefreshState.endRefresh()
                        Toast.makeText(context, "Data pulled from cloud successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                FirebaseSyncHelper.pushLocalDataToFirebase(context) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        localTrigger++
                        pullToRefreshState.endRefresh()
                        Toast.makeText(context, "Data pushed to cloud successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Performance Optimization: Load heavy SharedPreferences and Gson deserialization asynchronously on Dispatchers.IO
    var userData by remember { mutableStateOf(UserData()) }
    var savedPlanJson by remember { mutableStateOf<String?>(null) }
    var planMap by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var activeDay by remember { mutableStateOf("Day_1") }
    var activeDayNum by remember { mutableStateOf(1) }
    var completedCount by remember { mutableStateOf(0) }
    var totalExercises by remember { mutableStateOf(0) }
    var durationSec by remember { mutableStateOf(0) }
    var caloriesBurned by remember { mutableStateOf(0f) }
    var workouts by remember { mutableStateOf(emptyList<WorkoutLog>()) }

    LaunchedEffect(combinedTrigger) {
        withContext(Dispatchers.IO) {
            val uData = FirebaseSyncHelper.getGlobalUserData(context)
            val planJson = sharedPrefs.getString("SAVED_WORKOUT_PLAN", null)
            val pMap: Map<String, List<String>> = if (planJson != null) {
                val type = object : TypeToken<Map<String, List<String>>>() {}.type
                try {
                    Gson().fromJson(planJson, type)
                } catch (e: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }
            val actDay = WorkoutProgressHelper.getActiveDay(context, pMap)
            val actDayNum = WorkoutProgressHelper.getDayNumber(actDay)
            val exList = pMap[actDay] ?: emptyList()
            val totEx = exList.size
            val compCount = exList.indices.count {
                WorkoutProgressHelper.isExerciseCompleted(context, actDay, it)
            }
            val durSec = WorkoutProgressHelper.getDayDuration(context, actDay)
            val calBurned = WorkoutProgressHelper.getDayCalories(context, actDay)
            val wrk = FirebaseSyncHelper.getWorkouts(context)

            // Push to Main state updates
            userData = uData
            savedPlanJson = planJson
            planMap = pMap
            activeDay = actDay
            activeDayNum = actDayNum
            completedCount = compCount
            totalExercises = totEx
            durationSec = durSec
            caloriesBurned = calBurned
            workouts = wrk
        }
    }

    val todayWorkouts = remember(workouts) {
        workouts.filter {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.format(java.util.Date(it.timestamp)) == sdf.format(java.util.Date())
        }
    }
    val todayCalories = remember(todayWorkouts) {
        todayWorkouts.sumOf { it.caloriesBurned.toDouble() }.toFloat()
    }
    val todayMinutes = remember(todayWorkouts) {
        todayWorkouts.sumOf { (it.durationSeconds / 60).toDouble() }.toInt()
    }

    val displayDuration = if (durationSec > 0) "${durationSec / 60} min" else "0 min"
    val displayCalories = if (caloriesBurned > 0f) "${caloriesBurned.toInt()} kcal" else "0 kcal"
    val userGoal = userData.goal.ifBlank { "Lose Weight" }
    val displaySubtitle = "$userGoal in 30 Days • Day $activeDayNum"
    val progressPercent = if (totalExercises > 0) completedCount.toFloat() / totalExercises.toFloat() else 0f

    val nestedScrollModifier = if (isLocalAccount) {
        Modifier
    } else {
        Modifier.nestedScroll(pullToRefreshState.nestedScrollConnection)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .then(nestedScrollModifier)
    ) {
        // Main Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 48.dp, bottom = 140.dp)
        ) {
            // Welcome Back Header (Inline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Welcome Back,", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(userName, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }

                // Profile Shortcut
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E201E))
                        .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), CircleShape)
                        .bounceClick { onNavigateProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    if (profilePhotoUrl != null) {
                        Image(
                            painter = rememberAsyncImagePainter(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(profilePhotoUrl)
                                    .crossfade(true)
                                    .build(),
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                            ),
                            contentDescription = "Profile Photo",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = BrandLime,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Section Header (Dynamic Date & Time) - isolated recomposition
            DynamicDateTimeHeader()

            // Cloud Sync Status Badge (Only for non-local accounts)
            if (!isLocalAccount) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFirstRefresh) BrandLime.copy(alpha = 0.15f) else Color(0xFF1E201E))
                        .border(1.dp, if (isFirstRefresh) BrandLime.copy(alpha = 0.3f) else CardOverlayColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isFirstRefresh) BrandLime else Color(0xFF4CAF50))
                        )
                        Text(
                            text = if (isFirstRefresh) "Cloud Sync: Pull to download data" else "Cloud Sync: Ready to backup",
                            color = if (isFirstRefresh) BrandLime else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Action Cards
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Today's Cumulative Metrics Row (side-by-side, no horizontal scrolling)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HomeMetricCard(
                        title = "Workouts",
                        value = "${todayWorkouts.size}",
                        description = "Completed",
                        icon = Icons.Default.FitnessCenter,
                        modifier = Modifier.weight(1f)
                    )
                    HomeMetricCard(
                        title = "Energy",
                        value = String.format(java.util.Locale.US, "%.0f kcal", todayCalories),
                        description = "Burned",
                        icon = Icons.Default.OfflineBolt,
                        modifier = Modifier.weight(1f)
                    )
                    HomeMetricCard(
                        title = "Time",
                        value = "$todayMinutes min",
                        description = "Active",
                        icon = Icons.Default.Timer,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (savedPlanJson != null) {
                    // Continue Progress (Promoted Large Card)
                    TrainiumMetricStyleCard(
                        onClick = onContinueProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 1. Top Section: Badge and Action Indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(BrandLime)
                                )
                                Text(
                                    text = "ACTIVE ROUTINE",
                                    color = BrandLime,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BrandLime.copy(alpha = 0.15f))
                                    .border(1.dp, BrandLime.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Day $activeDayNum",
                                    color = BrandLime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 2. Title & Subtitle Section
                        Text(
                            text = "Continue Progress",
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = displaySubtitle,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = CardOverlayColor.copy(alpha = 0.08f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. Grid of Metric Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Duration Chip
                            MetricChip(
                                value = displayDuration,
                                label = "Duration",
                                icon = Icons.Default.Timer,
                                modifier = Modifier.weight(1f)
                            )
                            // Calories Chip
                            MetricChip(
                                value = displayCalories,
                                label = "Calories",
                                icon = Icons.Default.OfflineBolt,
                                modifier = Modifier.weight(1f)
                            )
                            // Exercises Chip
                            MetricChip(
                                value = "$completedCount/$totalExercises",
                                label = "Exercises",
                                icon = Icons.Default.FitnessCenter,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 4. Progress bar header & indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Today's Progress",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${(progressPercent * 100).toInt()}% Done",
                                color = BrandLime,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { progressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = BrandLime,
                            trackColor = CardOverlayColor.copy(alpha = 0.08f)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // 5. Bright full width action button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(CircleShape)
                                .background(BrandLime)
                                .clickable { onContinueProgress() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = BackgroundBlack,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "RESUME DAY $activeDayNum SESSION",
                                    color = BackgroundBlack,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                } else {
                    // Generate AI Plan Promoted Card (Revamped - Matches the visual theme of the Active Plan Card)
                    TrainiumMetricStyleCard(
                        onClick = onNavigatePersonalizedWorkout,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 1. Top Section: Badge and AI Indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(BrandLime)
                                )
                                Text(
                                    text = "RECOMMENDED ROUTINE",
                                    color = BrandLime,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BrandLime.copy(alpha = 0.15f))
                                    .border(1.dp, BrandLime.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "AI Plan",
                                    color = BrandLime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 2. Title & Subtitle Section
                        Text(
                            text = "Generate AI Workout",
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Tailored 30-day program based on your biometric profile.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = CardOverlayColor.copy(alpha = 0.08f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. Small pointed description to fill the empty space
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PointedDescriptionItem(text = "Hyper-personalized 30-day training calendar")
                            PointedDescriptionItem(text = "Aligned dynamically to your scanned biometrics")
                            PointedDescriptionItem(text = "Adaptive AI progressions that scale with strength")
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // 4. Bright full width action button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(CircleShape)
                                .background(BrandLime)
                                .clickable { onNavigatePersonalizedWorkout() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "GENERATE AI WORKOUT PLAN",
                                    color = BackgroundBlack,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                // Custom Workout
                HomeActionItem(
                    title = "Custom Workout",
                    description = "Build your own routine",
                    icon = Icons.Default.FitnessCenter,
                    onClick = onNavigateCustomWorkout
                )



                // Trainium AI Coach
                HomeActionItem(
                    title = "Trainium AI",
                    description = "Chat with your AI coach",
                    icon = ImageVector.vectorResource(id = R.drawable.google_logo),
                    tint = Color.Unspecified,
                    onClick = onNavigateChat
                )

                Spacer(modifier = Modifier.height(16.dp))

                // AI Biometrics Profile Card
                BiometricsProfileCard(
                    userData = userData,
                    onEditBmiClick = { showBmiDialog = true }
                )

                if (savedPlanJson != null || userData.heightCm > 0f || userData.weightKg > 0f) {
                    Spacer(modifier = Modifier.height(16.dp))
                    // Reset/Clear Data Button
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(160.dp)
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
                                .bounceClick {
                                    showResetPlanDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Reset Data",
                                    tint = AccentRed.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Reset Data",
                                    color = AccentRed.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }



        // Floating Glass Bottom Navigation Dock
        val context = LocalContext.current
        TrainiumBottomDock(
            activeTab = "home",
            onTabSelected = { tab ->
                navigateToTab(context, tab)
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Pull To Refresh Indicator
        if (!isLocalAccount) {
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Color(0xFF1E201E),
                contentColor = BrandLime
            )
        }

        if (showResetPlanDialog) {
            AlertDialog(
                onDismissRequest = { showResetPlanDialog = false },
                title = {
                    Text(
                        text = "Reset All Data?",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete your current personalized workout plan and clear your estimated biometric profile? This will clear all progress and cannot be undone.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showResetPlanDialog = false
                            onResetPlan()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentRed,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Reset Data", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showResetPlanDialog = false }
                    ) {
                        Text("Cancel", color = CardOverlayColor.copy(alpha = 0.6f))
                    }
                },
                containerColor = Color(0xFF1E201E),
                shape = RoundedCornerShape(24.dp)
            )
        }

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
                                userData = FirebaseSyncHelper.getGlobalUserData(context)
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
    }
}

@Composable
fun DynamicDateTimeHeader(modifier: Modifier = Modifier) {
    val sdf = remember { java.text.SimpleDateFormat("EEEE, MMMM d • h:mm:ss a", java.util.Locale.getDefault()) }
    var currentDateTime by remember {
        mutableStateOf(sdf.format(java.util.Calendar.getInstance().time))
    }

    LaunchedEffect(sdf) {
        while (true) {
            currentDateTime = sdf.format(java.util.Calendar.getInstance().time)
            kotlinx.coroutines.delay(1000L)
        }
    }

    Text(
        text = currentDateTime,
        color = TextPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Black,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun HomeActionItem(
    title: String,
    description: String,
    icon: ImageVector,
    isAi: Boolean = false,
    tint: Color = BrandLime,
    onClick: () -> Unit
) {
    TrainiumGlassCard(
        isActive = false,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardOverlayColor.copy(alpha = 0.05f))
                    .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isAi) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(BrandLime.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "AI",
                                color = BrandLime,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Go",
                tint = TextSecondary.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun FocusCard(
    title: String,
    subtitle: String,
    tag: String,
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(145.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, CardOverlayColor.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable { }
    ) {
        Image(
            painter = rememberAsyncImagePainter(imageUrl),
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, BackgroundBlack.copy(alpha = 0.9f))
                    )
                )
        )

        // Tag at top left
        Box(
            modifier = Modifier
                .padding(10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BackgroundBlack.copy(alpha = 0.6f))
                .border(1.dp, CardOverlayColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .align(Alignment.TopStart)
        ) {
            Text(
                tag.uppercase(),
                color = BrandLime,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        // Info text at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

// --- AI BIOMETRICS PROFILE COMPOSABLE ---

@Composable
fun BiometricsProfileCard(
    userData: UserData?,
    onEditBmiClick: () -> Unit
) {
    TrainiumGlassCard(isActive = false) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = BrandLime,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Biometrics Profile",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onEditBmiClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile Metrics",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (userData == null) {
                Text(
                    text = "No biometric scan history found. Please complete profile body scanner onboarding.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            } else {
                // Main stats: Age, Gender, Goal, Level
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ProfileHeaderItem("Age", "${userData.age} yrs")
                    ProfileHeaderItem("Gender", userData.gender)
                    ProfileHeaderItem("Goal", userData.goal)
                    ProfileHeaderItem("Level", userData.level)
                }

                HorizontalDivider(color = CardOverlayColor.copy(alpha = 0.08f), thickness = 1.dp)

                // The 15 body metrics
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "ESTIMATED MEASUREMENTS",
                        color = CardOverlayColor.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricRow("Height", userData.heightCm, "cm")
                            MetricRow("Weight", userData.weightKg, "kg")
                            MetricRow("Chest", userData.chestCm, "cm")
                            MetricRow("Waist", userData.waistCm, "cm")
                            MetricRow("Hip", userData.hipCm, "cm")
                            MetricRow("Bicep", userData.bicepCm, "cm")
                            MetricRow("Thigh", userData.thighCm, "cm")
                            MetricRow("Calf", userData.calfCm, "cm")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricRow("Ankle", userData.ankleCm, "cm")
                            MetricRow("Arm Length", userData.armLengthCm, "cm")
                            MetricRow("Forearm", userData.forearmCm, "cm")
                            MetricRow("Leg Length", userData.legLengthCm, "cm")
                            MetricRow("Shoulder Width", userData.shoulderBreadthCm, "cm")
                            MetricRow("Sld-to-Crotch", userData.shoulderToCrotchCm, "cm")
                            MetricRow("Wrist", userData.wristCm, "cm")
                            MetricRow("BMI Index", userData.bmi, "")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileHeaderItem(label: String, value: String) {
    Column {
        Text(label, color = TextSecondary, fontSize = 10.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MetricRow(label: String, value: Float, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(
            text = String.format(Locale.US, "%.1f %s", value, unit).trim(),
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HomeMetricCard(
    title: String,
    value: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            SurfaceLow.copy(alpha = 0.97f),
            SurfaceLow.copy(alpha = 0.93f)
        )
    )
    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            CardOverlayColor.copy(alpha = 0.18f),
            CardOverlayColor.copy(alpha = 0.04f)
        )
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundBrush)
            .border(1.dp, borderBrush, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = BrandLime,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = description,
                color = TextSecondary.copy(alpha = 0.8f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MetricChip(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111111).copy(alpha = 0.6f))
            .border(1.dp, CardOverlayColor.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = BrandLime,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun HighlightBadge(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CardOverlayColor.copy(alpha = 0.03f))
            .border(1.dp, CardOverlayColor.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = BrandLime,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PointedDescriptionItem(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(BrandLime)
        )
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}



