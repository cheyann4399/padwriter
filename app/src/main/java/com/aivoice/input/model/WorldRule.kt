package com.aivoice.input.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [
        Index("projectId"),
        Index("ruleId", unique = true)
    ]
)
data class WorldRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val ruleId: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)
