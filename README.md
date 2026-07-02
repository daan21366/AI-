小玩具，我什么都不懂，全程ai做出来的，我没有技术，就只是放在我的仓库里罢了，各位大神不必在意

功能少，个人用来写给自己玩的吗，手机用户直接下载.apk文件就行

用户需遵守当地法律法规，严禁用于生成违法内容

作者不承担用户违规使用带来的后果
# 我的角色 - AI 情感陪伴 App

一个纯本地的 AI 角色扮演聊天 App，**用户自备 API Key**（BYOK），直连各大模型厂商，数据全部存在本地。

## 功能

- 🎭 **多角色管理** — 创建多个 AI 角色，每个角色有独立的设定文档
- 💬 **SSE 流式聊天** — 实时打字机效果，支持 DeepSeek / 通义千问 / 智谱 AI / Kimi / 豆包 / OpenAI / 自定义
- 🧠 **自动记忆提取** — AI 每 10 轮对话自动提取核心记忆和用户画像
- 🎨 **8 套主题配色** — 丁香紫 / 薄荷绿 / 暖橙 / 天空蓝 / 樱花粉 / 暗夜黑 / 奶油白 / 海军蓝
- ⏰ **主动聊天** — 角色可根据性格主动发起对话，可聊现实话题，支持定时消息分隔语法
- 📦 **数据导入/导出** — 兼容新旧两种备份格式
- 🕒 **消息时间戳** — 每条消息显示发送时间
- 📋 **文字复制** — 长按消息可选中复制

## 截图

<!-- 截图待补充 -->

## 快速开始

### 1. 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34

### 2. 配置 SDK 路径

```bash
cp local.properties.example local.properties
```

编辑 `local.properties`，填入你的 Android SDK 路径：

```properties
sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
```

### 3. 直接安装（免编译）

项目根目录已包含编译好的 APK：

```
我的角色.apk
```

直接传到手机上安装即可使用。

### 4. 自行编译

用 Android Studio 打开项目根目录，或命令行：

```bash
./gradlew assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`

### 5. 使用

1. 安装 APK 到手机
2. 打开 App → 设置 → 配置 API Key（DeepSeek / 通义千问 / 等）
3. 创建角色 → 开始聊天

## 技术架构

```
MVVM + Jetpack Compose + Room + OkHttp SSE
```

| 层 | 技术 | 职责 |
|----|------|------|
| UI | Jetpack Compose + Material3 | 4 个页面 + 底部导航 |
| ViewModel | AndroidViewModel + StateFlow | 业务逻辑、状态管理 |
| Data | Room (SQLite) | 角色、消息持久化 |
| Network | OkHttp SSE + callbackFlow | 流式 API 调用 |
| Models | Gson | JSON 序列化 |

## 项目结构

```
app/src/main/java/com/aicompanion/nativeapp/
├── AiCompanionApplication.kt      # 应用入口
├── MainActivity.kt                 # 底部导航 + 主题
├── data/
│   ├── db/                         # Room 数据库
│   │   ├── AppDatabase.kt
│   │   ├── PersonaDao.kt
│   │   └── MessageDao.kt
│   ├── model/Models.kt             # 数据实体
│   └── repository/ChatRepository.kt # 核心业务 + Prompt 构建
├── network/
│   ├── ApiModels.kt                # API 请求/响应模型
│   ├── ProviderPresets.kt          # 7 家 API 提供商配置
│   └── SseClient.kt                # SSE 流式客户端
├── ui/
│   ├── screens/                    # 4 个界面
│   │   ├── ChatScreen.kt
│   │   ├── PersonaScreen.kt
│   │   ├── MemoryScreen.kt
│   │   └── SettingsScreen.kt
│   └── theme/                      # 8 套主题配色
│       ├── Color.kt
│       └── Theme.kt
└── viewmodel/                      # ViewModel 层
    ├── ChatViewModel.kt
    ├── MemoryViewModel.kt
    ├── PersonaViewModel.kt
    └── SettingsViewModel.kt
```

## API 提供商

| 提供商 | 默认模型 |
|--------|---------|
| DeepSeek | deepseek-chat |
| 通义千问 | qwen-turbo |
| 智谱 AI | glm-4-flash |
| Kimi | moonshot-v1-8k |
| 豆包 | doubao-pro-32k |
| OpenAI | gpt-4o-mini |
| 自定义 | 自定义 Base URL + 模型名 |

## 备份格式

### 导出文件命名

```
ai-companion-backup-2026-07-02.json      # 第一次导出
ai-companion-backup-2026-07-02 (1).json  # 同天第二次导出
```

### JSON 结构

```json
{
  "app": "我的角色",
  "version": 1,
  "personas": [...],
  "messages": [...],
  "settings": { "api_keys": {...}, "active_provider": "deepseek" }
}
```

## 构建环境参考

开发此项目使用的构建环境：

- **JDK**: Eclipse Temurin JDK 17
- **Gradle**: 8.4
- **Android SDK**: 34
- **Kotlin**: 1.9.22
- **Compose BOM**: 2024.02.00
