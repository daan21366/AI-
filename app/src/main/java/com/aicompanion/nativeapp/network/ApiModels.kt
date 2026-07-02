package com.aicompanion.nativeapp.network

data class ApiMessage(val role: String, val content: String)

data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = true,
    val max_tokens: Int = 2000,
    val temperature: Double = 0.7
)
