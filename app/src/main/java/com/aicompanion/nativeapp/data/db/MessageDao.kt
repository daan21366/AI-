package com.aicompanion.nativeapp.data.db

import androidx.room.*
import com.aicompanion.nativeapp.data.model.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE personaId = :personaId ORDER BY timestamp ASC")
    fun getMessages(personaId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE personaId = :personaId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(personaId: String, limit: Int): List<MessageEntity>

    @Insert
    suspend fun insert(msg: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE personaId = :personaId")
    suspend fun deleteByPersona(personaId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE personaId = :personaId")
    suspend fun count(personaId: String): Int

    @Query("SELECT * FROM messages WHERE personaId = :personaId ORDER BY timestamp ASC")
    suspend fun getAllForPersona(personaId: String): List<MessageEntity>

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
