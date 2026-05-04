package com.aivoice.input.repository

import androidx.room.withTransaction
import com.aivoice.input.db.AppDatabase
import com.aivoice.input.db.CharacterWithContext
import com.aivoice.input.db.WorldRuleWithContext
import com.aivoice.input.model.Outline
import com.aivoice.input.model.draft.ClassificationResult
import com.aivoice.input.model.draft.MappingDraft
import com.aivoice.input.model.enums.DraftAction
import com.aivoice.input.model.enums.SettingType
import com.aivoice.input.model.router.ExecutionResult

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
    private val mappingRepository: MappingRepository,
    private val beatRepository: BeatRepository
) {
    suspend fun loadBeatContext(beatId: String): BeatContext {
        return database.withTransaction {
            val characters = database.beatMappingDao().getCharactersForBeat(beatId)
            val worldRules = database.beatMappingDao().getWorldRulesForBeat(beatId)
            val outline = database.outlineDao().getActiveByBeat(beatId)

            android.util.Log.d("BeatContextService", "loadBeatContext: beatId=$beatId, characters=${characters.size}, worldRules=${worldRules.size}, outline=${outline != null}")

            BeatContext(
                beatId = beatId,
                characters = characters,
                worldRules = worldRules,
                outline = outline
            )
        }
    }

    /**
     * 保存新的统一执行结果
     * 支持人设/世界观全局关联
     * 返回创建的节拍 ID 列表
     */
    suspend fun saveExecutionResult(
        projectId: Long,
        result: ExecutionResult,
        existingBeatIds: List<String>
    ): List<String> {
        android.util.Log.d("BeatContextService", "saveExecutionResult: projectId=$projectId, existingBeatIds=${existingBeatIds.size}")
        android.util.Log.d("BeatContextService", "saveExecutionResult: characters=${result.characters?.created?.size}, worldRules=${result.worldRules?.created?.size}, outline=${result.outline != null}")

        var newBeatIds = listOf<String>()

        database.withTransaction {
            val charIdMap = mutableMapOf<String, String>()
            val ruleIdMap = mutableMapOf<String, String>()

            // 处理节拍变更，获取新创建的节拍 ID
            result.beats?.let {
                newBeatIds = saveBeatChangesAndReturnIds(projectId, it)
            }

            // 合并现有节拍 ID 和新创建的节拍 ID
            val allBeatIds = existingBeatIds + newBeatIds
            android.util.Log.d("BeatContextService", "allBeatIds: existing=${existingBeatIds.size}, new=${newBeatIds.size}, total=${allBeatIds.size}")
            android.util.Log.d("BeatContextService", "allBeatIds values: ${allBeatIds.joinToString()}")

            // 处理人设变更
            result.characters?.let { chars ->
                android.util.Log.d("BeatContextService", "Processing ${chars.created.size} characters to create")
                chars.created.forEach { draft ->
                    android.util.Log.d("BeatContextService", "Creating character: ${draft.name}")
                    val character = characterRepository.createCharacter(projectId, draft)
                    val key = "NEW_CHAR_${draft.name}"
                    charIdMap[key] = character.charId
                    android.util.Log.d("BeatContextService", "Created character: ${draft.name} -> ${character.charId}")
                }
                chars.updated.forEach { draft ->
                    characterRepository.updateCharacter(draft.targetId, draft.content)
                }
                chars.deleted.forEach { charId ->
                    characterRepository.deleteCharacter(charId)
                }
            }

            // 处理世界观变更
            result.worldRules?.let { rules ->
                android.util.Log.d("BeatContextService", "Processing ${rules.created.size} worldRules to create")
                rules.created.forEach { draft ->
                    android.util.Log.d("BeatContextService", "Creating worldRule: ${draft.title}")
                    val worldRule = worldRuleRepository.createWorldRule(projectId, draft)
                    val key = "NEW_RULE_${draft.title}"
                    ruleIdMap[key] = worldRule.ruleId
                    android.util.Log.d("BeatContextService", "Created worldRule: ${draft.title} -> ${worldRule.ruleId}")
                }
                rules.updated.forEach { draft ->
                    worldRuleRepository.updateWorldRule(draft.targetId, draft.content)
                }
                rules.deleted.forEach { ruleId ->
                    worldRuleRepository.deleteWorldRule(ruleId)
                }
            }

            // 处理大纲变更
            result.outline?.let { outline ->
                android.util.Log.d("BeatContextService", "Processing outline: beatId=${outline.beatId}")
                saveOutlineChanges(projectId, outline, newBeatIds.firstOrNull() ?: "")
            }

            // 处理映射关系
            if (result.mappings.isNotEmpty() && allBeatIds.isNotEmpty()) {
                android.util.Log.d("BeatContextService", "Processing ${result.mappings.size} mappings for ${allBeatIds.size} beats")

                // 创建节拍序号到实际 ID 的映射
                val beatIndexToId = allBeatIds.mapIndexed { index, beatId ->
                    "beat_${index + 1}" to beatId
                }.toMap()

                val correctedMappings = result.mappings.mapNotNull { draft ->
                    val correctedSettingId = when (draft.settingType) {
                        SettingType.CHARACTER -> charIdMap[draft.settingId] ?: draft.settingId
                        SettingType.WORLD_RULE -> ruleIdMap[draft.settingId] ?: draft.settingId
                        else -> draft.settingId
                    }

                    // 将 beat_1, beat_2 等转换为实际节拍 ID
                    val actualBeatId = when {
                        draft.beatId == "ALL" -> {
                            // 不再支持 ALL，跳过
                            android.util.Log.w("BeatContextService", "Skipping ALL mapping, use specific beat IDs")
                            return@mapNotNull null
                        }
                        draft.beatId.startsWith("beat_") || draft.beatId.startsWith("BEAT_") -> {
                            // 转换 beat_1 为实际节拍 ID
                            beatIndexToId[draft.beatId.lowercase()] ?: run {
                                android.util.Log.w("BeatContextService", "Unknown beat index: ${draft.beatId}")
                                return@mapNotNull null
                            }
                        }
                        else -> draft.beatId
                    }

                    MappingDraft(
                        beatId = actualBeatId,
                        settingType = draft.settingType,
                        settingId = correctedSettingId,
                        contextType = draft.contextType,
                        contextNote = draft.contextNote
                    )
                }

                android.util.Log.d("BeatContextService", "Saving ${correctedMappings.size} mappings")
                mappingRepository.saveMappings(projectId, correctedMappings)
            }

            // 处理词库
            if (result.glossary.isNotEmpty()) {
                val glossaryEntities = result.glossary.mapNotNull { draft ->
                    // 跳过没有 sourceId 的词库条目
                    if (draft.sourceId.isNullOrEmpty()) {
                        android.util.Log.w("BeatContextService", "Skipping glossary entry without sourceId: ${draft.word}")
                        return@mapNotNull null
                    }

                    val correctedSourceId = when (draft.type) {
                        com.aivoice.input.model.enums.GlossaryType.CHARACTER -> charIdMap[draft.sourceId] ?: draft.sourceId
                        com.aivoice.input.model.enums.GlossaryType.WORLD -> ruleIdMap[draft.sourceId] ?: draft.sourceId
                        else -> draft.sourceId
                    }

                    com.aivoice.input.model.Glossary(
                        projectId = projectId,
                        word = draft.word,
                        type = draft.type,
                        sourceId = correctedSourceId,
                        priority = draft.priority,
                        aliases = draft.aliases.joinToString(",")
                    )
                }
                if (glossaryEntities.isNotEmpty()) {
                    database.glossaryDao().insertAll(glossaryEntities)
                }
            }
        }

        return newBeatIds
    }

    private suspend fun saveBeatChangesAndReturnIds(projectId: Long, changes: com.aivoice.input.model.router.BeatChanges): List<String> {
        return when (changes.action) {
            "CREATE" -> {
                changes.beats.map { draft ->
                    val beat = beatRepository.createBeat(
                        projectId = projectId,
                        title = draft.title,
                        summary = draft.summary,
                        type = draft.type
                    )
                    beat.beatId
                }
            }
            "UPDATE" -> {
                changes.beats.firstOrNull()?.let { draft ->
                    beatRepository.updateBeat(changes.targetBeatId, draft.title, draft.summary)
                }
                emptyList()
            }
            "INSERT" -> {
                changes.beats.firstOrNull()?.let { draft ->
                    beatRepository.insertBeatAt(projectId, changes.position, draft)
                }
                emptyList()
            }
            "DELETE" -> {
                beatRepository.deleteBeat(changes.targetBeatId)
                emptyList()
            }
            else -> emptyList()
        }
    }

    private suspend fun saveOutlineChanges(projectId: Long, changes: com.aivoice.input.model.router.OutlineChanges, firstBeatId: String) {
        // 使用实际的节拍 ID
        val actualBeatId = if (changes.beatId.startsWith("BEAT_") || changes.beatId.startsWith("beat_")) {
            firstBeatId  // 使用第一个实际节拍 ID
        } else {
            changes.beatId
        }

        when (changes.action) {
            "CREATE" -> {
                outlineRepository.createOutline(projectId, actualBeatId, changes.content)
            }
            "UPDATE" -> {
                outlineRepository.updateOutlineByBeat(actualBeatId, changes.content)
            }
            "APPEND" -> {
                outlineRepository.appendToOutline(projectId, actualBeatId, changes.content, changes.appendPosition)
            }
        }
    }

    // 保留原有方法供兼容
    suspend fun saveClassificationResult(
        projectId: Long,
        result: ClassificationResult,
        currentBeatId: String
    ) {
        database.withTransaction {
            val charIdMap = mutableMapOf<String, String>()
            val ruleIdMap = mutableMapOf<String, String>()

            result.characters.forEach { draft ->
                when (draft.action) {
                    DraftAction.CREATE -> {
                        val character = characterRepository.createCharacter(projectId, draft)
                        val key = draft.targetId.ifEmpty { "NEW_CHAR_${draft.name}" }
                        charIdMap[key] = character.charId
                    }
                    DraftAction.UPDATE -> {
                        characterRepository.updateCharacter(draft.targetId, draft.content)
                    }
                    null -> {
                        val character = characterRepository.createCharacter(projectId, draft)
                        val key = "NEW_CHAR_${draft.name}"
                        charIdMap[key] = character.charId
                    }
                }
            }

            result.worldRules.forEach { draft ->
                when (draft.action) {
                    DraftAction.CREATE -> {
                        val worldRule = worldRuleRepository.createWorldRule(projectId, draft)
                        val key = draft.targetId.ifEmpty { "NEW_RULE_${draft.title}" }
                        ruleIdMap[key] = worldRule.ruleId
                    }
                    DraftAction.UPDATE -> {
                        worldRuleRepository.updateWorldRule(draft.targetId, draft.content)
                    }
                    null -> {
                        val worldRule = worldRuleRepository.createWorldRule(projectId, draft)
                        val key = "NEW_RULE_${draft.title}"
                        ruleIdMap[key] = worldRule.ruleId
                    }
                }
            }

            result.outline?.let {
                outlineRepository.createOrUpdateOutline(projectId, it)
            }

            if (result.mappings.isNotEmpty()) {
                val correctedMappings = result.mappings.map { draft ->
                    val correctedSettingId = when (draft.settingType) {
                        SettingType.CHARACTER -> {
                            if (draft.settingId.isNotEmpty()) {
                                charIdMap[draft.settingId] ?: draft.settingId
                            } else {
                                charIdMap.entries.firstOrNull { it.key.startsWith("NEW_CHAR_") }?.value ?: ""
                            }
                        }
                        SettingType.WORLD_RULE -> {
                            if (draft.settingId.isNotEmpty()) {
                                ruleIdMap[draft.settingId] ?: draft.settingId
                            } else {
                                ruleIdMap.entries.firstOrNull { it.key.startsWith("NEW_RULE_") }?.value ?: ""
                            }
                        }
                        else -> draft.settingId
                    }
                    draft.copy(settingId = correctedSettingId)
                }
                mappingRepository.saveMappings(projectId, correctedMappings)
            }
        }
    }
}
