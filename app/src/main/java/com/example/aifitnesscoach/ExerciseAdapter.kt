package com.example.aifitnesscoach

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ExerciseAdapter(
    private val exerciseList: List<ExerciseConfig>
) : RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder>() {

    val selectedExercises = mutableSetOf<ExerciseConfig>()

    class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val exerciseName: TextView = view.findViewById(R.id.exerciseName)
        // The ViewHolder now holds an ImageView
        val checkBoxImage: ImageView = view.findViewById(R.id.exerciseCheckBoxImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exerciseList[position]
        holder.exerciseName.text = exercise.name

        // Manually set the correct drawable based on the selection state
        if (selectedExercises.contains(exercise)) {
            holder.checkBoxImage.setImageResource(R.drawable.custom_checkbox_checked)
        } else {
            holder.checkBoxImage.setImageResource(R.drawable.custom_checkbox_unchecked)
        }

        holder.itemView.setOnClickListener {
            if (selectedExercises.contains(exercise)) {
                selectedExercises.remove(exercise)
            } else {
                selectedExercises.add(exercise)
            }
            // Redraw the item to show the new state
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = exerciseList.size
}