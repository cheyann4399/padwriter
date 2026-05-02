package com.aivoice.input.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [
        Index("beatId"),
        Index("projectId")
    ]
)
data class Outline(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val beatId: String,
    val version: Int = 1,
    val isActive: Boolean = true,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)
