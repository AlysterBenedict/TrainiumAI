package com.example.aifitnesscoach

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifitnesscoach.databinding.ActivityChatbotBinding
import com.example.aifitnesscoach.network.ChatChoice
import com.example.aifitnesscoach.network.ChatMessage
import com.example.aifitnesscoach.network.ChatRequest
import com.example.aifitnesscoach.network.LMRetrofitClient
import com.example.aifitnesscoach.network.UserData
import com.google.gson.Gson
import kotlinx.coroutines.launch

class ChatbotActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatbotBinding
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private var userData: UserData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatbotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadUserData()
        setupRecyclerView()
        setupListeners()

        // Initial greeting
        addMessage(ChatMessage("assistant", "Hello! I'm Tranium AI, your personal fitness intelligence. I have your workout plan and metrics. How can I help you today?"))
    }

    private fun loadUserData() {
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val userDataJson = sharedPrefs.getString("SAVED_USER_METRICS", null)
        if (userDataJson != null) {
            userData = Gson().fromJson(userDataJson, UserData::class.java)
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(chatMessages)
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatbotActivity)
            adapter = chatAdapter
        }
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener { finish() }

        binding.sendButton.setOnClickListener {
            val messageText = binding.messageEditText.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
                binding.messageEditText.text.clear()
            }
        }

        binding.chipDiet.setOnClickListener { sendMessage("Suggest a diet plan based on my metrics.") }
        binding.chipMotivation.setOnClickListener { sendMessage("Give me a motivational quote for my workout.") }
        binding.chipForm.setOnClickListener { sendMessage("What are some general tips to improve workout form?") }
    }

    private fun sendMessage(message: String) {
        addMessage(ChatMessage("user", message))
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val systemContext = constructSystemPrompt()
                val messagesToSend = mutableListOf<ChatMessage>()
                messagesToSend.add(ChatMessage("system", systemContext))
                messagesToSend.addAll(chatMessages) // Add history

                val request = ChatRequest(messages = messagesToSend)
                val response = LMRetrofitClient.instance.chatCompletion(request)

                val aiResponse = response.choices.firstOrNull()?.message?.content ?: "I'm sorry, I couldn't generate a response."
                addMessage(ChatMessage("assistant", aiResponse))

            } catch (e: Exception) {
                addMessage(ChatMessage("assistant", "Error: ${e.message}. Please ensure LM Studio is running."))
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        chatMessages.add(message)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        binding.chatRecyclerView.scrollToPosition(chatMessages.size - 1)
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

    // Inner Adapter Class for simplicity
    inner class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

        inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val messageText: TextView = itemView.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return ChatViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val message = messages[position]
            holder.messageText.text = message.content
            
            val params = holder.messageText.layoutParams as ViewGroup.MarginLayoutParams
            
            if (message.role == "user") {
                holder.messageText.setBackgroundResource(R.drawable.bg_message_user)
                holder.messageText.setTextColor(holder.itemView.context.getColor(android.R.color.black))
                holder.messageText.textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                params.marginStart = 100
                params.marginEnd = 0
            } else {
                holder.messageText.setBackgroundResource(R.drawable.bg_message_ai)
                holder.messageText.setTextColor(holder.itemView.context.getColor(R.color.gold))
                holder.messageText.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                params.marginStart = 0
                params.marginEnd = 100
            }
            holder.messageText.layoutParams = params
        }

        override fun getItemCount() = messages.size
    }
}
