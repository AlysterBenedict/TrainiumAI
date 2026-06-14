package com.example.aifitnesscoach

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.ui.res.vectorResource
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Android
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class ProfileActivity_ui : AppCompatActivity() {

    private var userName = "Aly Sam"
    private var profilePhotoUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            userName = currentUser.displayName ?: "User"
            profilePhotoUrl = getHighResProfilePhotoUrl(currentUser.photoUrl?.toString())
        } else {
            val globalPrefs = getSharedPreferences("global_prefs", Context.MODE_PRIVATE)
            userName = globalPrefs.getString("local_user_name", "Guest") ?: "Guest"
            profilePhotoUrl = null
        }

        val sharedPrefs = getTrainiumPrefs("app_prefs")
        val initialBiometric = sharedPrefs.getBoolean("biometric_enabled", false)

        setContent {
            var biometricEnabled by remember { mutableStateOf(initialBiometric) }
            var currentScreen by remember { mutableStateOf("profile") }

            TrainiumTheme {
                BackHandler(enabled = currentScreen != "profile") {
                    currentScreen = "profile"
                }

                if (currentScreen == "settings") {
                    SettingsScreen(onBack = { currentScreen = "profile" })
                } else if (currentScreen == "edit_profile") {
                    EditProfileScreen(onBack = { currentScreen = "profile" })
                } else if (currentScreen == "help_feedback") {
                    HelpFeedbackScreen(onBack = { currentScreen = "profile" })
                } else if (currentScreen == "about") {
                    AboutScreen(onBack = { currentScreen = "profile" })
                } else if (currentScreen == "theme") {
                    ThemeScreen(onBack = { currentScreen = "profile" })
                } else {
                    ProfileScreen(
                        userName = userName,
                        profilePhotoUrl = profilePhotoUrl,
                        biometricEnabled = biometricEnabled,
                        onBiometricToggleChanged = { enabled ->
                            val success = handleBiometricToggle(enabled)
                            if (success) {
                                biometricEnabled = enabled
                            }
                        },
                        onNavigateToSettings = { currentScreen = "settings" },
                        onNavigateToEditProfile = { currentScreen = "edit_profile" },
                        onNavigateToHelpFeedback = { currentScreen = "help_feedback" },
                        onNavigateToAbout = { currentScreen = "about" },
                        onNavigateToTheme = { currentScreen = "theme" },
                        onLogout = { performLogout() },
                        onBack = { finish() },
                        onNavigateHome = {
                            val intent = Intent(this, HomeActivity_ui::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            startActivity(intent)
                            finish()
                        },
                        onNavigateCustomWorkout = {
                            startActivity(Intent(this, CustomWorkoutActivity_ui::class.java))
                            finish()
                        },
                        onNavigateChat = {
                            startActivity(Intent(this, ChatbotActivity_ui::class.java))
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun handleBiometricToggle(enabled: Boolean): Boolean {
        val biometricManager = BiometricManager.from(this)
        if (enabled && biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Biometric authentication is not available on this device.", Toast.LENGTH_LONG).show()
            return false
        }
        getTrainiumPrefs("app_prefs")
            .edit()
            .putBoolean("biometric_enabled", enabled)
            .apply()

        val msg = if (enabled) "Biometric lock enabled" else "Biometric lock disabled"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun performLogout() {
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            com.example.aifitnesscoach.network.FirebaseSyncHelper.clearLocalCache(this, currentUser.uid)
            Firebase.auth.signOut()
        } else {
            val globalPrefs = getSharedPreferences("global_prefs", Context.MODE_PRIVATE)
            globalPrefs.edit().putBoolean("is_local_user", false).apply()
        }
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, LoginActivity_ui::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

@Composable
fun ProfileScreen(
    userName: String,
    profilePhotoUrl: String?,
    biometricEnabled: Boolean,
    onBiometricToggleChanged: (Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToHelpFeedback: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateCustomWorkout: () -> Unit,
    onNavigateChat: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Ambient Glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
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
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 96.dp, bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Header Area
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(BrandLime, Color(0xFF353535))
                        )
                    )
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(BackgroundBlack)
                        .padding(2.dp)
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
                            contentDescription = "Profile Pic",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color(0xFF1F1F1F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile Pic",
                                tint = BrandLime,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = userName,
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )


            Spacer(modifier = Modifier.height(32.dp))

            // Settings Options List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Biometric Lock Item
                TrainiumGlassCard(isActive = false) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CardOverlayColor.copy(alpha = 0.05f))
                                .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric Lock",
                                tint = BrandLime,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Biometric Lock", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Secure app access", color = TextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = onBiometricToggleChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BackgroundBlack,
                                checkedTrackColor = BrandLime,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = Color(0xFF1F1F1F)
                            )
                        )
                    }
                }

                ProfileOptionItem(
                    title = "Edit Profile",
                    icon = Icons.Default.Edit,
                    onClick = onNavigateToEditProfile
                )
                ProfileOptionItem(
                    title = "Theme",
                    icon = Icons.Default.Palette,
                    onClick = onNavigateToTheme
                )
                ProfileOptionItem(
                    title = "App and Workout Settings",
                    icon = Icons.Default.ManageAccounts,
                    onClick = onNavigateToSettings
                )
                ProfileOptionItem(
                    title = "Help & Feedback",
                    icon = Icons.Default.Help,
                    onClick = onNavigateToHelpFeedback
                )
                ProfileOptionItem(
                    title = "About Trainium AI",
                    icon = Icons.Default.Info,
                    onClick = onNavigateToAbout
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Logout Button
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
                    .bounceClick { onLogout() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Logout",
                        tint = AccentRed.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Logout",
                        color = AccentRed.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Top Header
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
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "Profile",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Floating Glass Bottom Navigation Dock
        val context = LocalContext.current
        TrainiumBottomDock(
            activeTab = "profile",
            onTabSelected = { tab ->
                navigateToTab(context, tab)
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun ProfileOptionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit = {}
) {
    TrainiumGlassCard(
        isActive = false,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardOverlayColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Go",
                tint = TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getTrainiumPrefs("app_prefs") }

    var exerciseTimer by remember { mutableStateOf(prefs.getInt("pref_exercise_duration_seconds", 30).toString()) }
    var plankTimer by remember { mutableStateOf(prefs.getInt("pref_plank_duration_seconds", 80).toString()) }
    var restTimer by remember { mutableStateOf(prefs.getInt("pref_rest_duration_seconds", 15).toString()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 96.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Edit Workout Timers",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Adjust the default durations below. Plank timer has a separate preference, while other timed exercises share the common timer.",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = exerciseTimer,
                onValueChange = { exerciseTimer = it },
                label = { Text("Common Exercise Timer (s)", color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = BrandLime,
                    unfocusedBorderColor = CardOverlayColor.copy(alpha = 0.12f),
                    focusedContainerColor = Color(0xFF111111),
                    unfocusedContainerColor = Color(0xFF111111)
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = plankTimer,
                onValueChange = { plankTimer = it },
                label = { Text("Plank Hold Timer (s)", color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = BrandLime,
                    unfocusedBorderColor = CardOverlayColor.copy(alpha = 0.12f),
                    focusedContainerColor = Color(0xFF111111),
                    unfocusedContainerColor = Color(0xFF111111)
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = restTimer,
                onValueChange = { restTimer = it },
                label = { Text("Rest Timer (s)", color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = BrandLime,
                    unfocusedBorderColor = CardOverlayColor.copy(alpha = 0.12f),
                    focusedContainerColor = Color(0xFF111111),
                    unfocusedContainerColor = Color(0xFF111111)
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            TrainiumButton(
                text = "SAVE SETTINGS",
                onClick = {
                    val exSecs = exerciseTimer.toIntOrNull()
                    val pkSecs = plankTimer.toIntOrNull()
                    val restSecs = restTimer.toIntOrNull()
                    if (exSecs == null || pkSecs == null || restSecs == null || exSecs <= 0 || pkSecs <= 0 || restSecs <= 0) {
                        Toast.makeText(context, "Please enter valid positive numbers.", Toast.LENGTH_SHORT).show()
                    } else {
                        prefs.edit()
                            .putInt("pref_exercise_duration_seconds", exSecs)
                            .putInt("pref_plank_duration_seconds", pkSecs)
                            .putInt("pref_rest_duration_seconds", restSecs)
                            .apply()
                        Toast.makeText(context, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                }
            )

            var modelDeletedState by remember { mutableStateOf(false) }
            val modelFile = remember(modelDeletedState) { java.io.File(context.filesDir, "gemma-4-E2B-it.litertlm") }
            val isModelDownloaded = remember(modelDeletedState) { modelFile.exists() && modelFile.length() > 2_000_000_000L }
            var showDeleteModelDialog by remember { mutableStateOf(false) }

            if (isModelDownloaded) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(280.dp)
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
                            .bounceClick { showDeleteModelDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Model",
                                tint = AccentRed.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Delete Offline AI Model (2.41 GB)",
                                color = AccentRed.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            if (showDeleteModelDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteModelDialog = false },
                    containerColor = Color(0xFF111111),
                    title = {
                        Text(
                            text = "Delete AI Model?",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "Are you sure you want to delete the offline AI model? This will free up 2.41 GB of storage and clear your local chat history, but you will not be able to use the AI Coach offline until you download it again.",
                            color = CardOverlayColor.copy(alpha = 0.8f)
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteModelDialog = false
                                try {
                                    if (modelFile.exists()) {
                                        modelFile.delete()
                                    }
                                    val tempFile = java.io.File(context.filesDir, "gemma-4-E2B-it.litertlm.tmp")
                                    if (tempFile.exists()) {
                                        tempFile.delete()
                                    }
                                    // Clear local chat history
                                    context.getTrainiumPrefs("saved_chats_prefs").edit().clear().apply()
                                    com.example.aifitnesscoach.ChatbotActivity_ui.chatMessages.clear()
                                    com.example.aifitnesscoach.ChatbotActivity_ui.chatMessages.add(
                                        com.example.aifitnesscoach.network.ChatMessage(
                                            "assistant",
                                            "Hello! I'm Trainium AI, your personal fitness intelligence. Multilingual assistant. I have your workout plan and metrics. How can I help you today?"
                                        )
                                    )
                                    com.example.aifitnesscoach.ChatbotActivity_ui.activeSessionId = "chat_" + System.currentTimeMillis()

                                    Toast.makeText(context, "Offline AI model and chat history deleted successfully.", Toast.LENGTH_SHORT).show()
                                    modelDeletedState = true
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error deleting model: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteModelDialog = false }
                        ) {
                            Text("Cancel", color = TextPrimary)
                        }
                    }
                )
            }
        }

        // Top Header
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
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                    text = "App and Workout Settings",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getTrainiumPrefs("app_prefs") }
    val gson = remember { com.google.gson.Gson() }

    // Load existing UserData
    val userDataJson = prefs.getString("SAVED_USER_METRICS", null)
    val userData = remember {
        if (userDataJson != null) {
            try {
                gson.fromJson(userDataJson, com.example.aifitnesscoach.network.UserData::class.java)
            } catch (e: Exception) {
                com.example.aifitnesscoach.network.UserData()
            }
        } else {
            com.example.aifitnesscoach.network.UserData()
        }
    }

    var ageStr by remember { mutableStateOf(if (userData.age > 0) userData.age.toString() else "") }
    var heightStr by remember { mutableStateOf(if (userData.heightCm > 0f) userData.heightCm.toString() else "") }
    var weightStr by remember { mutableStateOf(if (userData.weightKg > 0f) userData.weightKg.toString() else "") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Edit Profile Metrics",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Update your basic body metrics. These will be used to calculate your BMI and personalize your AI coaching recommendations.",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = ageStr,
                onValueChange = { ageStr = it },
                label = { Text("Age (years)", color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = BrandLime,
                    unfocusedBorderColor = CardOverlayColor.copy(alpha = 0.12f),
                    focusedContainerColor = Color(0xFF111111),
                    unfocusedContainerColor = Color(0xFF111111)
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = heightStr,
                onValueChange = { heightStr = it },
                label = { Text("Height (cm)", color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = BrandLime,
                    unfocusedBorderColor = CardOverlayColor.copy(alpha = 0.12f),
                    focusedContainerColor = Color(0xFF111111),
                    unfocusedContainerColor = Color(0xFF111111)
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = weightStr,
                onValueChange = { weightStr = it },
                label = { Text("Weight (kg)", color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = BrandLime,
                    unfocusedBorderColor = CardOverlayColor.copy(alpha = 0.12f),
                    focusedContainerColor = Color(0xFF111111),
                    unfocusedContainerColor = Color(0xFF111111)
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(40.dp))

            TrainiumButton(
                text = "SAVE PROFILE",
                onClick = {
                    val age = ageStr.toIntOrNull()
                    val height = heightStr.toFloatOrNull()
                    val weight = weightStr.toFloatOrNull()

                    if (age == null || height == null || weight == null || age <= 0 || height <= 0f || weight <= 0f) {
                        Toast.makeText(context, "Please enter valid metrics.", Toast.LENGTH_SHORT).show()
                    } else {
                        val heightM = height / 100f
                        val bmi = weight / (heightM * heightM)

                        val updatedUserData = userData.copy(
                            age = age,
                            heightCm = height,
                            weightKg = weight,
                            bmi = bmi
                        )

                        prefs.edit()
                            .putString("SAVED_USER_METRICS", gson.toJson(updatedUserData))
                            .apply()

                        // Sync with database
                        com.example.aifitnesscoach.network.FirebaseSyncHelper.syncProfileToFirebase(context, updatedUserData)

                        // Log weight change if updated
                        if (weight != userData.weightKg) {
                            com.example.aifitnesscoach.network.FirebaseSyncHelper.addWeight(context, weight)
                        }

                        Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                }
            )
        }

        // Top Header
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
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                    text = "Edit Profile",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

data class HelpGuideData(
    val title: String,
    val icon: ImageVector,
    val content: String,
    val useOriginalTint: Boolean = false
)

@Composable
fun HelpGuideItem(
    title: String,
    icon: ImageVector,
    content: String,
    expanded: Boolean,
    useOriginalTint: Boolean = false,
    onToggle: () -> Unit
) {
    TrainiumGlassCard(
        isActive = expanded,
        onClick = onToggle
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (expanded) BrandLime.copy(alpha = 0.2f) else CardOverlayColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (useOriginalTint) Color.Unspecified else (if (expanded) BrandLime else TextPrimary),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextSecondary
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = CardOverlayColor.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = content,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpFeedbackScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var feedbackText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Suggestion") }
    val categories = listOf("Bug", "Suggestion", "Question")
    val googleIcon = ImageVector.vectorResource(id = R.drawable.google_logo)

    val helpGuides = remember(googleIcon) {
        listOf(
            HelpGuideData(
                title = "How to Use Trainium AI Coach",
                icon = googleIcon,
                content = "1. Tap the 'Coach' tab in the bottom navigation dock.\n2. Type your question or ask about workout plans, diet tips, or muscle targets in the input field.\n3. Ask questions in Kannada, Hindi, Tamil, Telugu, Malayalam, or English to receive expert answers instantly and completely offline.",
                useOriginalTint = true
            ),
            HelpGuideData(
                title = "How to Use the 30-Day Program",
                icon = Icons.Default.FitnessCenter,
                content = "1. From the Home screen, tap 'Generate AI Workout Plan'.\n2. Complete your details to let the system generate your plan based on estimated body metrics.\n3. Tap 'Continue Progress' on the Home screen to launch the day's routine, follow the active timer, video guides, and listen to the voice coaching."
            ),
            HelpGuideData(
                title = "How to Use Custom Workout Builder",
                icon = Icons.Default.Edit,
                content = "1. Tap the 'Custom Workout' card on the Home screen.\n2. Click the '+' button or search for exercises to add them to your custom list.\n3. Configure reps, sets, and durations for each exercise, then save your custom routine to play it anytime."
            ),
            HelpGuideData(
                title = "How to Use Analytics & Goal Tracking",
                icon = Icons.Default.Info,
                content = "1. Tap the 'Reports' tab in the bottom navigation dock to view charts of completed workouts, calorie burn, and active minutes.\n2. Swipe to the 'Weight' tab to log your current weight daily and set target goals to monitor weight trends over time."
            ),
            HelpGuideData(
                title = "How to Configure Settings & Security",
                icon = Icons.Default.Fingerprint,
                content = "1. Navigate to your Profile tab.\n2. Toggle 'Biometric Lock' to secure your app access behind your fingerprint or device PIN.\n3. Tap 'App and Workout Settings' to adjust the common exercise timer, rest timer, or plank hold duration to your preference."
            )
        )
    }

    var expandedIndex by remember { mutableStateOf(-1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Ambient Glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
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
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 96.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "How to Use Trainium AI",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            // Features cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                helpGuides.forEachIndexed { index, guide ->
                    val isExpanded = expandedIndex == index
                    HelpGuideItem(
                        title = guide.title,
                        icon = guide.icon,
                        content = guide.content,
                        expanded = isExpanded,
                        useOriginalTint = guide.useOriginalTint,
                        onToggle = {
                            expandedIndex = if (isExpanded) -1 else index
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Feedback Card
            TrainiumGlassCard(isActive = false) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CardOverlayColor.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RateReview,
                                contentDescription = "Feedback",
                                tint = BrandLime,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Send Us Feedback",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Have a bug report or feature request?",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Divider(color = CardOverlayColor.copy(alpha = 0.08f))

                    Text(
                        text = "Category",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { category ->
                            val isSelected = selectedCategory == category
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (isSelected) BrandLime else CardOverlayColor.copy(alpha = 0.05f))
                                    .border(1.dp, if (isSelected) BrandLime else CardOverlayColor.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                                    .clickable { selectedCategory = category },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = category,
                                    color = if (isSelected) BackgroundBlack else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        placeholder = { Text("Tell us what we can improve...", color = TextSecondary, fontSize = 14.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = BrandLime,
                            unfocusedBorderColor = CardOverlayColor.copy(alpha = 0.12f),
                            focusedContainerColor = Color(0xFF111111),
                            unfocusedContainerColor = Color(0xFF111111)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        maxLines = 5
                    )

                    TrainiumButton(
                        text = "SUBMIT FEEDBACK",
                        onClick = {
                            if (feedbackText.trim().isEmpty()) {
                                Toast.makeText(context, "Please write a description first.", Toast.LENGTH_SHORT).show()
                            } else {
                                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = android.net.Uri.parse("mailto:")
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf("alynemoclaw@gmail.com"))
                                    putExtra(Intent.EXTRA_SUBJECT, "Trainium AI Feedback - $selectedCategory")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        """
                                        Hello Trainium Support Team,

                                        You have received new feedback from a user:

                                        --------------------------------------------------
                                        Category: $selectedCategory
                                        --------------------------------------------------

                                        Feedback Details:
                                        $feedbackText

                                        --------------------------------------------------
                                        Sent via Trainium AI App Help & Feedback Module
                                        """.trimIndent()
                                    )
                                }
                                try {
                                    context.startActivity(Intent.createChooser(emailIntent, "Send Email via..."))
                                    feedbackText = ""
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "No email client found.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }

        // Top Header
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
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                    text = "Help & Feedback",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun getHighResProfilePhotoUrl(photoUrl: String?): String? {
    if (photoUrl == null) return null
    return if (photoUrl.contains("googleusercontent.com")) {
        photoUrl.replace(Regex("([/=])s\\d+(-c)?"), "$1s500-c")
    } else {
        photoUrl
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val googleIcon = ImageVector.vectorResource(id = R.drawable.google_logo)

    val features = remember(googleIcon) {
        listOf(
            HelpGuideData(
                title = "Fully On-Device Privacy",
                icon = Icons.Default.Lock,
                content = "Your privacy is our core priority. All biometric photo scans, body metric estimations, and AI coach conversations are processed 100% locally on your device's neural engines. None of your photos, camera streams, or personal metrics are ever uploaded to the cloud or shared with third parties. Work out with complete peace of mind, fully offline."
            ),
            HelpGuideData(
                title = "Offline AI Coaching (Gemma 4 E2B)",
                icon = googleIcon,
                content = "Powered by Google DeepMind's Gemma 4 E2B, Trainium AI provides offline, on-device expert fitness guidance. Gemma 4 E2B is a dense, natively multimodal model designed for high-efficiency mobile deployments. It can process text, image, and audio inputs with near-zero latency, running locally with a compact ~1GB memory footprint. You can consult it about customized workout routines, diet plans, target muscle groups, or ask questions in languages like Kannada, Hindi, Tamil, Telugu, Malayalam, and English.",
                useOriginalTint = true
            ),
            HelpGuideData(
                title = "Personalized 30-Day Program",
                icon = Icons.Default.FitnessCenter,
                content = "Our core engine estimates 15+ body metrics (including height, weight, body fat %, BMI, waist-to-hip ratio, muscle mass, chest/waist/hip measurements, and more) during onboarding to generate a fully tailored 30-Day Workout Program. The program tracks daily active minutes, calorie burn, and progress through exercises with video guides and active timers."
            ),
            HelpGuideData(
                title = "Custom Workout Builder",
                icon = Icons.Default.Edit,
                content = "For users who prefer to build their own routines, the Custom Workout feature offers complete flexibility. You can add your own set of exercises, customize repetitions, and configure durations to match your daily schedule."
            ),
            HelpGuideData(
                title = "Analytics & Goal Tracking",
                icon = Icons.Default.Info,
                content = "Track your fitness journey using comprehensive data visualization. View lifetime completed workouts, calorie burn, active minutes, and weekly consistency streaks. Log weight daily and set target weights in the reports tab to monitor your weight trends over time."
            ),
            HelpGuideData(
                title = "Preferences & Security (Biometric Lock)",
                icon = Icons.Default.Fingerprint,
                content = "Customize exercise, plank hold, and rest timers to suit your pacing. Restrict app launch behind fingerprint or device credentials by enabling Biometric Lock in your profile for enhanced data security."
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Ambient Glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
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
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 96.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Trainium AI Features",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            // Features list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var expandedIndex by remember { mutableStateOf(-1) }
                features.forEachIndexed { index, feature ->
                    val isExpanded = expandedIndex == index
                    HelpGuideItem(
                        title = feature.title,
                        icon = feature.icon,
                        content = feature.content,
                        expanded = isExpanded,
                        useOriginalTint = feature.useOriginalTint,
                        onToggle = {
                            expandedIndex = if (isExpanded) -1 else index
                        }
                    )
                }
            }
        }

        // Top Header
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
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                    text = "About Trainium AI",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ThemeScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getTrainiumPrefs("app_prefs") }

    var selectedPalette by remember {
        mutableStateOf(prefs.getString("theme_color_palette", "lime") ?: "lime")
    }
    var selectedMode by remember {
        mutableStateOf(prefs.getString("theme_mode", "dark") ?: "dark")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Ambient Glow using current theme color
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
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
                .padding(horizontal = 24.dp)
                .padding(top = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Color Palette",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Horizontal Scroller for color palettes
            val palettes = listOf(
                PaletteOption("lime", "Lime", Color(0xFFCCFF00)),
                PaletteOption("red", "Red", Color(0xFFFF3B30)),
                PaletteOption("blue", "Blue", Color(0xFF007AFF)),
                PaletteOption("purple", "Purple", Color(0xFFAF52DE)),
                PaletteOption("orange", "Orange", Color(0xFFFF9500)),
                PaletteOption("pink", "Pink", Color(0xFFFF2D55)),
                PaletteOption("yellow", "Yellow", Color(0xFFFFCC00)),
                PaletteOption("material", "Material UI", null)
            )

            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(palettes.size) { index ->
                    val option = palettes[index]
                    val isSelected = selectedPalette == option.id

                    TrainiumGlassCard(
                        isActive = isSelected,
                        onClick = {
                            selectedPalette = option.id
                            prefs.edit().putString("theme_color_palette", option.id).apply()
                        },
                        modifier = Modifier.width(100.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (option.color != null) {
                                            Brush.sweepGradient(listOf(option.color, option.color.copy(alpha = 0.7f)))
                                        } else {
                                            Brush.sweepGradient(listOf(Color(0xFF007AFF), Color(0xFFFF2D55), Color(0xFFCCFF00), Color(0xFF007AFF)))
                                        }
                                    )
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) TextPrimary else CardOverlayColor.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = option.name,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Appearance Mode",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val modes = listOf(
                    ModeOption("light", "Light", Icons.Default.LightMode),
                    ModeOption("dark", "Dark", Icons.Default.DarkMode),
                    ModeOption("system", "System", Icons.Default.Android)
                )

                modes.forEach { mode ->
                    val isSelected = selectedMode == mode.id
                    TrainiumGlassCard(
                        isActive = isSelected,
                        onClick = {
                            selectedMode = mode.id
                            prefs.edit().putString("theme_mode", mode.id).apply()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = mode.icon,
                                contentDescription = mode.name,
                                tint = if (isSelected) BrandLime else TextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = mode.name,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Top Header
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
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                    text = "App Theme",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

data class PaletteOption(val id: String, val name: String, val color: Color?)
data class ModeOption(val id: String, val name: String, val icon: ImageVector)
