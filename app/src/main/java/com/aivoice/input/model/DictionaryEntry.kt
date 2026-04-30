package com.aivoice.input.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictionary")
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val original: String,
    val replacement: String,
    val enabled: Boolean = true
)
