package com.aicompanion.nativeapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicompanion.nativeapp.data.db.AppDatabase
import com.aicompanion.nativeapp.data.model.PersonaEntity
import com.aicompanion.nativeapp.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PersonaUiState(
    val personas: List<PersonaEntity> = emptyList(),
    val activePersona: PersonaEntity? = null,
    val showCreateDialog: Boolean = false,
    val editingPersona: PersonaEntity? = null,
    val deletingPersona: PersonaEntity? = null,
    val snackbarMessage: String? = null,
)

class PersonaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repo = ChatRepository(db)
    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(PersonaUiState())
    val uiState: StateFlow<PersonaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getAllPersonas().collect { personas ->
                _uiState.update {
                    it.copy(
                        personas = personas,
                        activePersona = personas.firstOrNull { p -> p.isActive }
                    )
                }
            }
        }
    }

    fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true, editingPersona = null) }
    }

    fun dismissCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false, editingPersona = null) }
    }

    fun startEdit(persona: PersonaEntity) {
        _uiState.update { it.copy(showCreateDialog = true, editingPersona = persona) }
    }

    fun showDeleteConfirm(persona: PersonaEntity) {
        _uiState.update { it.copy(deletingPersona = persona) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(deletingPersona = null) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun createPersona(name: String, personaDoc: String) {
        if (name.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "名称不能为空") }
            return
        }
        viewModelScope.launch {
            try {
                val persona = repo.createPersona(name.trim(), personaDoc.trim())
                prefs.edit().putString("active_persona_id", persona.id).apply()
                _uiState.update { it.copy(showCreateDialog = false, snackbarMessage = "角色「$name」已创建") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "创建失败: ${e.message}") }
            }
        }
    }

    fun updatePersona(name: String, personaDoc: String) {
        val editing = _uiState.value.editingPersona ?: return
        if (name.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "名称不能为空") }
            return
        }
        viewModelScope.launch {
            try {
                repo.updatePersona(editing.copy(name = name.trim(), personaDoc = personaDoc.trim()))
                _uiState.update { it.copy(showCreateDialog = false, editingPersona = null, snackbarMessage = "已更新") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "更新失败: ${e.message}") }
            }
        }
    }

    fun deletePersona(persona: PersonaEntity) {
        viewModelScope.launch {
            try {
                repo.deletePersona(persona)
                // If we deleted the active persona, switch to another
                if (persona.isActive) {
                    val remaining = _uiState.value.personas.filter { it.id != persona.id }
                    if (remaining.isNotEmpty()) {
                        repo.switchActivePersona(remaining.first().id)
                        prefs.edit().putString("active_persona_id", remaining.first().id).apply()
                    } else {
                        prefs.edit().remove("active_persona_id").apply()
                    }
                }
                _uiState.update { it.copy(deletingPersona = null, snackbarMessage = "已删除「${persona.name}」") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "删除失败: ${e.message}") }
            }
        }
    }

    fun switchToPersona(persona: PersonaEntity) {
        viewModelScope.launch {
            try {
                repo.switchActivePersona(persona.id)
                prefs.edit().putString("active_persona_id", persona.id).apply()
                _uiState.update { it.copy(snackbarMessage = "已切换到「${persona.name}」") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "切换失败: ${e.message}") }
            }
        }
    }
}
