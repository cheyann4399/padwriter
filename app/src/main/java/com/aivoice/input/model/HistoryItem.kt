package com.aivoice.input.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalText: String,
    val polishedText: String,
    val style: PolishStyle,
    val timestamp: Long = System.currentTimeMillis()
)
