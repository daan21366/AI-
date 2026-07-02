package com.aicompanion.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aicompanion.nativeapp.data.model.PersonaEntity
import com.aicompanion.nativeapp.ui.theme.ThemePresets
import com.aicompanion.nativeapp.viewmodel.PersonaViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(
    currentTheme: Int = 0,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel: PersonaViewModel = viewModel()
) {
    val colors = if (currentTheme in ThemePresets.indices) ThemePresets[currentTheme] else ThemePresets[0]
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = Modifier.padding(bottom = bottomInset),
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateDialog() },
                containerColor = colors.primary,
                contentColor = colors.onUserBubble,
            ) {
                Icon(Icons.Default.Add, contentDescription = "创建角色")
            }
        }
    ) { padding ->
        val personas = state.personas

        if (personas.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = colors.textSecondary.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "还没有角色",
                        fontSize = 18.sp,
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "点击右下角 + 创建第一个角色",
                        fontSize = 14.sp,
                        color = colors.textSecondary.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(personas, key = { it.id }) { persona ->
                    PersonaCard(
                        persona = persona,
                        isActive = persona.isActive,
                        colors = colors,
                        onEdit = { viewModel.startEdit(persona) },
                        onActivate = { viewModel.switchToPersona(persona) },
                        onDelete = { viewModel.showDeleteConfirm(persona) }
                    )
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    // 创建/编辑弹窗 — 用 key 强制每次打开都是新的 composition
    if (state.showCreateDialog) {
        val dialogKey = state.editingPersona?.id ?: "create_${System.currentTimeMillis()}"
        key(dialogKey) {
            PersonaEditDialog(
                editingPersona = state.editingPersona,
                onDismiss = { viewModel.dismissCreateDialog() },
                onConfirm = { name, doc ->
                    if (state.editingPersona != null) {
                        viewModel.updatePersona(name, doc)
                    } else {
                        viewModel.createPersona(name, doc)
                    }
                }
            )
        }
    }

    // 删除确认弹窗
    state.deletingPersona?.let { persona ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirm() },
            title = { Text("确认删除") },
            text = { Text("确定要删除角色「${persona.name}」吗？\n\n该角色的所有聊天记录也会被一并删除，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deletePersona(persona) },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.danger)
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirm() }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun PersonaCard(
    persona: PersonaEntity,
    isActive: Boolean,
    colors: com.aicompanion.nativeapp.ui.theme.AppColors,
    onEdit: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, colors.primary) else null,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = persona.name.take(1),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = persona.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.success.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    "当前",
                                    fontSize = 11.sp,
                                    color = colors.success,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${persona.convCount} 轮对话 · ${formatDate(persona.createdAt)}",
                        fontSize = 12.sp,
                        color = colors.textSecondary.copy(alpha = 0.7f)
                    )
                }
            }

            // 角色设定预览
            if (persona.personaDoc.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = persona.personaDoc,
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isActive) {
                    TextButton(onClick = onActivate) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("启用", fontSize = 13.sp)
                    }
                }
                TextButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑", fontSize = 13.sp)
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.danger)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除", fontSize = 13.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonaEditDialog(
    editingPersona: PersonaEntity?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, personaDoc: String) -> Unit,
) {
    val isEditing = editingPersona != null
    var name by remember { mutableStateOf(editingPersona?.name ?: "") }
    var personaDoc by remember { mutableStateOf(editingPersona?.personaDoc ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEditing) "编辑角色" else "创建角色",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("角色名称") },
                    placeholder = { Text("给你的角色起个名字") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = personaDoc,
                    onValueChange = { personaDoc = it },
                    label = { Text("角色设定（avatar.md 格式）") },
                    placeholder = {
                        Text(
                            "# 基本信息\n" +
                            "姓名：\n" +
                            "年龄：\n" +
                            "性别：\n" +
                            "性格：\n\n" +
                            "# 背景故事\n\n" +
                            "# 说话风格\n\n" +
                            "# 与用户的关系\n"
                        )
                    },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, personaDoc) },
                enabled = name.isNotBlank()
            ) {
                Text(if (isEditing) "保存" else "创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
}
