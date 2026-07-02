package com.aicompanion.nativeapp.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings as AndroidSettings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicompanion.nativeapp.data.db.AppDatabase
import com.aicompanion.nativeapp.data.model.MessageEntity
import com.aicompanion.nativeapp.data.model.PersonaEntity
import com.aicompanion.nativeapp.data.repository.ChatRepository
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

data class SettingsUiState(
    val totalPersonas: Int = 0,
    val totalMessages: Int = 0,
    val selectedProvider: String = "deepseek",
    val apiKeys: Map<String, String> = emptyMap(),
    val customBaseUrl: String = "",
    val customModel: String = "",
    val testingConnection: Boolean = false,
    val testResult: String? = null, // 测试连接的结果文本
    val testSuccess: Boolean = false,
    val exporting: Boolean = false,
    val importing: Boolean = false,
    val exportedPath: String = "",
    val toastMessage: String? = null,
    // Personality response setting
    val personalityResponseEnabled: Boolean = true,
    // Proactive chat setting
    val proactiveChatEnabled: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repo = ChatRepository(db)
    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        observeStats()
    }

    private fun observeStats() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.getAllPersonas().collect { personas ->
                    var totalMsgs = 0
                    for (p in personas) {
                        totalMsgs += repo.getMessageCount(p.id)
                    }
                    _uiState.update { it.copy(totalPersonas = personas.size, totalMessages = totalMsgs) }
                }
            } catch (_: Exception) { }
        }
    }

    private fun loadSettings() {
        val provider = prefs.getString("active_provider", "deepseek") ?: "deepseek"
        val apiKeys = mutableMapOf<String, String>()
        val providers = listOf("deepseek", "qwen", "zhipu", "kimi", "doubao", "openai", "custom")
        for (p in providers) {
            val key = prefs.getString("api_key_$p", "") ?: ""
            if (key.isNotEmpty()) {
                apiKeys[p] = key
            }
        }
        val personalityEnabled = prefs.getBoolean("personality_response_enabled", false)
        val proactiveChatEnabled = prefs.getBoolean("proactive_chat_enabled", false)
        _uiState.update {
            it.copy(
                selectedProvider = provider,
                apiKeys = apiKeys,
                customBaseUrl = prefs.getString("custom_base_url", "") ?: "",
                customModel = prefs.getString("custom_model", "") ?: "",
                personalityResponseEnabled = true,
                proactiveChatEnabled = proactiveChatEnabled
            )
        }
    }

    fun selectProvider(providerKey: String) {
        prefs.edit().putString("active_provider", providerKey).apply()
        val apiKey = prefs.getString("api_key_$providerKey", "") ?: ""
        val newApiKeys = _uiState.value.apiKeys.toMutableMap()
        newApiKeys[providerKey] = apiKey
        _uiState.update {
            it.copy(
                selectedProvider = providerKey,
                apiKeys = newApiKeys
            )
        }
    }

    fun updateApiKey(providerKey: String, apiKey: String) {
        prefs.edit().putString("api_key_$providerKey", apiKey).apply()
        val newApiKeys = _uiState.value.apiKeys.toMutableMap()
        newApiKeys[providerKey] = apiKey
        _uiState.update { it.copy(apiKeys = newApiKeys) }
    }

    fun updateCustomBaseUrl(url: String) {
        prefs.edit().putString("custom_base_url", url).apply()
        _uiState.update { it.copy(customBaseUrl = url) }
    }

    fun updateCustomModel(model: String) {
        prefs.edit().putString("custom_model", model).apply()
        _uiState.update { it.copy(customModel = model) }
    }

    fun testConnection() {
        val state = _uiState.value
        val baseUrl = if (state.selectedProvider == "custom") state.customBaseUrl else
            com.aicompanion.nativeapp.network.ProviderPresets.getProvider(state.selectedProvider).baseUrl
        val apiKey = state.apiKeys[state.selectedProvider] ?: ""
        val model = if (state.selectedProvider == "custom") state.customModel else
            com.aicompanion.nativeapp.network.ProviderPresets.getProvider(state.selectedProvider).defaultModel

        if (apiKey.isBlank()) {
            _uiState.update { it.copy(testResult = "请先输入 API Key", testSuccess = false) }
            return
        }

        _uiState.update { it.copy(testingConnection = true, testResult = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = repo.testConnection(baseUrl, apiKey, model)
                _uiState.update {
                    it.copy(
                        testingConnection = false,
                        testResult = if (success) "连接成功！API 可用" else "连接失败，请检查 API Key 和网络",
                        testSuccess = success
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        testingConnection = false,
                        testResult = "连接异常: ${e.message?.take(80) ?: "未知错误"}",
                        testSuccess = false
                    )
                }
            }
        }
    }

    fun exportData() {
        _uiState.update { it.copy(exporting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val personas = repo.getAllPersonasSync()
                val gson = Gson()

                val personasArray = gson.toJsonTree(personas).asJsonArray
                val messagesArray = gson.toJsonTree(mutableListOf<MessageEntity>()).asJsonArray

                for (persona in personas) {
                    val msgs = repo.getAllMessagesForPersona(persona.id)
                    for (msg in msgs) {
                        val msgJson = gson.toJsonTree(msg).asJsonObject
                        messagesArray.add(msgJson)
                    }
                }

                // Also export settings
                val settingsJson = JsonObject().apply {
                    val providers = listOf("deepseek", "qwen", "zhipu", "kimi", "doubao", "openai", "custom")
                    val keysJson = JsonObject()
                    for (p in providers) {
                        val key = prefs.getString("api_key_$p", "") ?: ""
                        keysJson.addProperty(p, key)
                    }
                    add("api_keys", keysJson)
                    addProperty("active_provider", prefs.getString("active_provider", "deepseek") ?: "deepseek")
                    addProperty("custom_base_url", prefs.getString("custom_base_url", "") ?: "")
                    addProperty("custom_model", prefs.getString("custom_model", "") ?: "")
                    addProperty("personality_response_enabled", prefs.getBoolean("personality_response_enabled", false))
                    addProperty("proactive_chat_enabled", prefs.getBoolean("proactive_chat_enabled", false))
                }

                val backupJson = JsonObject().apply {
                    addProperty("app", "我的角色")
                    addProperty("version", 1)
                    addProperty("exportTime", System.currentTimeMillis())
                    add("personas", personasArray)
                    add("messages", messagesArray)
                    add("settings", settingsJson)
                }

                val jsonStr = gson.toJson(backupJson)
                _uiState.update {
                    it.copy(
                        exporting = false,
                        toastMessage = "导出成功！"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        exporting = false,
                        toastMessage = "导出失败: ${e.message?.take(100) ?: "未知错误"}"
                    )
                }
            }
        }
    }

    /**
     * 通过 SAF (Storage Access Framework) 将备份写入用户选择的位置
     */
    fun exportDataToUri(uri: Uri) {
        _uiState.update { it.copy(exporting = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val personas = repo.getAllPersonasSync()
                val gson = Gson()
                val personasArray = JsonArray()
                val messagesArray = JsonArray()

                for (persona in personas) {
                    val personaJson = gson.toJsonTree(persona).asJsonObject
                    personasArray.add(personaJson)
                    val msgs = repo.getAllMessagesForPersona(persona.id)
                    for (msg in msgs) {
                        messagesArray.add(gson.toJsonTree(msg))
                    }
                }

                val settingsJson = JsonObject().apply {
                    val providers = listOf("deepseek", "qwen", "zhipu", "kimi", "doubao", "openai", "custom")
                    val keysJson = JsonObject()
                    for (p in providers) {
                        keysJson.addProperty(p, prefs.getString("api_key_$p", "") ?: "")
                    }
                    add("api_keys", keysJson)
                    addProperty("active_provider", prefs.getString("active_provider", "deepseek") ?: "deepseek")
                    addProperty("custom_base_url", prefs.getString("custom_base_url", "") ?: "")
                    addProperty("custom_model", prefs.getString("custom_model", "") ?: "")
                    addProperty("personality_response_enabled", prefs.getBoolean("personality_response_enabled", false))
                    addProperty("proactive_chat_enabled", prefs.getBoolean("proactive_chat_enabled", false))
                }

                val backupJson = JsonObject().apply {
                    addProperty("app", "我的角色")
                    addProperty("version", 1)
                    addProperty("exportTime", System.currentTimeMillis())
                    add("personas", personasArray)
                    add("messages", messagesArray)
                    add("settings", settingsJson)
                }

                val context = getApplication<Application>()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(gson.toJson(backupJson).toByteArray(Charsets.UTF_8))
                } ?: throw Exception("无法写入文件")

                _uiState.update {
                    it.copy(
                        exporting = false,
                        exportedPath = uri.toString(),
                        toastMessage = "导出成功！"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        exporting = false,
                        toastMessage = "导出失败: ${e.message?.take(100) ?: "未知错误"}"
                    )
                }
            }
        }
    }

    /**
     * 导入备份 — 兼容两种格式：
     *
     * 1) Native 格式 (version=1):
     *    { "personas": [{ id, name, personaDoc, createdAt: Long, ... }],
     *      "messages": [{ id, personaId, role, content, timestamp: Long }] }
     *
     * 2) WebView 旧版格式 (version=2 或无 version):
     *    { "personas": [{ id, name, persona: "角色设定", createdAt: "ISO字符串", history: [{role,content,time}] }],
     *      "activeId": "...", "apiKeys": {...} }
     */
    fun importData(uri: Uri) {
        _uiState.update { it.copy(importing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("无法打开文件")
                val jsonStr = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                val gson = Gson()
                val root = gson.fromJson(jsonStr, JsonObject::class.java)
                    ?: throw Exception("文件格式不正确，不是有效的 JSON")

                val personasArray = root.getAsJsonArray("personas")
                    ?: throw Exception("文件缺少 personas 数据")

                // ---- 判断备份格式 ----
                // 旧版 WebView 格式的 personas 中，createdAt 是 ISO 字符串
                // 新版 Native 格式中，createdAt 是 JSON 数字 (Long)
                val isLegacyFormat = run {
                    if (personasArray.size() == 0) false
                    else {
                        val first = personasArray[0].asJsonObject
                        val ct = first.get("createdAt")
                        ct != null && !ct.isJsonNull && ct.isJsonPrimitive && ct.asJsonPrimitive.isString
                    }
                }

                // ── 不再清空已有数据，导入的内容和现有数据共存 ──
                // 所有导入的角色都生成新 ID、设为非活跃，避免冲突

                var personaCount = 0
                var messageCount = 0
                val oldIdToNewId = mutableMapOf<String, String>() // 旧角色ID → 新角色ID 映射

                if (isLegacyFormat) {
                    // ====== 旧版 WebView 格式 ======
                    for (item in personasArray) {
                        val obj = item.asJsonObject
                        val oldPId = obj.get("id")?.asString
                            ?: java.util.UUID.randomUUID().toString()
                        val newPId = java.util.UUID.randomUUID().toString()
                        oldIdToNewId[oldPId] = newPId
                        val pName = obj.get("name")?.asString ?: "未命名角色"
                        val pDoc = obj.get("persona")?.asString ?: ""
                        val pCoreMemory = obj.get("coreMemory")?.asString ?: ""
                        val pUserProfile = obj.get("userProfile")?.asString ?: ""
                        val pConvCount = obj.get("convCount")?.asInt ?: 0
                        val pCreatedAt = parseTimestamp(obj.get("createdAt"))

                        val persona = PersonaEntity(
                            id = newPId,
                            name = pName,
                            personaDoc = pDoc,
                            coreMemory = pCoreMemory,
                            userProfile = pUserProfile,
                            isActive = false, // 永不覆盖当前活跃角色
                            convCount = pConvCount,
                            createdAt = pCreatedAt
                        )
                        repo.insertPersona(persona)
                        personaCount++

                        // 旧版消息嵌套在 persona.history[] 中
                        val historyArray = obj.getAsJsonArray("history")
                        if (historyArray != null) {
                            for (msgItem in historyArray) {
                                val mObj = msgItem.asJsonObject
                                val mRole = mObj.get("role")?.asString ?: "user"
                                val mContent = mObj.get("content")?.asString ?: ""
                                val mTimestamp = parseTimestamp(mObj.get("time"))

                                val msg = MessageEntity(
                                    id = 0, // 自增新 ID
                                    personaId = newPId,
                                    role = mRole,
                                    content = mContent,
                                    timestamp = mTimestamp
                                )
                                repo.insertMessage(msg)
                                messageCount++
                            }
                        }
                    }

                    // 旧版设置：apiKeys 在顶层（注意大小写）
                    val legacyApiKeys = root.getAsJsonObject("apiKeys")
                    if (legacyApiKeys != null) {
                        importApiKeysFromObject(legacyApiKeys)
                    }
                    val legacyProvider = root.get("activeProvider")?.asString
                        ?: root.get("active_provider")?.asString
                    if (!legacyProvider.isNullOrEmpty()) {
                        prefs.edit().putString("active_provider", legacyProvider).apply()
                    }

                } else {
                    // ====== 新版 Native 格式 ======
                    for (item in personasArray) {
                        val obj = item.asJsonObject
                        val oldPId = obj.get("id")?.asString
                            ?: java.util.UUID.randomUUID().toString()
                        val newPId = java.util.UUID.randomUUID().toString()
                        oldIdToNewId[oldPId] = newPId

                        val persona = PersonaEntity(
                            id = newPId,
                            name = obj.get("name")?.asString ?: "未命名角色",
                            personaDoc = obj.get("personaDoc")?.asString ?: "",
                            coreMemory = obj.get("coreMemory")?.asString ?: "",
                            userProfile = obj.get("userProfile")?.asString ?: "",
                            isActive = false, // 永不覆盖当前活跃角色
                            convCount = obj.get("convCount")?.asInt ?: 0,
                            createdAt = parseTimestamp(obj.get("createdAt"))
                        )
                        repo.insertPersona(persona)
                        personaCount++
                    }

                    val messagesArray = root.getAsJsonArray("messages")
                    if (messagesArray != null) {
                        for (item in messagesArray) {
                            val obj = item.asJsonObject
                            val oldPersonaId = obj.get("personaId")?.asString ?: ""
                            // 映射到新角色 ID（找不到就用原ID——极小概率落到已有角色上，但可以接受）
                            val mappedPersonaId = oldIdToNewId[oldPersonaId] ?: oldPersonaId
                            val msg = MessageEntity(
                                id = 0, // 自增新 ID
                                personaId = mappedPersonaId,
                                role = obj.get("role")?.asString ?: "user",
                                content = obj.get("content")?.asString ?: "",
                                timestamp = parseTimestamp(obj.get("timestamp"))
                            )
                            repo.insertMessage(msg)
                            messageCount++
                        }
                    }

                    // 新版设置
                    val settingsObj = root.getAsJsonObject("settings")
                    if (settingsObj != null) {
                        val keysObj = settingsObj.getAsJsonObject("api_keys")
                        if (keysObj != null) {
                            importApiKeysFromObject(keysObj)
                        }
                        settingsObj.get("active_provider")?.asString?.let {
                            prefs.edit().putString("active_provider", it).apply()
                        }
                        settingsObj.get("custom_base_url")?.asString?.let {
                            prefs.edit().putString("custom_base_url", it).apply()
                        }
                        settingsObj.get("custom_model")?.asString?.let {
                            prefs.edit().putString("custom_model", it).apply()
                        }
                        settingsObj.get("personality_response_enabled")?.asBoolean?.let {
                            prefs.edit().putBoolean("personality_response_enabled", it).apply()
                        }
                        settingsObj.get("proactive_chat_enabled")?.asBoolean?.let {
                            prefs.edit().putBoolean("proactive_chat_enabled", it).apply()
                        }
                    }
                }

                // Reload settings to reflect imported values
                loadSettings()

                _uiState.update {
                    it.copy(
                        importing = false,
                        toastMessage = "导入成功！$personaCount 个角色，$messageCount 条消息"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        importing = false,
                        toastMessage = "导入失败: ${e.message?.take(100) ?: "文件格式错误"}"
                    )
                }
            }
        }
    }

    /**
     * 将 JSON 元素安全地解析为 Long 时间戳。
     * 支持两种输入：
     *   - JSON 数字 → 直接取 long 值
     *   - ISO 8601 字符串 (如 "2026-07-01T09:33:11.272Z") → 用 Instant.parse 转换
     *   - 缺失/null → 返回 System.currentTimeMillis()
     */
    private fun parseTimestamp(element: JsonElement?): Long {
        if (element == null || element.isJsonNull) return System.currentTimeMillis()
        return try {
            if (element.isJsonPrimitive) {
                val prim = element.asJsonPrimitive
                if (prim.isNumber) prim.asLong
                else if (prim.isString) Instant.parse(prim.asString).toEpochMilli()
                else System.currentTimeMillis()
            } else {
                System.currentTimeMillis()
            }
        } catch (e: Exception) {
            // 解析失败时 fallback 到当前时间
            System.currentTimeMillis()
        }
    }

    /**
     * 从 JSON 对象中批量导入 API Keys
     */
    private fun importApiKeysFromObject(keysObj: JsonObject) {
        val providers = listOf("deepseek", "qwen", "zhipu", "kimi", "doubao", "openai", "custom")
        for (p in providers) {
            // 兼容多种可能的大小写 key 名
            var key = keysObj.get(p)?.asString ?: ""
            if (key.isEmpty()) {
                key = keysObj.get(p.replaceFirstChar { it.uppercase() })?.asString ?: ""
            }
            if (key.isNotEmpty()) {
                prefs.edit().putString("api_key_$p", key).apply()
            }
        }
    }

    fun openBackupFolder() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val backupFiles = downloadDir.listFiles { f ->
                    f.extension == "json" && f.name.startsWith("ai-companion-backup")
                }
                if (backupFiles == null || backupFiles.isEmpty()) {
                    _uiState.update {
                        it.copy(toastMessage = "下载目录中没有找到备份文件(.json)")
                    }
                    return@launch
                }

                // Open Downloads folder
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.parse(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                        ),
                        "resource/folder"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val fileList = backupFiles.sortedByDescending { it.lastModified() }
                        .joinToString("\n") { "• ${it.name} (${it.length() / 1024}KB)" }

                    _uiState.update {
                        it.copy(toastMessage = "下载目录中找到：\n$fileList")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(toastMessage = "无法打开文件夹: ${e.message?.take(80)}")
                }
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.deleteAllData()
                // Also clear active_persona_id
                prefs.edit().remove("active_persona_id").apply()

                _uiState.update {
                    it.copy(toastMessage = "已清空所有角色和聊天数据")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(toastMessage = "清空失败: ${e.message?.take(80)}")
                }
            }
        }
    }

    fun dismissToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun toggleProactiveChat() {
        val newValue = !_uiState.value.proactiveChatEnabled
        prefs.edit().putBoolean("proactive_chat_enabled", newValue).apply()
        _uiState.update { it.copy(proactiveChatEnabled = newValue) }
    }
}
