package com.aicompanion.nativeapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicompanion.nativeapp.data.db.AppDatabase
import com.aicompanion.nativeapp.data.model.PersonaEntity
import com.aicompanion.nativeapp.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MemoryUiState(
    val activePersona: PersonaEntity? = null,
    val convCount: Int = 0,
    val coreMemory: String = "",
    val userProfile: String = "",
    val personaDoc: String = "",
    val loaded: Boolean = false,
)

class MemoryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repo = ChatRepository(db)

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // 监听所有角色 → 找到激活的那个 → 加载其记忆数据
            repo.getAllPersonas().collect { personas ->
                val active = personas.firstOrNull { it.isActive }
                if (active != null) {
                    _uiState.update {
                        it.copy(
                            activePersona = active,
                            convCount = active.convCount,
                            coreMemory = active.coreMemory,
                            userProfile = active.userProfile,
                            personaDoc = active.personaDoc,
                            loaded = true,
                        )
                    }
                } else {
                    _uiState.update { it.copy(loaded = true, activePersona = null) }
                }
            }
        }
    }
}
