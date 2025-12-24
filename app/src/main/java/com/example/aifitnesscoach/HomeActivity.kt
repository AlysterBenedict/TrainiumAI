package com.example.aifitnesscoach

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aifitnesscoach.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.profileButton.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.customWorkoutCard.setOnClickListener {
            startActivity(Intent(this, CustomWorkoutActivity::class.java))
        }

        binding.personalisedWorkoutCard.setOnClickListener {
            startActivity(Intent(this, PhotoInstructionsActivity::class.java))
        }

        binding.continueProgressCard.setOnClickListener {
            continueProgress()
        }

        binding.aiAssistantCard.setOnClickListener {
            startActivity(Intent(this, ChatbotActivity::class.java))
        }
    }

    private fun continueProgress() {
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedPlanJson = sharedPrefs.getString("SAVED_WORKOUT_PLAN", null)

        if (savedPlanJson != null) {
            val intent = Intent(this, WorkoutPlanActivity::class.java).apply {
                putExtra("WORKOUT_PLAN", savedPlanJson)
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "No saved workout plan found.", Toast.LENGTH_SHORT).show()
        }
    }
}