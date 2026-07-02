package com.aicompanion.nativeapp.network

data class ProviderConfig(
    val name: String,
    val baseUrl: String,
    val defaultModel: String
)

object ProviderPresets {
    val presets = mapOf(
        "deepseek" to ProviderConfig("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
        "qwen" to ProviderConfig("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-turbo"),
        "zhipu" to ProviderConfig("智谱 AI", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
        "kimi" to ProviderConfig("Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
        "doubao" to ProviderConfig("豆包", "https://ark.cn-beijing.volces.com/api/v3", "doubao-pro-32k"),
        "openai" to ProviderConfig("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        "custom" to ProviderConfig("自定义", "", "")
    )

    fun getProvider(key: String): ProviderConfig =
        presets[key] ?: presets["deepseek"]!!
}
