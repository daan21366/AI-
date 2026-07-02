package com.aicompanion.nativeapp.network

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

class SseClient {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    sealed class SseEvent {
        data class Delta(val content: String) : SseEvent()
        data object Done : SseEvent()
        data class Error(val message: String) : SseEvent()
    }

    fun streamChat(
        baseUrl: String,
        apiKey: String,
        request: ChatRequest
    ): Flow<SseEvent> = callbackFlow {
        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        val jsonBody = gson.toJson(request)

        val httpRequest = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        var doneSent = false

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    if (!doneSent) {
                        doneSent = true
                        trySend(SseEvent.Done)
                    }
                    return
                }
                try {
                    val json = gson.fromJson(data, Map::class.java)
                    val choices = json["choices"] as? List<*>
                    if (choices != null && choices.isNotEmpty()) {
                        val choice = choices[0] as? Map<*, *>
                        val delta = choice?.get("delta") as? Map<*, *>
                        val content = delta?.get("content") as? String
                        if (!content.isNullOrEmpty()) {
                            trySend(SseEvent.Delta(content))
                        }
                    }
                } catch (_: Exception) { }
            }

            override fun onClosed(eventSource: EventSource) {
                if (!doneSent) {
                    doneSent = true
                    trySend(SseEvent.Done)
                }
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val errorBody = try { response?.body?.string() } catch (_: Exception) { null }
                val msg = t?.message
                    ?: errorBody
                    ?: response?.message
                    ?: "连接失败，请检查网络和 API Key"
                trySend(SseEvent.Error(msg))
                close()
            }
        }

        val eventSource = EventSources.createFactory(client)
            .newEventSource(httpRequest, listener)

        awaitClose {
            eventSource.cancel()
        }
    }

    suspend fun testConnection(baseUrl: String, apiKey: String, model: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val testReq = ChatRequest(
                    model = model,
                    messages = listOf(ApiMessage("user", "ping")),
                    stream = false,
                    max_tokens = 1
                )
                val url = "${baseUrl.trimEnd('/')}/chat/completions"
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(gson.toJson(testReq).toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { it.isSuccessful }
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ApiMessage>,
        maxTokens: Int = 256,
        temperature: Double = 0.7
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = ChatRequest(
                    model = model,
                    messages = messages,
                    stream = false,
                    max_tokens = maxTokens,
                    temperature = temperature
                )
                val url = "${baseUrl.trimEnd('/')}/chat/completions"
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(gson.toJson(request).toRequestBody("application/json".toMediaType()))
                    .build()
                val response = client.newCall(req).execute()
                val body = response.body?.string() ?: return@withContext null
                if (!response.isSuccessful) return@withContext null
                val json = gson.fromJson(body, Map::class.java)
                val choices = json["choices"] as? List<*>
                val choice = choices?.firstOrNull() as? Map<*, *>
                val message = choice?.get("message") as? Map<*, *>
                message?.get("content") as? String
            } catch (_: Exception) {
                null
            }
        }
    }
}
