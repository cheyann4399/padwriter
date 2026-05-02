package com.aivoice.input.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aivoice.input.model.WorldRule
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldRuleDao {
    @Query("SELECT * FROM WorldRule WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun getByProject(projectId: Long): Flow<List<WorldRule>>

    @Query("SELECT * FROM WorldRule WHERE ruleId = :ruleId")
    suspend fun getByRuleId(ruleId: String): WorldRule?

    @Insert
    suspend fun insert(rule: WorldRule): Long

    @Query("UPDATE WorldRule SET ruleId = :ruleId WHERE id = :id")
    suspend fun updateRuleId(id: Long, ruleId: String)

    @Query("UPDATE WorldRule SET content = :content, updatedAt = :updatedAt WHERE ruleId = :ruleId")
    suspend fun updateContent(ruleId: String, content: String, updatedAt: Long)

    @Query("DELETE FROM WorldRule WHERE ruleId = :ruleId")
    suspend fun deleteByRuleId(ruleId: String)
}
