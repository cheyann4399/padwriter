package com.aivoice.input.model.draft

data class ClassificationResult(
    val characters: List<CharacterDraft>,
    val outline: OutlineDraft?,
    val worldRules: List<WorldRuleDraft>,
    val mappings: List<MappingDraft>,
    val conflictCheck: com.aivoice.input.ai.ConflictCheck? = null
)
