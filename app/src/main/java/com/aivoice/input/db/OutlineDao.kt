package com.aivoice.input.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aivoice.input.model.Outline
import kotlinx.coroutines.flow.Flow

@Dao
interface OutlineDao {
    @Query("SELECT * FROM Outline WHERE beatId = :beatId AND isActive = 1 LIMIT 1")
    suspend fun getActiveByBeat(beatId: String): Outline?

    @Insert
    suspend fun insert(outline: Outline)

    @Query("UPDATE Outline SET isActive = 0 WHERE beatId = :beatId")
    suspend fun deactivateByBeat(beatId: String)

    @Query("DELETE FROM Outline WHERE beatId = :beatId")
    suspend fun deleteByBeatId(beatId: String)

    @Query("SELECT MAX(version) FROM Outline WHERE beatId = :beatId")
    suspend fun getMaxVersion(beatId: String): Int?

    @Query("SELECT * FROM Outline WHERE projectId = :projectId AND isActive = 1 ORDER BY updatedAt DESC")
    fun getByProject(projectId: Long): Flow<List<Outline>>
}
