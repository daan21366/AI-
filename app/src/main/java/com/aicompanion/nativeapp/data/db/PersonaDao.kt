package com.aicompanion.nativeapp.data.db

import androidx.room.*
import com.aicompanion.nativeapp.data.model.PersonaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas ORDER BY createdAt DESC")
    fun getAll(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): PersonaEntity?

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun getById(id: String): PersonaEntity?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(persona: PersonaEntity)

    @Update
    suspend fun update(persona: PersonaEntity)

    @Delete
    suspend fun delete(persona: PersonaEntity)

    @Query("UPDATE personas SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE personas SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)

    @Query("UPDATE personas SET convCount = convCount + 1 WHERE id = :id")
    suspend fun incrementConvCount(id: String)

    @Query("UPDATE personas SET coreMemory = :memory WHERE id = :id")
    suspend fun updateCoreMemory(id: String, memory: String)

    @Query("UPDATE personas SET userProfile = :profile WHERE id = :id")
    suspend fun updateUserProfile(id: String, profile: String)

    @Query("UPDATE personas SET personaDoc = :doc WHERE id = :id")
    suspend fun updatePersonaDoc(id: String, doc: String)

    @Query("SELECT * FROM personas ORDER BY createdAt DESC")
    suspend fun getAllSync(): List<PersonaEntity>

    @Query("DELETE FROM personas")
    suspend fun deleteAll()
}
