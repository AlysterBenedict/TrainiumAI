package com.example.aifitnesscoach.network

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.aifitnesscoach.getTrainiumPrefs

data class UserStats(
    var workoutsCount: Int = 0,
    var caloriesCount: Float = 0f,
    var durationMinutes: Int = 0,
    var currentStreak: Int = 0,
    var bestStreak: Int = 0,
    var dailyCalorieGoal: Float = 0f,
    var dailyTimeGoalMinutes: Int = 0,
    var todayWorkoutsCount: Int = 0,
    var todayCalories: Float = 0f,
    var todayMinutes: Int = 0
)

data class WorkoutLog(
    var id: String = "",
    var workoutName: String = "",
    var timestamp: Long = 0,
    var durationSeconds: Int = 0,
    var caloriesBurned: Float = 0f,
    var accuracy: Int = 90,
    var completedExercises: List<String> = emptyList()
)

data class WeightLog(
    var id: String = "",
    var weight: Float = 0f,
    var timestamp: Long = 0
)

object FirebaseSyncHelper {
    private const val TAG = "FirebaseSyncHelper"
    private const val PREFS_NAME = "fitness_tracker_prefs"
    private const val KEY_STATS = "user_stats_cache"
    private const val KEY_WORKOUTS = "workouts_log_cache"
    private const val KEY_WEIGHTS = "weights_log_cache"

    private val gson = Gson()

    @Volatile
    private var cachedUserStats: UserStats? = null

    // --- Local Caching Methods ---

    fun getUserStats(context: Context): UserStats {
        cachedUserStats?.let { return it }
        val prefs = context.getTrainiumPrefs(PREFS_NAME)
        val json = prefs.getString(KEY_STATS, null)
        val stats = if (json != null) {
            try {
                gson.fromJson(json, UserStats::class.java)
            } catch (e: Exception) {
                UserStats()
            }
        } else {
            // Check if SAVED_USER_METRICS exists to estimate initial stats
            val initialStats = UserStats()
            saveUserStatsLocally(context, initialStats)
            initialStats
        }

        // Recalculate stats dynamically based on the filtered workouts list to ensure perfect sync
        val workouts = getWorkouts(context)
        stats.workoutsCount = workouts.size
        stats.caloriesCount = workouts.sumOf { it.caloriesBurned.toDouble() }.toFloat()
        stats.durationMinutes = workouts.sumOf { it.durationSeconds } / 60
        stats.currentStreak = calculateStreak(workouts.sortedBy { it.timestamp })
        if (stats.currentStreak > stats.bestStreak) {
            stats.bestStreak = stats.currentStreak
        }

        // Today's metrics calculation
        val todayWorkouts = workouts.filter {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.format(java.util.Date(it.timestamp)) == sdf.format(java.util.Date())
        }
        stats.todayWorkoutsCount = todayWorkouts.size
        stats.todayCalories = todayWorkouts.sumOf { it.caloriesBurned.toDouble() }.toFloat()
        stats.todayMinutes = todayWorkouts.sumOf { (it.durationSeconds / 60).toDouble() }.toInt()

        cachedUserStats = stats
        return stats
    }

    fun saveUserStats(context: Context, stats: UserStats) {
        val prefs = context.getTrainiumPrefs(PREFS_NAME)
        prefs.edit().putString(KEY_STATS, gson.toJson(stats)).apply()
        syncStatsToFirebase(context, stats)
        cachedUserStats = stats
    }

    private fun saveUserStatsLocally(context: Context, stats: UserStats) {
        val prefs = context.getTrainiumPrefs(PREFS_NAME)
        prefs.edit().putString(KEY_STATS, gson.toJson(stats)).apply()
        cachedUserStats = stats
    }

    fun is30DayWorkout(workout: WorkoutLog): Boolean {
        val name = workout.workoutName.uppercase()
        val id = workout.id.uppercase()
        return id.startsWith("30DAY_") || 
               id.startsWith("DAY_") || 
               id.startsWith("DAY ") ||
               (name.contains("DAY") && name.contains("WORKOUT"))
    }

    private fun getRawWorkouts(context: Context): List<WorkoutLog> {
        val prefs = context.getTrainiumPrefs(PREFS_NAME)
        val json = prefs.getString(KEY_WORKOUTS, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<WorkoutLog>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun getWorkouts(context: Context): List<WorkoutLog> {
        val raw = getRawWorkouts(context)
        val appPrefs = context.getTrainiumPrefs("app_prefs")
        val resetTime = appPrefs.getLong("PLAN_RESET_TIMESTAMP", 0L)
        return if (resetTime > 0L) {
            raw.filter { it.timestamp >= resetTime }
        } else {
            raw
        }
    }

    fun saveWorkouts(context: Context, workouts: List<WorkoutLog>) {
        cachedUserStats = null
        val appPrefs = context.getTrainiumPrefs("app_prefs")
        val resetTime = appPrefs.getLong("PLAN_RESET_TIMESTAMP", 0L)
        val filtered = if (resetTime > 0L) {
            workouts.filter { it.timestamp >= resetTime }
        } else {
            workouts
        }
        val prefs = context.getTrainiumPrefs(PREFS_NAME)
        prefs.edit().putString(KEY_WORKOUTS, gson.toJson(filtered)).apply()
    }

    fun getWeights(context: Context): List<WeightLog> {
        val prefs = context.getTrainiumPrefs(PREFS_NAME)
        val json = prefs.getString(KEY_WEIGHTS, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<WeightLog>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            // Return empty list - no fake defaults before the user scans
            emptyList()
        }
    }

    fun saveWeights(context: Context, weights: List<WeightLog>) {
        val prefs = context.getTrainiumPrefs(PREFS_NAME)
        prefs.edit().putString(KEY_WEIGHTS, gson.toJson(weights)).apply()
    }

    // --- Global User Profile Updates ---

    fun getGlobalUserData(context: Context): UserData {
        val appPrefs = context.getTrainiumPrefs("app_prefs")
        val json = appPrefs.getString("SAVED_USER_METRICS", null)
        return if (json != null) {
            try {
                gson.fromJson(json, UserData::class.java) ?: UserData()
            } catch (e: Exception) {
                UserData()
            }
        } else {
            val defaultData = UserData()
            appPrefs.edit().putString("SAVED_USER_METRICS", gson.toJson(defaultData)).apply()
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                syncProfileToFirebase(context, defaultData)
            }
            defaultData
        }
    }

    /**
     * Updates height and weight globally in SAVED_USER_METRICS so all activities, 
     * calculators (BMI), and chatbot prompts see the changes immediately.
     */
    fun updateHeightAndWeight(context: Context, height: Float, weight: Float) {
        val appPrefs = context.getTrainiumPrefs("app_prefs")
        val currentUserData = getGlobalUserData(context)
        val updated = currentUserData.copy(
            heightCm = height,
            weightKg = weight,
            bmi = weight / ((height / 100f) * (height / 100f))
        )
        appPrefs.edit().putString("SAVED_USER_METRICS", gson.toJson(updated)).apply()
        Log.d(TAG, "Global UserData updated: Weight = $weight, Height = $height, BMI = ${updated.bmi}")
        
        // Sync profile to Firebase
        syncProfileToFirebase(context, updated)
        
        // Log weight change to weight list for tracking
        addWeight(context, weight)
    }

    /**
     * Updates goal weight globally inside SAVED_USER_METRICS
     */
    fun updateGoalWeight(context: Context, goalWeight: Float) {
        // We will store goal weight inside UserStats for simplicity
        val stats = getUserStats(context)
        val prefs = context.getTrainiumPrefs(PREFS_NAME)
        prefs.edit().putFloat("GOAL_WEIGHT", goalWeight).apply()
        
        // Push update to Firebase if authenticated
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val syncPrefs = context.getTrainiumPrefs(PREFS_NAME)
            val isFullSyncCompleted = syncPrefs.getBoolean("FULL_SYNC_COMPLETED", false)
            if (!isFullSyncCompleted) {
                Log.d(TAG, "Skipping goal weight upload because full sync has not completed yet.")
                return
            }

            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(userId)
                .set(mapOf("goalWeight" to goalWeight), SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "Goal weight updated in Firestore.") }
        }
    }

    fun getGoalWeight(context: Context): Float {
        val prefs = context.getTrainiumPrefs(PREFS_NAME)
        if (!prefs.contains("GOAL_WEIGHT")) {
            prefs.edit().putFloat("GOAL_WEIGHT", 0f).apply()
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                val isFullSyncCompleted = prefs.getBoolean("FULL_SYNC_COMPLETED", false)
                if (isFullSyncCompleted) {
                    val db = FirebaseFirestore.getInstance()
                    db.collection("users").document(userId)
                        .set(mapOf("goalWeight" to 0f), SetOptions.merge())
                }
            }
        }
        return prefs.getFloat("GOAL_WEIGHT", 0f)
    }

    // --- Operations to Add Logs ---
    fun addOrUpdateWorkout(context: Context, workout: WorkoutLog) {
        val workouts = getWorkouts(context).toMutableList()
        if (workout.id.isEmpty()) {
            workout.id = "w_" + System.currentTimeMillis()
        }
        val existingIndex = workouts.indexOfFirst { it.id == workout.id }
        val stats = getUserStats(context)

        if (existingIndex != -1) {
            val existing = workouts[existingIndex]
            val diffCalories = workout.caloriesBurned - existing.caloriesBurned
            val oldMins = existing.durationSeconds / 60
            val newMins = workout.durationSeconds / 60
            val diffMins = newMins - oldMins

            workouts[existingIndex] = workout
            stats.caloriesCount += diffCalories
            stats.durationMinutes += diffMins
        } else {
            workouts.add(workout)
            stats.workoutsCount += 1
            stats.caloriesCount += workout.caloriesBurned
            stats.durationMinutes += (workout.durationSeconds / 60)
        }

        saveWorkouts(context, workouts)

        // Today's metrics calculation
        val todayWorkouts = workouts.filter {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.format(java.util.Date(it.timestamp)) == sdf.format(java.util.Date())
        }
        stats.todayWorkoutsCount = todayWorkouts.size
        stats.todayCalories = todayWorkouts.sumOf { it.caloriesBurned.toDouble() }.toFloat()
        stats.todayMinutes = todayWorkouts.sumOf { (it.durationSeconds / 60).toDouble() }.toInt()

        val allWorkouts = workouts.sortedBy { it.timestamp }
        stats.currentStreak = calculateStreak(allWorkouts)
        if (stats.currentStreak > stats.bestStreak) {
            stats.bestStreak = stats.currentStreak
        }

        saveUserStats(context, stats)
        syncWorkoutToFirebase(workout)
    }

    fun addWorkout(context: Context, workout: WorkoutLog) {
        val workouts = getWorkouts(context).toMutableList()
        // Generate random ID if empty
        if (workout.id.isEmpty()) {
            workout.id = "w_" + System.currentTimeMillis()
        }
        workouts.add(workout)
        saveWorkouts(context, workouts)

        // Update Cumulative/Lifetime Stats
        val stats = getUserStats(context)
        stats.workoutsCount += 1
        stats.caloriesCount += workout.caloriesBurned
        stats.durationMinutes += (workout.durationSeconds / 60)
        
        // Recalculate today's metrics
        val todayWorkouts = workouts.filter {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.format(java.util.Date(it.timestamp)) == sdf.format(java.util.Date())
        }
        stats.todayWorkoutsCount = todayWorkouts.size
        stats.todayCalories = todayWorkouts.sumOf { it.caloriesBurned.toDouble() }.toFloat()
        stats.todayMinutes = todayWorkouts.sumOf { (it.durationSeconds / 60).toDouble() }.toInt()

        // Calculate dynamic streak based on days
        val allWorkouts = workouts.sortedBy { it.timestamp }
        stats.currentStreak = calculateStreak(allWorkouts)
        if (stats.currentStreak > stats.bestStreak) {
            stats.bestStreak = stats.currentStreak
        }

        saveUserStats(context, stats)
        syncWorkoutToFirebase(workout)
    }
    fun addWeight(context: Context, weightVal: Float, timestamp: Long = System.currentTimeMillis()) {
        val weights = getWeights(context).toMutableList()
        val entryId = "wt_" + System.currentTimeMillis()
        val entry = WeightLog(entryId, weightVal, timestamp)
        weights.add(entry)
        saveWeights(context, weights)

        syncWeightToFirebase(entry)
    }

    // --- Streak Calculation Logic ---

    private fun calculateStreak(sortedWorkouts: List<WorkoutLog>): Int {
        if (sortedWorkouts.isEmpty()) return 0
        
        // Group completed workouts by date (YYYY-MM-DD)
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val activeDays = sortedWorkouts.map { fmt.format(java.util.Date(it.timestamp)) }.toSet()

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val calendar = java.util.Calendar.getInstance()
        
        var streak = 0
        var checkDate = calendar.time
        
        // Check today and iterate backwards
        var isConsecutive = true
        var isTodayChecked = false
        
        while (isConsecutive) {
            val dateStr = sdf.format(checkDate)
            if (activeDays.contains(dateStr)) {
                streak++
                if (sdf.format(java.util.Date()) == dateStr) {
                    isTodayChecked = true
                }
            } else {
                // If we miss today, the streak is not broken yet (user might workout later today)
                if (sdf.format(java.util.Date()) == dateStr) {
                    // Skip today but check yesterday
                } else {
                    isConsecutive = false
                }
            }
            calendar.add(java.util.Calendar.DATE, -1)
            checkDate = calendar.time
        }
        
        // If streak is 0 but user worked out yesterday, it remains valid
        return streak
    }

    // --- Firebase Sync Methods ---

    fun syncProfileToFirebase(context: Context, userData: UserData) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val syncPrefs = context.getTrainiumPrefs(PREFS_NAME)
        val isFullSyncCompleted = syncPrefs.getBoolean("FULL_SYNC_COMPLETED", false)
        if (!isFullSyncCompleted) {
            Log.d(TAG, "Skipping syncProfileToFirebase because a full pull sync has not completed yet on this device.")
            return
        }

        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(userId)
            .set(mapOf("profile" to userData), SetOptions.merge())
            .addOnSuccessListener { Log.d(TAG, "Profile metrics synchronized to Firebase.") }
            .addOnFailureListener { e -> Log.e(TAG, "Error syncing Profile", e) }
    }

    fun syncWorkoutPlanToFirebase(context: Context, workoutPlanJson: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        try {
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            val plan: Map<String, List<String>> = gson.fromJson(workoutPlanJson, type)
            db.collection("users").document(userId)
                .set(mapOf("workoutPlan" to plan), SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "Workout plan synchronized to Firebase.") }
                .addOnFailureListener { e -> Log.e(TAG, "Error syncing Workout plan", e) }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing workout plan JSON", e)
        }
    }

    private fun syncStatsToFirebase(context: Context, stats: UserStats) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val syncPrefs = context.getTrainiumPrefs(PREFS_NAME)
        val isFullSyncCompleted = syncPrefs.getBoolean("FULL_SYNC_COMPLETED", false)
        if (!isFullSyncCompleted) {
            Log.d(TAG, "Skipping syncStatsToFirebase because a full pull sync has not completed yet on this device.")
            return
        }

        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(userId)
            .set(mapOf("stats" to stats), SetOptions.merge())
            .addOnSuccessListener { Log.d(TAG, "UserStats synchronized to Firebase.") }
            .addOnFailureListener { e -> Log.e(TAG, "Error syncing UserStats", e) }
    }

    private fun syncWorkoutToFirebase(workout: WorkoutLog) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(userId)
            .collection("workouts").document(workout.id)
            .set(workout)
            .addOnSuccessListener { Log.d(TAG, "WorkoutLog synchronized to Firebase.") }
    }

    private fun syncWeightToFirebase(weight: WeightLog) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(userId)
            .collection("weights").document(weight.id)
            .set(weight)
            .addOnSuccessListener { Log.d(TAG, "WeightLog synchronized to Firebase.") }
    }

    /**
     * Pushes all local settings, profile metrics, workout plans, stats, workouts list,
     * and weights list to Firestore to ensure cloud database matches local state.
     */
    fun pushLocalDataToFirebase(context: Context, onComplete: () -> Unit = {}) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            onComplete()
            return
        }

        val syncPrefs = context.getTrainiumPrefs(PREFS_NAME)
        val isFullSyncCompleted = syncPrefs.getBoolean("FULL_SYNC_COMPLETED", false)
        if (!isFullSyncCompleted) {
            Log.d(TAG, "Skipping pushLocalDataToFirebase because a full pull sync has not completed yet on this device.")
            onComplete()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val appPrefs = context.getTrainiumPrefs("app_prefs")

        // 1. Prepare UserData profile
        val profileJson = appPrefs.getString("SAVED_USER_METRICS", null)
        val profile = if (profileJson != null) {
            try {
                gson.fromJson(profileJson, UserData::class.java)
            } catch (e: Exception) {
                UserData()
            }
        } else {
            UserData()
        }

        // 2. Prepare UserStats
        val stats = getUserStats(context)

        // 3. Prepare Goal Weight
        val goalWeight = getGoalWeight(context)

        // 4. Prepare Workout Plan
        val savedPlanJson = appPrefs.getString("SAVED_WORKOUT_PLAN", null)
        val workoutPlanMap = if (savedPlanJson != null) {
            try {
                val type = object : TypeToken<Map<String, List<String>>>() {}.type
                gson.fromJson<Map<String, List<String>>>(savedPlanJson, type)
            } catch (e: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }

        // 5. Prepare planResetTimestamp
        val planResetTimestamp = appPrefs.getLong("PLAN_RESET_TIMESTAMP", 0L)

        // 6. Build the user document payload
        val userDoc = mutableMapOf<String, Any>(
            "profile" to profile,
            "stats" to stats,
            "goalWeight" to goalWeight,
            "planResetTimestamp" to planResetTimestamp
        )
        if (workoutPlanMap.isNotEmpty()) {
            userDoc["workoutPlan"] = workoutPlanMap
        }

        // 7. Write main user document to Firestore
        db.collection("users").document(userId)
            .set(userDoc, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Main user document successfully pushed to Firestore.")

                val syncPrefs = context.getTrainiumPrefs(PREFS_NAME)
                val lastSyncTimestamp = syncPrefs.getLong("LAST_SYNC_TIMESTAMP", 0L)
                val currentPushTimestamp = System.currentTimeMillis()

                val workoutsToPush = getWorkouts(context).filter { it.timestamp > lastSyncTimestamp }
                val weightsToPush = getWeights(context).filter { it.timestamp > lastSyncTimestamp }

                fun proceedWithWeights() {
                    if (weightsToPush.isEmpty()) {
                        Log.d(TAG, "No new weights to push. Push sync complete.")
                        syncPrefs.edit().putLong("LAST_SYNC_TIMESTAMP", currentPushTimestamp).apply()
                        onComplete()
                        return
                    }

                    val weightsBatch = db.batch()
                    val weightsRef = db.collection("users").document(userId).collection("weights")
                    for (weight in weightsToPush) {
                        if (weight.id.isNotEmpty()) {
                            weightsBatch.set(weightsRef.document(weight.id), weight)
                        }
                    }

                    weightsBatch.commit()
                        .addOnSuccessListener {
                            Log.d(TAG, "New weights successfully pushed. Push sync complete.")
                            syncPrefs.edit().putLong("LAST_SYNC_TIMESTAMP", currentPushTimestamp).apply()
                            onComplete()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed pushing weights during sync", e)
                            onComplete()
                        }
                }

                if (workoutsToPush.isEmpty()) {
                    Log.d(TAG, "No new workouts to push.")
                    proceedWithWeights()
                } else {
                    val workoutsBatch = db.batch()
                    val workoutsRef = db.collection("users").document(userId).collection("workouts")
                    for (workout in workoutsToPush) {
                        if (workout.id.isNotEmpty()) {
                            workoutsBatch.set(workoutsRef.document(workout.id), workout)
                        }
                    }

                    workoutsBatch.commit()
                        .addOnSuccessListener {
                            Log.d(TAG, "New workouts successfully pushed.")
                            proceedWithWeights()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed pushing workouts during sync", e)
                            onComplete()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed pushing user document during sync", e)
                onComplete()
            }
    }

    /**
     * Call this at application startup or when user logs in to download latest data from Firestore
     */
    fun performFullSync(context: Context, onComplete: () -> Unit = {}) {
        cachedUserStats = null
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            onComplete()
            return
        }

        val db = FirebaseFirestore.getInstance()
        
        // 1. Sync UserStats, Profile, and WorkoutPlan
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Sync planResetTimestamp
                    val firestoreResetTime = document.getLong("planResetTimestamp")
                    if (firestoreResetTime != null) {
                        val appPrefs = context.getTrainiumPrefs("app_prefs")
                        val localResetTime = appPrefs.getLong("PLAN_RESET_TIMESTAMP", 0L)
                        if (firestoreResetTime > localResetTime) {
                            appPrefs.edit().putLong("PLAN_RESET_TIMESTAMP", firestoreResetTime).apply()
                        }
                    }

                    val firestoreStats = document.get("stats", UserStats::class.java)
                    val firestoreGoalWeight = document.getDouble("goalWeight")
                    if (firestoreStats != null) {
                        // Merge goals and bestStreak
                        val local = getUserStats(context)
                        local.dailyCalorieGoal = firestoreStats.dailyCalorieGoal
                        local.dailyTimeGoalMinutes = firestoreStats.dailyTimeGoalMinutes
                        if (firestoreStats.bestStreak > local.bestStreak) {
                            local.bestStreak = firestoreStats.bestStreak
                        }
                        saveUserStatsLocally(context, local)
                    } else {
                        val local = getUserStats(context)
                        saveUserStatsLocally(context, local)
                    }
                    if (firestoreGoalWeight != null) {
                        updateGoalWeight(context, firestoreGoalWeight.toFloat())
                    } else {
                        val localGoalWeight = getGoalWeight(context)
                        updateGoalWeight(context, localGoalWeight)
                    }

                    // Sync Profile & Plan
                    val appPrefs = context.getTrainiumPrefs("app_prefs")
                    val localProfileJson = appPrefs.getString("SAVED_USER_METRICS", null)
                    val firestoreProfile = document.get("profile", UserData::class.java)
                    if (firestoreProfile != null) {
                        appPrefs.edit().putString("SAVED_USER_METRICS", gson.toJson(firestoreProfile)).apply()
                    } else {
                        val localUserData = if (localProfileJson != null) {
                            try {
                                gson.fromJson(localProfileJson, UserData::class.java)
                            } catch (e: Exception) {
                                UserData()
                            }
                        } else {
                            UserData()
                        }
                        appPrefs.edit().putString("SAVED_USER_METRICS", gson.toJson(localUserData)).apply()
                        syncProfileToFirebase(context, localUserData)
                    }
                    
                    val localPlanJson = appPrefs.getString("SAVED_WORKOUT_PLAN", null)
                    val firestorePlan = document.get("workoutPlan")
                    if (firestorePlan != null) {
                        try {
                            val type = object : TypeToken<Map<String, List<String>>>() {}.type
                            val planMap: Map<String, List<String>> = gson.fromJson(gson.toJson(firestorePlan), type)
                            sanitizeAndSaveWorkoutPlan(context, planMap)
                        } catch (e: Exception) {
                            appPrefs.edit().putString("SAVED_WORKOUT_PLAN", gson.toJson(firestorePlan)).apply()
                        }
                    } else if (localPlanJson != null) {
                        try {
                            val type = object : TypeToken<Map<String, List<String>>>() {}.type
                            val planMap: Map<String, List<String>> = gson.fromJson(localPlanJson, type)
                            sanitizeAndSaveWorkoutPlan(context, planMap)
                        } catch (e: Exception) {
                            syncWorkoutPlanToFirebase(context, localPlanJson)
                        }
                    }
                } else {
                    // Document doesn't exist in Firestore. Let's initialize defaults (0s)
                    val defaultData = UserData()
                    val defaultStats = UserStats()
                    val defaultGoalWeight = 0f
                    val resetTime = 0L

                    // Save locally
                    val appPrefs = context.getTrainiumPrefs("app_prefs")
                    appPrefs.edit().putString("SAVED_USER_METRICS", gson.toJson(defaultData)).apply()
                    appPrefs.edit().putLong("PLAN_RESET_TIMESTAMP", resetTime).apply()
                    
                    val statsPrefs = context.getTrainiumPrefs(PREFS_NAME)
                    statsPrefs.edit()
                        .putString(KEY_STATS, gson.toJson(defaultStats))
                        .putFloat("GOAL_WEIGHT", defaultGoalWeight)
                        .putBoolean("FULL_SYNC_COMPLETED", true)
                        .apply()

                    // Sync to Firestore
                    val initialDoc = mapOf(
                        "profile" to defaultData,
                        "stats" to defaultStats,
                        "goalWeight" to defaultGoalWeight,
                        "planResetTimestamp" to resetTime
                    )
                    db.collection("users").document(userId).set(initialDoc, SetOptions.merge())
                }

                // 2. Sync Workouts List
                db.collection("users").document(userId).collection("workouts").get()
                    .addOnSuccessListener { querySnapshot ->
                        val remoteWorkouts = querySnapshot.toObjects(WorkoutLog::class.java)
                        val localWorkouts = getWorkouts(context)
                        val mergedWorkouts = (remoteWorkouts + localWorkouts).distinctBy { it.id }
                        saveWorkouts(context, mergedWorkouts)

                        // 3. Sync Weights List
                        db.collection("users").document(userId).collection("weights").get()
                            .addOnSuccessListener { weightSnapshot ->
                                val remoteWeights = weightSnapshot.toObjects(WeightLog::class.java)
                                val localWeights = getWeights(context)
                                val mergedWeights = (remoteWeights + localWeights).distinctBy { it.id }
                                saveWeights(context, mergedWeights)
                                
                                // Recalculate stats dynamically based on the updated workouts to ensure perfect sync
                                val stats = getUserStats(context)
                                saveUserStats(context, stats)

                                val syncPrefs = context.getTrainiumPrefs(PREFS_NAME)
                                syncPrefs.edit()
                                    .putLong("LAST_SYNC_TIMESTAMP", System.currentTimeMillis())
                                    .putBoolean("FULL_SYNC_COMPLETED", true)
                                    .apply()

                                Log.i(TAG, "Full Firebase sync completed successfully.")
                                onComplete()
                            }
                            .addOnFailureListener { onComplete() }
                    }
                    .addOnFailureListener { onComplete() }
            }
            .addOnFailureListener { onComplete() }
    }

    fun sanitizeAndSaveWorkoutPlan(context: Context, planMap: Map<String, List<String>>): String {
        val mutableMap = planMap.toMutableMap()
        val day1Keys = listOf("Day_1", "Day_01", "Day 1", "Day 01")
        var day1Exercises: List<String>? = null
        for (key in day1Keys) {
            val list = mutableMap[key]
            if (list != null && list.isNotEmpty() && list.any { it.isNotBlank() }) {
                day1Exercises = list
                break
            }
        }
        for (key in day1Keys) {
            mutableMap.remove(key)
        }
        if (day1Exercises == null || day1Exercises.size < 12) {
            day1Exercises = listOf(
                "CAT-COW STRETCH", "SQUAT", "PUSH-UP", "PLANK",
                "JUMPING JACKS", "GLUTE BRIDGE", "LEG RAISES", "MOUNTAIN CLIMBER",
                "LUNGE", "SUPERMAN", "BURPEES", "CHILD'S POSE"
            )
        }
        mutableMap["Day_1"] = day1Exercises

        val sanitizedJson = gson.toJson(mutableMap)
        val appPrefs = context.getTrainiumPrefs("app_prefs")
        appPrefs.edit().putString("SAVED_WORKOUT_PLAN", sanitizedJson).apply()

        // Also upload to Firebase Firestore
        syncWorkoutPlanToFirebase(context, sanitizedJson)
        return sanitizedJson
    }

    fun syncSharedPreferencesToDatabase(context: Context) {
        val appPrefs = context.getTrainiumPrefs("app_prefs")
        val savedPlanJson = appPrefs.getString("SAVED_WORKOUT_PLAN", null) ?: return
        
        val type = object : TypeToken<Map<String, List<String>>>() {}.type
        val planMap: Map<String, List<String>> = try {
            gson.fromJson(savedPlanJson, type)
        } catch (e: Exception) {
            emptyMap()
        }
        
        for (dayTitle in planMap.keys) {
            val durKey = "DURATION_$dayTitle"
            val calKey = "CALORIES_$dayTitle"
            val completedKey = "COMPLETED_EXERCISES_$dayTitle"
            
            val durationSec = appPrefs.getInt(durKey, 0)
            val caloriesBurned = appPrefs.getFloat(calKey, 0f)
            val completedSet = appPrefs.getStringSet(completedKey, emptySet()) ?: emptySet()
            
            if (durationSec > 0 || caloriesBurned > 0f || completedSet.isNotEmpty()) {
                val workouts = getWorkouts(context)
                val dayLogs = workouts.filter { it.id.startsWith("30day_${dayTitle}_") }
                val loggedDur = dayLogs.sumOf { it.durationSeconds }
                val loggedCal = dayLogs.sumOf { it.caloriesBurned.toDouble() }.toFloat()
                
                // Compare checkmarks checklist as well
                val mostRecentLog = dayLogs.maxByOrNull { it.timestamp }
                val loggedCompletedList = mostRecentLog?.completedExercises ?: emptyList()
                val currentCompletedList = completedSet.toList().sorted()
                
                if (loggedDur != durationSec || loggedCal != caloriesBurned || loggedCompletedList != currentCompletedList) {
                    val dateString = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
                    val diffDur = durationSec - loggedDur
                    val diffCal = caloriesBurned - loggedCal
                    
                    val todayDurKey = "DURATION_${dayTitle}_${dateString}"
                    val todayCalKey = "CALORIES_${dayTitle}_${dateString}"
                    
                    val currentTodayDur = appPrefs.getInt(todayDurKey, 0)
                    val currentTodayCal = appPrefs.getFloat(todayCalKey, 0f)
                    
                    val nextTodayDur = currentTodayDur + diffDur
                    val nextTodayCal = currentTodayCal + diffCal
                    
                    appPrefs.edit()
                        .putInt(todayDurKey, nextTodayDur)
                        .putFloat(todayCalKey, nextTodayCal)
                        .apply()

                    val log = WorkoutLog(
                        id = "30day_${dayTitle}_${dateString}",
                        workoutName = "${dayTitle.replace("_", " ")} Workout",
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = nextTodayDur,
                        caloriesBurned = nextTodayCal,
                        accuracy = 90,
                        completedExercises = currentCompletedList
                    )
                    addOrUpdateWorkout(context, log)
                }
            } else {
                val workouts = getWorkouts(context)
                val dayLogs = workouts.filter { it.id.startsWith("30day_${dayTitle}_") }
                if (dayLogs.isNotEmpty()) {
                    val loggedDur = dayLogs.sumOf { it.durationSeconds }
                    val loggedCal = dayLogs.sumOf { it.caloriesBurned.toDouble() }.toFloat()
                    
                    // Restore exact completed checkmarks checklist
                    val mostRecentLog = dayLogs.maxByOrNull { it.timestamp }
                    val completedSetToRestore = if (mostRecentLog != null && mostRecentLog.completedExercises.isNotEmpty()) {
                        mostRecentLog.completedExercises.toSet()
                    } else {
                        // Fallback to checking all exercises as completed (backward compatibility)
                        val dayExercises = planMap[dayTitle] ?: emptyList()
                        dayExercises.indices.map { it.toString() }.toSet()
                    }
                    
                    appPrefs.edit()
                        .putInt(durKey, loggedDur)
                        .putFloat(calKey, loggedCal)
                        .putStringSet(completedKey, completedSetToRestore)
                        .apply()
                }
            }
        }
    }

    fun delete30DayWorkoutLog(context: Context, dayTitle: String) {
        cachedUserStats = null
        val prefix = "30day_${dayTitle}_"
        val workouts = getRawWorkouts(context).toMutableList()
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val db = FirebaseFirestore.getInstance()
        
        val iterator = workouts.iterator()
        var adjusted = false
        val stats = getUserStats(context)
        
        while (iterator.hasNext()) {
            val workout = iterator.next()
            if (workout.id.startsWith(prefix)) {
                stats.workoutsCount = Math.max(0, stats.workoutsCount - 1)
                stats.caloriesCount = Math.max(0f, stats.caloriesCount - workout.caloriesBurned)
                stats.durationMinutes = Math.max(0, stats.durationMinutes - (workout.durationSeconds / 60))
                
                if (userId != null) {
                    db.collection("users").document(userId)
                        .collection("workouts").document(workout.id)
                        .delete()
                        .addOnSuccessListener { Log.d(TAG, "WorkoutLog ${workout.id} deleted from Firestore.") }
                }
                iterator.remove()
                adjusted = true
            }
        }
        
        if (adjusted) {
            saveWorkouts(context, workouts)
            
            // Recalculate today's metrics
            val todayWorkouts = workouts.filter {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                sdf.format(java.util.Date(it.timestamp)) == sdf.format(java.util.Date())
            }
            stats.todayWorkoutsCount = todayWorkouts.size
            stats.todayCalories = todayWorkouts.sumOf { it.caloriesBurned.toDouble() }.toFloat()
            stats.todayMinutes = todayWorkouts.sumOf { (it.durationSeconds / 60).toDouble() }.toInt()
            
            val allWorkouts = workouts.sortedBy { it.timestamp }
            stats.currentStreak = calculateStreak(allWorkouts)
            if (stats.currentStreak > stats.bestStreak) {
                stats.bestStreak = stats.currentStreak
            }
            saveUserStats(context, stats)
        }
    }

    fun clearAll30DayWorkoutLogs(context: Context) {
        cachedUserStats = null
        val workouts = getRawWorkouts(context).toMutableList()
        val stats = getUserStats(context)
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        val iterator = workouts.iterator()
        while (iterator.hasNext()) {
            val workout = iterator.next()
            stats.workoutsCount = Math.max(0, stats.workoutsCount - 1)
            stats.caloriesCount = Math.max(0f, stats.caloriesCount - workout.caloriesBurned)
            stats.durationMinutes = Math.max(0, stats.durationMinutes - (workout.durationSeconds / 60))

            if (userId != null) {
                db.collection("users").document(userId)
                    .collection("workouts").document(workout.id)
                    .delete()
            }
            iterator.remove()
        }

        // Proactively delete all 30 potential workout documents directly from Firestore to ensure server cleanup
        if (userId != null) {
            for (dayNum in 1..30) {
                db.collection("users").document(userId)
                    .collection("workouts").document("30day_Day_$dayNum")
                    .delete()
                db.collection("users").document(userId)
                    .collection("workouts").document("30day_Day $dayNum")
                    .delete()
            }
        }

        saveWorkouts(context, workouts)

        // Recalculate today's metrics
        val todayWorkouts = workouts.filter {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.format(java.util.Date(it.timestamp)) == sdf.format(java.util.Date())
        }
        stats.todayWorkoutsCount = todayWorkouts.size
        stats.todayCalories = todayWorkouts.sumOf { it.caloriesBurned.toDouble() }.toFloat()
        stats.todayMinutes = todayWorkouts.sumOf { (it.durationSeconds / 60).toDouble() }.toInt()

        val allWorkouts = workouts.sortedBy { it.timestamp }
        stats.currentStreak = calculateStreak(allWorkouts)
        if (stats.currentStreak > stats.bestStreak) {
            stats.bestStreak = stats.currentStreak
        }

        saveUserStats(context, stats)

        // Set plan reset timestamp in app_prefs to prevent race condition restoration of deleted workouts
        val resetTime = System.currentTimeMillis()
        val appPrefs = context.getTrainiumPrefs("app_prefs")
        appPrefs.edit().putLong("PLAN_RESET_TIMESTAMP", resetTime).apply()

        // Sync planResetTimestamp to Firebase user document
        if (userId != null) {
            db.collection("users").document(userId)
                .set(mapOf("planResetTimestamp" to resetTime), SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "planResetTimestamp synchronized to Firebase.") }
        }
    }

    fun deleteWorkoutPlan(context: Context) {
        cachedUserStats = null
        val appPrefs = context.getTrainiumPrefs("app_prefs")
        
        // 1. Clear completion progress keys in app_prefs
        val edit = appPrefs.edit()
        for (day in 1..30) {
            val dayTitle1 = "Day_$day"
            val dayTitle2 = "Day $day"
            edit.remove("COMPLETED_EXERCISES_$dayTitle1")
                .remove("DURATION_$dayTitle1")
                .remove("CALORIES_$dayTitle1")
                .remove("COMPLETED_EXERCISES_$dayTitle2")
                .remove("DURATION_$dayTitle2")
                .remove("CALORIES_$dayTitle2")
        }
        
        // Set profile metrics to default (0s)
        val defaultProfile = UserData()
        edit.remove("SAVED_WORKOUT_PLAN")
            .putString("SAVED_USER_METRICS", gson.toJson(defaultProfile))
            .apply()

        // 2. Clear workouts logs & reset stats
        clearAll30DayWorkoutLogs(context)
        
        // Ensure UserStats is completely cleared to defaults (0s)
        val defaultStats = UserStats()
        saveUserStats(context, defaultStats)

        // 3. Clear weights locally and on Firestore
        val weights = getWeights(context)
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val db = FirebaseFirestore.getInstance()
            for (weightEntry in weights) {
                db.collection("users").document(userId)
                    .collection("weights").document(weightEntry.id)
                    .delete()
            }
        }
        saveWeights(context, emptyList())

        // 4. Reset goal weight to default 0
        val statsPrefs = context.getTrainiumPrefs(PREFS_NAME)
        statsPrefs.edit()
            .putFloat("GOAL_WEIGHT", 0f)
            .remove("LAST_SYNC_TIMESTAMP")
            .apply()

        // 5. Sync reset to Firestore if authenticated
        if (userId != null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(userId)
                .set(mapOf(
                    "workoutPlan" to com.google.firebase.firestore.FieldValue.delete(),
                    "profile" to defaultProfile,
                    "stats" to defaultStats,
                    "goalWeight" to 0f
                ), SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "Workout plan deleted and profile/stats/goal reset to 0 in Firestore.") }
        }
    }

    fun clearLocalCache(context: Context, googleUserId: String) {
        val prefs1 = context.getSharedPreferences("${PREFS_NAME}_google_$googleUserId", Context.MODE_PRIVATE)
        prefs1.edit().clear().apply()

        val prefs2 = context.getSharedPreferences("app_prefs_google_$googleUserId", Context.MODE_PRIVATE)
        prefs2.edit()
            .remove("SAVED_WORKOUT_PLAN")
            .remove("biometric_enabled")
            .apply()
        Log.d(TAG, "Local preferences cache cleared for Google user $googleUserId on logout.")
    }
}
