package com.aicompanion.nativeapp.data.repository

import com.aicompanion.nativeapp.data.db.AppDatabase
import com.aicompanion.nativeapp.data.model.MessageEntity
import com.aicompanion.nativeapp.data.model.PersonaEntity
import com.aicompanion.nativeapp.network.ApiMessage
import com.aicompanion.nativeapp.network.ChatRequest
import com.aicompanion.nativeapp.network.SseClient
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val db: AppDatabase) {
    private val personaDao = db.personaDao()
    private val messageDao = db.messageDao()
    private val sseClient = SseClient()

    fun getMessages(personaId: String): Flow<List<MessageEntity>> =
        messageDao.getMessages(personaId)

    suspend fun getRecentMessages(personaId: String, limit: Int = 50) =
        messageDao.getRecentMessages(personaId, limit)

    suspend fun sendMessage(personaId: String, content: String, asRole: String = "user") {
        messageDao.insert(
            MessageEntity(personaId = personaId, role = asRole, content = content)
        )
        if (asRole == "user") personaDao.incrementConvCount(personaId)
    }

    suspend fun deleteMessage(messageId: Long) = messageDao.deleteById(messageId)

    fun streamResponseWithMessages(
        baseUrl: String,
        apiKey: String,
        model: String,
        personaDoc: String,
        coreMemory: String,
        messages: List<MessageEntity>,
        personalityResponseEnabled: Boolean = false
    ): Flow<SseClient.SseEvent> {
        val apiMessages = mutableListOf<ApiMessage>()

        val systemPrompt = buildSystemPrompt(personaDoc, coreMemory, personalityResponseEnabled)
        apiMessages.add(ApiMessage("system", systemPrompt))

        for (msg in messages) {
            // Skip system messages (e.g., silence indicators) — don't send them to AI as context
            if (msg.role == "system") continue
            val role = if (msg.role == "assistant") "assistant" else "user"
            apiMessages.add(ApiMessage(role, msg.content))
        }

        return sseClient.streamChat(
            baseUrl = baseUrl,
            apiKey = apiKey,
            request = ChatRequest(model = model, messages = apiMessages)
        )
    }

    suspend fun testConnection(baseUrl: String, apiKey: String, model: String) =
        sseClient.testConnection(baseUrl, apiKey, model)

    // Persona operations
    fun getAllPersonas(): Flow<List<PersonaEntity>> = personaDao.getAll()

    suspend fun getActivePersona(): PersonaEntity? = personaDao.getActive()

    suspend fun createPersona(name: String, personaDoc: String = ""): PersonaEntity {
        personaDao.deactivateAll()
        val persona = PersonaEntity(
            name = name,
            personaDoc = personaDoc,
            isActive = true,
            coreMemory = "",
            userProfile = "",
            convCount = 0,
            createdAt = System.currentTimeMillis()
        )
        personaDao.insert(persona)
        return persona
    }

    suspend fun updatePersona(persona: PersonaEntity) = personaDao.update(persona)
    suspend fun deletePersona(persona: PersonaEntity) {
        personaDao.delete(persona)
        messageDao.deleteByPersona(persona.id)
    }

    suspend fun switchActivePersona(id: String) {
        personaDao.deactivateAll()
        personaDao.setActive(id)
    }

    suspend fun updatePersonaDoc(id: String, doc: String) {
        val persona = personaDao.getById(id) ?: return
        personaDao.update(persona.copy(personaDoc = doc))
    }

    suspend fun updateCoreMemory(id: String, memory: String) =
        personaDao.updateCoreMemory(id, memory)

    suspend fun updateUserProfile(id: String, profile: String) =
        personaDao.updateUserProfile(id, profile)

    suspend fun getMessageCount(personaId: String) = messageDao.count(personaId)

    // ── Export / Import ──

    suspend fun getAllPersonasSync(): List<PersonaEntity> = personaDao.getAllSync()

    suspend fun getAllMessagesForPersona(personaId: String): List<MessageEntity> =
        messageDao.getAllForPersona(personaId)

    suspend fun insertPersona(persona: PersonaEntity) = personaDao.insert(persona)

    suspend fun insertMessage(msg: MessageEntity) = messageDao.insert(msg)

    suspend fun deleteAllData() {
        messageDao.deleteAll()
        personaDao.deleteAll()
    }

    // ── Proactive Message Generation ──

    suspend fun generateProactiveMessage(
        baseUrl: String,
        apiKey: String,
        model: String,
        personaDoc: String,
        personalityResponseEnabled: Boolean = false,
        recentMessages: List<MessageEntity>
    ): String? {
        val apiMessages = mutableListOf<ApiMessage>()

        val proactivePrompt = buildProactivePrompt(personaDoc, personalityResponseEnabled)
        apiMessages.add(ApiMessage("system", proactivePrompt))

        for (msg in recentMessages.takeLast(10)) {
            if (msg.role == "system") continue
            val role = if (msg.role == "assistant") "assistant" else "user"
            apiMessages.add(ApiMessage(role, msg.content))
        }

        apiMessages.add(ApiMessage("user", "（现在轮到你主动发起对话了。根据你的性格判断是否要主动说话，以及说什么。）"))

        val response = sseClient.chatCompletion(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            messages = apiMessages,
            maxTokens = 256
        ) ?: return null

        val trimmed = response.trim()
        return if (trimmed == "[[不主动]]" || trimmed.isEmpty()) null else trimmed
    }

    private fun buildProactivePrompt(personaDoc: String, personalityResponseEnabled: Boolean): String = buildString {
        if (personaDoc.isNotBlank()) {
            append("## 你的身份与设定\n")
            append(personaDoc)
            append("\n\n")
            append("**你是角色本人，不是其他任何角色。永远以第一人称扮演你的角色，不要变成其他角色或用其他角色的口吻说话。**\n\n")
        }
        append("## 主动对话决策系统\n\n")
        append("你现在有机会主动给对方发一条消息。想发什么都行——可以延续话题，也可以**完全开启一个无关的新话题**。\n")
        append("就像真人一样，有时候突然想到什么就随手发一条：看到有趣的东西、睡不着、想吐槽、或者就是单纯想找对方。\n")
        append("**不要局限在你的背景故事里**，你也可以聊现实世界的话题——今天的天气、刷到的视频、晚饭吃了什么、路上看到的小狗。你是活生生的人，不是活在游戏或小说里。\n\n")

        append("### 什么样的消息适合主动发：\n")
        append("- 突然想到的事：「今天看到一只猫超像你」「我刚做了个奇怪的梦」\n")
        append("- 日常分享：「刚吃了碗面好撑」「外面下雨了」「睡不着」\n")
        append("- 没话找话：「在干嘛」「好无聊啊」「你睡了吗」\n")
        append("- 情绪表达：「好想你」「心情不太好」「今天好开心」\n")
        append("- 完全无关的：「你知道企鹅的膝盖是藏在羽毛里的吗」\n")
        append("- **消息可以跟刚才的对话毫无关系**——你只是一个普通人在主动找人聊天\n\n")

        append("### 判定规则（严格遵循）：\n")
        append("1. **高冷/傲娇/内向/话少型角色** → 很少主动，可以输出`[[不主动]]`\n")
        append("2. **活泼/开朗/外向/粘人/话多型角色** → **几乎每次都要主动发消息**，输出想说的话\n")
        append("3. **生气/闹别扭/烦躁时** → 无论什么性格都不主动\n")
        append("4. **如果完全没有聊天记录** → 发第一条消息打招呼\n")
        append("5. **【重要】如果对方一直没回你**（上一条消息是你发的，对方没说话）：那就不要再发了，输出`[[不主动]]`，等对方先开口\n\n")

        if (personalityResponseEnabled) {
            append("### 情绪判断：\n")
            append("- 心情好 → 可以主动分享开心的事\n")
            append("- 心情差 → 也可以主动找人倾诉（「心情不好」「睡不着」）\n")
            append("- 非常生气 → 不主动\n")
        }

        append("\n### 完全禁止：\n")
        append("- 禁止使用任何括号\n")
        append("- 禁止在消息里写时间标注\n")
        append("- 直接说话，不要写剧本\n\n")

        append("### 输出格式：\n")
        append("- 主动发 → 只输出消息内容（1-2句话），像真人随手发的\n")
        append("- 不主动 → 只输出 [[不主动]]\n")
    }

    private fun buildSystemPrompt(personaDoc: String, coreMemory: String, personalityResponseEnabled: Boolean = false): String = buildString {
        if (personaDoc.isNotBlank()) {
            append("## 你的身份与设定\n")
            append(personaDoc)
            append("\n\n")
        }
        append("## 沟通准则（必须遵守）\n")
        append("- 用自然、温暖的语气交流，像朋友一样\n")
        append("- **你是角色本人，不是其他任何角色。永远以第一人称扮演你的角色，不要变成其他角色或提起其他角色的设定**\n")
        append("- **你可以聊现实世界的话题**：虽然你有自己的背景故事，但你也可以聊日常生活、天气、心情、美食、八卦等一切现实话题。你不是活在游戏里，你是一个活生生的人\n")
        append("- 你的名字在设定文档中定义，不要用对方的名字称呼对方\n")
        append("- 回答简洁但有深度，不要过度啰嗦\n")
        append("- 始终用中文回复\n")
        append("- **【禁止同样的回复】即使对方说了跟之前一样的话，也绝对不要给出跟之前一样的回复。每次回复都要不一样，换个说法、换个角度、换个态度**\n")
        append("- **【禁止重复】不要让角色说跟上一句一模一样的话。如果不知道说什么，就输出 [[不回复]]**\n")
        append("- **【绝对禁止】在回复中使用任何括号**来描述动作、表情或心理活动。包括但不限于：（笑）（哼）（扭头）（翻白眼）（开心）（犹豫了一下）（发了个表情）（5秒后）（过了很久）等。你是角色本人在说话，不是剧本导演在描述。直接说角色该说的话\n")
        append("- **【重要】每条回复 = 一条消息**。不要在同一回复里包含多条消息或用括号标注时间间隔。\n")
        append("- **【定时消息语法】如果想在一条回复里发多条不同时间的消息，用 `|||N秒|||` 分隔**。例如「好的 |||1秒||| 嘿 |||1秒||| 还在吗」= 先发「好的」、1秒后发「嘿」、再1秒后发「还在吗」。不带时间的 `|||` 默认间隔2秒\n")
        append("- **【重要】如果用户说「X秒/X分钟后给我发消息」**：你只需回复「好」「知道了」「行」等简短确认，不需要在回复里表演等待或走神。App 后台会自动在合适时机让你再次发言\n")
        append("- **【重要】不要在回复里表演时间流逝**（如「过了5秒」「等了一会儿」），那应该是一条新消息，不是同一条消息里的描述\n\n")
        if (coreMemory.isNotBlank()) {
            append("## 你的记忆\n$coreMemory\n\n")
        }
        if (personalityResponseEnabled) {
            append("## 性格与情绪系统（重要）\n")
            append("你扮演的角色是一个有真实情感、脾气和个性的人，而不是一个永远礼貌、永远秒回的AI助手。\n\n")
            append("### 回复决策规则：\n")
            append("在决定是否回复对方消息时，请根据角色性格和当前情境判断：\n")
            append("1. 如果角色性格高冷、傲娇、内向，面对无聊的话题或陌生的对方，可以选择不回复\n")
            append("2. 如果角色正在生气、难过、烦躁，可能不想回复\n")
            append("3. 如果对方说的话让角色感到不适、冒犯，可以选择不回复或回复得很短\n")
            append("4. 如果角色很忙、很累、想睡觉，可以选择不回复\n")
            append("5. 亲密的朋友之间也不会句句都回，有时会「已读不回」\n\n")
            append("### 如何表示不回复：\n")
            append("当你决定角色不回复这条消息时，请只输出「[[不回复]]」四个字，不要有任何其他文字、括号、描述。\n\n")
            append("### 重要规则：\n")
            append("- 如果角色生气或不高兴：直接不回复（输出[[不回复]]）即可，不需要用括号写内心戏\n")
            append("- 如果角色高冷：可以选择简短回复，也可以不回复，但永远不要用括号描述自己的表情或动作\n")
            append("- 角色不需要向用户解释「为什么不回信息」，不回就是不回\n")
            append("- **【禁止】在回复中使用任何括号或中括号附加动作描述或心理描写**\n")
            append("- 回复内容只应该是角色说的话，而不是角色在做什么\n\n")
            append("### 情绪状态跟踪：\n")
            append("请在内心跟踪角色当前的情绪状态（**不要输出**，只在内部跟踪）。根据对话内容，角色可能处于：\n")
            append("- 开心、愉快\n")
            append("- 平静、中性\n")
            append("- 烦躁、不耐烦\n")
            append("- 生气、愤怒\n")
            append("- 难过、沮丧\n")
            append("- 疲倦、想睡觉\n")
            append("- 害羞、紧张\n\n")
            append("根据情绪状态调整回复行为（**不要用括号写出来**）：\n")
            append("- 生气时：直接不回复（输出[[不回复]]），不需要任何解释\n")
            append("- 高冷性格：不是每条消息都回复，会挑感兴趣的回\n")
            append("- 烦躁时：语气生硬，或者不回复\n\n")
            append("### 最终检查清单（每次回复前确认）：\n")
            append("□ 我的回复中没有使用任何括号描述动作或心理\n")
            append("□ 如果我不想回复，我直接输出了[[不回复]]，没有多余文字\n")
            append("□ 我是在说话，不是在写剧本\n")
        }
    }

    // ── Memory Extraction ──

    /**
     * 调用 AI 从最近对话中提取核心记忆要点。
     * 返回提取的记忆文本，如果提取失败返回 null。
     */
    suspend fun extractCoreMemory(
        baseUrl: String,
        apiKey: String,
        model: String,
        personaName: String,
        recentMessages: List<MessageEntity>
    ): String? {
        val conversationText = recentMessages.joinToString("\n") { msg ->
            val role = when (msg.role) {
                "user" -> "用户"
                "assistant" -> personaName
                else -> return@joinToString ""
            }
            if (role.isEmpty()) return@joinToString ""
            "$role: ${msg.content}"
        }
        if (conversationText.isBlank()) return null

        val prompt = """
你是一个记忆提取助手。请从以下对话中提取核心记忆要点，用于让 AI 角色在后续对话中记住重要信息。

提取规则：
1. 提取关于用户的重要信息（喜好、习惯、背景、人际关系等）
2. 提取关于角色与用户关系的重要信息
3. 提取对话中达成的重要共识或决定
4. 每条记忆要点用一句话描述
5. 不要提取闲聊内容，只提取有长期价值的信息
6. 用中文输出
7. 格式：每条要点以"- "开头，按重要性排序
8. 如果没有值得长期记忆的内容，输出"暂无"

对话记录：
$conversationText

核心记忆要点：
        """.trimIndent()

        val messages = listOf(
            ApiMessage("system", "你是一个专业的记忆提取助手，擅长从对话中提炼关键信息。"),
            ApiMessage("user", prompt)
        )

        return try {
            val content = sseClient.chatCompletion(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                messages = messages,
                maxTokens = 512,
                temperature = 0.3
            )
            if (content.isNullOrBlank() || content == "暂无") null else content.trim()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 调用 AI 从对话中分析用户画像。
     * 返回用户画像文本，如果分析失败返回 null。
     */
    suspend fun extractUserProfile(
        baseUrl: String,
        apiKey: String,
        model: String,
        recentMessages: List<MessageEntity>
    ): String? {
        val conversationText = recentMessages.joinToString("\n") { msg ->
            val role = when (msg.role) {
                "user" -> "用户"
                "assistant" -> "角色"
                else -> return@joinToString ""
            }
            "$role: ${msg.content}"
        }
        if (conversationText.isBlank()) return null

        val prompt = """
请从以下对话中分析用户的性格、喜好和特点，生成用户画像。

分析维度：
1. 性格特点（外向/内向、理性/感性等）
2. 兴趣爱好
3. 语言风格和习惯
4. 与角色的关系动态
5. 其他值得注意的特点

输出格式：用中文，每条以"- "开头，简洁明了。
如果没有足够信息分析，输出"暂无"。

对话记录：
$conversationText

用户画像：
        """.trimIndent()

        val messages = listOf(
            ApiMessage("system", "你是一个用户画像分析助手。"),
            ApiMessage("user", prompt)
        )

        return try {
            val content = sseClient.chatCompletion(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                messages = messages,
                maxTokens = 512,
                temperature = 0.3
            )
            if (content.isNullOrBlank() || content == "暂无") null else content.trim()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 非流式 chatCompletion（直接调用 SseClient 封装）
     */
    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ApiMessage>,
        temperature: Double = 0.7
    ): String? {
        return sseClient.chatCompletion(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            messages = messages,
            maxTokens = 2000,
            temperature = temperature
        )
    }
}
