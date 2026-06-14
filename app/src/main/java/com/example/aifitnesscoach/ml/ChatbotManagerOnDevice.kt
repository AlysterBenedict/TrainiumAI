package com.example.aifitnesscoach.ml

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

class ChatbotManagerOnDevice private constructor(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    @Volatile
    var isInitialized = false
        private set

    @Volatile
    var isModelFileMissing = false
        private set

    /**
     * Initializes the LiteRT-LM engine.
     * Copies the model file from assets if it does not exist in internal storage.
     */
    suspend fun initialize(systemInstruction: String) = withContext(Dispatchers.IO) {
        if (isInitialized) {
            updateSystemInstruction(systemInstruction)
            return@withContext
        }

        val destFile = File(context.filesDir, "gemma-4-E2B-it.litertlm")
        val modelExists = destFile.exists() && destFile.length() > 2_000_000_000L

        if (!modelExists) {
            isModelFileMissing = true
            throw FileNotFoundException("Model file gemma-4-E2B-it.litertlm is missing from internal storage.")
        }

        val modelPath = destFile.absolutePath

        try {
            Log.i(TAG, "Initializing LiteRT-LM Engine...")
            val engineConfig = EngineConfig(modelPath = modelPath)
            val newEngine = Engine(engineConfig)
            newEngine.initialize()

            Log.i(TAG, "Creating LiteRT-LM Conversation...")
            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemInstruction)
            )
            val newConversation = newEngine.createConversation(conversationConfig)

            engine = newEngine
            conversation = newConversation
            isInitialized = true
            Log.i(TAG, "On-device Chatbot initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LiteRT-LM Engine", e)
            throw e
        }
    }

    /**
     * Updates the system instruction of the conversation by creating a new conversation
     * using the already initialized engine.
     */
    suspend fun updateSystemInstruction(systemInstruction: String) = withContext(Dispatchers.IO) {
        val activeEngine = engine
        if (activeEngine != null) {
            try {
                conversation?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing previous conversation", e)
            }
            try {
                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(systemInstruction)
                )
                conversation = activeEngine.createConversation(conversationConfig)
                Log.i(TAG, "Conversation system instruction updated successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update conversation system instruction", e)
            }
        }
    }

    /**
     * Sends a message to the active on-device conversation and returns a stream Flow of response text chunks.
     */
    fun sendMessageStream(message: String): Flow<String> {
        val activeConversation = conversation ?: throw IllegalStateException("Conversation is not initialized.")
        return activeConversation.sendMessageAsync(message)
            .map { it.toString() }
            .flowOn(Dispatchers.Default)
    }

    fun close() {
        try {
            conversation?.close()
            engine?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LiteRT-LM resources", e)
        } finally {
            conversation = null
            engine = null
            isInitialized = false
        }
    }

    companion object {
        private const val TAG = "ChatbotManagerOnDevice"

        @Volatile
        private var INSTANCE: ChatbotManagerOnDevice? = null

        fun getInstance(context: Context): ChatbotManagerOnDevice {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatbotManagerOnDevice(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
