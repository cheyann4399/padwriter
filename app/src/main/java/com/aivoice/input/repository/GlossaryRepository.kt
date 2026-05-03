package com.aivoice.input.repository

import androidx.room.withTransaction
import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.DictionaryEntry
import com.aivoice.input.model.Glossary
import com.aivoice.input.model.draft.GlossaryDraft
import com.aivoice.input.model.enums.GlossaryPriority
import kotlinx.coroutines.flow.Flow

class GlossaryRepository(private val database: AppDatabase) {
    fun getGlossaryForProject(projectId: Long): Flow<List<Glossary>> =
        database.glossaryDao().getByProject(projectId)

    suspend fun saveGlossary(projectId: Long, items: List<GlossaryDraft>) {
        database.withTransaction {
            items.forEach { draft ->
                database.glossaryDao().insertIgnore(
                    Glossary(
                        projectId = projectId,
                        word = draft.word,
                        type = draft.type,
                        sourceId = draft.sourceId,
                        priority = draft.priority
                    )
                )
            }
        }
    }

    suspend fun syncToDictionary(projectId: Long) {
        database.withTransaction {
            val glossary = database.glossaryDao().getByProjectOnce(projectId)
            val entries = glossary.map { item ->
                DictionaryEntry(
                    original = item.word,
                    replacement = item.word
                )
            }
            database.dictionaryDao().insertAll(entries)
        }
    }

    suspend fun deleteGlossaryForProject(projectId: Long) {
        database.glossaryDao().deleteByProject(projectId)
    }
}