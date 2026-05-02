package com.aivoice.input.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.aivoice.input.model.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM Project ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Project>>

    @Query("SELECT * FROM Project WHERE id = :id")
    suspend fun getById(id: Long): Project?

    @Insert
    suspend fun insert(project: Project): Long

    @Update
    suspend fun update(project: Project)
}
