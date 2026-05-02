package com.aivoice.input.repository

import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.BeatMapping
import com.aivoice.input.model.draft.MappingDraft

class MappingRepository(private val database: AppDatabase) {
    suspend fun saveMappings(projectId: Long, mappings: List<MappingDraft>) {
        val entities = mappings.map { draft ->
            BeatMapping(
                projectId = projectId,
                beatId = draft.beatId,
                settingType = draft.settingType,
                settingId = draft.settingId,
                contextType = draft.contextType,
                contextNote = draft.contextNote
            )
        }
        database.beatMappingDao().insertAll(entities)
    }

    suspend fun getMappingsForBeat(beatId: String): List<BeatMapping> =
        database.beatMappingDao().getByBeat(beatId)

    suspend fun deleteMappingsForBeat(beatId: String) {
        database.beatMappingDao().deleteByBeatId(beatId)
    }
}