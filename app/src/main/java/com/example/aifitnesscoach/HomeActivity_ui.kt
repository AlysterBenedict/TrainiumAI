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
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowForward
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
import com.example.aifitnesscoach.network.UserData
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson

class HomeActivity_ui : AppCompatActivity() {

    private var userName = "User"
    private var profilePhotoUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = Firebase.auth.currentUser
        currentUser?.let {
            userName = it.displayName?.split(" ")?.firstOrNull() ?: "User"
            profilePhotoUrl = it.photoUrl?.toString()
        }

        // Initialize a mock workout plan and metrics if none exist, so the user can test the screens
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (sharedPrefs.getString("SAVED_WORKOUT_PLAN", null) == null) {
            val mockPlan = mapOf(
                "Day_1" to listOf("Deep Squats", "Push-ups", "Plank Hold"),
                "Day_2" to listOf("Forward Lunges", "Bicep Curls", "High Knees"),
                "Day_3" to listOf("Burpees", "Inchworms", "Glute Bridges")
            )
            val mockUserMetrics = UserData(
                age = 25, gender = "Male", heightCm = 178f, weightKg = 72f,
                goal = "Build Muscle", level = "Intermediate", bmi = 22.7f,
                chestCm = 95f, waistCm = 80f, hipCm = 98f, thighCm = 56f, bicepCm = 34f
            )
            sharedPrefs.edit()
                .putString("SAVED_WORKOUT_PLAN", Gson().toJson(mockPlan))
                .putString("SAVED_USER_METRICS", Gson().toJson(mockUserMetrics))
                .apply()
        }

        setContent {
            TrainiumTheme {
                HomeScreen(
                    userName = userName,
                    profilePhotoUrl = profilePhotoUrl,
                    onNavigateChat = { navigateToTab(this, "coach") },
                    onNavigateCustomWorkout = { startActivity(Intent(this, CustomWorkoutActivity_ui::class.java)) },
                    onNavigatePersonalizedWorkout = { startActivity(Intent(this, PhotoInstructionsActivity_ui::class.java)) },
                    onNavigateProfile = { navigateToTab(this, "profile") },
                    onContinueProgress = { continueProgress() }
                )
            }
        }
    }

    private fun continueProgress() {
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
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

@Composable
fun HomeScreen(
    userName: String,
    profilePhotoUrl: String?,
    onNavigateChat: () -> Unit,
    onNavigateCustomWorkout: () -> Unit,
    onNavigatePersonalizedWorkout: () -> Unit,
    onNavigateProfile: () -> Unit,
    onContinueProgress: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Main Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 112.dp, bottom = 110.dp)
        ) {
            // Section Header
            Text(
                text = "Let's crush it today 🔥",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main Action Cards
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Continue Progress (Promoted Large Card)
                TrainiumGlassCard(
                    isActive = true,
                    onClick = onContinueProgress
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(BrandLime, GradientEnd)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "Trending Up",
                                tint = Color.Black,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Continue Progress",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hypertrophy Phase • Day 12",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Go",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("45 min", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Duration", color = TextSecondary, fontSize = 12.sp)
                        }
                        Column {
                            Text("320 kcal", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Calories", color = TextSecondary, fontSize = 12.sp)
                        }
                        Column {
                            Text("5 sets", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Main Workout", color = TextSecondary, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { 0.75f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = BrandLime,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }

                // Custom Workout
                HomeActionItem(
                    title = "Custom Workout",
                    description = "Build your own routine",
                    icon = Icons.Default.FitnessCenter,
                    onClick = onNavigateCustomWorkout
                )

                // Personalized Workout
                HomeActionItem(
                    title = "Personalized Workout",
                    description = "Tailored to your body",
                    icon = Icons.Default.Psychology,
                    isAi = true,
                    onClick = onNavigatePersonalizedWorkout
                )

                // Trainium AI Coach
                HomeActionItem(
                    title = "Trainium AI",
                    description = "Chat with your AI coach",
                    icon = Icons.Default.SmartToy,
                    onClick = onNavigateChat
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Daily Focus
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Focus 🌟",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { }
                ) {
                    Text("Show more", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Show more",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Focus Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FocusCard(
                    title = "Mobility Flow",
                    subtitle = "Focus on recovery",
                    tag = "12 Min",
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuAWTq2aIeS4B5uTRkjh7uB_CmAmwyw_r957TclDsF1POwN7_Taj1LRd6jqg_YpNDbCmcILXz8K-BNLVZr9UgP1iHrYKlEjsJUrsTe1QSzaApmIU4d3WPirB1w5ZS9EUKRgQEqvCq3_cT8F2yMq7uWNkgLyG_cTh1Xs5PufJQeBY8ji10VwgmxtI1Rp8O9sj1-ggxDiSOVqjaC16k2TmMKB0714kLJe5jesbk-lE9Rtx5A_JRVmdz1fm36muKsRxAGt4UbNUTUUcEbuV",
                    modifier = Modifier.weight(1f)
                )

                FocusCard(
                    title = "AI Static Hold",
                    subtitle = "Build endurance",
                    tag = "Core",
                    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBy2j_DnIuQssAQzP2iOVBu7i0X4md6lbgVhGEzot8XIftppoNMEYzrOc0GD5nqD5ij_BR6vvbQEraESXC3VIu8nZdpmdY-h59KcfeePP4XuKY9gmAXy9izpVxjyrlMsYOgfzJu6W7YPw6cLCJQUGmG75hZ4XiAya_45yvt19QWj6Y-QEpyilGw0Ro3ci5a0gNhKiSbjdEj1gBaXfOIujfm2_0AGKsKn5pQAh_zgI8rhyB7a_eBDFwJMLR6_B4A4e9v7ZWLudAB9xvV",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Top Glass AppBar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(96.dp)
                .background(Color.Black.copy(alpha = 0.85f))
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Welcome Back,", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(userName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Chat Shortcut
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(BrandLime, GradientEnd)
                                )
                            )
                            .bounceClick { onNavigateChat() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Chat",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Profile Shortcut
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E201E))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            .bounceClick { onNavigateProfile() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (profilePhotoUrl != null) {
                            Image(
                                painter = rememberAsyncImagePainter(profilePhotoUrl),
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
    }
}

@Composable
fun HomeActionItem(
    title: String,
    description: String,
    icon: ImageVector,
    isAi: Boolean = false,
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
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = BrandLime,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = Color.White,
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
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
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
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        // Tag at top left
        Box(
            modifier = Modifier
                .padding(10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
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
                color = Color.White,
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


