package com.aivoice.input.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aivoice.input.model.BeatMapping
import com.aivoice.input.model.enums.SettingType

@Dao
interface BeatMappingDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<BeatMapping>)

    @Query("DELETE FROM BeatMapping WHERE beatId = :beatId")
    suspend fun deleteByBeatId(beatId: String)

    @Query("DELETE FROM BeatMapping WHERE settingId = :settingId AND settingType = :settingType")
    suspend fun deleteBySettingId(settingId: String, settingType: SettingType)

    @Query("SELECT * FROM BeatMapping WHERE beatId = :beatId")
    suspend fun getByBeat(beatId: String): List<BeatMapping>

    @Query("""
        SELECT c.*, m.contextType, m.contextNote, m.isActive
        FROM Character c
        INNER JOIN BeatMapping m ON m.settingId = c.charId
        WHERE m.beatId = :beatId AND m.settingType = 'CHARACTER'
        ORDER BY m.isActive DESC
    """)
    suspend fun getCharactersForBeat(beatId: String): List<CharacterWithContext>

    @Query("""
        SELECT w.*, m.contextType, m.contextNote, m.isActive
        FROM WorldRule w
        INNER JOIN BeatMapping m ON m.settingId = w.ruleId
        WHERE m.beatId = :beatId AND m.settingType = 'WORLD_RULE'
        ORDER BY m.isActive DESC
    """)
    suspend fun getWorldRulesForBeat(beatId: String): List<WorldRuleWithContext>

    @Query("UPDATE BeatMapping SET isActive = :isActive WHERE beatId = :beatId AND settingId = :settingId AND settingType = :settingType")
    suspend fun updateActiveState(beatId: String, settingId: String, settingType: SettingType, isActive: Boolean)
}

// Helper data classes for queries
data class CharacterWithContext(
    val id: Long,
    val projectId: Long,
    val charId: String,
    val name: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val contextType: com.aivoice.input.model.enums.ContextType,
    val contextNote: String,
    val isActive: Boolean
)

data class WorldRuleWithContext(
    val id: Long,
    val projectId: Long,
    val ruleId: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val contextType: com.aivoice.input.model.enums.ContextType,
    val contextNote: String,
    val isActive: Boolean
)
