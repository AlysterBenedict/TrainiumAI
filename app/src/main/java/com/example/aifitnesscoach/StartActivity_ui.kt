package com.example.aifitnesscoach

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.firebase.auth.FirebaseAuth

class StartActivity_ui : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val quotes = resources.getStringArray(R.array.motivational_quotes)
        val randomQuote = quotes.random()

        setContent {
            TrainiumTheme {
                SplashScreen(randomQuote)
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = FirebaseAuth.getInstance().currentUser
            val globalPrefs = getSharedPreferences("global_prefs", MODE_PRIVATE)
            val isLocalUser = globalPrefs.getBoolean("is_local_user", false)

            if (currentUser != null || isLocalUser) {
                val sharedPrefs = getTrainiumPrefs("app_prefs")
                val isBiometricEnabled = sharedPrefs.getBoolean("biometric_enabled", false)

                if (isBiometricEnabled) {
                    showBiometricPrompt()
                } else {
                    startActivity(Intent(this, HomeActivity_ui::class.java))
                    finish()
                }
            } else {
                startActivity(Intent(this, LoginActivity_ui::class.java))
                finish()
            }
        }, 3000)
    }

    private fun showBiometricPrompt() {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val biometricPrompt = androidx.biometric.BiometricPrompt(this, executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    android.widget.Toast.makeText(
                        applicationContext,
                        "Authentication error: $errString",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    if (errorCode == androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        finishAffinity()
                    }
                }

                override fun onAuthenticationSucceeded(
                    result: androidx.biometric.BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    android.widget.Toast.makeText(
                        applicationContext,
                        "Authentication succeeded!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    startActivity(Intent(this@StartActivity_ui, HomeActivity_ui::class.java))
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    android.widget.Toast.makeText(
                        applicationContext,
                        "Authentication failed",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            })

        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for AI Fitness Coach")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

@Composable
fun SplashScreen(quote: String) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3000)
        )
    }

    val context = LocalContext.current
    val isGemmaDownloaded = remember {
        java.io.File(context.filesDir, "gemma-4-E2B-it.litertlm").exists()
    }

    val stepLabels = remember(isGemmaDownloaded) {
        if (isGemmaDownloaded) {
            listOf(
                Pair("Loading body metrics AI model...", "Body metrics AI model loaded"),
                Pair("Loading workout generator AI model...", "Workout generator AI model loaded"),
                Pair("Loading pose estimator...", "Pose estimator loaded"),
                Pair("Loading Trainium AI...", "Trainium AI loaded")
            )
        } else {
            listOf(
                Pair("Loading body metrics AI model...", "Body metrics AI model loaded"),
                Pair("Loading workout generator AI model...", "Workout generator AI model loaded"),
                Pair("Loading pose estimator...", "Pose estimator loaded")
            )
        }
    }

    val numSteps = stepLabels.size
    val stepDuration = 0.85f / numSteps

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack),
        contentAlignment = Alignment.Center
    ) {
        // Ambient lime glow at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandLime.copy(alpha = 0.12f), Color.Transparent),
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardOverlayColor.copy(alpha = 0.04f))
                    .border(1.dp, CardOverlayColor.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_playstore),
                    contentDescription = "Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "TRAINIUM",
                color = TextPrimary,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "AI FITNESS COACH",
                color = TextSecondary.copy(alpha = 0.65f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .width(220.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = BrandLime,
                trackColor = CardOverlayColor.copy(alpha = 0.1f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Beautiful interactive sequential loading task flow list
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceLow.copy(alpha = 0.4f))
                    .border(1.dp, CardOverlayColor.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                stepLabels.forEachIndexed { index, pair ->
                    val isVisible = progress.value >= index * stepDuration
                    val isCompleted = progress.value >= (index + 1) * stepDuration
                    val isActive = isVisible && !isCompleted

                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(animationSpec = tween(400)) + expandVertically(animationSpec = tween(400)),
                        exit = fadeOut(animationSpec = tween(400)) + shrinkVertically(animationSpec = tween(400))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Completed",
                                    tint = BrandLime,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else if (isActive) {
                                CircularProgressIndicator(
                                    color = BrandLime,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Box(modifier = Modifier.size(16.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = if (isCompleted) pair.second else pair.first,
                                color = if (isActive) TextPrimary else TextSecondary.copy(alpha = 0.7f),
                                fontSize = 12.5.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "\"$quote\"",
            color = TextPrimary,
            fontSize = 16.sp,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .padding(horizontal = 32.dp)
        )
    }
}
