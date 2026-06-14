package com.example.aifitnesscoach.network

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface FitnessApiService_func {
    @Multipart
    @POST("/predict_biometrics")
    suspend fun predictBiometrics(
        @Part frontalImage: MultipartBody.Part,
        @Part sideImage: MultipartBody.Part
    ): BiometricsResponse

    @POST("/generate_workout")
    suspend fun generateWorkout(@Body userData: UserData): WorkoutResponse
}

data class BiometricsResponse(
    val biometrics: Map<String, Float>
)

data class UserData(
    @SerializedName("Age") val age: Int = 0,
    @SerializedName("Gender") val gender: String = "",
    @SerializedName("height_cm") val heightCm: Float = 0f,
    @SerializedName("weight_kg") val weightKg: Float = 0f,
    @SerializedName("Goal") val goal: String = "",
    @SerializedName("level") val level: String = "",
    @SerializedName("BMI") val bmi: Float = 0f,
    @SerializedName("chest_cm") val chestCm: Float = 0f,
    @SerializedName("waist_cm") val waistCm: Float = 0f,
    @SerializedName("hip_cm") val hipCm: Float = 0f,
    @SerializedName("thigh_cm") val thighCm: Float = 0f,
    @SerializedName("bicep_cm") val bicepCm: Float = 0f,
    @SerializedName("ankle") val ankleCm: Float = 0f,
    @SerializedName("arm-length") val armLengthCm: Float = 0f,
    @SerializedName("calf") val calfCm: Float = 0f,
    @SerializedName("forearm") val forearmCm: Float = 0f,
    @SerializedName("leg-length") val legLengthCm: Float = 0f,
    @SerializedName("shoulder-breadth") val shoulderBreadthCm: Float = 0f,
    @SerializedName("shoulder-to-crotch") val shoulderToCrotchCm: Float = 0f,
    @SerializedName("wrist") val wristCm: Float = 0f
)

data class WorkoutResponse(
    @SerializedName("workout_plan") val workoutPlan: Map<String, List<String>>
)
