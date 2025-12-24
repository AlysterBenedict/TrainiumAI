package com.example.aifitnesscoach.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

interface LMApiService {
    @POST("/v1/chat/completions")
    suspend fun chatCompletion(@Body request: ChatRequest): ChatResponse
}

data class ChatRequest(
    val model: String = "local-model", // Uses whatever is loaded in LM Studio
    val messages: List<ChatMessage>,
    val temperature: Float = 0.7f,
    val max_tokens: Int = -1, // -1 usually means "infinite" or model context limit in LM Studio
    val stream: Boolean = false
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<ChatChoice>
)

data class ChatChoice(
    val message: ChatMessage
)
