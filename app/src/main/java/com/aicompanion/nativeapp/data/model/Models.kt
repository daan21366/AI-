package com.aicompanion.nativeapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personaId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val personaDoc: String = "",
    val coreMemory: String = "",
    val userProfile: String = "",
    val isActive: Boolean = false,
    val convCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
