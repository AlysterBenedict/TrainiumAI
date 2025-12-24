package com.example.aifitnesscoach

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import androidx.appcompat.app.AppCompatActivity
import com.example.aifitnesscoach.databinding.ActivityRestBinding

class RestActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityRestBinding
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityRestBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // --- Retrieve data using consistent keys ---
        val restDuration = intent.getLongExtra(Constants.EXTRA_REST_DURATION, 15000)
        val nextExerciseName = intent.getStringExtra(Constants.EXTRA_NEXT_EXERCISE_NAME) ?: "Finish"
        val workoutPlan = intent.getStringArrayListExtra(Constants.EXTRA_WORKOUT_PLAN)
        val nextExerciseIndex = intent.getIntExtra(Constants.EXTRA_CURRENT_INDEX, 0)
        val exerciseDuration = intent.getLongExtra(Constants.EXTRA_EXERCISE_DURATION, 30000)

        viewBinding.nextExerciseText.text = nextExerciseName

        viewBinding.skipRestButton.setOnClickListener {
            goToNextExercise(workoutPlan, nextExerciseIndex, exerciseDuration, restDuration)
        }

        countDownTimer = object : CountDownTimer(restDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                viewBinding.restTimerText.text = (millisUntilFinished / 1000 + 1).toString()
            }

            override fun onFinish() {
                goToNextExercise(workoutPlan, nextExerciseIndex, exerciseDuration, restDuration)
            }
        }.start()
    }

    private fun goToNextExercise(
        plan: ArrayList<String>?,
        index: Int,
        exDuration: Long,
        restDuration: Long
    ) {
        countDownTimer?.cancel()
        val intent = Intent(this, WorkoutActivity::class.java).apply {
            // --- Pass data using consistent keys ---
            putStringArrayListExtra(Constants.EXTRA_WORKOUT_PLAN, plan)
            putExtra(Constants.EXTRA_CURRENT_INDEX, index)
            putExtra(Constants.EXTRA_EXERCISE_DURATION, exDuration)
            putExtra(Constants.EXTRA_REST_DURATION, restDuration)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}