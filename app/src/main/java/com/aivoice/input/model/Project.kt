package com.aivoice.input.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index("updatedAt")])
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val premise: String,
    val createdAt: Long,
    val updatedAt: Long
)
