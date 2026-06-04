package com.example.aifitnesscoach

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
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
        currentUser?.let {
            userName = it.displayName ?: "User"
            profilePhotoUrl = it.photoUrl?.toString()
        }

        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val initialBiometric = sharedPrefs.getBoolean("biometric_enabled", false)

        setContent {
            var biometricEnabled by remember { mutableStateOf(initialBiometric) }

            TrainiumTheme {
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

    private fun handleBiometricToggle(enabled: Boolean): Boolean {
        val biometricManager = BiometricManager.from(this)
        if (enabled && biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Biometric authentication is not available on this device.", Toast.LENGTH_LONG).show()
            return false
        }
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("biometric_enabled", enabled)
            .apply()

        val msg = if (enabled) "Biometric lock enabled" else "Biometric lock disabled"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun performLogout() {
        Firebase.auth.signOut()
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
            .background(Color.Black)
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
                .padding(top = 96.dp, bottom = 110.dp),
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
                        .background(Color.Black)
                        .padding(2.dp)
                ) {
                    if (profilePhotoUrl != null) {
                        Image(
                            painter = rememberAsyncImagePainter(profilePhotoUrl),
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
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Elite Member",
                color = TextSecondary,
                fontSize = 14.sp
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
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
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
                            Text("Biometric Lock", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Secure app access", color = TextSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = onBiometricToggleChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = BrandLime,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = Color(0xFF1F1F1F)
                            )
                        )
                    }
                }

                ProfileOptionItem(title = "Edit Profile", icon = Icons.Default.Edit)
                ProfileOptionItem(title = "Account Settings", icon = Icons.Default.ManageAccounts)
                ProfileOptionItem(title = "Notifications", icon = Icons.Default.NotificationsActive)
                ProfileOptionItem(title = "Help & Feedback", icon = Icons.Default.Help)
                ProfileOptionItem(title = "About Trainium AI", icon = Icons.Default.Info)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Logout Button
            TrainiumButton(
                text = "LOGOUT",
                onClick = onLogout,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Logout",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }

        // Top Header
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
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Profile",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                        .bounceClick { },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
    icon: ImageVector
) {
    TrainiumGlassCard(
        isActive = false,
        onClick = { }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                color = Color.White,
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
