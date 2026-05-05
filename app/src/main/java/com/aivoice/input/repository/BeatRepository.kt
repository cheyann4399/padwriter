package com.aivoice.input.repository

import androidx.room.withTransaction
import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.Beat
import com.aivoice.input.model.enums.BeatType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BeatRepository(private val database: AppDatabase) {
    fun getBeatsForProject(projectId: Long): Flow<List<Beat>> =
        database.beatDao().getByProject(projectId)

    suspend fun createBeat(
        projectId: Long,
        title: String,
        summary: String,
        type: BeatType
    ): Beat {
        return database.withTransaction {
            val maxOrder = database.beatDao().getMaxOrder(projectId) ?: 0
            val beat = Beat(
                projectId = projectId,
                beatId = UUID.randomUUID().toString().take(8),
                title = title,
                summary = summary,
                type = type,
                order = maxOrder + 1,
                createdAt = System.currentTimeMillis()
            )
            val id = database.beatDao().insert(beat)
            beat.copy(id = id)
        }
    }

    suspend fun deleteBeat(beatId: String) {
        database.withTransaction {
            val beat = database.beatDao().getByBeatId(beatId) ?: return@withTransaction
            val projectId = beat.projectId

            database.beatDao().deleteByBeatId(beatId)
            database.beatMappingDao().deleteByBeatId(beatId)
            database.outlineDao().deleteByBeatId(beatId)
            database.beatDao().shiftOrderAfterDelete(projectId, beat.order)
        }
    }

    suspend fun getNextBeat(currentBeatId: String): Beat? =
        database.beatDao().getNextBeat(currentBeatId)

    suspend fun reorderBeats(projectId: Long, beatIds: List<String>) {
        database.withTransaction {
            beatIds.forEachIndexed { index, beatId ->
                database.beatDao().updateOrder(beatId, index + 1)
            }
        }
    }

    suspend fun updateBeat(beatId: String, title: String, summary: String) {
        database.beatDao().updateTitleAndSummary(beatId, title, summary)
    }

    suspend fun insertBeatAt(projectId: Long, position: Int, draft: com.aivoice.input.model.draft.BeatDraft) {
        database.withTransaction {
            // 先将后续节拍的 order 都加 1
            database.beatDao().shiftOrderAfterInsert(projectId, position)
            // 然后插入新节拍
            val beat = Beat(
                projectId = projectId,
                beatId = UUID.randomUUID().toString().take(8),
                title = draft.title,
                summary = draft.summary,
                type = draft.type,
                order = position,
                createdAt = System.currentTimeMillis()
            )
            database.beatDao().insert(beat)
        }
    }
}