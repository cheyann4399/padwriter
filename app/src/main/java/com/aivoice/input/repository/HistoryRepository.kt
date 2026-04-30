package com.aivoice.input.repository

import com.aivoice.input.db.HistoryDao
import com.aivoice.input.model.HistoryItem
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: HistoryDao) {

    fun getAll(): Flow<List<HistoryItem>> = dao.getAll()

    suspend fun add(item: HistoryItem): Long = dao.insert(item)

    suspend fun delete(item: HistoryItem) = dao.delete(item)

    suspend fun deleteAll() = dao.deleteAll()
}
