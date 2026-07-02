package com.aicompanion.nativeapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aicompanion.nativeapp.data.model.MessageEntity
import com.aicompanion.nativeapp.ui.theme.ThemePresets
import com.aicompanion.nativeapp.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentTheme: Int = 0,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    onNavigateToPersona: () -> Unit = {},
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val colors = ThemePresets.getOrElse(currentTheme) { ThemePresets[0] }

    // Auto-scroll to bottom
    LaunchedEffect(uiState.messages.size, uiState.streamingContent) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size)
        }
    }

    // Error auto-dismiss
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            kotlinx.coroutines.delay(5000)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colors.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = uiState.activePersona?.name ?: "我的角色",
                            fontWeight = FontWeight.W500,
                            fontSize = 16.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.textPrimary
                )
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        when {
            // State 1: No personas at all
            uiState.personas.isEmpty() && uiState.activePersona == null -> {
                EmptyChatState(
                    modifier = Modifier.padding(innerPadding),
                    onNavigateToPersona = onNavigateToPersona
                )
            }
            // State 2: Personas exist but no active one (shouldn't normally happen, but handle gracefully)
            uiState.activePersona == null -> {
                NoActivePersonaState(
                    modifier = Modifier.padding(innerPadding),
                    onNavigateToPersona = onNavigateToPersona
                )
            }
            // State 3: Normal chat
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Message list
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        reverseLayout = false,
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp,
                            top = innerPadding.calculateTopPadding() + 8.dp,
                            bottom = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { msg ->
                            when (msg.role) {
                                "system" -> {
                                    SystemMessageBubble(msg = msg, colors = colors)
                                }
                                else -> {
                                    MessageBubble(msg = msg, colors = colors)
                                }
                            }
                        }

                        // Streaming indicator (content arriving)
                        if (uiState.isStreaming && uiState.streamingContent.isNotEmpty()) {
                            item(key = "streaming") {
                                StreamingBubble(
                                    content = uiState.streamingContent,
                                    colors = colors
                                )
                            }
                        }

                        // Typing indicator (waiting for first token)
                        if (uiState.isStreaming && uiState.streamingContent.isEmpty()) {
                            item(key = "typing") {
                                TypingIndicator(colors = colors)
                            }
                        }
                    }

                    // Error banner
                    uiState.error?.let { error ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.danger.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = error,
                                    fontSize = 13.sp,
                                    color = colors.danger,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.dismissError() }) {
                                    Text("关闭", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Input area — bottomInset from outer Scaffold to clear the NavigationBar
                    ChatInputBar(
                        text = uiState.inputText,
                        onTextChange = { viewModel.onInputChange(it) },
                        onSend = { viewModel.sendMessage() },
                        isStreaming = uiState.isStreaming,
                        enabled = uiState.activePersona != null,
                        colors = colors,
                        modifier = Modifier.padding(bottom = bottomInset)
                    )
                }
            }
        }
    }
}

@Composable
fun SystemMessageBubble(msg: MessageEntity, colors: com.aicompanion.nativeapp.ui.theme.AppColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 24.dp),
            shape = RoundedCornerShape(12.dp),
            color = colors.card.copy(alpha = 0.5f),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                SelectionContainer {
                    Text(
                        text = msg.content,
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = colors.textSecondary.copy(alpha = 0.7f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: MessageEntity, colors: com.aicompanion.nativeapp.ui.theme.AppColors) {
    val isUser = msg.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(if (isUser) colors.userBubble else colors.aiBubble)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    SelectionContainer {
                        Text(
                            text = msg.content,
                            color = if (isUser) colors.onUserBubble else colors.onAiBubble,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatTimestamp(msg.timestamp),
                        fontSize = 10.sp,
                        color = (if (isUser) colors.onUserBubble else colors.onAiBubble).copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun StreamingBubble(content: String, colors: com.aicompanion.nativeapp.ui.theme.AppColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .background(colors.aiBubble)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = content,
                    color = colors.onAiBubble,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "正在输入...",
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
fun TypingIndicator(colors: com.aicompanion.nativeapp.ui.theme.AppColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .background(colors.aiBubble)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.onAiBubble.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isStreaming: Boolean,
    enabled: Boolean,
    colors: com.aicompanion.nativeapp.ui.theme.AppColors,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = colors.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (enabled) "输入消息..." else "请先选择角色",
                        fontSize = 14.sp,
                        color = colors.textSecondary
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.card,
                    unfocusedContainerColor = colors.card,
                    focusedBorderColor = colors.primary.copy(alpha = 0.4f),
                    unfocusedBorderColor = colors.cardStroke,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.primary
                ),
                shape = RoundedCornerShape(21.dp),
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = enabled && text.isNotBlank() && !isStreaming,
                modifier = Modifier.size(42.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.primary,
                    disabledContainerColor = colors.primary.copy(alpha = 0.3f)
                ),
                shape = CircleShape
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.textPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyChatState(
    modifier: Modifier = Modifier,
    onNavigateToPersona: () -> Unit = {}
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "还没有角色",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "去「角色」页面创建你的第一个 AI 角色",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onNavigateToPersona) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("去创建角色")
            }
        }
    }
}

@Composable
fun NoActivePersonaState(
    modifier: Modifier = Modifier,
    onNavigateToPersona: () -> Unit = {}
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "请先选择一个角色",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "去「角色」页面点击「启用」选择一个角色开始聊天",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onNavigateToPersona) {
                Text("选择角色")
            }
        }
    }
}

/**
 * 将时间戳格式化为可读时间。
 * 今天的消息显示 HH:mm，非今天的显示 MM-dd HH:mm。
 */
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    val msgDate = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    return if (msgDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) &&
        msgDate.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    ) {
        sdf.format(Date(timestamp))
    } else {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
