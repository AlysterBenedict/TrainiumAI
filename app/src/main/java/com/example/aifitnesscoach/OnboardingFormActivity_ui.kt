package com.example.aifitnesscoach

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import com.example.aifitnesscoach.network.RetrofitClient_func
import com.example.aifitnesscoach.network.UserData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

class OnboardingFormActivity_ui : AppCompatActivity() {

    private var biometricsData: Map<String, Float>? = null
    private var frontalImageUri: Uri? = null
    private var sideImageUri: Uri? = null
    private var isLoading = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve extras
        val biometricsJson = intent.getStringExtra("BIOMETRICS_DATA")
        if (biometricsJson != null) {
            val type = object : TypeToken<Map<String, Float>>() {}.type
            biometricsData = Gson().fromJson(biometricsJson, type)
        }

        val frontalUriStr = intent.getStringExtra("FRONTAL_IMAGE_URI")
        val sideUriStr = intent.getStringExtra("SIDE_IMAGE_URI")
        if (frontalUriStr != null) frontalImageUri = Uri.parse(frontalUriStr)
        if (sideUriStr != null) sideImageUri = Uri.parse(sideUriStr)

        setContent {
            TrainiumTheme {
                OnboardingFormScreen(
                    frontalImageUri = frontalImageUri,
                    sideImageUri = sideImageUri,
                    isLoading = isLoading.value,
                    onSubmit = { age, gender, goal, difficulty ->
                        generateWorkoutPlan(age, gender, goal, difficulty)
                    }
                )
            }
        }
    }

    private fun generateWorkoutPlan(ageStr: String, gender: String, goal: String, difficulty: String) {
        if (ageStr.trim().isEmpty()) {
            Toast.makeText(this, "Please enter your age", Toast.LENGTH_SHORT).show()
            return
        }
        if (biometricsData == null) {
            Toast.makeText(this, "Biometric data is missing.", Toast.LENGTH_SHORT).show()
            return
        }

        val age = ageStr.toIntOrNull()
        if (age == null) {
            Toast.makeText(this, "Invalid age entered", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading.value = true

        val height = biometricsData?.get("height_cm") ?: 0f
        val weight = biometricsData?.get("weight_kg") ?: 0f

        if (height == 0f || weight == 0f) {
            Toast.makeText(this, "Could not determine height and weight from image.", Toast.LENGTH_LONG).show()
            isLoading.value = false
            return
        }

        val chest = biometricsData?.get("chest") ?: 0f
        val waist = biometricsData?.get("waist") ?: 0f
        val hip = biometricsData?.get("hip") ?: 0f
        val thigh = biometricsData?.get("thigh") ?: 0f
        val bicep = biometricsData?.get("bicep") ?: 0f
        val bmi = (weight / ((height / 100) * (height / 100)))

        val userData = UserData(
            age = age, gender = gender, heightCm = height, weightKg = weight,
            goal = goal, level = difficulty, bmi = bmi,
            chestCm = chest, waistCm = waist, hipCm = hip, thighCm = thigh, bicepCm = bicep
        )

        lifecycleScope.launch {
            try {
                val response = RetrofitClient_func.fitnessApi.generateWorkout(userData)
                isLoading.value = false

                val workoutPlanJson = Gson().toJson(response.workoutPlan)
                val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                sharedPrefs.edit()
                    .putString("SAVED_WORKOUT_PLAN", workoutPlanJson)
                    .putString("SAVED_USER_METRICS", Gson().toJson(userData))
                    .apply()

                val intent = Intent(this@OnboardingFormActivity_ui, WorkoutPlanActivity_ui::class.java).apply {
                    putExtra("WORKOUT_PLAN", workoutPlanJson)
                }
                startActivity(intent)
                finishAffinity()

            } catch (e: Exception) {
                isLoading.value = false
                Log.e("OnboardingFormActivity_ui", "Error generating workout plan", e)
                Toast.makeText(this@OnboardingFormActivity_ui, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingFormScreen(
    frontalImageUri: Uri?,
    sideImageUri: Uri?,
    isLoading: Boolean,
    onSubmit: (age: String, gender: String, goal: String, difficulty: String) -> Unit
) {
    val scrollState = rememberScrollState()

    var age by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("Male") }
    
    val goalsList = stringArrayResource(id = R.array.fitness_goals)
    var selectedGoal by remember { mutableStateOf(goalsList.firstOrNull() ?: "") }
    var isGoalDropdownExpanded by remember { mutableStateOf(false) }

    val difficultyList = stringArrayResource(id = R.array.fitness_levels)
    var selectedDifficulty by remember { mutableStateOf(difficultyList.firstOrNull() ?: "") }
    var isDifficultyDropdownExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp)
                .padding(bottom = 80.dp), // space for floating progress/button
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Personalize Your Plan",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                text = "We've analyzed your biometrics. Please complete your profile parameters.",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Frontal and Side Image Previews
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111111))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (frontalImageUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(frontalImageUri),
                            contentDescription = "Frontal View",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("Frontal", color = TextSecondary)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111111))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (sideImageUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(sideImageUri),
                            contentDescription = "Side View",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("Side", color = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Age Input
            OutlinedTextField(
                value = age,
                onValueChange = { age = it },
                label = { Text("Age", color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = BrandLime,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    focusedContainerColor = Color(0xFF111111),
                    unfocusedContainerColor = Color(0xFF111111)
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Gender Select
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Gender", color = TextSecondary, fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectedGender = "Male" }
                    ) {
                        RadioButton(
                            selected = selectedGender == "Male",
                            onClick = { selectedGender = "Male" },
                            colors = RadioButtonDefaults.colors(selectedColor = BrandLime)
                        )
                        Text("Male", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(32.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectedGender = "Female" }
                    ) {
                        RadioButton(
                            selected = selectedGender == "Female",
                            onClick = { selectedGender = "Female" },
                            colors = RadioButtonDefaults.colors(selectedColor = BrandLime)
                        )
                        Text("Female", color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Goal Dropdown
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Goal", color = TextSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF111111))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .clickable { isGoalDropdownExpanded = true }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(selectedGoal, color = Color.White)
                    DropdownMenu(
                        expanded = isGoalDropdownExpanded,
                        onDismissRequest = { isGoalDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .background(Color(0xFF111111))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    ) {
                        goalsList.forEach { goalOption ->
                            DropdownMenuItem(
                                text = { Text(goalOption, color = Color.White) },
                                onClick = {
                                    selectedGoal = goalOption
                                    isGoalDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Difficulty Dropdown
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Fitness Level", color = TextSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF111111))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .clickable { isDifficultyDropdownExpanded = true }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(selectedDifficulty, color = Color.White)
                    DropdownMenu(
                        expanded = isDifficultyDropdownExpanded,
                        onDismissRequest = { isDifficultyDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .background(Color(0xFF111111))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    ) {
                        difficultyList.forEach { diffOption ->
                            DropdownMenuItem(
                                text = { Text(diffOption, color = Color.White) },
                                onClick = {
                                    selectedDifficulty = diffOption
                                    isDifficultyDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Button / Loader Overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
                .padding(24.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = BrandLime,
                    modifier = Modifier.align(Alignment.Center).size(36.dp)
                )
            } else {
                TrainiumButton(
                    text = "GENERATE WORKOUT PLAN",
                    onClick = { onSubmit(age, selectedGender, selectedGoal, selectedDifficulty) }
                )
            }
        }
    }
}
