package com.example.aifitnesscoach

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class DayAdapter(
    private val days: List<String>,
    private val onDayClicked: (String) -> Unit
) : RecyclerView.Adapter<DayAdapter.DayViewHolder>() {

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayNumberTextView: TextView = view.findViewById(R.id.dayNumberTextView)
        val workoutTitleTextView: TextView = view.findViewById(R.id.workoutTitleTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val dayKey = days[position]
        
        // Always use position + 1 for display to ensure "01", "02", etc.
        val dayNumber = position + 1
        val formattedDay = String.format(Locale.US, "%02d", dayNumber)
        
        holder.dayNumberTextView.text = formattedDay
        holder.workoutTitleTextView.text = "Full Body Workout" // Or dynamic title if available

        holder.itemView.setOnClickListener {
            onDayClicked(dayKey)
        }
    }

    override fun getItemCount() = days.size
}