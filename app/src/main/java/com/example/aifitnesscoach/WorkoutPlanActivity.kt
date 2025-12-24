package com.example.aifitnesscoach

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aifitnesscoach.databinding.ActivityWorkoutPlanBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class WorkoutPlanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkoutPlanBinding
    private lateinit var workoutPlan: Map<String, List<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutPlanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val workoutPlanJson = intent.getStringExtra("WORKOUT_PLAN")
        workoutPlan = if (workoutPlanJson != null) {
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            Gson().fromJson(workoutPlanJson, type)
        } else {
            emptyMap()
        }

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        // This correctly sorts numerically by extracting the number from the "Day_X" string
        val days = workoutPlan.keys.sortedBy { it.substringAfter('_').toInt() }// Sort days numerically
        val adapter = DayAdapter(days) { day ->
            // When a day is clicked, start the DayExercisesActivity
            val exercises = workoutPlan[day]
            val intent = Intent(this, DayExercisesActivity::class.java).apply {
                putExtra("DAY_TITLE", day)
                // Convert list to ArrayList to pass via Intent
                putStringArrayListExtra("EXERCISES_LIST", ArrayList(exercises))
            }
            startActivity(intent)
        }
        binding.daysRecyclerView.adapter = adapter
        binding.daysRecyclerView.layoutManager = LinearLayoutManager(this)
    }
}