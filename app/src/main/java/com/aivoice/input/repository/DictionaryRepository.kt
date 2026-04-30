package com.aivoice.input.repository

import com.aivoice.input.db.DictionaryDao
import com.aivoice.input.model.DictionaryEntry
import kotlinx.coroutines.flow.Flow

class DictionaryRepository(private val dao: DictionaryDao) {

    fun getEnabledEntries(): Flow<List<DictionaryEntry>> = dao.getEnabled()

    suspend fun addEntry(original: String, replacement: String): Long {
        return dao.insert(DictionaryEntry(original = original, replacement = replacement))
    }

    suspend fun updateEntry(entry: DictionaryEntry) {
        dao.update(entry)
    }

    suspend fun deleteEntry(entry: DictionaryEntry) {
        dao.delete(entry)
    }
}
