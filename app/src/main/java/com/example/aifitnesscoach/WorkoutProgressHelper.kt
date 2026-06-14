package com.example.aifitnesscoach

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object WorkoutProgressHelper {

    private const val PREFS_NAME = "app_prefs"
    private val gson = Gson()

    /**
     * Checks if a specific exercise index is completed for the given day.
     */
    fun isExerciseCompleted(context: Context, dayTitle: String, exerciseIndex: Int): Boolean {
        val sharedPrefs = context.getTrainiumPrefs(PREFS_NAME)
        val completedSet = sharedPrefs.getStringSet("COMPLETED_EXERCISES_$dayTitle", emptySet()) ?: emptySet()
        return completedSet.contains(exerciseIndex.toString())
    }

    /**
     * Marks an exercise index as completed, accumulating duration and calories.
     */
    fun markExerciseCompleted(
        context: Context,
        dayTitle: String,
        exerciseIndex: Int,
        durationSeconds: Int,
        caloriesBurned: Float
    ) {
        val sharedPrefs = context.getTrainiumPrefs(PREFS_NAME)
        val completedKey = "COMPLETED_EXERCISES_$dayTitle"
        val completedSet = (sharedPrefs.getStringSet(completedKey, emptySet()) ?: emptySet()).toMutableSet()
        completedSet.add(exerciseIndex.toString())

        val durKey = "DURATION_$dayTitle"
        val calKey = "CALORIES_$dayTitle"
        val currentDur = sharedPrefs.getInt(durKey, 0)
        val currentCal = sharedPrefs.getFloat(calKey, 0f)

        sharedPrefs.edit()
            .putStringSet(completedKey, completedSet)
            .putInt(durKey, currentDur + durationSeconds)
            .putFloat(calKey, currentCal + caloriesBurned)
            .apply()
    }

    /**
     * Resets the progress for a specific day.
     */
    fun resetDayProgress(context: Context, dayTitle: String) {
        val sharedPrefs = context.getTrainiumPrefs(PREFS_NAME)
        val edit = sharedPrefs.edit()
            .remove("COMPLETED_EXERCISES_$dayTitle")
            .remove("DURATION_$dayTitle")
            .remove("CALORIES_$dayTitle")
            
        val allKeys = sharedPrefs.all.keys
        for (key in allKeys) {
            if (key.startsWith("DURATION_${dayTitle}_") || key.startsWith("CALORIES_${dayTitle}_")) {
                edit.remove(key)
            }
        }
        edit.apply()
        com.example.aifitnesscoach.network.FirebaseSyncHelper.delete30DayWorkoutLog(context, dayTitle)
    }

    /**
     * Resets progress for all days in the workout plan.
     */
    fun resetAllPlanProgress(context: Context, days: Collection<String>) {
        val sharedPrefs = context.getTrainiumPrefs(PREFS_NAME)
        val edit = sharedPrefs.edit()
        for (dayTitle in days) {
            edit.remove("COMPLETED_EXERCISES_$dayTitle")
                .remove("DURATION_$dayTitle")
                .remove("CALORIES_$dayTitle")
        }
        
        val allKeys = sharedPrefs.all.keys
        for (key in allKeys) {
            for (dayTitle in days) {
                if (key.startsWith("DURATION_${dayTitle}_") || key.startsWith("CALORIES_${dayTitle}_")) {
                    edit.remove(key)
                }
            }
        }
        edit.apply()
    }

    /**
     * Retrieves the accumulated duration in seconds for a specific day.
     */
    fun getDayDuration(context: Context, dayTitle: String): Int {
        val sharedPrefs = context.getTrainiumPrefs(PREFS_NAME)
        return sharedPrefs.getInt("DURATION_$dayTitle", 0)
    }

    /**
     * Retrieves the accumulated calories for a specific day.
     */
    fun getDayCalories(context: Context, dayTitle: String): Float {
        val sharedPrefs = context.getTrainiumPrefs(PREFS_NAME)
        return sharedPrefs.getFloat("CALORIES_$dayTitle", 0f)
    }

    /**
     * Extracts the numeric day value from a day key (e.g. "Day_1" or "Day 1" -> 1).
     */
    fun getDayNumber(key: String): Int {
        val clean = key.replace("Day", "").replace("_", "").trim()
        return clean.toIntOrNull() ?: 1
    }

    /**
     * Checks if a Day is completed (all exercises completed).
     * If it is a Rest Day, it is considered completed if the previous day is completed.
     */
    fun isDayCompleted(context: Context, dayTitle: String, totalExercises: Int): Boolean {
        val dayNum = getDayNumber(dayTitle)
        if (dayNum % 4 == 0) {
            // Rest Day is completed if the previous day is completed
            if (dayNum > 1) {
                val prevDayTitle = if (dayTitle.contains("_")) "Day_${dayNum - 1}" else "Day ${dayNum - 1}"
                val prevDayTotal = getDayTotalExercisesCount(context, prevDayTitle)
                return isWorkoutDayCompleted(context, prevDayTitle, prevDayTotal)
            }
            return true
        }
        return isWorkoutDayCompleted(context, dayTitle, totalExercises)
    }

    private fun isWorkoutDayCompleted(context: Context, dayTitle: String, totalExercises: Int): Boolean {
        if (totalExercises == 0) return false
        val sharedPrefs = context.getTrainiumPrefs(PREFS_NAME)
        val completedSet = sharedPrefs.getStringSet("COMPLETED_EXERCISES_$dayTitle", emptySet()) ?: emptySet()
        return completedSet.size >= totalExercises
    }

    private fun getDayTotalExercisesCount(context: Context, dayTitle: String): Int {
        val sharedPrefs = context.getTrainiumPrefs(PREFS_NAME)
        val savedPlanJson = sharedPrefs.getString("SAVED_WORKOUT_PLAN", null) ?: return 0
        return try {
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            val plan: Map<String, List<String>> = gson.fromJson(savedPlanJson, type)
            plan[dayTitle]?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Determines the current active day from the 30-day program.
     * It is the first day that is not completed.
     */
    fun getActiveDay(context: Context, workoutPlan: Map<String, List<String>>): String {
        val sortedDays = workoutPlan.keys.sortedBy { getDayNumber(it) }
        if (sortedDays.isEmpty()) return "Day_1"
        
        for (day in sortedDays) {
            val totalExercises = workoutPlan[day]?.size ?: 0
            if (!isDayCompleted(context, day, totalExercises)) {
                return day
            }
        }
        // If all completed, return the last day
        return sortedDays.lastOrNull() ?: "Day_1"
    }

    /**
     * Checks if a specific exercise is timed (as opposed to rep-based).
     */
    fun isExerciseTimed(name: String): Boolean {
        val upper = name.uppercase()
        var config = Exercises_func.list.find { it.name.equals(name, ignoreCase = true) }
        if (config == null) {
            val mappedName = when (name.trim().lowercase()) {
                "deep squats", "deep squat" -> "SQUAT"
                "push-ups", "pushups", "pushup", "push-up" -> "PUSH-UP"
                "plank hold", "plank" -> "PLANK"
                "jumping jacks" -> "JUMPING JACKS"
                "glute bridges", "glute bridge" -> "GLUTE BRIDGE"
                "lying leg raises", "leg raises", "leg raise" -> "LEG RAISES"
                "mountain climbers", "mountain climber" -> "MOUNTAIN CLIMBER"
                "bodyweight lunges", "lunges", "lunge" -> "LUNGE"
                "superman hold", "superman" -> "SUPERMAN"
                "burpees", "burpee" -> "BURPEES"
                else -> null
            }
            if (mappedName != null) {
                config = Exercises_func.list.find { it.name == mappedName }
            }
        }
        if (config == null) {
            config = Exercises_func.list.find {
                val cleanName = name.uppercase().replace("-", " ").replace(" ", "")
                val cleanList = it.name.uppercase().replace("-", " ").replace(" ", "")
                cleanName.contains(cleanList) || cleanList.contains(cleanName)
            }
        }

        return if (config != null) {
            config.exerciseType == "timed"
        } else {
            upper.contains("PLANK") || upper.contains("SKIPPING") || upper.contains("FLUTTER") ||
                    upper.contains("SCISSOR") || upper.contains("POSE") || upper.contains("WALL SIT") ||
                    upper.contains("CIRCLES") || upper.contains("T-POSE")
        }
    }

    /**
     * Estimates calories and duration dynamically based on the list of exercises present.
     */
    fun getEstimatedStatsForDay(context: Context, exercises: List<String>): Pair<Int, Float> {
        if (exercises.isEmpty()) return Pair(0, 0f)
        if (exercises.size == 1 && exercises[0].equals("Rest Day", ignoreCase = true)) {
            return Pair(0, 0f)
        }

        var totalSeconds = 0
        var totalCalories = 0f

        val prefs = context.getTrainiumPrefs(PREFS_NAME)
        val defaultExerciseSec = prefs.getInt("pref_exercise_duration_seconds", 30)
        val defaultPlankSec = prefs.getInt("pref_plank_duration_seconds", 80)
        val restSec = prefs.getInt("pref_rest_duration_seconds", 15)

        val weightKg = com.example.aifitnesscoach.network.FirebaseSyncHelper.getGlobalUserData(context).weightKg.let { if (it > 0f) it else 70f }

        for (exerciseName in exercises) {
            val isTimed = isExerciseTimed(exerciseName)
            val durationSeconds = if (isTimed) {
                if (exerciseName.uppercase().contains("PLANK") && !exerciseName.uppercase().contains("SIDE PLANK")) {
                    defaultPlankSec
                } else {
                    defaultExerciseSec
                }
            } else {
                // For rep-based exercises, estimate 16 reps * 3.5 seconds = 56 seconds
                56
            }

            totalSeconds += durationSeconds

            // Add calorie burn
            val burnRate = getCalorieBurnRate(exerciseName, weightKg)
            totalCalories += durationSeconds * burnRate

            // Add rest time (except for the last exercise)
            totalSeconds += restSec
        }

        // Subtract the extra rest time added after the last exercise
        if (totalSeconds >= restSec) {
            totalSeconds -= restSec
        }

        val durationMinutes = Math.max(1, (totalSeconds + 59) / 60) // ceiling rounding to minutes, at least 1 min
        return Pair(durationMinutes, totalCalories)
    }

    /**
     * Gets the calorie burn rate (kcal per second) based on the exercise name and user weight.
     */
    fun getCalorieBurnRate(exerciseName: String, weightKg: Float = 70f): Float {
        val name = exerciseName.trim().uppercase()
        val met = when {
            name.contains("BURPEE") || name.contains("JACK") -> 11.0f // high intensity
            name.contains("SQUAT") || name.contains("LUNGE") || name.contains("PUSH-UP") || name.contains("CURL") -> 9.0f // moderate intensity
            name.contains("PLANK") || name.contains("BRIDGE") || name.contains("HOLD") -> 6.0f // static/core
            name.contains("STRETCH") || name.contains("POSE") -> 2.5f // low intensity/recovery
            else -> 7.5f // default
        }
        return (met * 3.5f * weightKg) / 12000f
    }

    /**
     * Formats default reps/duration for exercises to match the design (e.g. 00:30 or x16).
     */
    fun getExerciseDurationOrReps(context: Context, name: String): String {
        val isTimed = isExerciseTimed(name)
        val upper = name.uppercase()
        return if (isTimed) {
            val prefs = context.getTrainiumPrefs(PREFS_NAME)
            val durationSeconds = if (upper.contains("PLANK") && !upper.contains("SIDE PLANK")) {
                prefs.getInt("pref_plank_duration_seconds", 80)
            } else {
                prefs.getInt("pref_exercise_duration_seconds", 30)
            }
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        } else {
            "x16"
        }
    }
}
