package com.example.aifitnesscoach

// Data classes to match the structure of your preprocessing_data.json
data class ScalerData(
    val min_: List<Double>,
    val scale_: List<Double>
)

data class EncoderData(
    val classes_: List<String>
)

data class PreprocessingData(
    val scaler: ScalerData,
    val encoder: EncoderData
)