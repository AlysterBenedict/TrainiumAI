package com.example.aifitnesscoach

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aifitnesscoach.databinding.ActivityCustomWorkoutBinding

class CustomWorkoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomWorkoutBinding
    private lateinit var exerciseAdapter: ExerciseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomWorkoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupStartButton()
    }

    private fun setupRecyclerView() {
        exerciseAdapter = ExerciseAdapter(Exercises.list)
        binding.exerciseRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CustomWorkoutActivity)
            adapter = exerciseAdapter
        }
    }

    private fun setupStartButton() {
        binding.startWorkoutButton.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startWorkout()
        }
    }

    private fun startWorkout() {
        val selected = exerciseAdapter.selectedExercises
        val exerciseDurationStr = binding.exerciseDurationInput.text.toString()
        val restDurationStr = binding.restDurationInput.text.toString()

        if (selected.isEmpty()) {
            Toast.makeText(this, "Please select at least one exercise.", Toast.LENGTH_SHORT).show()
            return
        }
        if (exerciseDurationStr.isBlank() || restDurationStr.isBlank()) {
            Toast.makeText(this, "Please enter both durations.", Toast.LENGTH_SHORT).show()
            return
        }

        val exerciseDuration = exerciseDurationStr.toLong() * 1000
        val restDuration = restDurationStr.toLong() * 1000

        val intent = Intent(this, WorkoutActivity::class.java).apply {
            putStringArrayListExtra(Constants.EXTRA_WORKOUT_PLAN, ArrayList(selected.map { it.name }))
            putExtra(Constants.EXTRA_EXERCISE_DURATION, exerciseDuration)
            putExtra(Constants.EXTRA_REST_DURATION, restDuration)
            putExtra(Constants.EXTRA_CURRENT_INDEX, 0)
        }
        startActivity(intent)
    }
}
