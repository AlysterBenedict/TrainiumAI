package com.example.aifitnesscoach

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class WorkoutGenerator(private val context: Context) {

    // --- Model and Data Members ---
    private var bodyMetricModel: Module? = null
    private var workoutGenModel: Module? = null
    private var preprocessingData: PreprocessingData? = null
    private var tokenizer: Map<String, Int>? = null
    private val idxToWord: Map<Int, String>?

    // --- Constants ---
    companion object {
        const val IMAGE_WIDTH = 224
        const val IMAGE_HEIGHT = 224
    }

    init {
        // Corrected file names with the .pt extension
        bodyMetricModel = loadModel("body_metric_estimator.pt")
        workoutGenModel = loadModel("workout_generator.pt")
        preprocessingData = loadJson("preprocessing_data.json", PreprocessingData::class.java)
        tokenizer = loadJson("tokenizer.json", Map::class.java) as? Map<String, Int>
        idxToWord = tokenizer?.entries?.associate { (key, value) -> value to key }
    }


    /**
     * The main public function to generate a workout plan.
     */
    fun generatePlan(
        frontalImageUri: Uri,
        sideImageUri: Uri,
        age: Int,
        gender: String,
        level: String,
        goal: String,
        callback: (List<String>?) -> Unit
    ) {
        // Run in a background thread to avoid blocking the UI
        Thread {
            try {
                // 1. Preprocess images
                val frontalBitmap = uriToBitmap(frontalImageUri)
                val sideBitmap = uriToBitmap(sideImageUri)
                val frontalTensor = preprocessImage(frontalBitmap)
                val sideTensor = preprocessImage(sideBitmap)

                // 2. Run Body Metric Model
                val bodyMetrics = runBodyMetricModel(frontalTensor, sideTensor)

                // 3. Preprocess user profile data
                val profileTensor = createProfileTensor(age, gender, level, goal, bodyMetrics)

                // 4. Run Workout Generator Model
                val workoutPlan = runWorkoutGeneratorModel(profileTensor)

                // 5. Return the result on the main thread
                callback(workoutPlan)

            } catch (e: Exception) {
                e.printStackTrace()
                callback(null) // Return null on error
            }
        }.start()
    }


    // --- Private Helper and Model Execution Functions ---

    private fun runBodyMetricModel(frontalTensor: Tensor, sideTensor: Tensor): FloatArray {
        // Run the model and get the output tensor
        val outputTensor = bodyMetricModel!!.forward(
            IValue.from(frontalTensor),
            IValue.from(sideTensor)
        ).toTensor()
        return outputTensor.dataAsFloatArray
    }

    private fun runWorkoutGeneratorModel(profileTensor: Tensor): List<String> {
        // The transformer model requires a target sequence to start with.
        // We'll start with a sequence of zeros.
        val targetSequence = Tensor.fromBlob(LongArray(1 * 30 * 20), longArrayOf(1, 30, 20))

        val outputTensor = workoutGenModel!!.forward(
            IValue.from(profileTensor),
            IValue.from(targetSequence)
        ).toTensor()

        val outputArray = outputTensor.dataAsFloatArray
        val shape = outputTensor.shape() // Shape should be [1, 30, 20, vocab_size]

        val generatedExercises = mutableListOf<String>()
        val vocabSize = shape[3].toInt()
        val maxExercisesPerDay = shape[2].toInt()

        // We only need the output for the first day for this implementation
        for (j in 0 until maxExercisesPerDay) {
            val startIndex = j * vocabSize
            val endIndex = startIndex + vocabSize
            val exerciseScores = outputArray.sliceArray(startIndex until endIndex)

            val predictedIndex = exerciseScores.indices.maxByOrNull { exerciseScores[it] } ?: -1
            val predictedWord = idxToWord?.get(predictedIndex) ?: "<unk>"

            if (predictedWord != "<pad>" && predictedWord != "<unk>") {
                generatedExercises.add(predictedWord)
            }
        }
        return generatedExercises
    }


    private fun createProfileTensor(age: Int, gender: String, level: String, goal: String, metrics: FloatArray): Tensor {
        val pData = preprocessingData ?: throw IOException("Preprocessing data not loaded")

        // Create one-hot encoded vectors for categorical features
        val levelVector = floatArrayOf(
            if (level == "Beginner") 1f else 0f,
            if (level == "Intermediate") 1f else 0f,
            if (level == "Advanced") 1f else 0f
        )
        val goalVector = floatArrayOf(
            if (goal == "Lose Weight") 1f else 0f,
            if (goal == "Gain Muscle") 1f else 0f,
            if (goal == "Gain Stamina") 1f else 0f,
            if (goal == "General Fitness") 1f else 0f
        )
        val genderVector = floatArrayOf(if (gender == "Male") 1f else 0f)

        // Combine all features into a single array
        val combinedFeatures = floatArrayOf(age.toFloat()) + genderVector + levelVector + goalVector + metrics

        // Apply scaling
        val scaledFeatures = FloatArray(combinedFeatures.size)
        for (i in combinedFeatures.indices) {
            val min = pData.scaler.min_[i].toFloat()
            val scale = pData.scaler.scale_[i].toFloat()
            scaledFeatures[i] = (combinedFeatures[i] - min) * scale
        }

        // Convert to PyTorch Tensor
        return Tensor.fromBlob(scaledFeatures, longArrayOf(1, scaledFeatures.size.toLong()))
    }

    private fun preprocessImage(bitmap: Bitmap?): Tensor {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap!!, IMAGE_WIDTH, IMAGE_HEIGHT, true)
        // Normalize the image and convert it to a tensor
        return TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )
    }

    private fun uriToBitmap(uri: Uri): Bitmap {
        return context.contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it)
        }
    }

    @Throws(IOException::class)
    private fun getAssetFilePath(assetName: String): String {
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

    private fun loadModel(modelName: String): Module? {
        return try {
            Module.load(getAssetFilePath(modelName))
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun <T> loadJson(fileName: String, classOfT: Class<T>): T? {
        return try {
            context.assets.open(fileName).bufferedReader().use {
                Gson().fromJson(it, classOfT)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}