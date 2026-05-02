package com.aivoice.input.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aivoice.input.model.enums.BeatType

@Entity(
    indices = [
        Index("projectId"),
        Index("beatId", unique = true),
        Index("order")
    ]
)
data class Beat(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val beatId: String,
    val title: String,
    val summary: String,
    val type: BeatType,
    val order: Int,
    val createdAt: Long
)
