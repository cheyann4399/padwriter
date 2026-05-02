package com.aivoice.input.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aivoice.input.model.enums.GlossaryPriority
import com.aivoice.input.model.enums.GlossaryType

@Entity(
    indices = [
        Index("projectId"),
        Index("word")
    ]
)
data class Glossary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val word: String,
    val type: GlossaryType,
    val sourceId: String,
    val priority: GlossaryPriority
)
