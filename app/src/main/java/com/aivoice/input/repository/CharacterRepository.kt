package com.aivoice.input.repository

import androidx.room.withTransaction
import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.Character
import com.aivoice.input.model.draft.CharacterDraft
import com.aivoice.input.model.enums.SettingType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CharacterRepository(private val database: AppDatabase) {
    fun getCharactersForProject(projectId: Long): Flow<List<Character>> =
        database.characterDao().getByProject(projectId)

    suspend fun getCharacterByCharId(charId: String): Character? =
        database.characterDao().getByCharId(charId)

    suspend fun createCharacter(projectId: Long, draft: CharacterDraft): Character {
        val character = Character(
            projectId = projectId,
            charId = UUID.randomUUID().toString().take(8),
            name = draft.name,
            content = draft.content,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val id = database.characterDao().insert(character)
        return character.copy(id = id)
    }

    suspend fun updateCharacter(charId: String, content: String) {
        database.characterDao().updateContent(
            charId = charId,
            content = content,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun deleteCharacter(charId: String) {
        database.withTransaction {
            database.characterDao().deleteByCharId(charId)
            database.beatMappingDao().deleteBySettingId(charId, SettingType.CHARACTER)
            database.glossaryDao().deleteBySourceId(charId)
        }
    }
}