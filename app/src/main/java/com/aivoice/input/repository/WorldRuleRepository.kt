package com.aivoice.input.repository

import androidx.room.withTransaction
import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.WorldRule
import com.aivoice.input.model.draft.WorldRuleDraft
import com.aivoice.input.model.enums.SettingType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class WorldRuleRepository(private val database: AppDatabase) {
    fun getWorldRulesForProject(projectId: Long): Flow<List<WorldRule>> =
        database.worldRuleDao().getByProject(projectId)

    suspend fun createWorldRule(projectId: Long, draft: WorldRuleDraft): WorldRule {
        val rule = WorldRule(
            projectId = projectId,
            ruleId = UUID.randomUUID().toString().take(8),
            title = draft.title,
            content = draft.content,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val id = database.worldRuleDao().insert(rule)
        return rule.copy(id = id)
    }

    suspend fun updateWorldRule(ruleId: String, content: String) {
        database.worldRuleDao().updateContent(
            ruleId = ruleId,
            content = content,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun deleteWorldRule(ruleId: String) {
        database.withTransaction {
            database.worldRuleDao().deleteByRuleId(ruleId)
            database.beatMappingDao().deleteBySettingId(ruleId, SettingType.WORLD_RULE)
            database.glossaryDao().deleteBySourceId(ruleId)
        }
    }
}