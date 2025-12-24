package com.example.aifitnesscoach

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.LinearInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class StartActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        val quotes = resources.getStringArray(R.array.motivational_quotes)
        val randomQuote = quotes.random()
        
        val quoteTextView = findViewById<TextView>(R.id.quoteTextView)
        quoteTextView.text = "\"$randomQuote\""

        val progressBar = findViewById<ProgressBar>(R.id.loadingProgressBar)
        ObjectAnimator.ofInt(progressBar, "progress", 0, 100).apply {
            duration = 3000
            interpolator = LinearInterpolator()
            start()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                // Check if biometric lock is enabled
                val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                val isBiometricEnabled = sharedPrefs.getBoolean("biometric_enabled", false)

                if (isBiometricEnabled) {
                    showBiometricPrompt()
                } else {
                    // User is signed in, go to Home Dashboard
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                }
            } else {
                // No user is signed in, go to Login
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }, 3000) // 3 seconds delay
    }

    private fun showBiometricPrompt() {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val biometricPrompt = androidx.biometric.BiometricPrompt(this, executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    android.widget.Toast.makeText(applicationContext,
                        "Authentication error: $errString", android.widget.Toast.LENGTH_SHORT)
                        .show()
                    // Optionally finish or allow retry
                }

                override fun onAuthenticationSucceeded(
                    result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    android.widget.Toast.makeText(applicationContext,
                        "Authentication succeeded!", android.widget.Toast.LENGTH_SHORT)
                        .show()
                    startActivity(Intent(this@StartActivity, HomeActivity::class.java))
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    android.widget.Toast.makeText(applicationContext, "Authentication failed",
                        android.widget.Toast.LENGTH_SHORT)
                        .show()
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