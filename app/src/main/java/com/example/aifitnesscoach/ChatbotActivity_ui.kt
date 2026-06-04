package com.example.aifitnesscoach

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.example.aifitnesscoach.network.ChatMessage
import com.example.aifitnesscoach.network.ChatRequest
import com.example.aifitnesscoach.network.RetrofitClient_func
import com.example.aifitnesscoach.network.UserData
import com.google.gson.Gson
import kotlinx.coroutines.launch

class ChatbotActivity_ui : AppCompatActivity() {

    private val chatMessages = mutableStateListOf<ChatMessage>()
    private var userData: UserData? = null
    private var isLoading = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadUserData()

        // Welcome message
        if (chatMessages.isEmpty()) {
            chatMessages.add(
                ChatMessage(
                    "assistant",
                    "Hello! I'm Trainium AI, your personal fitness intelligence. I have your workout plan and metrics. How can I help you today?"
                )
            )
        }

        setContent {
            var inputMessageText by remember { mutableStateOf("") }

            TrainiumTheme {
                ChatbotScreen(
                    messages = chatMessages,
                    inputMessageText = inputMessageText,
                    isLoading = isLoading.value,
                    onInputMessageChanged = { inputMessageText = it },
                    onSendMessage = { message ->
                        if (message.trim().isNotEmpty()) {
                            sendMessage(message)
                            inputMessageText = ""
                        }
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun loadUserData() {
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val userDataJson = sharedPrefs.getString("SAVED_USER_METRICS", null)
        if (userDataJson != null) {
            userData = Gson().fromJson(userDataJson, UserData::class.java)
        }
    }

    private fun sendMessage(message: String) {
        chatMessages.add(ChatMessage("user", message))
        isLoading.value = true

        lifecycleScope.launch {
            try {
                val systemContext = constructSystemPrompt()
                val messagesToSend = mutableListOf<ChatMessage>()
                messagesToSend.add(ChatMessage("system", systemContext))
                messagesToSend.addAll(chatMessages)

                val request = ChatRequest(messages = messagesToSend)
                val response = RetrofitClient_func.chatbotApi.chatCompletion(request)

                val aiResponse = response.choices.firstOrNull()?.message?.content
                    ?: "I'm sorry, I couldn't generate a response."
                chatMessages.add(ChatMessage("assistant", aiResponse))

            } catch (e: java.lang.Exception) {
                chatMessages.add(
                    ChatMessage(
                        "assistant",
                        "Error: ${e.message}. Please ensure LM Studio is running."
                    )
                )
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun constructSystemPrompt(): String {
        val sb = StringBuilder("You are an expert AI Fitness Coach. You are helpful, motivating, and knowledgeable.")
        userData?.let {
            sb.append("\nUser Metrics:")
            sb.append("\nAge: ${it.age}, Gender: ${it.gender}")
            sb.append("\nHeight: ${it.heightCm}cm, Weight: ${it.weightKg}kg")
            sb.append("\nGoal: ${it.goal}, Level: ${it.level}")
            sb.append("\nBMI: ${it.bmi}")
        }
        sb.append("\nAnswer the user's questions based on these metrics and general fitness knowledge. Keep answers concise.")
        return sb.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotScreen(
    messages: List<ChatMessage>,
    inputMessageText: String,
    isLoading: Boolean,
    onInputMessageChanged: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Chat List & Chips
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 176.dp) // space for bottom input bar + bottom dock
        ) {
            Spacer(modifier = Modifier.height(72.dp)) // space for TopBar

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
            }

            // Suggestion Chips (only show when not loading)
            if (!isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChipItem(
                        text = "Diet Plan",
                        onClick = { onSendMessage("Suggest a diet plan based on my metrics.") }
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

        // Subpage Header TopAppBar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(72.dp)
                .background(Color.Black.copy(alpha = 0.85f))
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Trainium AI Chat",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(40.dp)) // spacer for centering
            }
        }

        // Bottom Input Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp) // Lift it above the bottom navigation dock!
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.85f))
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputMessageText,
                    onValueChange = onInputMessageChanged,
                    placeholder = { Text("Ask Trainium AI...", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandLime,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color(0xFF111111),
                        unfocusedContainerColor = Color(0xFF111111)
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 3
                )

                if (isLoading) {
                    CircularProgressIndicator(
                        color = BrandLime,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(BrandLime, GradientEnd)
                                )
                            )
                            .clickable { onSendMessage(inputMessageText) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
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
    }
}

@Composable
fun ChatBubble(message: ChatMessage, isUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(BrandLime, GradientEnd)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "AI",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        val bubbleBackground = if (isUser) {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.08f),
                    Color.White.copy(alpha = 0.02f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    BrandLime.copy(alpha = 0.14f),
                    BrandLime.copy(alpha = 0.03f)
                )
            )
        }

        val bubbleBorderColor = if (isUser) {
            Color.White.copy(alpha = 0.08f)
        } else {
            BrandLime.copy(alpha = 0.25f)
        }

        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(bubbleBackground)
                .border(
                    width = 1.dp,
                    color = bubbleBorderColor,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.content ?: "",
                color = Color.White,
                fontSize = 14.sp
            )
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
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
