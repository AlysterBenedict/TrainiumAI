package com.example.aifitnesscoach

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.aifitnesscoach.databinding.FragmentPlanBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class PlanFragment : Fragment() {

    private var _binding: FragmentPlanBinding? = null
    private val binding get() = _binding!!
    private lateinit var exerciseAdapter: ExerciseAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ... (user info and RecyclerView setup code remains the same)
        val user = Firebase.auth.currentUser
        user?.let {
            binding.welcomeTextView.text = "Welcome, ${it.displayName}!"
            Glide.with(this)
                .load(it.photoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_me)
                .into(binding.userProfileImageView)
        }

        exerciseAdapter = ExerciseAdapter(Exercises.list)
        binding.exerciseRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = exerciseAdapter
        }


        // UPDATED: The click listener now provides a 'view'
        binding.startWorkoutButton.setOnBounceClickListener { buttonView ->
            // Trigger haptic feedback
            buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startWorkout()
        }
    }

    private fun startWorkout() {
        val selected = exerciseAdapter.selectedExercises
        val exerciseDurationStr = binding.exerciseDurationInput.text.toString()
        val restDurationStr = binding.restDurationInput.text.toString()

        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), "Please select at least one exercise.", Toast.LENGTH_SHORT).show()
            return
        }
        if (exerciseDurationStr.isBlank() || restDurationStr.isBlank()) {
            Toast.makeText(requireContext(), "Please enter both durations.", Toast.LENGTH_SHORT).show()
            return
        }

        val exerciseDuration = exerciseDurationStr.toLong() * 1000
        val restDuration = restDurationStr.toLong() * 1000

        val intent = Intent(requireActivity(), WorkoutActivity::class.java).apply {
            putStringArrayListExtra(Constants.EXTRA_WORKOUT_PLAN, ArrayList(selected.map { it.name }))
            putExtra(Constants.EXTRA_EXERCISE_DURATION, exerciseDuration)
            putExtra(Constants.EXTRA_REST_DURATION, restDuration)
            putExtra(Constants.EXTRA_CURRENT_INDEX, 0)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}