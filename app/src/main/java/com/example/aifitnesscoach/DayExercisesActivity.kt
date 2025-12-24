package com.example.aifitnesscoach

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aifitnesscoach.databinding.ActivityDayExercisesBinding

class DayExercisesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDayExercisesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDayExercisesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dayTitle = intent.getStringExtra("DAY_TITLE") ?: "Day_1"
        val exercises = intent.getStringArrayListExtra("EXERCISES_LIST") ?: arrayListOf()

        // Format "Day_1" to "Day 01"
        val dayNumber = dayTitle.substringAfter('_').toIntOrNull() ?: 1
        val formattedTitle = String.format(java.util.Locale.US, "Day %02d", dayNumber)
        binding.dayTitleTextView.text = formattedTitle

        setupRecyclerView(exercises)

        binding.startWorkoutButton.setOnClickListener {
            startWorkoutSequence(exercises, 0)
        }
    }

    private fun setupRecyclerView(exercises: ArrayList<String>) {
        val adapter = ExerciseListAdapter(exercises) { exerciseName ->
            // Find the index of the clicked exercise
            val index = exercises.indexOf(exerciseName)
            if (index != -1) {
                startWorkoutSequence(exercises, index)
            }
        }
        binding.exercisesRecyclerView.adapter = adapter
        binding.exercisesRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun startWorkoutSequence(exercises: ArrayList<String>, startIndex: Int) {
        val intent = Intent(this, WorkoutActivity::class.java).apply {
            putStringArrayListExtra(Constants.EXTRA_WORKOUT_PLAN, exercises)
            putExtra(Constants.EXTRA_CURRENT_INDEX, startIndex)
            // Pass default durations or retrieve from settings/intent if available
            putExtra(Constants.EXTRA_EXERCISE_DURATION, 30000L) // 30 seconds default
            putExtra(Constants.EXTRA_REST_DURATION, 15000L)     // 15 seconds default
        }
        startActivity(intent)
    }
}