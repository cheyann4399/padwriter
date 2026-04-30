package com.aivoice.input.db

import androidx.room.*
import com.aivoice.input.model.DictionaryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary WHERE enabled = 1 ORDER BY original")
    fun getEnabled(): Flow<List<DictionaryEntry>>

    @Query("SELECT * FROM dictionary ORDER BY original")
    fun getAll(): Flow<List<DictionaryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DictionaryEntry): Long

    @Update
    suspend fun update(entry: DictionaryEntry)

    @Delete
    suspend fun delete(entry: DictionaryEntry)

    @Query("DELETE FROM dictionary WHERE original = :original")
    suspend fun deleteByOriginal(original: String)
}