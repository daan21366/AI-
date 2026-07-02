package com.aicompanion.nativeapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aicompanion.nativeapp.network.ProviderPresets
import com.aicompanion.nativeapp.ui.theme.ThemePresets
import com.aicompanion.nativeapp.viewmodel.SettingsUiState
import com.aicompanion.nativeapp.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    currentTheme: Int = 0,
    onThemeChange: (Int) -> Unit = {},
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    val viewModel: SettingsViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDialog by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }

    // File picker for import - only JSON files
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importData(it) }
    }

    // Show snackbar for toast messages
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.dismissToast()
        }
    }

    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("确认清空") },
            text = { Text("将删除所有角色、聊天记录和数据。此操作不可撤销！") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clearAllData()
                }) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.padding(bottom = bottomInset),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                "${state.totalPersonas} 个角色 · ${state.totalMessages} 条消息",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // ── API 提供商 ──
            SectionTitle("API 提供商")
            Spacer(modifier = Modifier.height(8.dp))

            // Provider selector dropdown
            var dropdownExpanded by remember { mutableStateOf(false) }
            val config = ProviderPresets.getProvider(state.selectedProvider)

            Box {
                OutlinedCard(
                    onClick = { dropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(config.name, style = MaterialTheme.typography.bodyLarge)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    ProviderPresets.presets.forEach { (key, cfg) ->
                        DropdownMenuItem(
                            text = { Text(cfg.name) },
                            onClick = {
                                viewModel.selectProvider(key)
                                dropdownExpanded = false
                            },
                            leadingIcon = {
                                if (key == state.selectedProvider) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // API Key
            val currentApiKey = state.apiKeys[state.selectedProvider] ?: ""
            OutlinedTextField(
                value = currentApiKey,
                onValueChange = { viewModel.updateApiKey(state.selectedProvider, it) },
                label = { Text("API Key") },
                placeholder = { Text("在此输入 ${config.name} 的 API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showApiKey) "隐藏" else "显示"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Test connection button
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.testConnection()
                },
                enabled = !state.testingConnection,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.testingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("测试中...")
                } else {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("测试连接")
                }
            }

            // 测试结果反馈（内联显示，比 Snackbar 更直观）
            val testResult = state.testResult
            val testSuccess = state.testSuccess
            if (testResult != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (testSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (testSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (testSuccess) Color(0xFF4CAF50) else Color(0xFFE53935),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            testResult,
                            fontSize = 13.sp,
                            color = if (testSuccess) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
            }

            // Custom provider inputs
            if (state.selectedProvider == "custom") {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.customBaseUrl,
                    onValueChange = { viewModel.updateCustomBaseUrl(it) },
                    label = { Text("API 地址") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.customModel,
                    onValueChange = { viewModel.updateCustomModel(it) },
                    label = { Text("模型名称") },
                    placeholder = { Text("gpt-4o-mini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
            }

            Divider(modifier = Modifier.padding(vertical = 24.dp))

            // ── 聊天设置 ──
            SectionTitle("聊天设置")
            Spacer(modifier = Modifier.height(8.dp))

            // Proactive chat toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "主动聊天",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "AI角色会在合适的时机主动发消息给你，活泼外向的角色更爱主动聊天，生气时也会闹脾气不说话",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = state.proactiveChatEnabled,
                        onCheckedChange = { viewModel.toggleProactiveChat() }
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 24.dp))

            // ── 主题配色 ──
            SectionTitle("主题配色")
            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(((ThemePresets.size + 3) / 4 * 90).dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = false
            ) {
                itemsIndexed(ThemePresets) { index, colors ->
                    ThemeColorCard(
                        colors = colors,
                        isSelected = index == currentTheme,
                        onClick = { onThemeChange(index) }
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 24.dp))

            // ── 数据管理 ──
            SectionTitle("数据管理")
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // SAF 保存文件，用户选位置，随时可删
                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri != null) {
                        viewModel.exportDataToUri(uri)
                    }
                }

                Button(
                    onClick = {
                        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        val fileName = "ai-companion-backup-${dateFormat.format(java.util.Date())}.json"
                        exportLauncher.launch(fileName)
                    },
                    enabled = !state.exporting && !state.importing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.exporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("导出备份")
                }

                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    enabled = !state.exporting && !state.importing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.importing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("导入备份")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("清空所有数据")
                }
            }

            // Show latest export path
            if (state.exportedPath.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "上次导出: ${state.exportedPath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }

            Spacer(modifier = Modifier.height(80.dp)) // bottom padding for nav bar
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ThemeColorCard(
    colors: com.aicompanion.nativeapp.ui.theme.AppColors,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) colors.primary else Color.Transparent
    val checkColor = if (isSelected) colors.primary else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(colors.primary.copy(alpha = 0.3f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.userBubble)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 30.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.aiBubble)
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(checkColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            colors.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) colors.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1
        )
    }
}

@Composable
private fun Divider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )
}
