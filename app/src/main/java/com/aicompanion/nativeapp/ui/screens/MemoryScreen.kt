package com.aicompanion.nativeapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aicompanion.nativeapp.ui.theme.ThemePresets
import com.aicompanion.nativeapp.viewmodel.MemoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MemoryScreen(
    currentTheme: Int = 0,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: MemoryViewModel = viewModel()
) {
    val colors = ThemePresets.getOrElse(currentTheme) { ThemePresets[0] }
    val state by viewModel.uiState.collectAsState()

    if (!state.loaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = colors.primary)
        }
        return
    }

    val persona = state.activePersona
    if (persona == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = colors.textSecondary.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("请先在角色页面创建角色", color = colors.textSecondary, fontSize = 15.sp)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 头部：角色名 + 对话统计
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.card),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "角色：${persona.name}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    AssistChip(
                        onClick = {},
                        label = { Text("${persona.convCount} 轮对话") },
                        leadingIcon = {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text("始于 ${formatDate(persona.createdAt)}") },
                        leadingIcon = {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
        }

        // 角色设定
        if (persona.personaDoc.isNotBlank()) {
            SectionCard(
                title = "角色设定文档",
                icon = Icons.Default.Description,
                content = persona.personaDoc,
                colors = colors,
            )
        }

        // 核心记忆
        SectionCard(
            title = "核心记忆",
            icon = Icons.Default.Memory,
            content = if (persona.coreMemory.isNotBlank()) persona.coreMemory else "暂无记忆数据。\n\n每 10 轮对话后，AI 会自动从聊天中提取要点存入核心记忆。",
            colors = colors,
            isDimmed = persona.coreMemory.isBlank(),
        )

        // 用户画像
        SectionCard(
            title = "用户画像",
            icon = Icons.Default.Face,
            content = if (persona.userProfile.isNotBlank()) persona.userProfile else "暂无用户画像。\n\nAI 会根据你们的对话自动分析你的偏好和特点。",
            colors = colors,
            isDimmed = persona.userProfile.isBlank(),
        )

        // 人格进化说明
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.card),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "人格进化机制",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "每进行约 50 轮对话后，AI 会根据你们的聊天内容和你的偏好，自动微调角色设定和回复风格，让角色随着时间推移「长大」。",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    lineHeight = 19.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: String,
    colors: com.aicompanion.nativeapp.ui.theme.AppColors,
    isDimmed: Boolean = false,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = content,
                fontSize = 13.sp,
                color = if (isDimmed) colors.textSecondary.copy(alpha = 0.5f) else colors.textSecondary,
                lineHeight = 19.sp
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
}
