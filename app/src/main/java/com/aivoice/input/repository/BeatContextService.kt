package com.aivoice.input.repository

import androidx.room.withTransaction
import com.aivoice.input.db.AppDatabase
import com.aivoice.input.db.CharacterWithContext
import com.aivoice.input.db.WorldRuleWithContext
import com.aivoice.input.model.Outline
import com.aivoice.input.model.draft.ClassificationResult

data class BeatContext(
    val beatId: String,
    val characters: List<CharacterWithContext>,
    val worldRules: List<WorldRuleWithContext>,
    val outline: Outline?
)

class BeatContextService(
    private val database: AppDatabase,
    private val characterRepository: CharacterRepository,
    private val worldRuleRepository: WorldRuleRepository,
    private val outlineRepository: OutlineRepository,
    private val mappingRepository: MappingRepository
) {
    suspend fun loadBeatContext(beatId: String): BeatContext {
        return database.withTransaction {
            val characters = database.beatMappingDao().getCharactersForBeat(beatId)
            val worldRules = database.beatMappingDao().getWorldRulesForBeat(beatId)
            val outline = database.outlineDao().getActiveByBeat(beatId)

            BeatContext(
                beatId = beatId,
                characters = characters,
                worldRules = worldRules,
                outline = outline
            )
        }
    }

    suspend fun saveClassificationResult(
        projectId: Long,
        result: ClassificationResult,
        currentBeatId: String
    ) {
        database.withTransaction {
            result.characters.forEach { draft ->
                when (draft.action) {
                    com.aivoice.input.model.enums.DraftAction.CREATE -> {
                        characterRepository.createCharacter(projectId, draft)
                    }
                    com.aivoice.input.model.enums.DraftAction.UPDATE -> {
                        characterRepository.updateCharacter(draft.targetId, draft.content)
                    }
                }
            }

            result.worldRules.forEach { draft ->
                when (draft.action) {
                    com.aivoice.input.model.enums.DraftAction.CREATE -> {
                        worldRuleRepository.createWorldRule(projectId, draft)
                    }
                    com.aivoice.input.model.enums.DraftAction.UPDATE -> {
                        worldRuleRepository.updateWorldRule(draft.targetId, draft.content)
                    }
                }
            }

            result.outline?.let {
                outlineRepository.createOrUpdateOutline(projectId, it)
            }

            if (result.mappings.isNotEmpty()) {
                mappingRepository.saveMappings(projectId, result.mappings)
            }
        }
    }
}