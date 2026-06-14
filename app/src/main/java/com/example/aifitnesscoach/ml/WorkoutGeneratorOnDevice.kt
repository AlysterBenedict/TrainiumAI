package com.example.aifitnesscoach.ml

import android.content.Context
import android.util.Log
import com.example.aifitnesscoach.network.UserData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.util.Random

class WorkoutGeneratorOnDevice private constructor(private val context: Context) {

    private var module: Module? = null
    private var idxToWord: Map<Long, String> = emptyMap()

    // StandardScaler parameters compiled from original scaler.pkl
    private val means = floatArrayOf(
        41.4409f,       // Age
        172.52915f,     // height_cm
        86.25694f,      // weight_kg
        29.106802f,     // BMI
        103.0181f,      // chest_cm
        95.81841f,      // waist_cm
        106.55925f,     // hip_cm
        63.54808f,      // thigh_cm
        37.761974f      // bicep_cm
    )

    private val scales = floatArrayOf(
        13.8304f,       // Age
        11.486592f,     // height_cm
        21.34799f,      // weight_kg
        7.2768025f,     // BMI
        14.08691f,      // chest_cm
        19.523878f,     // waist_cm
        12.501459f,     // hip_cm
        8.838547f,      // thigh_cm
        5.6388717f      // bicep_cm
    )

    init {
        try {
            val modelPath = assetFilePath(context, "trainium_sota_transformer_model.pt")
            module = Module.load(modelPath)
            Log.i(TAG, "Workout PyTorch model loaded successfully from assets.")

            // Load tokenizer vocabulary mapping
            val tokenizerStream = context.assets.open("tokenizer.json")
            val tokenizerJson = tokenizerStream.bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, Long>>() {}.type
            val wordToIdx: Map<String, Long> = Gson().fromJson(tokenizerJson, type)
            idxToWord = wordToIdx.entries.associate { it.value to it.key }
            Log.i(TAG, "Tokenizer vocabulary loaded: ${idxToWord.size} entries.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing WorkoutGeneratorOnDevice", e)
        }
    }

    /**
     * Generates a 30-day progressive workout plan on-device.
     */
    fun generateWorkout(userData: UserData): Map<String, List<String>> {
        val model = module ?: throw IllegalStateException("Workout generator model is not loaded.")
        if (idxToWord.isEmpty()) {
            throw IllegalStateException("Vocabulary mappings are empty.")
        }

        val profileData = preprocessProfile(userData)
        val profileTensor = Tensor.fromBlob(profileData, longArrayOf(1, 18))

        // Store the dynamic sequence: starts with shape [1, 1, 20], filled with 0s
        val maxExercises = 20
        var dayCount = 1
        var sequenceList = LongArray(maxExercises) { 0L }

        for (dayIdx in 0 until 29) {
            val sequenceTensor = Tensor.fromBlob(sequenceList, longArrayOf(1, dayCount.toLong(), maxExercises.toLong()))
            val output = model.forward(
                IValue.from(profileTensor),
                IValue.from(sequenceTensor)
            )
            val outputTensor = output.toTensor()
            val logits = outputTensor.dataAsFloatArray

            // Shape of output logits is [1, dayCount, 20, vocabSize (51)]
            val vocabSize = idxToWord.size
            val lastDayOffset = (dayCount - 1) * maxExercises * vocabSize

            val nextDayIds = LongArray(maxExercises)
            val random = Random()

            for (slot in 0 until maxExercises) {
                val offset = lastDayOffset + (slot * vocabSize)
                val slotLogits = FloatArray(vocabSize)
                for (v in 0 until vocabSize) {
                    slotLogits[v] = logits[offset + v]
                }

                // Top-K (k=10) Filtering
                val k = 10
                val candidates = slotLogits.indices.map { idx -> Pair(idx, slotLogits[idx]) }
                    .sortedByDescending { it.second }
                    .take(k)

                // Softmax
                val maxLogit = candidates.maxOf { it.second }
                var sumExp = 0.0f
                val exps = FloatArray(k)
                for (j in 0 until k) {
                    exps[j] = kotlin.math.exp(candidates[j].second - maxLogit)
                    sumExp += exps[j]
                }
                val probs = FloatArray(k)
                for (j in 0 until k) {
                    probs[j] = exps[j] / sumExp
                }

                // Multinomial sampling
                val r = random.nextFloat()
                var cumSum = 0.0f
                var sampledIdx = candidates[0].first
                for (j in 0 until k) {
                    cumSum += probs[j]
                    if (r <= cumSum) {
                        sampledIdx = candidates[j].first
                        break
                    }
                }
                nextDayIds[slot] = sampledIdx.toLong()
            }

            // Concatenate nextDayIds to the sequence
            val newSequence = LongArray(sequenceList.size + maxExercises)
            System.arraycopy(sequenceList, 0, newSequence, 0, sequenceList.size)
            System.arraycopy(nextDayIds, 0, newSequence, sequenceList.size, nextDayIds.size)
            sequenceList = newSequence
            dayCount++
        }

        // Decode the final sequence of length 30 days
        val workoutPlan = mutableMapOf<String, List<String>>()
        val vocabSize = idxToWord.size

        for (d in 0 until dayCount) {
            val dayExercises = mutableListOf<String>()
            for (slot in 0 until maxExercises) {
                val exerciseId = sequenceList[d * maxExercises + slot]
                if (exerciseId == 0L) {
                    break
                }
                val name = idxToWord[exerciseId] ?: "<unk>"
                if (name != "<unk>" && name != "<pad>" && name != "Rest Day" && !dayExercises.contains(name)) {
                    dayExercises.add(name)
                }
            }

            val dayKey = "Day_${d + 1}"
            if (dayExercises.isNotEmpty()) {
                workoutPlan[dayKey] = dayExercises
            }
        }

        // Hardcoded Day 1 exercises since ML model starts generating from Day 2 onwards
        workoutPlan["Day_1"] = listOf(
            "CAT-COW STRETCH", "SQUAT", "PUSH-UP", "PLANK",
            "JUMPING JACKS", "GLUTE BRIDGE", "LEG RAISES", "MOUNTAIN CLIMBER",
            "LUNGE", "SUPERMAN", "BURPEES", "CHILD'S POSE"
        )

        // Apply progressive overload: insert a rest day every 4th day, replacing any generated workout
        for (dayIdx in 1..30) {
            if (dayIdx % 4 == 0) {
                workoutPlan["Day_$dayIdx"] = listOf("Rest Day")
            }
        }

        return workoutPlan
    }

    private fun preprocessProfile(userData: UserData): FloatArray {
        val profile = FloatArray(18)

        // Scaling continuous inputs via (x - mean) / scale
        profile[0] = (userData.age - means[0]) / scales[0]
        profile[1] = (userData.heightCm - means[1]) / scales[1]
        profile[2] = (userData.weightKg - means[2]) / scales[2]
        profile[3] = (userData.bmi - means[3]) / scales[3]
        profile[4] = (userData.chestCm - means[4]) / scales[4]
        profile[5] = (userData.waistCm - means[5]) / scales[5]
        profile[6] = (userData.hipCm - means[6]) / scales[6]
        profile[7] = (userData.thighCm - means[7]) / scales[7]
        profile[8] = (userData.bicepCm - means[8]) / scales[8]

        // One-hot encode Gender ('Female', 'Male')
        if (userData.gender.equals("Female", ignoreCase = true)) {
            profile[9] = 1.0f
            profile[10] = 0.0f
        } else {
            profile[9] = 0.0f
            profile[10] = 1.0f
        }

        // One-hot encode Goal ('Gain Muscle', 'Gain Stamina', 'General Fitness', 'Lose Weight')
        profile[11] = if (userData.goal.equals("Gain Muscle", ignoreCase = true) || userData.goal.equals("Muscle Gain", ignoreCase = true)) 1.0f else 0.0f
        profile[12] = if (userData.goal.equals("Gain Stamina", ignoreCase = true)) 1.0f else 0.0f
        profile[13] = if (userData.goal.equals("General Fitness", ignoreCase = true)) 1.0f else 0.0f
        profile[14] = if (userData.goal.equals("Lose Weight", ignoreCase = true) || userData.goal.equals("Weight Loss", ignoreCase = true)) 1.0f else 0.0f

        // One-hot encode level ('Advanced', 'Beginner', 'Intermediate')
        profile[15] = if (userData.level.equals("Advanced", ignoreCase = true)) 1.0f else 0.0f
        profile[16] = if (userData.level.equals("Beginner", ignoreCase = true)) 1.0f else 0.0f
        profile[17] = if (userData.level.equals("Intermediate", ignoreCase = true)) 1.0f else 0.0f

        return profile
    }

    companion object {
        private const val TAG = "WorkoutGeneratorOD"

        @Volatile
        private var INSTANCE: WorkoutGeneratorOnDevice? = null

        fun getInstance(context: Context): WorkoutGeneratorOnDevice {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WorkoutGeneratorOnDevice(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun assetFilePath(context: Context, assetName: String): String {
            val file = File(context.filesDir, assetName)
            if (file.exists() && file.length() > 0) {
                return file.absolutePath
            }
            context.assets.open(assetName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    outputStream.flush()
                }
            }
            return file.absolutePath
        }
    }
}
