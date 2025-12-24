// File: app/src/main/java/com/example/aifitnesscoach/WorkoutCompleteActivity.kt
package com.example.aifitnesscoach

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.aifitnesscoach.databinding.ActivityWorkoutCompleteBinding

class WorkoutCompleteActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityWorkoutCompleteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityWorkoutCompleteBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.endButton.setOnClickListener {
            // Change the destination from StartActivity to HomeActivity
            val intent = Intent(this, HomeActivity::class.java)
            // These flags ensure a fresh start at the home screen
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }
}