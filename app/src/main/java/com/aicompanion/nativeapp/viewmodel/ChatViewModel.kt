package com.aicompanion.nativeapp.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicompanion.nativeapp.data.db.AppDatabase
import com.aicompanion.nativeapp.data.model.MessageEntity
import com.aicompanion.nativeapp.data.model.PersonaEntity
import com.aicompanion.nativeapp.data.repository.ChatRepository
import com.aicompanion.nativeapp.network.ProviderPresets
import com.aicompanion.nativeapp.network.SseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val activePersona: PersonaEntity? = null,
    val personas: List<PersonaEntity> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingContent: String = "",
    val inputText: String = "",
    val error: String? = null,
    // Provider settings
    val activeProvider: String = "deepseek",
    val apiKey: String = "",
    val model: String = "deepseek-chat",
    val baseUrl: String = "https://api.deepseek.com/v1",
    // Personality response setting
    val personalityResponseEnabled: Boolean = false,
    // Proactive chat setting
    val proactiveChatEnabled: Boolean = false,
    // Track last user activity time to avoid interrupting
    val lastUserActivityTime: Long = System.currentTimeMillis()
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repo = ChatRepository(db)
    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var proactiveTimerJob: Job? = null
    private var messageObserverJob: Job? = null
    private var consecutiveProactiveMisses = 0   // 用户没回应的连续主动消息数

    init {
        loadSettings()
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            repo.getAllPersonas().collect { personas ->
                val activeId = prefs.getString("active_persona_id", null)
                    ?: personas.firstOrNull()?.id
                val activePersona = if (activeId != null) {
                    personas.find { it.id == activeId } ?: personas.firstOrNull()
                } else null

                val prevActiveId = _uiState.value.activePersona?.id
                _uiState.update { it.copy(personas = personas, activePersona = activePersona) }

                // Only re-observe messages when the active persona changes
                if (activeId != null && activeId != prevActiveId) {
                    observeMessages(activeId)
                }

                // 只有开关打开且有活跃角色时才运行定时器，避免空转
                if (activePersona != null && prefs.getBoolean("proactive_chat_enabled", false)) {
                    startProactiveTimer()
                } else {
                    stopProactiveTimer()
                }
            }
        }
    }

    private fun observeMessages(personaId: String) {
        messageObserverJob?.cancel()
        messageObserverJob = viewModelScope.launch {
            repo.getMessages(personaId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    private fun loadSettings() {
        val providerKey = prefs.getString("active_provider", "deepseek") ?: "deepseek"
        val config = ProviderPresets.getProvider(providerKey)
        val apiKey = prefs.getString("api_key_$providerKey", "") ?: ""
        val proactiveChatEnabled = prefs.getBoolean("proactive_chat_enabled", false)

        _uiState.update {
            it.copy(
                activeProvider = providerKey,
                apiKey = apiKey,
                model = if (providerKey == "custom") (prefs.getString("custom_model", "") ?: "").ifBlank { "gpt-4o-mini" } else config.defaultModel,
                baseUrl = if (providerKey == "custom") (prefs.getString("custom_base_url", "") ?: "") else config.baseUrl,
                personalityResponseEnabled = true,
                proactiveChatEnabled = proactiveChatEnabled
            )
        }

        // Listen for settings changes
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            when {
                key == "active_provider" || key?.startsWith("api_key_") == true || key == "custom_base_url" || key == "custom_model" -> {
                    val pk = prefs.getString("active_provider", "deepseek") ?: "deepseek"
                    val cfg = ProviderPresets.getProvider(pk)
                    val ak = prefs.getString("api_key_$pk", "") ?: ""
                    val customUrl = prefs.getString("custom_base_url", "") ?: ""
                    val customModel = prefs.getString("custom_model", "") ?: ""
                    _uiState.update {
                        it.copy(
                            activeProvider = pk,
                            apiKey = ak,
                            model = if (pk == "custom") customModel.ifBlank { "gpt-4o-mini" } else cfg.defaultModel,
                            baseUrl = if (pk == "custom") customUrl else cfg.baseUrl
                        )
                    }
                }
                key == "proactive_chat_enabled" -> {
                    val enabled = prefs.getBoolean("proactive_chat_enabled", false)
                    _uiState.update { it.copy(proactiveChatEnabled = enabled) }
                    if (enabled) startProactiveTimer() else stopProactiveTimer()
                }
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text, error = null) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isEmpty() || state.isStreaming) return

        val persona = state.activePersona ?: return
        val personaId = persona.id

        if (state.apiKey.isBlank() || state.baseUrl.isBlank()) {
            _uiState.update { it.copy(error = "请先在设置页面配置 API Key") }
            return
        }

        _uiState.update { it.copy(inputText = "", isStreaming = true, streamingContent = "", error = null, lastUserActivityTime = System.currentTimeMillis()) }
        consecutiveProactiveMisses = 0 // 用户发消息了，重置miss计数

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Save user message
                repo.sendMessage(personaId, text, "user")

                // Get recent messages for context
                val recentMsgs = repo.getRecentMessages(personaId, 40)

                // Stream response
                val fullResponse = StringBuilder()
                var displayedResponse = ""
                var isNoReply = false
                var isDuplicateStreaming = false  // 流式阶段已判定为重复
                repo.streamResponseWithMessages(
                    baseUrl = state.baseUrl,
                    apiKey = state.apiKey,
                    model = state.model,
                    personaDoc = persona.personaDoc,
                    coreMemory = persona.coreMemory,
                    messages = recentMsgs.reversed().takeLast(40),
                    personalityResponseEnabled = state.personalityResponseEnabled
                ).collect { event ->
                    when (event) {
                        is SseClient.SseEvent.Delta -> {
                            fullResponse.append(event.content)
                            val cleaned = stripParentheses(fullResponse.toString()).trim()

                            // 检查 [[不回复]]
                            if (cleaned.startsWith("[[不回复]]")) {
                                isNoReply = true
                                _uiState.update { it.copy(streamingContent = "") }
                            }

                            if (!isNoReply) {
                                // 去重检测：检查完整内容是否和历史消息重复
                                // 用全量比对（只在长度足够时检查），防止碎片误判
                                val isDuplicate = cleaned.length >= 3 &&
                                    _uiState.value.messages.any { msg ->
                                        msg.role == "assistant" && cleaned == msg.content.trim()
                                    }

                                if (isDuplicate) {
                                    // 重复了 → 清空屏幕，不再显示
                                    isDuplicateStreaming = true
                                    isNoReply = true
                                    _uiState.update { it.copy(streamingContent = "") }
                                } else {
                                    // 安全内容 → 只显示比上次多的新部分，避免闪烁
                                    if (cleaned.length > displayedResponse.length) {
                                        displayedResponse = cleaned
                                        _uiState.update { it.copy(streamingContent = cleaned) }
                                    }
                                }
                            }
                        }
                        is SseClient.SseEvent.Done -> {
                            val rawResponse = fullResponse.toString().trim()
                            val cleanResponse = stripParentheses(rawResponse)
                            val finalResponse = cleanResponse.trim()

                            // Done 阶段的去重（兜底，防止流式阶段没匹配到完全一致的内容）
                            val anyDuplicate = _uiState.value.messages.any {
                                it.role == "assistant" && finalResponse == it.content.trim()
                            }

                            if (finalResponse.startsWith("[[不回复]]") || (isNoReply && !isDuplicateStreaming)) {
                                // AI 主动选择不回复
                                repo.sendMessage(personaId, "🤐 对方沉默中...", "system")
                            } else if (anyDuplicate || isDuplicateStreaming) {
                                // 重复回复
                                repo.sendMessage(personaId, "🤐 对方沉默了...", "system")
                            } else if (cleanResponse.isNotEmpty()) {
                                // 支持两种分隔语法：
                                // 1. |||N秒|||  — 等 N 秒后发下一条（如 |||1秒|||）
                                // 2. |||        — 等默认 2 秒后发下一条
                                // 例如："好的 |||1秒||| 嘿 |||1秒||| 还在吗"
                                val timedSegments = parseTimedSegments(cleanResponse)
                                if (timedSegments.size > 1) {
                                    for ((msg, delayMs) in timedSegments) {
                                        repo.sendMessage(personaId, msg, "assistant")
                                        if (delayMs > 0) delay(delayMs)
                                    }
                                } else {
                                    // 单条消息
                                    repo.sendMessage(personaId, cleanResponse, "assistant")
                                }
                            }
                            _uiState.update { it.copy(isStreaming = false, streamingContent = "") }

                            // ── 记忆自动提取：每 10 轮触发 ──
                            val personaForExtraction = _uiState.value.activePersona
                            if (personaForExtraction != null && personaForExtraction.convCount > 0 && personaForExtraction.convCount % 10 == 0) {
                                launch(Dispatchers.IO) {
                                    try {
                                        val recentMsgs = repo.getRecentMessages(personaId, 50)
                                        val newMemory = repo.extractCoreMemory(
                                            baseUrl = state.baseUrl,
                                            apiKey = state.apiKey,
                                            model = state.model,
                                            personaName = personaForExtraction.name,
                                            recentMessages = recentMsgs
                                        )
                                        if (newMemory != null) {
                                            repo.updateCoreMemory(personaId, newMemory)
                                        }
                                        val newProfile = repo.extractUserProfile(
                                            baseUrl = state.baseUrl,
                                            apiKey = state.apiKey,
                                            model = state.model,
                                            recentMessages = recentMsgs
                                        )
                                        if (newProfile != null) {
                                            repo.updateUserProfile(personaId, newProfile)
                                        }
                                    } catch (_: Exception) { }
                                }
                            }

                            // ── 每次回复完成后，立即尝试一次主动消息 ──
                            // 直接读 prefs，不依赖可能漏掉的 listener
                            if (prefs.getBoolean("proactive_chat_enabled", false)) {
                                launch(Dispatchers.IO) {
                                    delay(3_000L)
                                    tryProactiveMessage()
                                }
                            }
                        }
                        is SseClient.SseEvent.Error -> {
                            _uiState.update {
                                it.copy(
                                    isStreaming = false,
                                    streamingContent = "",
                                    error = event.message
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingContent = "",
                        error = e.message ?: "发送失败"
                    )
                }
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch { repo.deleteMessage(messageId) }
    }

    fun createPersona(name: String, personaDoc: String = "") {
        viewModelScope.launch {
            val persona = repo.createPersona(name, personaDoc)
            prefs.edit().putString("active_persona_id", persona.id).apply()
        }
    }

    fun switchPersona(personaId: String) {
        viewModelScope.launch {
            repo.switchActivePersona(personaId)
            prefs.edit().putString("active_persona_id", personaId).apply()
            observeMessages(personaId)
        }
    }

    fun deletePersona(personaId: String) {
        viewModelScope.launch {
            val persona = _uiState.value.personas.find { it.id == personaId } ?: return@launch
            repo.deletePersona(persona)
            if (_uiState.value.activePersona?.id == personaId) {
                val next = _uiState.value.personas.firstOrNull { it.id != personaId }
                if (next != null) {
                    switchPersona(next.id)
                } else {
                    _uiState.update { it.copy(activePersona = null, messages = emptyList()) }
                }
            }
        }
    }

    fun updatePersonaDoc(doc: String) {
        val persona = _uiState.value.activePersona ?: return
        viewModelScope.launch { repo.updatePersonaDoc(persona.id, doc) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun updateProviderConfig(providerKey: String, apiKey: String, model: String, baseUrl: String) {
        prefs.edit().apply {
            putString("active_provider", providerKey)
            putString("api_key_$providerKey", apiKey)
            apply()
        }
        _uiState.update {
            it.copy(activeProvider = providerKey, apiKey = apiKey, model = model, baseUrl = baseUrl)
        }
    }

    // ──────────────────────────────────────────────
    // Proactive Chat Timer
    // ──────────────────────────────────────────────

    private fun startProactiveTimer() {
        if (proactiveTimerJob?.isActive == true) {
            Log.d("ProactiveChat", "Timer already active, skipping")
            return
        }
        stopProactiveTimer()
        Log.d("ProactiveChat", "Starting proactive timer")
        proactiveTimerJob = viewModelScope.launch {
            delay(3_000L)
            Log.d("ProactiveChat", "Timer initial delay done, entering loop")
            while (isActive) {
                try {
                    tryProactiveMessage()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e // 不吞 CancellationException，让协程正常结束
                } catch (e: Exception) {
                    Log.e("ProactiveChat", "Error in tryProactiveMessage: ${e.message}")
                }
                // 如果开关已关，直接退出循环
                if (!prefs.getBoolean("proactive_chat_enabled", false)) {
                    Log.d("ProactiveChat", "Switch was turned off, stopping timer")
                    break
                }
                // 如果用户一直没回，主动消息连续落空，就逐步拉长检查间隔
                val baseInterval = when {
                    consecutiveProactiveMisses >= 5 -> 120_000L  // 连续5次没回 → 2分钟后再试
                    consecutiveProactiveMisses >= 3 -> 60_000L   // 连续3次没回 → 1分钟后再试
                    else -> 15_000L                              // 正常 → 15秒
                }
                val jitter = Random.nextLong(0L, 10_001L)
                val interval = baseInterval + jitter
                Log.d("ProactiveChat", "Next check in ${interval / 1000}s (misses=$consecutiveProactiveMisses)")
                delay(interval)
            }
        }
    }

    private fun stopProactiveTimer() {
        Log.d("ProactiveChat", "Stopping proactive timer")
        proactiveTimerJob?.cancel()
        proactiveTimerJob = null
    }

    private suspend fun tryProactiveMessage() {
        val state = _uiState.value
        // 直接从 SharedPreferences 读取开关状态，不依赖可能漏掉的 listener
        val actuallyEnabled = prefs.getBoolean("proactive_chat_enabled", false)
        Log.d("ProactiveChat", "tryProactiveMessage: uiState=${state.proactiveChatEnabled}, prefs=$actuallyEnabled, persona=${state.activePersona?.name}")

        if (!actuallyEnabled) {
            Log.d("ProactiveChat", "-> blocked: proactiveChatEnabled=false")
            return
        }
        val persona = state.activePersona ?: run {
            Log.d("ProactiveChat", "-> blocked: no active persona")
            return
        }
        if (state.apiKey.isBlank() || state.baseUrl.isBlank()) {
            Log.d("ProactiveChat", "-> blocked: API key or base URL is blank")
            return
        }

        val timeSinceLastActivity = System.currentTimeMillis() - state.lastUserActivityTime
        Log.d("ProactiveChat", "-> timeSinceLastActivity=${timeSinceLastActivity / 1000}s (need >= 3s)")
        if (timeSinceLastActivity < 3_000L) {
            Log.d("ProactiveChat", "-> blocked: user active extremely recently")
            return
        }

        val recentProactive = state.messages.any { msg ->
            msg.role == "assistant" &&
            msg.content.startsWith("🟢 ") &&
            (System.currentTimeMillis() - msg.timestamp) < 60_000L
        }
        // 新格式（无前缀）的主动消息：30秒内不重复
        val lastAssistant = state.messages.lastOrNull { it.role == "assistant" && !it.content.startsWith("🤐") }
        val veryRecentProactive = lastAssistant != null &&
            (System.currentTimeMillis() - lastAssistant.timestamp) < 30_000L
        if (recentProactive || veryRecentProactive) {
            Log.d("ProactiveChat", "-> blocked: recent proactive message exists")
            return
        }

        Log.d("ProactiveChat", "-> All checks passed, calling generateProactiveMessage")
        val recentMsgs = repo.getRecentMessages(persona.id, 20)
        val result = repo.generateProactiveMessage(
            baseUrl = state.baseUrl,
            apiKey = state.apiKey,
            model = state.model,
            personaDoc = persona.personaDoc,
            personalityResponseEnabled = state.personalityResponseEnabled,
            recentMessages = recentMsgs
        )

        if (result != null) {
            Log.d("ProactiveChat", "-> Generated message: $result")
            val cleanResult = stripParentheses(result)
            if (cleanResult.isNotBlank()) {
                repo.sendMessage(persona.id, cleanResult, "assistant")
                Log.d("ProactiveChat", "-> Proactive message saved")
                consecutiveProactiveMisses = 0  // 主动发了，重置计数
            } else {
                Log.d("ProactiveChat", "-> Skipped: all content was parentheses")
                consecutiveProactiveMisses++
            }
        } else {
            Log.d("ProactiveChat", "-> AI chose not to send (null/[[不主动]])")
            consecutiveProactiveMisses++
        }

        // 如果最后一条消息是 AI 发的（用户没回），增加 miss 计数
        val lastMsg = _uiState.value.messages.lastOrNull { !it.content.startsWith("🤐") }
        if (lastMsg != null && lastMsg.role == "assistant") {
            consecutiveProactiveMisses++
            Log.d("ProactiveChat", "-> User didn't reply, misses=$consecutiveProactiveMisses")
        }
    }

    /**
     * 清洗 AI 回复中的括号内容（中文括号、英文括号、以及括号内的所有文字）。
     * 例如：（哼）才不要 → 才不要
     *       才不要（扭头） → 才不要
     *       （笑）你好（犹豫） → 你好
     * 递归执行直到没有括号剩余。
     */
    private fun stripParentheses(text: String): String {
        var result = text
        while (true) {
            val cleaned = result
                .replace(Regex("\\([^()（）]*\\)"), "")
                .replace(Regex("（[^()（）]*）"), "")
                .trim()
            if (cleaned == result) break
            result = cleaned
        }
        return result
    }

    /**
     * 解析带时间的分隔消息。
     * 输入： "好的 |||1秒||| 嘿 |||2秒||| 还在吗"
     * 输出： [("好的",0), ("嘿",1000), ("还在吗",2000)]
     *
     * 支持： |||N秒||| 、 |||N秒|||
     * 无时间标注的 ||| 默认间隔 2000ms
     */
    private fun parseTimedSegments(text: String): List<Pair<String, Long>> {
        // 先按 |||数字秒||| 切分
        val pattern = Regex("""\|\|\|(\d+)\s*秒?\s*\|\|\|""")
        val rawParts = pattern.split(text).map { it.trim() }.filter { it.isNotEmpty() }
        val delays = pattern.findAll(text).map { it.groupValues[1].toLong() * 1000 }.toList()

        // 如果没有带时间的分隔符，再用普通 ||| 切分（默认 2 秒间隔）
        if (rawParts.size <= 1) {
            // 检查是否包含普通 |||
            if (text.contains("|||")) {
                val parts = text.split("|||").map { it.trim() }.filter { it.isNotEmpty() }
                return parts.mapIndexed { index, part ->
                    val delay = if (index < parts.size - 1) 2000L else 0L
                    part to delay
                }
            }
            return listOf(text to 0L)
        }

        // 带时间的分隔
        val result = mutableListOf<Pair<String, Long>>()
        for (i in rawParts.indices) {
            val segment = rawParts[i]
            val delay = if (i < delays.size) delays[i] else 0L
            result.add(segment to delay)
        }
        return result
    }
}
