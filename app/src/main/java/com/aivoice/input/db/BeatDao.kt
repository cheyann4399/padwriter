package com.aivoice.input.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.aivoice.input.model.Beat
import kotlinx.coroutines.flow.Flow

@Dao
interface BeatDao {
    @Query("SELECT * FROM Beat WHERE projectId = :projectId ORDER BY `order` ASC")
    fun getByProject(projectId: Long): Flow<List<Beat>>

    @Query("SELECT * FROM Beat WHERE beatId = :beatId")
    suspend fun getByBeatId(beatId: String): Beat?

    @Insert
    suspend fun insert(beat: Beat): Long

    @Update
    suspend fun update(beat: Beat)

    @Query("UPDATE Beat SET beatId = :beatId WHERE id = :id")
    suspend fun updateBeatId(id: Long, beatId: String)

    @Query("DELETE FROM Beat WHERE beatId = :beatId")
    suspend fun deleteByBeatId(beatId: String)

    @Query("SELECT MAX(`order`) FROM Beat WHERE projectId = :projectId")
    suspend fun getMaxOrder(projectId: Long): Int?

    @Query("UPDATE Beat SET `order` = `order` - 1 WHERE projectId = :projectId AND `order` > :deletedOrder")
    suspend fun shiftOrderAfterDelete(projectId: Long, deletedOrder: Int)

    @Query("UPDATE Beat SET `order` = :order WHERE beatId = :beatId")
    suspend fun updateOrder(beatId: String, order: Int)

    @Query("SELECT * FROM Beat WHERE `order` > (SELECT `order` FROM Beat WHERE beatId = :beatId) ORDER BY `order` ASC LIMIT 1")
    suspend fun getNextBeat(beatId: String): Beat?
}
