package com.aivoice.input.model

import androidx.room.Entity
import androidx.room.Index
import com.aivoice.input.model.enums.ContextType
import com.aivoice.input.model.enums.SettingType

@Entity(
    primaryKeys = ["beatId", "settingId", "settingType"],
    indices = [
        Index("beatId"),
        Index("settingId"),
        Index("settingType"),
        Index("projectId")
    ]
)
data class BeatMapping(
    val projectId: Long,
    val beatId: String,
    val settingType: SettingType,
    val settingId: String,
    val contextType: ContextType = ContextType.STATE,
    val contextNote: String = "",
    val isActive: Boolean = true
)
