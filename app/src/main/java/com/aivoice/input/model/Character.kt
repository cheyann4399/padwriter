package com.aivoice.input.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [
        Index("projectId"),
        Index("charId", unique = true)
    ]
)
data class Character(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val charId: String,
    val name: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)
