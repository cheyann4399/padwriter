package com.aivoice.input.repository

import androidx.room.withTransaction
import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.Outline
import com.aivoice.input.model.draft.OutlineDraft
import kotlinx.coroutines.flow.Flow

class OutlineRepository(private val database: AppDatabase) {
    suspend fun getActiveOutline(beatId: String): Outline? =
        database.outlineDao().getActiveByBeat(beatId)

    fun getOutlinesForProject(projectId: Long): Flow<List<Outline>> =
        database.outlineDao().getByProject(projectId)

    suspend fun createOrUpdateOutline(projectId: Long, draft: OutlineDraft) {
        database.withTransaction {
            database.outlineDao().deactivateByBeat(draft.beatId)
            val currentVersion = database.outlineDao().getMaxVersion(draft.beatId) ?: 0

            val outline = Outline(
                projectId = projectId,
                beatId = draft.beatId,
                version = currentVersion + 1,
                isActive = true,
                content = draft.content,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            database.outlineDao().insert(outline)
        }
    }
}