package com.aivoice.input.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aivoice.input.model.Character
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM Character WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun getByProject(projectId: Long): Flow<List<Character>>

    @Query("SELECT * FROM Character WHERE charId = :charId")
    suspend fun getByCharId(charId: String): Character?

    @Insert
    suspend fun insert(character: Character): Long

    @Query("UPDATE Character SET charId = :charId WHERE id = :id")
    suspend fun updateCharId(id: Long, charId: String)

    @Query("UPDATE Character SET content = :content, updatedAt = :updatedAt WHERE charId = :charId")
    suspend fun updateContent(charId: String, content: String, updatedAt: Long)

    @Query("DELETE FROM Character WHERE charId = :charId")
    suspend fun deleteByCharId(charId: String)
}
