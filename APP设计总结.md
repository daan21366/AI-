# AI 情感陪伴 App「我的角色」— 完整设计总结

> 生成时间：2026-07-02
> 项目路径：`D:/workbuddy默认/ai-compan-ion-native/`

---

## 一、架构总览

### 技术栈
```
MVVM + Jetpack Compose + Room + OkHttp SSE
```

### 分层结构
```
UI (Compose Screen)
  ↕ StateFlow 观察
ViewModel (AndroidViewModel)
  ↕ suspend / Flow
Repository (ChatRepository) ←→ Dao (Room) / SseClient (网络)
                                      ↕
                                  SQLite DB / API 厂商
```

### 关键依赖
| 库 | 版本 | 用途 |
|----|------|------|
| Compose BOM | 2024.02.00 | UI |
| Room | 2.6.1 | 本地数据库 |
| OkHttp + SSE | 4.12.0 | 流式 API |
| Gson | 2.10.1 | JSON |
| KSP | 1.9.22-1.0.16 | Room 编译 |

### 构建环境
```
JDK:     D:/workbuddy默认/安的小本/daily-profit-app/build-env/jdk17/jdk/
Gradle:  D:/workbuddy默认/安的小本/daily-profit-app/build-env/gradle/gradle-8.4/
SDK:     D:/workbuddy默认/安的小本/daily-profit-app/build-env/android-sdk/
```

---

## 二、文件结构与职责

| 文件 | 行数 | 职责 |
|------|------|------|
| `MainActivity.kt` | 95 | 底部导航栏 + Scaffold padding 传递 |
| `ChatViewModel.kt` | ~510 | 聊天逻辑、流式响应、主动聊天定时器、去重、消息分割 |
| `SettingsViewModel.kt` | ~600 | 设置管理、API key、导出(SAF)/导入/清空 |
| `PersonaViewModel.kt` | 131 | 角色 CRUD |
| `MemoryViewModel.kt` | 51 | 记忆展示 |
| `ChatRepository.kt` | ~265 | SSE 流式调用、System Prompt 构建、记忆提取、画像分析 |
| `SseClient.kt` | ~150 | OkHttp SSE 封装、非流式 chatCompletion |
| `ChatScreen.kt` | ~510 | 聊天 UI、消息气泡、时间戳、输入框 |
| `PersonaScreen.kt` | 364 | 角色管理 UI |
| `MemoryScreen.kt` | 214 | 记忆页面 |
| `SettingsScreen.kt` | ~540 | 设置 UI、主题、数据管理 |
| `Models.kt` | 30 | PersonaEntity + MessageEntity |
| `AppDatabase.kt` | 35 | Room 单例 |
| `PersonaDao.kt` | 50 | 角色 DAO |
| `MessageDao.kt` | 35 | 消息 DAO |
| `ApiModels.kt` | 15 | ChatRequest / ApiMessage |
| `ProviderPresets.kt` | 30 | 7 家 API 提供商配置 |
| `Color.kt` | ~100 | 8 套配色 |
| `Theme.kt` | ~40 | MaterialTheme 封装 |
| `AiCompanionApplication.kt` | 10 | Application 入口 |

---

## 三、数据模型

### PersonaEntity
```kotlin
@Entity(tableName = "personas")
data class PersonaEntity(
    val id: String = UUID.randomUUID().toString(),  // 主键
    val name: String,
    val personaDoc: String = "",         // 角色设定文档
    val coreMemory: String = "",         // 核心记忆（AI自动提取）
    val userProfile: String = "",        // 用户画像（AI自动分析）
    val isActive: Boolean = false,       // 是否当前活跃角色
    val convCount: Int = 0,              // 对话轮数
    val createdAt: Long = System.currentTimeMillis()
)
```

### MessageEntity
```kotlin
@Entity(tableName = "messages")
data class MessageEntity(
    val id: Long = 0,                    // 自增主键
    val personaId: String,               // 所属角色 ID
    val role: String,                    // "user" / "assistant" / "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
```

---

## 四、核心功能流程

### 4.1 发送消息（sendMessage）
```
用户输入 → sendMessage()
  ↓
保存用户消息到 DB
  ↓
生气沉默检查（已删除，详见错误记录）
  ↓
预检测重复问题（已删除，详见错误记录）
  ↓
调用 streamResponseWithMessages()
  ├─ 构建 System Prompt（角色设定 + 核心记忆 + 性格化规则）
  ├─ 构建上下文（最近 40 条，过滤 system 消息）
  └─ 调用 SSE 流式 API
  ↓
流式处理每个 Delta 事件
  ├─ 累计 fullResponse
  ├─ stripParentheses() 清洗括号内容
  ├─ 流式去重检查（匹配历史消息 → 立即拦截）
  └─ 实时更新 streamingContent 到 UI
  ↓
Done 事件
  ├─ [[不回复]] → 存沉默标记
  ├─ 重复回复（兜底） → 存沉默标记
  ├─ 正常回复 → 检查 ||| 语法 → 逐条保存（带间隔）
  └─ 触发记忆提取（每 10 轮）
  ↓
完成
```

### 4.2 主动聊天（Proactive Chat）
```
Timer 启动条件：有活跃角色（不再依赖开关）
  ↓
首次检查延迟：3 秒
  ↓
定时器循环（每 10-20 秒）
  ├─ 检查 proactiveChatEnabled 开关（关则跳过）
  ├─ 检查 activePersona 和 API Key
  ├─ 检查距离上次用户活动 > 3 秒
  ├─ 检查最近 30 秒内没有主动消息
  └─ 通过 → 调用 generateProactiveMessage()
  ↓
generateProactiveMessage
  ├─ 构建 Proactive System Prompt（鼓励随机/跑题/日常消息）
  ├─ 传入最近 20 条消息作为上下文
  └─ 非流式 API 调用
  ↓
返回结果
  ├─ "[[不主动]]" → 跳过
  ├─ 正常消息 → stripParentheses() 后存为助理消息
  └─ 重复消息 → 跳过
  ↓
backoff 机制
  ├─ 连续 3 次无回应 → 间隔拉到 60 秒
  ├─ 连续 5 次无回应 → 间隔拉到 120 秒
  └─ 用户发消息 → 立即恢复 15 秒
```

### 4.3 导出备份
```
点击「导出备份」
  ↓
弹出 SAF 系统文件选择器（ACTION_CREATE_DOCUMENT）
  ↓
用户选择位置（如「下载」）
  ↓
写入 JSON（含所有角色 + 消息 + 设置）
  ↓
Toast 提示成功
```

### 4.4 导入备份
```
点击「导入备份」→ 系统文件选择器
  ↓
读取 JSON
  ↓
自动判断格式
  ├─ createdAt 为字符串 → 旧版 WebView 格式
  └─ createdAt 为数字 → 新版 Native 格式
  ↓
不删除任何现有数据
  ├─ 所有导入角色生成新 UUID
  ├─ 所有导入角色设为非活跃（isActive=false）
  ├─ 消息的 personaId 通过 oldId→newId 映射
  └─ API 设置可选择覆盖
```

---

## 五、System Prompt 设计

### 聊天 System Prompt（buildSystemPrompt）
构建于 `ChatRepository.kt`，包含：
1. **角色身份设定** — 用户自定义 personaDoc
2. **沟通准则** — 自然、第一人称、禁止括号、禁止重复
3. **核心记忆** — AI 自动提取的长期记忆
4. **性格与情绪系统**（开启时）：
   - 回复决策规则（高冷可不回、生气可不回）
   - `[[不回复]]` 标记格式
   - 禁止括号、禁止剧本式描写
   - 禁止重复回复
   - 可聊现实话题，不局限背景故事
   - 用户说「X秒后发消息」时只需确认，App 自动处理
   - `|||N秒|||` 语法支持多条定时消息

### 主动聊天 Prompt（buildProactivePrompt）
构建于 `ChatRepository.kt`，包含：
1. 角色身份设定
2. 主动消息类型：突发奇想、日常分享、半夜睡不着、情绪表达、完全跑题
3. 判定规则：高冷 → 少主动，活泼 → 必须主动
4. 禁止括号、禁止时间标注
5. 如果对方没回 → 不继续发（`[[不主动]]`）

---

## 六、全部 Bug 与修复记录

### Bug 1: Color.kt 5位 hex
- **问题**：`Color(0xFF7C5CF)` 只有 5 位，编译不通过
- **修复**：补为 `Color(0xFF7C5CF0)`

### Bug 2: ChatViewModel 被还原
- **问题**：某次修改导致 ChatViewModel 丢失全部功能
- **修复**：完全重写

### Bug 3: 底部导航栏遮挡
- **问题**：NavigationBar 的 padding 没传给子页面，输入框和 FAB 被导航栏挡住
- **修复**：`padding.calculateBottomPadding()` 传递给所有子 Screen

### Bug 4: 导入备份找不到 .json
- **问题**：MIME 类型 `application/json` 导致系统文件选择器不显示 .json 文件
- **修复**：改为 `arrayOf("*/*")`

### Bug 5: 导出/导入/清空全是 TODO
- **问题**：四个数据管理按钮全是空壳
- **修复**：全部实现完整逻辑

### Bug 6: SDK 路径错误
- **问题**：`local.properties` 指向错误的 SDK 路径
- **修复**：修正为 `D:/workbuddy默认/安的小本/daily-profit-app/build-env/android-sdk`

### Bug 7: SseClient 双重 Done
- **问题**：`[DONE]` 事件和 `onClosed` 回调都发送 Done
- **修复**：`onClosed` 不再重复发送

### Bug 8: 系统消息污染上下文
- **问题**：`🤐 对方沉默中...` 这类 system 消息被发给 AI
- **修复**：`streamResponseWithMessages` 中过滤 `role == "system"`

### Bug 9: 导入备份解析失败
- **问题**：旧版 WebView 备份的 `createdAt` 是 ISO 字符串，Entity 类型是 Long
- **修复**：`parseTimestamp()` 自动检测 String/Long 并转换

### Bug 10: ChatRepository.kt 编译失败（httpClient 被删除）
- **问题**：重构时删了 `httpClient` 但三个方法仍引用，加上混合 Gson/org.json 的 JsonArray 导致编译失败
- **修复**：改用 `sseClient.chatCompletion()`，删除全部手动 HTTP 代码

### Bug 11: 聊天框不能复制文字
- **问题**：Text 组件没有 SelectionContainer
- **修复**：`MessageBubble` 和 `SystemMessageBubble` 的 Text 外套 `SelectionContainer`

### Bug 12: 记忆提取不工作
- **问题**：`extractCoreMemory()` 和 `extractUserProfile()` 方法存在但从未被调用
- **修复**：`sendMessage()` 的 Done 分支添加每 10 轮自动触发

### Bug 13: [[不回复]] 判定不准确
- **问题**：精确匹配 `== "[[不回复]]"` 漏掉了带多余空格的回复
- **修复**：改为 `startsWith("[[不回复]]")`

### Bug 14: 导出文件路径不可删除
- **问题**：文件存在 `Android/data/...` 私有目录，用户无法访问
- **修复**：改为 SAF（系统文件选择器），用户自己选位置

### Bug 15: 导出覆盖同名文件
- **问题**：同一天多次导出会覆盖
- **修复**：同名文件自动追加 `(1)`, `(2)` 序号

### Bug 16: 导入覆盖现有数据
- **问题**：`repo.deleteAllData()` 清空全部已有数据
- **修复**：移除 deleteAllData，导入角色生成新 UUID，设为非活跃

### Bug 17: AI 括号内心戏
- **问题**：AI 用 `（哼）（扭头）（笑）` 等括号描述动作
- **修复**：代码层 `stripParentheses()` 暴力清洗 + Prompt 明确禁止

### Bug 18: AI 在一条消息里表演时间流逝
- **问题**：用户说"5秒后发消息"，AI 在同一条里写 `（5秒后）在吗`
- **修复**：Prompt 禁止时间标注 + `|||N秒|||` 语法支持真实延迟

### Bug 19: AI 复读（同样输入同样输出）
- **问题**：用户说两次「打你」，AI 回两次「你管我？」
- **修复**：流式去重 + 提醒 Prompt「禁止重复回复」

### Bug 20: AI 变其他角色口吻
- **问题**：胡桃用钟离的口吻说「那丫头」
- **修复**：Prompt 新增「你是角色本人，不是其他任何角色」

### Bug 21: AI 只聊背景故事，不聊现实
- **问题**：胡桃只会聊往生堂/提瓦特，不会聊现实话题
- **修复**：Prompt 新增「你可以聊现实世界的话题」

### Bug 22: 主动聊天一直发停不下来
- **问题**：用户不回复，AI 一直发
- **修复**：backoff 机制 + Prompt「对方没回就别发了」+ 主流程用 ||| 语法控制节奏

### Bug 23: 沉默模式误判
- **问题**：`isNoReply` 同时被[[不回复]]和去重使用导致 30s 沉默被错误触发
- **修复**：拆分为 `isNoReply` 和 `isDuplicateStreaming` 两个标志

### Bug 24: 沉默模式最终删除
- **问题**：沉默模式逻辑过于复杂，把控不住
- **修复**：整个沉默功能删除，回归简洁

### Bug 25: 预检测去重误杀
- **问题**：同样输入「笨」在不同上下文有不同含义，预检测直接跳过 API 调用不合理
- **修复**：预检测删除，仅保留流式输出去重

### Bug 26: 关闭主动聊天开关后 AI 仍在发消息
- **问题**：`tryProactiveMessage()` 依赖 `_uiState.proactiveChatEnabled` 判断开关状态，但该值由 `SharedPreferences.OnSharedPreferenceChangeListener` 更新。Android 上该监听器**不可靠**（可能不触发回调），导致 `_uiState` 里的值始终为 `true`，AI 继续发消息
- **修复**：`tryProactiveMessage()` 和 Done 处理器改为直接读取 `prefs.getBoolean("proactive_chat_enabled", false)`，不依赖 listener

### Bug 27: 开关关掉后定时器仍在空转
- **问题**：`observeData()` 中定时器无条件启动，`stopProactiveTimer()` 取消协程后 `CancellationException` 被 `catch (e: Exception)` 吞掉，协程未真正结束
- **修复**：
  - `observeData()` 改为先检查 `prefs` 再决定是否启动定时器
  - `catch` 改为只吞一般 `Exception`，`CancellationException` 重新抛出
  - 定时器循环内每次检查后再次读取 `prefs`，关闭时 `break` 退出循环
- **效果**：4 层独立保障确保开关关后定时器完全停止

---

## 七、当前仍存在的问题（未修）

| 问题 | 原因 | 建议 |
|------|------|------|
| 高冷角色依然每条都回 | AI 模型不遵守 Prompt 里的「可以不回复」指令 | 无解，换更强模型试试（GPT-4o） |
| 主动消息上下文不连贯 | 主动 API 调用独立于主对话流 | 加强提示词引导 |
| 流式去重仍会闪几个字 | 去重需要累积到完整匹配才能判定 | 可忍受，完全无延迟需复杂预测逻辑 |
| 导出文件不能自动打开/分享 | SAF 仅写入 URI，不触发打开 | 可加「分享」按钮 |

---

## 八、关键文件修改速查

| 修改点 | 涉及文件 | 说明 |
|--------|---------|------|
| SSE 流式回调 | `SseClient.kt` | OkHttp SSE + callbackFlow |
| 流式去重 | `ChatViewModel.kt` Delta/Done 分支 | 历史消息匹配 |
| stripParentheses | `ChatViewModel.kt` | 暴力清洗括号内容 |
| ||| 消息分割 | `ChatViewModel.kt` | 多条消息定时发送 |
| backoff 机制 | `ChatViewModel.kt` startProactiveTimer | 用户不回 → 拉长间隔 |
| 记忆提取 hook | `ChatViewModel.kt` Done 分支 | 每 10 轮触发 |
| System Prompt | `ChatRepository.kt` | 性格化 + 禁止括号 + 禁止重复 + 现实话题 |
| 主动 Prompt | `ChatRepository.kt` | 随机话题 + 停发逻辑 |
| 导出 SAF | `SettingsViewModel.kt` + `SettingsScreen.kt` | 系统文件选择器 |
| 导入共存 | `SettingsViewModel.kt` | 新 UUID + 非活跃 + ID 映射 |
| 时间戳显示 | `ChatScreen.kt` | 消息底部时间 |
| 文字复制 | `ChatScreen.kt` | SelectionContainer |
| 配色方案 | `Color.kt` | 8 套主题 |
| 角色 Prompt | `ChatRepository.kt` buildSystemPrompt | 整个性格化回复逻辑 |

---

## 九、备份文件格式

### 新版 Native 格式（version=1）
```json
{
  "app": "我的角色",
  "version": 1,
  "exportTime": 1719878400000,
  "personas": [{ "id": "uuid", "name": "...", "personaDoc": "...", ... }],
  "messages": [{ "id": 1, "personaId": "uuid", "role": "user", "content": "...", "timestamp": 1719878400000 }],
  "settings": { "api_keys": {...}, "active_provider": "deepseek", ... }
}
```

### 旧版 WebView 格式
- `createdAt` 为 ISO 字符串（如 `"2026-07-01T09:33:11.272Z"`）
- 消息在 `persona.history[]`，字段名 `time`
- 角色设定字段名 `persona` 而非 `personaDoc`
- 导入自动检测并转换
