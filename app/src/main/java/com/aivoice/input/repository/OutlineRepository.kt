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

    suspend fun createOutline(projectId: Long, beatId: String, content: String) {
        val outline = Outline(
            projectId = projectId,
            beatId = beatId,
            version = 1,
            isActive = true,
            content = content,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        database.outlineDao().insert(outline)
    }

    suspend fun updateOutlineByBeat(beatId: String, content: String) {
        database.outlineDao().updateContentByBeat(beatId, content, System.currentTimeMillis())
    }

    suspend fun appendToOutline(projectId: Long, beatId: String, content: String, position: String) {
        database.withTransaction {
            val existing = database.outlineDao().getActiveByBeat(beatId)
            if (existing != null) {
                val newContent = if (position == "START") {
                    content + "\n" + existing.content
                } else {
                    existing.content + "\n" + content
                }
                database.outlineDao().updateContentByBeat(beatId, newContent, System.currentTimeMillis())
            } else {
                createOutline(projectId, beatId, content)
            }
        }
    }
}