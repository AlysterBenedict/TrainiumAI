package com.example.aifitnesscoach

import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.lifecycleScope
import com.example.aifitnesscoach.ml.ChatbotManagerOnDevice
import com.example.aifitnesscoach.network.ChatMessage
import com.example.aifitnesscoach.network.ChatRequest
import com.example.aifitnesscoach.network.RetrofitClient_func
import com.example.aifitnesscoach.network.UserData
import com.example.aifitnesscoach.network.FirebaseSyncHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.painterResource
import java.io.File
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import com.example.aifitnesscoach.ml.ModelDownloader
import com.example.aifitnesscoach.ml.DownloadState
import com.example.aifitnesscoach.WorkoutProgressHelper
import androidx.compose.animation.core.*


class ChatbotActivity_ui : AppCompatActivity() {

    private var userData: UserData? = null
    private var isLoading = mutableStateOf(false)
    private var generationJob: kotlinx.coroutines.Job? = null
    private val savedChats = mutableStateListOf<SavedChatSession>()
    private var isModelDownloaded by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadUserData()
        loadSavedChatsList()

        val destFile = File(filesDir, "gemma-4-E2B-it.litertlm")
        isModelDownloaded = destFile.exists() && destFile.length() > 2_000_000_000L

        // Welcome message
        if (chatMessages.isEmpty()) {
            chatMessages.add(
                ChatMessage(
                    "assistant",
                    "Hello! I'm Trainium AI, your personal fitness intelligence. Multilingual assistant. I have your workout plan and metrics. How can I help you today?"
                )
            )
        }

        setContent {
            var inputMessageText by remember { mutableStateOf("") }

            TrainiumTheme {
                if (isModelDownloaded) {
                    ChatbotScreen(
                        messages = chatMessages,
                        inputMessageText = inputMessageText,
                        isLoading = isLoading.value,
                        savedChats = savedChats,
                        onInputMessageChanged = { inputMessageText = it },
                        onSendMessageRaw = { message ->
                            if (message.trim().isNotEmpty()) {
                                sendMessage(message)
                                inputMessageText = ""
                            }
                        },
                        onRefreshChat = { refreshChat() },
                        onDeleteSavedChat = { id -> deleteSavedChat(id) },
                        onStopResponse = {
                            stopGeneration()
                        },
                        onBack = { finish() }
                    )
                } else {
                    ModelDownloadScreen(
                        onDownloadFinished = {
                            isModelDownloaded = true
                            initializeOnDeviceChat()
                        },
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
        val destFile = File(filesDir, "gemma-4-E2B-it.litertlm")
        isModelDownloaded = destFile.exists() && destFile.length() > 2_000_000_000L
        if (isModelDownloaded) {
            initializeOnDeviceChat()
        }
    }

    private fun initializeOnDeviceChat() {
        lifecycleScope.launch {
            val manager = ChatbotManagerOnDevice.getInstance(this@ChatbotActivity_ui)
            val shouldShowLoading = !manager.isInitialized
            if (shouldShowLoading) {
                isLoading.value = true
            }
            try {
                val systemContext = constructSystemPrompt()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    manager.initialize(systemContext)
                }
                Log.i("ChatbotActivity", "On-device chatbot initialized successfully.")
            } catch (e: Exception) {
                Log.e("ChatbotActivity", "Error initializing on-device LLM", e)
                val isMissing = manager.isModelFileMissing
                val errorMsg = if (isMissing) {
                    "Offline LLM model not found!\n\nPlease place the `gemma-4-E2B-it.litertlm` file in assets or at `/data/data/com.example.aifitnesscoach/files/gemma-4-E2B-it.litertlm` on your device to enable entirely offline chat."
                } else {
                    "Error loading offline LLM: ${e.message}"
                }
                chatMessages.add(ChatMessage("assistant", errorMsg))
            } finally {
                if (shouldShowLoading) {
                    isLoading.value = false
                }
            }
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val sharedPrefs = getTrainiumPrefs("app_prefs")
            val userDataJson = sharedPrefs.getString("SAVED_USER_METRICS", null)
            if (userDataJson != null) {
                try {
                    val parsed = Gson().fromJson(userDataJson, UserData::class.java)
                    withContext(Dispatchers.Main) {
                        userData = parsed
                    }
                } catch (e: Exception) {
                    Log.e("ChatbotActivity", "Error parsing userDataJson", e)
                }
            }
        }
    }

    private fun sendMessage(message: String) {
        chatMessages.add(ChatMessage("user", message))
        isLoading.value = true

        val manager = ChatbotManagerOnDevice.getInstance(this@ChatbotActivity_ui)
        if (!manager.isInitialized) {
            val isMissing = manager.isModelFileMissing
            val errorMsg = if (isMissing) {
                "Offline LLM model not found!\n\nPlease place the `gemma-4-E2B-it.litertlm` file in assets or at `/data/data/com.example.aifitnesscoach/files/gemma-4-E2B-it.litertlm` on your device to enable entirely offline chat."
            } else {
                "Chatbot is initializing. Please wait a moment..."
            }
            chatMessages.add(ChatMessage("assistant", errorMsg))
            isLoading.value = false
            return
        }

        // Add placeholder message for assistant's streaming response
        val assistantMessageIndex = chatMessages.size
        chatMessages.add(ChatMessage("assistant", "Thinking..."))

        generationJob = lifecycleScope.launch {
            try {
                var responseContent = ""
                manager.sendMessageStream(message).collect { chunk ->
                    if (responseContent.isEmpty() || responseContent == "Thinking...") {
                        responseContent = chunk
                    } else {
                        responseContent += chunk
                    }
                    chatMessages[assistantMessageIndex] = ChatMessage("assistant", responseContent)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.i("ChatbotActivity", "Generation was cancelled by the user.")
                } else {
                    Log.e("ChatbotActivity", "Error during on-device generation", e)
                    val errorMsg = if (e.message?.contains("Conversation is not alive", ignoreCase = true) == true) {
                        "open a new chat."
                    } else {
                        "Error: ${e.message}"
                    }
                    chatMessages[assistantMessageIndex] = ChatMessage("assistant", errorMsg)
                }
            } finally {
                isLoading.value = false
                generationJob = null
            }
        }
    }

    private fun stopGeneration() {
        generationJob?.cancel()
        isLoading.value = false
        generationJob = null
        initializeOnDeviceChat()
    }

    private suspend fun constructSystemPrompt(): String = withContext(Dispatchers.IO) {
        val context = this@ChatbotActivity_ui
        val userData = FirebaseSyncHelper.getGlobalUserData(context)
        val userStats = FirebaseSyncHelper.getUserStats(context)
        val goalWeight = FirebaseSyncHelper.getGoalWeight(context)
        val weightLogs = FirebaseSyncHelper.getWeights(context)
        val workoutLogs = FirebaseSyncHelper.getWorkouts(context)

        val currentUser = FirebaseAuth.getInstance().currentUser
        val userName = currentUser?.displayName ?: "User"

        val sb = StringBuilder()
        sb.append("You are an expert AI Fitness Coach named Trainium AI. You must act ONLY as a fitness coach and discuss ONLY fitness-related topics such as workouts, diet, nutrition, exercise form, and physical health. If the user asks about any unrelated topics (e.g., history, math, coding, politics, general chat outside health), politely but firmly refuse to answer, explaining that you can only assist with fitness and nutrition queries.")
        sb.append("\nYour responses must be extremely efficient, straight to the point, and concise to save tokens and time. Do not use verbose introductions, pleasantries, or beat around the bush.")
        sb.append("\nFormat your output beautifully using standard markdown. Use double asterisks `**bold text**` for emphasis, headings, and highlighting metrics so they display correctly in bold style.")
        sb.append("\nUser Name: $userName. Address the user by their name naturally when appropriate.")
        sb.append("\n\nTrainium App Features Guide (answer user queries on how to use them):")
        sb.append("\n- AI Body Scan: estimates 15+ biometrics from frontal/side silhouette views (offline).")
        sb.append("\n- HUD Pose Tracking (AI Trainer Mode): front camera uses MediaPipe to track 50+ exercises in real-time, counts reps, shows HUD overlays (green/red), and speaks form cues.")
        sb.append("\n- Workout Generation: creates progressive 30-day schedules with deload cycles.")
        sb.append("\n- 3 Modes: AI Trainer (camera + voiceHUD), Self (timer + manual log), Tutorial (exercise demo videos).")
        sb.append("\n- Dashboard: tracks consistency streaks, daily calorie/time targets, weight logs.")
        sb.append("\n\nMultilingual Support:")
        sb.append("\n- You support multiple languages, including English, Kannada, Hindi, Tamil, Telugu, Malayalam, and others.")
        sb.append("\n- If the user requests assistance in a specific language (e.g., 'assist me in Kannada', 'Assist me in Hindi'), you MUST reply, coach, and interact entirely in that language, translating fitness concepts accurately while keeping the responses concise and focused only on fitness.")

        sb.append("\n\nUser Profile Metrics:")
        sb.append("\n- Age: ${userData.age}")
        sb.append("\n- Gender: ${userData.gender}")
        sb.append("\n- Height: ${userData.heightCm} cm")
        sb.append("\n- Current Weight: ${userData.weightKg} kg")
        sb.append("\n- BMI: ${String.format(java.util.Locale.US, "%.1f", userData.bmi)}")
        sb.append("\n- Fitness Goal: ${userData.goal}")
        sb.append("\n- Experience Level: ${userData.level}")

        sb.append("\n\nBody Circumference Measurements (cm):")
        sb.append("\n- Chest: ${userData.chestCm}")
        sb.append("\n- Waist: ${userData.waistCm}")
        sb.append("\n- Hip: ${userData.hipCm}")
        sb.append("\n- Thigh: ${userData.thighCm}")
        sb.append("\n- Bicep: ${userData.bicepCm}")
        sb.append("\n- Ankle: ${userData.ankleCm}")
        sb.append("\n- Arm Length: ${userData.armLengthCm}")
        sb.append("\n- Calf: ${userData.calfCm}")
        sb.append("\n- Forearm: ${userData.forearmCm}")
        sb.append("\n- Leg Length: ${userData.legLengthCm}")
        sb.append("\n- Shoulder Breadth: ${userData.shoulderBreadthCm}")
        sb.append("\n- Shoulder to Crotch: ${userData.shoulderToCrotchCm}")
        sb.append("\n- Wrist: ${userData.wristCm}")

        sb.append("\n\nGoal Weight: $goalWeight kg")

        sb.append("\n\nUser Activity Stats:")
        sb.append("\n- Total Workouts Completed: ${userStats.workoutsCount}")
        sb.append("\n- Total Calories Burned: ${userStats.caloriesCount} kcal")
        sb.append("\n- Total Active Duration: ${userStats.durationMinutes} minutes")
        sb.append("\n- Current Streak: ${userStats.currentStreak} days")
        sb.append("\n- Best Streak: ${userStats.bestStreak} days")
        sb.append("\n- Daily Calorie Burn Goal: ${userStats.dailyCalorieGoal} kcal")
        sb.append("\n- Daily Active Time Goal: ${userStats.dailyTimeGoalMinutes} minutes")
        sb.append("\n- Today's Workouts Completed: ${userStats.todayWorkoutsCount}")
        sb.append("\n- Today's Calories Burned: ${userStats.todayCalories} kcal")
        sb.append("\n- Today's Active Duration: ${userStats.todayMinutes} minutes")

        if (weightLogs.isNotEmpty()) {
            sb.append("\n\nWeight Log History (Last 5):")
            weightLogs.sortedByDescending { it.timestamp }.take(5).forEach { log ->
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(log.timestamp))
                sb.append("\n- $date: ${log.weight} kg")
            }
        }

        if (workoutLogs.isNotEmpty()) {
            sb.append("\n\nRecent Workouts Logged (Last 5):")
            workoutLogs.sortedByDescending { it.timestamp }.take(5).forEach { log ->
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(log.timestamp))
                sb.append("\n- $date: ${log.workoutName} (${log.durationSeconds / 60}m, ${log.caloriesBurned} kcal)")
            }
        }

        // Active workout plan metrics (if any)
        val appPrefs = context.getTrainiumPrefs("app_prefs")
        val planJson = appPrefs.getString("SAVED_WORKOUT_PLAN", null)
        val planMap: Map<String, List<String>> = if (planJson != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, List<String>>>() {}.type
                Gson().fromJson(planJson, type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }

        if (planMap.isNotEmpty()) {
            val activeDay = WorkoutProgressHelper.getActiveDay(context, planMap)
            val activeDayNum = WorkoutProgressHelper.getDayNumber(activeDay)
            val exercisesList = planMap[activeDay] ?: emptyList()
            val totalExercises = exercisesList.size
            val completedExercisesCount = exercisesList.indices.count {
                WorkoutProgressHelper.isExerciseCompleted(context, activeDay, it)
            }
            sb.append("\n\nActive 30-Day Workout Plan Progress:")
            sb.append("\n- Current Day: Day $activeDayNum ($activeDay)")
            sb.append("\n- Today's Scheduled Exercises: ${exercisesList.joinToString(", ")}")
            sb.append("\n- Today's Completed Exercises: $completedExercisesCount out of $totalExercises")
        }

        sb.toString()
    }

    private fun getSavedChats(): List<SavedChatSession> {
        val prefs = getTrainiumPrefs("saved_chats_prefs")
        val json = prefs.getString("SAVED_CHATS_LIST", null)
        return if (json != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<SavedChatSession>>() {}.type
                Gson().fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    private fun saveChats(chats: List<SavedChatSession>) {
        val prefs = getTrainiumPrefs("saved_chats_prefs")
        prefs.edit().putString("SAVED_CHATS_LIST", Gson().toJson(chats)).apply()
    }

    private suspend fun saveCurrentChatSession() = withContext(Dispatchers.IO) {
        val userMsgs = chatMessages.filter { it.role == "user" }
        if (userMsgs.isEmpty()) return@withContext // Don't save empty chats

        val firstUserQuery = userMsgs.first().content ?: ""
        val title = if (firstUserQuery.length > 25) {
            firstUserQuery.take(22) + "..."
        } else {
            firstUserQuery
        }

        val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy h:mm a", java.util.Locale.US).format(java.util.Date())
        val displayTitle = if (title.isNotBlank()) "\"$title\" ($dateStr)" else "Chat ($dateStr)"

        val messagesList = chatMessages.toList()
        val newSession = SavedChatSession(
            id = activeSessionId,
            timestamp = System.currentTimeMillis(),
            title = displayTitle,
            messages = messagesList
        )

        val existingChats = getSavedChats().toMutableList()
        val index = existingChats.indexOfFirst { it.id == activeSessionId }
        if (index != -1) {
            existingChats[index] = newSession
        } else {
            existingChats.add(0, newSession) // Add to top
        }
        saveChats(existingChats)
    }

    private fun refreshChat() {
        val hasUserMessages = chatMessages.any { it.role == "user" }
        lifecycleScope.launch {
            if (hasUserMessages) {
                saveCurrentChatSession()
                loadSavedChatsList() // Reload list for history dialog
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatbotActivity_ui, "Current chat saved locally!", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatbotActivity_ui, "Chat is already empty.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                chatMessages.clear()
                chatMessages.add(
                    ChatMessage(
                        "assistant",
                        "Hello! I'm Trainium AI, your personal fitness intelligence. Multilingual assistant. I have your workout plan and metrics. How can I help you today?"
                    )
                )
                activeSessionId = "chat_" + System.currentTimeMillis()
                initializeOnDeviceChat()
            }
        }
    }

    private fun deleteSavedChat(id: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val updated = getSavedChats().filter { it.id != id }
            saveChats(updated)
            withContext(Dispatchers.Main) {
                savedChats.clear()
                savedChats.addAll(updated)
                Toast.makeText(this@ChatbotActivity_ui, "Chat deleted.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSavedChatsList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val chats = getSavedChats()
            withContext(Dispatchers.Main) {
                savedChats.clear()
                savedChats.addAll(chats)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch {
            saveCurrentChatSession()
        }
    }

    companion object {
        val chatMessages = mutableStateListOf<ChatMessage>()
        var activeSessionId: String = "chat_" + System.currentTimeMillis()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotScreen(
    messages: List<ChatMessage>,
    inputMessageText: String,
    isLoading: Boolean,
    savedChats: List<SavedChatSession>,
    onInputMessageChanged: (String) -> Unit,
    onSendMessageRaw: (String) -> Unit,
    onRefreshChat: () -> Unit,
    onDeleteSavedChat: (String) -> Unit,
    onStopResponse: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val onSendMessage: (String) -> Unit = { message ->
        onSendMessageRaw(message)
        keyboardController?.hide()
    }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var selectedSessionForViewing by remember { mutableStateOf<SavedChatSession?>(null) }
    val scope = rememberCoroutineScope()
    val showScrollToBottomButton by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) {
                false
            } else {
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                if (lastVisibleItem == null) {
                    false
                } else {
                    val isLastItemVisible = lastVisibleItem.index == totalItems - 1
                    listState.firstVisibleItemIndex > 0 && !isLastItemVisible
                }
            }
        }
    }

    val context = LocalContext.current
    var todayWorkoutsCount by remember { mutableStateOf(0) }
    var activeDayNum by remember { mutableStateOf(0) }
    var userGoal by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val stats = FirebaseSyncHelper.getUserStats(context)
            val uData = FirebaseSyncHelper.getGlobalUserData(context)
            val planJson = context.getTrainiumPrefs("app_prefs").getString("SAVED_WORKOUT_PLAN", null)
            val planMap: Map<String, List<String>> = if (planJson != null) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<Map<String, List<String>>>() {}.type
                    Gson().fromJson(planJson, type) ?: emptyMap()
                } catch (e: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }
            val actDay = if (planMap.isNotEmpty()) WorkoutProgressHelper.getActiveDay(context, planMap) else ""
            val actDayN = if (actDay.isNotEmpty()) WorkoutProgressHelper.getDayNumber(actDay) else 0

            withContext(Dispatchers.Main) {
                todayWorkoutsCount = stats.todayWorkoutsCount
                activeDayNum = actDayN
                userGoal = uData.goal
            }
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Chat List & Chips
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 176.dp) // space for bottom input bar + bottom dock
        ) {
            Spacer(modifier = Modifier.height(96.dp)) // space for TopBar

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(messages) { msg ->
                    if (msg.role != "system") {
                        val isUser = msg.role == "user"
                        ChatBubble(message = msg, isUser = isUser)
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }

            // Suggestion Chips (only show when not loading)
            if (!isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Permanent Language Pill (First, emoji removed)
                    LanguagePill(
                        onLanguageSelected = { lang ->
                            onSendMessage("Assist me in $lang")
                        },
                        onChooseLanguageSelected = {
                            onInputMessageChanged("assist me in ")
                        }
                    )

                    if (todayWorkoutsCount > 0) {
                        SuggestionChipItem(
                            text = "Cooldown Stretch",
                            onClick = { onSendMessage("Suggest a 5-minute cooldown stretch routine for my post-workout recovery.") }
                        )
                        SuggestionChipItem(
                            text = "Post-Workout Meal",
                            onClick = { onSendMessage("What should I eat right now for optimal post-workout recovery based on my metrics?") }
                        )
                        SuggestionChipItem(
                            text = "Analyze Performance",
                            onClick = { onSendMessage("I completed a workout today. Can you analyze my progress and give feedback?") }
                        )
                    } else if (activeDayNum > 0) {
                        SuggestionChipItem(
                            text = "Today's Workout Tips",
                            onClick = { onSendMessage("Give me some specific tips and form guidance for Day $activeDayNum exercises.") }
                        )
                        SuggestionChipItem(
                            text = "Diet Plan",
                            onClick = {
                                onSendMessage(
                                    "Suggest a daily diet plan based on my metrics and goal. For each meal (Breakfast, Lunch, Dinner, Snacks), include details (Quantity, Calories, Time Window, Focus, Dishes) formatted as a list."
                                )
                            }
                        )
                        SuggestionChipItem(
                            text = "Motivation",
                            onClick = { onSendMessage("Give me a motivational quote for my workout.") }
                        )
                    } else {
                        SuggestionChipItem(
                            text = "Diet Plan",
                            onClick = {
                                onSendMessage(
                                    "Suggest a daily diet plan based on my metrics and goal. For each meal (Breakfast, Lunch, Dinner, Snacks), include details (Quantity, Calories, Time Window, Focus, Dishes) formatted as a list."
                                )
                            }
                        )
                        SuggestionChipItem(
                            text = "Motivation",
                            onClick = { onSendMessage("Give me a motivational quote for my workout.") }
                        )
                        SuggestionChipItem(
                            text = "Form Tips",
                            onClick = { onSendMessage("What are some general tips to improve workout form?") }
                        )
                    }
                }
            }
        }

        // Subpage Header TopAppBar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(96.dp)
                .background(BackgroundBlack.copy(alpha = 0.85f))
                .border(width = 1.dp, color = CardOverlayColor.copy(alpha = 0.05f))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CardOverlayColor.copy(alpha = 0.05f))
                        .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), CircleShape)
                        .bounceClick { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Trainium AI Chat",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "on device",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CardOverlayColor.copy(alpha = 0.05f))
                            .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), CircleShape)
                            .bounceClick { onRefreshChat() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "New Chat",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CardOverlayColor.copy(alpha = 0.05f))
                            .border(1.dp, CardOverlayColor.copy(alpha = 0.1f), CircleShape)
                            .bounceClick { showHistoryDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Saved Chats",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Bottom Input Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 104.dp) // Lift it above the bottom navigation dock!
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            SurfaceLow.copy(alpha = 0.97f),
                            SurfaceLow.copy(alpha = 0.93f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            CardOverlayColor.copy(alpha = 0.18f),
                            CardOverlayColor.copy(alpha = 0.04f)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = inputMessageText,
                    onValueChange = onInputMessageChanged,
                    placeholder = { Text("Ask Trainium AI...", color = TextSecondary, fontSize = 14.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputMessageText.trim().isNotEmpty()) {
                                onSendMessage(inputMessageText)
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AccentRed)
                            .bounceClick { onStopResponse() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color.White, shape = RoundedCornerShape(2.dp))
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(BrandLime, GradientEnd)
                                )
                            )
                            .bounceClick {
                                if (inputMessageText.trim().isNotEmpty()) {
                                    onSendMessage(inputMessageText)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = BackgroundBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Floating Glass Bottom Navigation Dock
        val context = LocalContext.current
        TrainiumBottomDock(
            activeTab = "coach",
            onTabSelected = { tab ->
                navigateToTab(context, tab)
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (showScrollToBottomButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 240.dp, end = 24.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF111111).copy(alpha = 0.9f))
                    .border(1.dp, CardOverlayColor.copy(alpha = 0.15f), CircleShape)
                    .bounceClick {
                        scope.launch {
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.size)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Scroll to bottom",
                    tint = BrandLime,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (showHistoryDialog) {
            SavedChatsDialog(
                savedChats = savedChats,
                onDismissRequest = { showHistoryDialog = false },
                onViewSession = { session ->
                    selectedSessionForViewing = session
                },
                onDeleteSession = { id ->
                    onDeleteSavedChat(id)
                }
            )
        }

        selectedSessionForViewing?.let { session ->
            ChatDetailsDialog(
                session = session,
                onDismissRequest = { selectedSessionForViewing = null }
            )
        }
    }
}

@Composable
fun ThinkingBubbleDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dotAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(0)
        ),
        label = "dotAlpha1"
    )
    val dotAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(200)
        ),
        label = "dotAlpha2"
    )
    val dotAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(400)
        ),
        label = "dotAlpha3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer(alpha = dotAlpha1)
                .background(BrandLime, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer(alpha = dotAlpha2)
                .background(BrandLime, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer(alpha = dotAlpha3)
                .background(BrandLime, CircleShape)
        )
    }
}

@Composable
fun AssistantAvatar(isThinking: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatarGlow")

    val haloScale by if (isThinking) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "haloScale"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    val haloAlpha by if (isThinking) {
        infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "haloAlpha"
        )
    } else {
        remember { mutableStateOf(0.0f) }
    }

    Box(contentAlignment = Alignment.Center) {
        // Glowing Halo
        if (isThinking || haloAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer(
                        scaleX = haloScale,
                        scaleY = haloScale,
                        alpha = haloAlpha
                    )
                    .background(BrandLime.copy(alpha = 0.4f), CircleShape)
            )
        }

        // Main Avatar Card
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(
                    width = 1.dp,
                    color = if (isThinking) BrandLime else CardOverlayColor.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.google_logo),
                contentDescription = "AI",
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

data class DietMeal(
    val category: String, // Breakfast, Lunch, Dinner, Snacks
    val details: String
)

fun parseDietPlan(content: String): List<DietMeal>? {
    val categories = listOf("breakfast", "lunch", "dinner", "snacks", "snack")
    val meals = mutableListOf<DietMeal>()

    val lowerContent = content.lowercase()
    if (!lowerContent.contains("breakfast") && !lowerContent.contains("lunch") && !lowerContent.contains("dinner")) {
        return null
    }

    val lines = content.split("\n")
    
    // Check if there is a markdown table
    val tableRows = lines.filter { it.trim().startsWith("|") && it.trim().endsWith("|") && it.contains("|") }
    if (tableRows.size >= 3) {
        val headerParts = tableRows.first().split("|").map { it.trim() }.filter { it.isNotEmpty() }
        val isSeparator = tableRows[1].replace("|", "").replace(":", "").replace("-", "").trim().isEmpty()
        val startIndex = if (isSeparator) 2 else 1
        
        for (i in startIndex until tableRows.size) {
            val rawParts = tableRows[i].split("|").map { it.trim() }
            val rowParts = if (rawParts.size > 2) rawParts.subList(1, rawParts.size - 1) else emptyList()
            if (rowParts.isNotEmpty()) {
                val category = rowParts.first().replace("**", "").replace("*", "").trim()
                val isCategoryValid = categories.any { category.lowercase().contains(it) }
                if (isCategoryValid) {
                    val detailsBuilder = StringBuilder()
                    for (colIdx in 1 until rowParts.size) {
                        if (colIdx < headerParts.size) {
                            val header = headerParts[colIdx]
                            val valText = rowParts[colIdx].trim()
                            if (valText.isNotEmpty() && valText != "-") {
                                detailsBuilder.append("**$header**: $valText\n")
                            }
                        } else {
                            val valText = rowParts[colIdx].trim()
                            if (valText.isNotEmpty() && valText != "-") {
                                detailsBuilder.append("$valText\n")
                            }
                        }
                    }
                    meals.add(DietMeal(category.replaceFirstChar { it.uppercase() }, detailsBuilder.toString().trim()))
                }
            }
        }
        if (meals.isNotEmpty()) {
            return meals
        }
    }

    // Fallback to list parsing
    var currentCategory = ""
    var currentDetails = java.lang.StringBuilder()

    for (line in lines) {
        val cleanLine = line.trim()
        if (cleanLine.isEmpty()) continue

        var foundCategory = ""
        for (cat in categories) {
            val pattern1 = "**${cat}**"
            val pattern2 = "${cat}:"
            if (cleanLine.lowercase().startsWith(pattern1) || 
                (cleanLine.lowercase().startsWith(cat) && cleanLine.contains(":")) ||
                cleanLine.lowercase().startsWith("- **${cat}**") ||
                cleanLine.lowercase().startsWith("### ${cat}") ||
                (cleanLine.lowercase().startsWith("- ${cat}") && cleanLine.contains(":"))
            ) {
                foundCategory = cat.replaceFirstChar { it.uppercase() }
                if (foundCategory == "Snack") foundCategory = "Snacks"
                break
            }
        }

        if (foundCategory.isNotEmpty()) {
            if (currentCategory.isNotEmpty() && currentDetails.isNotEmpty()) {
                meals.add(DietMeal(currentCategory, currentDetails.toString().trim()))
                currentDetails = java.lang.StringBuilder()
            }
            currentCategory = foundCategory
            
            val colonIndex = cleanLine.indexOf(":")
            if (colonIndex != -1 && colonIndex < cleanLine.length - 1) {
                var afterColon = cleanLine.substring(colonIndex + 1).trim()
                afterColon = afterColon.replace("**", "").replace("*", "").trim()
                if (afterColon.isNotEmpty()) {
                    currentDetails.append(afterColon).append("\n")
                }
            }
        } else {
            if (currentCategory.isNotEmpty()) {
                var cleanDetailsLine = cleanLine
                if (cleanDetailsLine.startsWith("-") || cleanDetailsLine.startsWith("*")) {
                    cleanDetailsLine = cleanDetailsLine.substring(1).trim()
                }
                cleanDetailsLine = cleanDetailsLine.replace("**", "").replace("*", "").trim()
                currentDetails.append(cleanDetailsLine).append("\n")
            }
        }
    }

    if (currentCategory.isNotEmpty() && currentDetails.isNotEmpty()) {
        meals.add(DietMeal(currentCategory, currentDetails.toString().trim()))
    }

    return if (meals.isNotEmpty()) meals else null
}

@Composable
fun InteractiveDietPlanCard(meals: List<DietMeal>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Restaurant,
                contentDescription = null,
                tint = BrandLime,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Daily Diet Plan",
                color = BrandLime,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(color = CardOverlayColor.copy(alpha = 0.08f), thickness = 1.dp)

        meals.forEach { meal ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = CardOverlayColor.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = meal.category,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = parseMarkdown(meal.details),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, isUser: Boolean) {
    val scale = remember { androidx.compose.animation.core.Animatable(0.9f) }
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(
                scaleX = scale.value,
                scaleY = scale.value,
                alpha = alpha.value
            ),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            AssistantAvatar(isThinking = (message.content == "Thinking..."))
            Spacer(modifier = Modifier.width(8.dp))
        }

        val backgroundBrush = Brush.verticalGradient(
            colors = listOf(
                SurfaceLow.copy(alpha = 0.97f),
                SurfaceLow.copy(alpha = 0.93f)
            )
        )
        val borderBrush = Brush.verticalGradient(
            colors = listOf(
                CardOverlayColor.copy(alpha = 0.18f),
                CardOverlayColor.copy(alpha = 0.04f)
            )
        )

        val parsedDietPlan = remember(message.content) {
            message.content?.let { parseDietPlan(it) }
        }

        Box(
            modifier = Modifier
                .then(if (isUser) Modifier.widthIn(max = 280.dp) else Modifier.weight(1f))
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundBrush)
                .border(
                    width = 1.dp,
                    brush = borderBrush,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            if (message.content == "Thinking...") {
                ThinkingBubbleDots()
            } else if (parsedDietPlan != null) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = parseMarkdown(message.content ?: ""),
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    InteractiveDietPlanCard(meals = parsedDietPlan)
                }
            } else {
                Text(
                    text = parseMarkdown(message.content ?: ""),
                    color = TextPrimary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

fun parseMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        val parts = text.split("**")
        for (i in parts.indices) {
            if (i % 2 == 1 && i < parts.size - 1) {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                append(parts[i])
                pop()
            } else if (i % 2 == 1 && i == parts.size - 1) {
                append("**")
                append(parts[i])
            } else {
                append(parts[i])
            }
        }
    }
}

@Composable
fun SuggestionChipItem(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardOverlayColor.copy(alpha = 0.04f))
            .border(1.dp, CardOverlayColor.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .bounceClick { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun LanguagePill(
    onLanguageSelected: (String) -> Unit,
    onChooseLanguageSelected: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SuggestionChipItem(
            text = "Languages",
            onClick = { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFF111111))
                .border(1.dp, CardOverlayColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
        ) {
            val languages = listOf("Kannada", "Hindi", "Tamil", "Telugu", "Malayalam")
            languages.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang, color = TextPrimary) },
                    onClick = {
                        expanded = false
                        onLanguageSelected(lang)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Choose your language :", color = BrandLime) },
                onClick = {
                    expanded = false
                    onChooseLanguageSelected()
                }
            )
        }
    }
}

data class SavedChatSession(
    val id: String,
    val timestamp: Long,
    val title: String,
    val messages: List<ChatMessage>
)

@Composable
fun SavedChatsDialog(
    savedChats: List<SavedChatSession>,
    onDismissRequest: () -> Unit,
    onViewSession: (SavedChatSession) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF111111),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Saved Chats History",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            if (savedChats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saved chats yet.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(savedChats) { session ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardOverlayColor.copy(alpha = 0.03f))
                                .border(1.dp, CardOverlayColor.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .clickable { onViewSession(session) }
                                .padding(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.title,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy h:mm a", java.util.Locale.US).format(java.util.Date(session.timestamp))
                                Text(
                                    text = dateStr,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(
                                onClick = { onDeleteSession(session.id) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = AccentRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close", color = BrandLime, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun ChatDetailsDialog(
    session: SavedChatSession,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF0C0C0C),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Chat History Details",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 2
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(session.messages) { msg ->
                    if (msg.role != "system") {
                        val isUser = msg.role == "user"
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                        ) {
                            Text(
                                text = if (isUser) "You" else "Trainium AI",
                                color = if (isUser) BrandLime else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 240.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isUser) 12.dp else 2.dp,
                                            bottomEnd = if (isUser) 2.dp else 12.dp
                                        )
                                    )
                                    .background(if (isUser) CardOverlayColor.copy(alpha = 0.05f) else BrandLime.copy(alpha = 0.05f))
                                    .border(1.dp, if (isUser) CardOverlayColor.copy(alpha = 0.08f) else BrandLime.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = parseMarkdown(msg.content ?: ""),
                                    color = TextPrimary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Back to History", color = BrandLime, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun ModelDownloadScreen(
    onDownloadFinished: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val downloader = remember { ModelDownloader.getInstance(context) }
    val downloadState by downloader.downloadState.collectAsState()

    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Success) {
            onDownloadFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BrandLime.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 96.dp), // Prevent overlap with bottom dock
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(CardOverlayColor.copy(alpha = 0.05f))
                    .border(1.dp, BrandLime.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.aifitnesscoach.R.drawable.google_logo),
                    contentDescription = "Google Logo",
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Activate Trainium AI",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "On-Device Personal Fitness Intelligence",
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardOverlayColor.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Model Engine", color = TextSecondary, fontSize = 13.sp)
                        Text("Google LiteRT-LM (Gemma 4 E2B)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Download Size", color = TextSecondary, fontSize = 13.sp)
                        Text("2.41 GB", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Privacy Protection", color = TextSecondary, fontSize = 13.sp)
                        Text("100% Offline & Private", color = BrandLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            when (val state = downloadState) {
                is DownloadState.Idle -> {
                    Text(
                        text = "A one-time download is required to run the AI model offline on your device.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                is DownloadState.Downloading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = state.progress,
                            color = BrandLime,
                            trackColor = Color(0xFF222222),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val percent = (state.progress * 100).toInt()
                            Text(
                                text = "$percent% completed",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val mbDownloaded = state.downloadedBytes / (1024 * 1024)
                            val mbTotal = state.totalBytes / (1024 * 1024)
                            Text(
                                text = "$mbDownloaded MB / $mbTotal MB",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "Speed: %.1f MB/s", state.speedMbSec),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            val etaText = if (state.etaSeconds > 0) {
                                val mins = state.etaSeconds / 60
                                val secs = state.etaSeconds % 60
                                if (mins > 0) "${mins}m ${secs}s remaining" else "${secs}s remaining"
                            } else {
                                "Calculating remaining time..."
                            }
                            Text(
                                text = etaText,
                                color = BrandLime,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                is DownloadState.Paused -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = state.progress,
                            color = BrandLime,
                            trackColor = Color(0xFF222222),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val percent = (state.progress * 100).toInt()
                            Text(
                                text = "$percent% completed (Paused)",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val mbDownloaded = state.downloadedBytes / (1024 * 1024)
                            val mbTotal = state.totalBytes / (1024 * 1024)
                            Text(
                                text = "$mbDownloaded MB / $mbTotal MB",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                is DownloadState.Verifying -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            color = BrandLime,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Verifying model integrity...",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is DownloadState.Success -> {
                    Text(
                        text = "Activation complete! Loading chatbot...",
                        color = BrandLime,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                is DownloadState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Download Failed",
                            color = AccentRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.message,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val isDownloading = downloadState is DownloadState.Downloading
            val isPaused = downloadState is DownloadState.Paused
            val isError = downloadState is DownloadState.Error
            val isIdle = downloadState is DownloadState.Idle
            val isVerifying = downloadState is DownloadState.Verifying

            if (!isVerifying && downloadState !is DownloadState.Success) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Main Action Button (Left)
                    Button(
                        onClick = {
                            when {
                                isIdle || isError -> downloader.startDownload()
                                isDownloading -> downloader.pauseDownload()
                                isPaused -> downloader.startDownload()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandLime,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isDownloading -> Icons.Default.Pause
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = "Button Icon",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isDownloading -> "Pause"
                                    isPaused -> "Resume"
                                    isError -> "Retry"
                                    else -> "Download"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Cancel button (Right)
                    if (isDownloading || isPaused) {
                        OutlinedButton(
                            onClick = { downloader.cancelDownload() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextPrimary
                            ),
                            border = BorderStroke(1.dp, CardOverlayColor.copy(alpha = 0.12f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Floating Glass Bottom Navigation Dock
        TrainiumBottomDock(
            activeTab = "coach",
            onTabSelected = { tab ->
                navigateToTab(context, tab)
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
