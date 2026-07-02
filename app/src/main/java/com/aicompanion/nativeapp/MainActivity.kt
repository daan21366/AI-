package com.aicompanion.nativeapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aicompanion.nativeapp.ui.screens.ChatScreen
import com.aicompanion.nativeapp.ui.screens.MemoryScreen
import com.aicompanion.nativeapp.ui.screens.PersonaScreen
import com.aicompanion.nativeapp.ui.screens.SettingsScreen
import com.aicompanion.nativeapp.ui.theme.AiCompanionTheme
import com.aicompanion.nativeapp.ui.theme.ThemePresets

data class BottomNavItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedTheme = prefs.getInt("current_theme", 0)

        setContent {
            var currentTheme by remember { mutableIntStateOf(savedTheme) }
            AiCompanionTheme(themeIndex = currentTheme) {
                MainScreen(
                    currentTheme = currentTheme,
                    onThemeChange = { themeIdx ->
                        currentTheme = themeIdx
                        prefs.edit().putInt("current_theme", themeIdx).apply()
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    currentTheme: Int = 0,
    onThemeChange: (Int) -> Unit = {},
) {
    val navItems = listOf(
        BottomNavItem("聊天", Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
        BottomNavItem("角色", Icons.Outlined.Face, Icons.Filled.Face),
        BottomNavItem("记忆", Icons.Outlined.Memory, Icons.Filled.Memory),
        BottomNavItem("设置", Icons.Outlined.Settings, Icons.Filled.Settings),
    )

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = ThemePresets.getOrElse(currentTheme) { ThemePresets[0] }.surface) {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                if (selectedTab == index) item.selectedIcon else item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        alwaysShowLabel = true,
                    )
                }
            }
        }
    ) { padding ->
        val bottomInset = padding.calculateBottomPadding()
        when (selectedTab) {
            0 -> ChatScreen(
                currentTheme = currentTheme,
                bottomInset = bottomInset,
                onNavigateToPersona = { selectedTab = 1 }
            )
            1 -> PersonaScreen(
                currentTheme = currentTheme,
                bottomInset = bottomInset
            )
            2 -> MemoryScreen(
                currentTheme = currentTheme,
                bottomInset = bottomInset
            )
            3 -> SettingsScreen(
                currentTheme = currentTheme,
                onThemeChange = onThemeChange,
                bottomInset = bottomInset
            )
        }
    }
}
