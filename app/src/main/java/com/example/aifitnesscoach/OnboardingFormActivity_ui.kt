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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import com.example.aifitnesscoach.ml.WorkoutGeneratorOnDevice
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
    private var showSuccessOverlay = mutableStateOf(false)

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
                Box(modifier = Modifier.fillMaxSize()) {
                    OnboardingFormScreen(
                        frontalImageUri = frontalImageUri,
                        sideImageUri = sideImageUri,
                        isLoading = isLoading.value,
                        onSubmit = { age, gender, goal, difficulty ->
                            generateWorkoutPlan(age, gender, goal, difficulty)
                        }
                    )

                    if (showSuccessOverlay.value) {
                        SuccessOverlay(message = "Personalized 30-day workout plan generated!")
                    }
                }
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
            chestCm = chest, waistCm = waist, hipCm = hip, thighCm = thigh, bicepCm = bicep,
            ankleCm = biometricsData?.get("ankle") ?: 0f,
            armLengthCm = biometricsData?.get("arm-length") ?: 0f,
            calfCm = biometricsData?.get("calf") ?: 0f,
            forearmCm = biometricsData?.get("forearm") ?: 0f,
            legLengthCm = biometricsData?.get("leg-length") ?: 0f,
            shoulderBreadthCm = biometricsData?.get("shoulder-breadth") ?: 0f,
            shoulderToCrotchCm = biometricsData?.get("shoulder-to-crotch") ?: 0f,
            wristCm = biometricsData?.get("wrist") ?: 0f
        )

        lifecycleScope.launch {
            try {
                val workoutPlan = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val generator = WorkoutGeneratorOnDevice.getInstance(this@OnboardingFormActivity_ui)
                    generator.generateWorkout(userData)
                }
                
                // Hide loader and show success overlay
                isLoading.value = false
                showSuccessOverlay.value = true

                val sharedPrefs = getTrainiumPrefs("app_prefs")
                sharedPrefs.edit()
                    .putString("SAVED_USER_METRICS", Gson().toJson(userData))
                    .apply()

                // Log the scanned weight into weight tracking history for Reports > Weight tab
                com.example.aifitnesscoach.network.FirebaseSyncHelper.addWeight(
                    this@OnboardingFormActivity_ui,
                    weight
                )

                // Sync profile and workout plan to Firebase Firestore
                com.example.aifitnesscoach.network.FirebaseSyncHelper.syncProfileToFirebase(this@OnboardingFormActivity_ui, userData)
                val sanitizedJson = com.example.aifitnesscoach.network.FirebaseSyncHelper.sanitizeAndSaveWorkoutPlan(this@OnboardingFormActivity_ui, workoutPlan)

                // Delay 1.5 seconds for the success overlay screen to show
                kotlinx.coroutines.delay(1500)
                showSuccessOverlay.value = false

                val intent = Intent(this@OnboardingFormActivity_ui, WorkoutPlanActivity_ui::class.java).apply {
                    putExtra("WORKOUT_PLAN", sanitizedJson)
                }
                startActivity(intent)
                finishAffinity()

            } catch (e: Exception) {
                isLoading.value = false
                Log.e("OnboardingFormActivity_ui", "Error generating workout plan locally", e)
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
            .background(BackgroundBlack)
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
                color = TextPrimary,
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
                        .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
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
                        .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
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
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = BrandLime,
                    unfocusedBorderColor = CardOverlayColor.copy(alpha = 0.12f),
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
                        Text("Male", color = TextPrimary)
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
                        Text("Female", color = TextPrimary)
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
                        .border(1.dp, CardOverlayColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .clickable { isGoalDropdownExpanded = true }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(selectedGoal, color = TextPrimary)
                    DropdownMenu(
                        expanded = isGoalDropdownExpanded,
                        onDismissRequest = { isGoalDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .background(Color(0xFF111111))
                            .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    ) {
                        goalsList.forEach { goalOption ->
                            DropdownMenuItem(
                                text = { Text(goalOption, color = TextPrimary) },
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
                        .border(1.dp, CardOverlayColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .clickable { isDifficultyDropdownExpanded = true }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(selectedDifficulty, color = TextPrimary)
                    DropdownMenu(
                        expanded = isDifficultyDropdownExpanded,
                        onDismissRequest = { isDifficultyDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .background(Color(0xFF111111))
                            .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    ) {
                        difficultyList.forEach { diffOption ->
                            DropdownMenuItem(
                                text = { Text(diffOption, color = TextPrimary) },
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
                        colors = listOf(Color.Transparent, BackgroundBlack.copy(alpha = 0.8f))
                    )
                )
                .padding(24.dp)
        ) {
            TrainiumButton(
                text = "GENERATE WORKOUT PLAN",
                onClick = { onSubmit(age, selectedGender, selectedGoal, selectedDifficulty) }
            )
        }

        // Full-screen Transformer processing loading animation overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundBlack),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    TransformerProcessingAnimation()

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "Generating your 30-day personalized workout plan using Transformer...",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Analyzing biometrics & local intelligence on-device",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun TransformerProcessingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "orbit")

    // Rotate animation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Counter rotate animation
    val counterRotationAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counter_rotation"
    )

    // Pulse animation for center
    val centerScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(140.dp)
    ) {
        // Outer glowing ring rotating clockwise
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(rotationZ = rotationAngle)
                .border(2.dp, Brush.sweepGradient(listOf(BrandLime, Color.Transparent, BrandLime)), CircleShape)
        )

        // Middle ring rotating counter-clockwise
        Box(
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer(rotationZ = counterRotationAngle)
                .border(1.5.dp, Brush.sweepGradient(listOf(CardOverlayColor.copy(alpha = 0.4f), Color.Transparent, BrandLime.copy(alpha = 0.3f))), CircleShape)
        )

        // Pulsing glowing core
        Box(
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer(scaleX = centerScale, scaleY = centerScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandLime, BrandLime.copy(alpha = 0.3f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(BrandLime)
            )
        }
    }
}

@Composable
private fun SuccessOverlay(message: String) {
    var scale by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        androidx.compose.animation.core.animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            )
        ) { value, _ ->
            scale = value
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale
                )
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF121212))
                .border(1.dp, CardOverlayColor.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
                .padding(horizontal = 32.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Checkmark Circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(BrandLime.copy(alpha = 0.15f))
                    .border(2.dp, BrandLime, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    tint = BrandLime,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = message,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
