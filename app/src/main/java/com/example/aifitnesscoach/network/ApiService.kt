package com.example.aifitnesscoach.network

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
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
    @SerializedName("Age") val age: Int,
    @SerializedName("Gender") val gender: String,
    @SerializedName("height_cm") val heightCm: Float,
    @SerializedName("weight_kg") val weightKg: Float,
    @SerializedName("Goal") val goal: String,
    @SerializedName("level") val level: String,
    @SerializedName("BMI") val bmi: Float,
    @SerializedName("chest_cm") val chestCm: Float,
    @SerializedName("waist_cm") val waistCm: Float,
    @SerializedName("hip_cm") val hipCm: Float,
    @SerializedName("thigh_cm") val thighCm: Float,
    @SerializedName("bicep_cm") val bicepCm: Float
)

data class WorkoutResponse(
    @SerializedName("workout_plan") val workoutPlan: Map<String, List<String>>
)