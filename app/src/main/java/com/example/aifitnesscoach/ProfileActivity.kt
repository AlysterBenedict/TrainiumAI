package com.example.aifitnesscoach

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import com.bumptech.glide.Glide
import com.example.aifitnesscoach.databinding.ActivityProfileBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isBiometricEnabled = sharedPrefs.getBoolean("biometric_enabled", false)
        binding.biometricSwitch.isChecked = isBiometricEnabled

        val user = Firebase.auth.currentUser
        user?.let {
            binding.nameTextView.text = it.displayName
            Glide.with(this).load(it.photoUrl).circleCrop().into(binding.profileImageView)
        }
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
            val biometricManager = BiometricManager.from(this)
            if (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
                Toast.makeText(this, "Biometric authentication is not available on this device.", Toast.LENGTH_LONG).show()
                binding.biometricSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }

            getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("biometric_enabled", isChecked)
                .apply()

            if (isChecked) {
                Toast.makeText(this, "Biometric lock enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Biometric lock disabled", Toast.LENGTH_SHORT).show()
            }
        }

        binding.logoutButton.setOnBounceClickListener { buttonView ->
            buttonView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            Firebase.auth.signOut()
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
