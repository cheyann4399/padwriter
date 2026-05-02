package com.aivoice.input.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aivoice.input.model.Glossary
import kotlinx.coroutines.flow.Flow

@Dao
interface GlossaryDao {
    @Query("SELECT * FROM Glossary WHERE projectId = :projectId ORDER BY priority DESC, word ASC")
    fun getByProject(projectId: Long): Flow<List<Glossary>>

    @Query("SELECT * FROM Glossary WHERE projectId = :projectId ORDER BY priority DESC, word ASC")
    suspend fun getByProjectOnce(projectId: Long): List<Glossary>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(item: Glossary)

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<Glossary>)

    @Query("DELETE FROM Glossary WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long)

    @Query("DELETE FROM Glossary WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)
}
