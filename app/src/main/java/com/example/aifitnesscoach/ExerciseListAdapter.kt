package com.example.aifitnesscoach

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class ExerciseListAdapter(
    private val exercises: List<String>,
    private val onExerciseClicked: (String) -> Unit
) : RecyclerView.Adapter<ExerciseListAdapter.ExerciseViewHolder>() {

    class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val exerciseNameTextView: TextView = view.findViewById(R.id.exerciseNameTextView)
        val exerciseIndexTextView: TextView = view.findViewById(R.id.exerciseIndexTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise_name, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exercises[position]
        
        holder.exerciseNameTextView.text = exercise
        holder.exerciseIndexTextView.text = String.format(Locale.US, "%02d", position + 1)

        // Make the item clickable only if it's not a "Rest Day"
        if (exercise != "Rest Day") {
            holder.itemView.setOnClickListener {
                onExerciseClicked(exercise)
            }
        } else {
            // Otherwise, remove the click listener
            holder.itemView.setOnClickListener(null)
        }
    }

    override fun getItemCount() = exercises.size
}