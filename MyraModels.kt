package com.myra.ai.network

data class GroundingSource(
    val title: String,
    val uri: String
)

data class ChatHistoryItem(
    val role: String, // "user" or "model"
    val content: String
)

data class MyraChatRequest(
    val message: String,
    val history: List<ChatHistoryItem> = emptyList(),
    val language: String = "hindi", // "hindi", "english", "hinglish"
    val personality: String = "jarvis",
    val userName: String = "Sir",
    val customInstructions: String = "",
    val memories: List<String> = emptyList(),
    val useSearch: Boolean = true
)

data class MyraChatResponse(
    val text: String,
    val sources: List<GroundingSource> = emptyList(),
    val searchQueries: List<String> = emptyList(),
    val model: String = "gemini-3.8-flash"
)
