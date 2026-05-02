# WriterPad Module Implementation Plan - Phase 1

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement data models, database layer, and repositories for WriterPad module.

**Architecture:** Room database with 7 new entities, DAOs for each entity, and repositories with transaction-safe operations. Uses UUID for business IDs and independent order field for beats.

**Tech Stack:** Kotlin, Room, Coroutines, Flow

---

## File Structure

### New Files to Create

```
app/src/main/java/com/aivoice/input/
├── model/
│   ├── Project.kt
│   ├── Beat.kt
│   ├── Character.kt
│   ├── Outline.kt
│   ├── WorldRule.kt
│   ├── BeatMapping.kt
│   ├── Glossary.kt
│   ├── enums/
│   │   ├── BeatType.kt
│   │   ├── SettingType.kt
│   │   ├── ContextType.kt
│   │   ├── GlossaryType.kt
│   │   ├── GlossaryPriority.kt
│   │   └── DraftAction.kt
│   └── draft/
│       ├── BeatDraft.kt
│       ├── CharacterDraft.kt
│       ├── OutlineDraft.kt
│       ├── WorldRuleDraft.kt
│       ├── MappingDraft.kt
│       ├── GlossaryDraft.kt
│       └── ClassificationResult.kt
├── db/
│   ├── ProjectDao.kt
│   ├── BeatDao.kt
│   ├── CharacterDao.kt
│   ├── OutlineDao.kt
│   ├── WorldRuleDao.kt
│   ├── BeatMappingDao.kt
│   └── GlossaryDao.kt
└── repository/
    ├── ProjectRepository.kt
    ├── BeatRepository.kt
    ├── CharacterRepository.kt
    ├── OutlineRepository.kt
    ├── WorldRuleRepository.kt
    ├── MappingRepository.kt
    ├── GlossaryRepository.kt
    └── BeatContextService.kt
```

### Files to Modify

```
app/src/main/java/com/aivoice/input/
├── db/AppDatabase.kt          # Add entities, DAOs, migration
└── db/Converters.kt           # Add type converters
```

---

## Task 1: Enum Definitions

**Files:**
- Create: `app/src/main/java/com/aivoice/input/model/enums/BeatType.kt`
- Create: `app/src/main/java/com/aivoice/input/model/enums/SettingType.kt`
- Create: `app/src/main/java/com/aivoice/input/model/enums/ContextType.kt`
- Create: `app/src/main/java/com/aivoice/input/model/enums/GlossaryType.kt`
- Create: `app/src/main/java/com/aivoice/input/model/enums/GlossaryPriority.kt`
- Create: `app/src/main/java/com/aivoice/input/model/enums/DraftAction.kt`

- [ ] **Step 1: Create BeatType enum**

```kotlin
// app/src/main/java/com/aivoice/input/model/enums/BeatType.kt
package com.aivoice.input.model.enums

enum class BeatType {
    OPENING,      // 起 - 开篇
    DEVELOPMENT,  // 承 - 发展
    TWIST,        // 转 - 转折
    CLOSING,      // 合 - 收尾
    FORESHADOW,   // 伏笔
    CLIMAX        // 高潮
}
```

- [ ] **Step 2: Create SettingType enum**

```kotlin
// app/src/main/java/com/aivoice/input/model/enums/SettingType.kt
package com.aivoice.input.model.enums

enum class SettingType {
    CHARACTER,
    OUTLINE,
    WORLD_RULE
}
```

- [ ] **Step 3: Create ContextType enum**

```kotlin
// app/src/main/java/com/aivoice/input/model/enums/ContextType.kt
package com.aivoice.input.model.enums

enum class ContextType {
    STATE,        // 状态：重伤、潜伏、暴走
    RELATION,     // 关系：敌对、盟友、暗恋
    EVENT,        // 事件：初遇、决斗、离别
    CONDITION     // 条件：月圆之夜、中毒状态下
}
```

- [ ] **Step 4: Create GlossaryType enum**

```kotlin
// app/src/main/java/com/aivoice/input/model/enums/GlossaryType.kt
package com.aivoice.input.model.enums

enum class GlossaryType {
    CHARACTER,    // 来源于人设
    WORLD,        // 来源于世界观
    MANUAL        // 手动添加
}
```

- [ ] **Step 5: Create GlossaryPriority enum**

```kotlin
// app/src/main/java/com/aivoice/input/model/enums/GlossaryPriority.kt
package com.aivoice.input.model.enums

enum class GlossaryPriority {
    HIGH,         // 主角名、核心设定
    MEDIUM,       // 重要配角、常用道具
    LOW           // 次要设定
}
```

- [ ] **Step 6: Create DraftAction enum**

```kotlin
// app/src/main/java/com/aivoice/input/model/enums/DraftAction.kt
package com.aivoice.input.model.enums

enum class DraftAction {
    CREATE,       // 新建设定
    UPDATE        // 更新已有设定
}
```

- [ ] **Step 7: Commit enums**

```bash
git add app/src/main/java/com/aivoice/input/model/enums/
git commit -m "feat: add enums for WriterPad data models

- BeatType: OPENING, DEVELOPMENT, TWIST, CLOSING, FORESHADOW, CLIMAX
- SettingType: CHARACTER, OUTLINE, WORLD_RULE
- ContextType: STATE, RELATION, EVENT, CONDITION
- GlossaryType: CHARACTER, WORLD, MANUAL
- GlossaryPriority: HIGH, MEDIUM, LOW
- DraftAction: CREATE, UPDATE

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 2: Draft Model Definitions

**Files:**
- Create: `app/src/main/java/com/aivoice/input/model/draft/BeatDraft.kt`
- Create: `app/src/main/java/com/aivoice/input/model/draft/CharacterDraft.kt`
- Create: `app/src/main/java/com/aivoice/input/model/draft/OutlineDraft.kt`
- Create: `app/src/main/java/com/aivoice/input/model/draft/WorldRuleDraft.kt`
- Create: `app/src/main/java/com/aivoice/input/model/draft/MappingDraft.kt`
- Create: `app/src/main/java/com/aivoice/input/model/draft/GlossaryDraft.kt`
- Create: `app/src/main/java/com/aivoice/input/model/draft/ClassificationResult.kt`

- [ ] **Step 1: Create BeatDraft**

```kotlin
// app/src/main/java/com/aivoice/input/model/draft/BeatDraft.kt
package com.aivoice.input.model.draft

import com.aivoice.input.model.enums.BeatType

data class BeatDraft(
    val title: String,
    val summary: String,
    val type: BeatType
)
```

- [ ] **Step 2: Create CharacterDraft**

```kotlin
// app/src/main/java/com/aivoice/input/model/draft/CharacterDraft.kt
package com.aivoice.input.model.draft

import com.aivoice.input.model.enums.ContextType
import com.aivoice.input.model.enums.DraftAction

data class CharacterDraft(
    val name: String,
    val content: String,
    val action: DraftAction,
    val targetId: String = "",
    val contextType: ContextType,
    val contextNote: String
)
```

- [ ] **Step 3: Create OutlineDraft**

```kotlin
// app/src/main/java/com/aivoice/input/model/draft/OutlineDraft.kt
package com.aivoice.input.model.draft

data class OutlineDraft(
    val content: String,
    val beatId: String
)
```

- [ ] **Step 4: Create WorldRuleDraft**

```kotlin
// app/src/main/java/com/aivoice/input/model/draft/WorldRuleDraft.kt
package com.aivoice.input.model.draft

import com.aivoice.input.model.enums.ContextType
import com.aivoice.input.model.enums.DraftAction

data class WorldRuleDraft(
    val title: String,
    val content: String,
    val action: DraftAction,
    val targetId: String = "",
    val contextType: ContextType,
    val contextNote: String
)
```

- [ ] **Step 5: Create MappingDraft**

```kotlin
// app/src/main/java/com/aivoice/input/model/draft/MappingDraft.kt
package com.aivoice.input.model.draft

import com.aivoice.input.model.enums.ContextType
import com.aivoice.input.model.enums.SettingType

data class MappingDraft(
    val beatId: String,
    val settingType: SettingType,
    val settingId: String,
    val contextType: ContextType,
    val contextNote: String
)
```

- [ ] **Step 6: Create GlossaryDraft**

```kotlin
// app/src/main/java/com/aivoice/input/model/draft/GlossaryDraft.kt
package com.aivoice.input.model.draft

import com.aivoice.input.model.enums.GlossaryPriority
import com.aivoice.input.model.enums.GlossaryType

data class GlossaryDraft(
    val word: String,
    val type: GlossaryType,
    val sourceId: String,
    val priority: GlossaryPriority
)
```

- [ ] **Step 7: Create ClassificationResult**

```kotlin
// app/src/main/java/com/aivoice/input/model/draft/ClassificationResult.kt
package com.aivoice.input.model.draft

data class ClassificationResult(
    val characters: List<CharacterDraft>,
    val outline: OutlineDraft?,
    val worldRules: List<WorldRuleDraft>,
    val mappings: List<MappingDraft>
)
```

- [ ] **Step 8: Commit draft models**

```bash
git add app/src/main/java/com/aivoice/input/model/draft/
git commit -m "feat: add draft models for AI classification results

- BeatDraft: title, summary, type
- CharacterDraft: name, content, action, targetId, contextType, contextNote
- OutlineDraft: content, beatId
- WorldRuleDraft: title, content, action, targetId, contextType, contextNote
- MappingDraft: beatId, settingType, settingId, contextType, contextNote
- GlossaryDraft: word, type, sourceId, priority
- ClassificationResult: characters, outline, worldRules, mappings

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 3: Entity Definitions

**Files:**
- Create: `app/src/main/java/com/aivoice/input/model/Project.kt`
- Create: `app/src/main/java/com/aivoice/input/model/Beat.kt`
- Create: `app/src/main/java/com/aivoice/input/model/Character.kt`
- Create: `app/src/main/java/com/aivoice/input/model/Outline.kt`
- Create: `app/src/main/java/com/aivoice/input/model/WorldRule.kt`
- Create: `app/src/main/java/com/aivoice/input/model/BeatMapping.kt`
- Create: `app/src/main/java/com/aivoice/input/model/Glossary.kt`

- [ ] **Step 1: Create Project entity**

```kotlin
// app/src/main/java/com/aivoice/input/model/Project.kt
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
```

- [ ] **Step 2: Create Beat entity**

```kotlin
// app/src/main/java/com/aivoice/input/model/Beat.kt
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
```

- [ ] **Step 3: Create Character entity**

```kotlin
// app/src/main/java/com/aivoice/input/model/Character.kt
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
```

- [ ] **Step 4: Create Outline entity**

```kotlin
// app/src/main/java/com/aivoice/input/model/Outline.kt
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
```

- [ ] **Step 5: Create WorldRule entity**

```kotlin
// app/src/main/java/com/aivoice/input/model/WorldRule.kt
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
```

- [ ] **Step 6: Create BeatMapping entity**

```kotlin
// app/src/main/java/com/aivoice/input/model/BeatMapping.kt
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
```

- [ ] **Step 7: Create Glossary entity**

```kotlin
// app/src/main/java/com/aivoice/input/model/Glossary.kt
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
```

- [ ] **Step 8: Commit entities**

```bash
git add app/src/main/java/com/aivoice/input/model/Project.kt
git add app/src/main/java/com/aivoice/input/model/Beat.kt
git add app/src/main/java/com/aivoice/input/model/Character.kt
git add app/src/main/java/com/aivoice/input/model/Outline.kt
git add app/src/main/java/com/aivoice/input/model/WorldRule.kt
git add app/src/main/java/com/aivoice/input/model/BeatMapping.kt
git add app/src/main/java/com/aivoice/input/model/Glossary.kt
git commit -m "feat: add Room entities for WriterPad module

- Project: novel project with premise
- Beat: story beat with UUID, type, order
- Character: character setting with UUID
- Outline: versioned outline per beat
- WorldRule: world building rules
- BeatMapping: many-to-many with context
- Glossary: project-specific word list

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 4: DAO Definitions

**Files:**
- Create: `app/src/main/java/com/aivoice/input/db/ProjectDao.kt`
- Create: `app/src/main/java/com/aivoice/input/db/BeatDao.kt`
- Create: `app/src/main/java/com/aivoice/input/db/CharacterDao.kt`
- Create: `app/src/main/java/com/aivoice/input/db/OutlineDao.kt`
- Create: `app/src/main/java/com/aivoice/input/db/WorldRuleDao.kt`
- Create: `app/src/main/java/com/aivoice/input/db/BeatMappingDao.kt`
- Create: `app/src/main/java/com/aivoice/input/db/GlossaryDao.kt`

- [ ] **Step 1: Create ProjectDao**

```kotlin
// app/src/main/java/com/aivoice/input/db/ProjectDao.kt
package com.aivoice.input.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.aivoice.input.model.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM Project ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Project>>

    @Query("SELECT * FROM Project WHERE id = :id")
    suspend fun getById(id: Long): Project?

    @Insert
    suspend fun insert(project: Project): Long

    @Update
    suspend fun update(project: Project)
}
```

- [ ] **Step 2: Create BeatDao**

```kotlin
// app/src/main/java/com/aivoice/input/db/BeatDao.kt
package com.aivoice.input.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.aivoice.input.model.Beat
import kotlinx.coroutines.flow.Flow

@Dao
interface BeatDao {
    @Query("SELECT * FROM Beat WHERE projectId = :projectId ORDER BY `order` ASC")
    fun getByProject(projectId: Long): Flow<List<Beat>>

    @Query("SELECT * FROM Beat WHERE beatId = :beatId")
    suspend fun getByBeatId(beatId: String): Beat?

    @Insert
    suspend fun insert(beat: Beat): Long

    @Update
    suspend fun update(beat: Beat)

    @Query("UPDATE Beat SET beatId = :beatId WHERE id = :id")
    suspend fun updateBeatId(id: Long, beatId: String)

    @Query("DELETE FROM Beat WHERE beatId = :beatId")
    suspend fun deleteByBeatId(beatId: String)

    @Query("SELECT MAX(`order`) FROM Beat WHERE projectId = :projectId")
    suspend fun getMaxOrder(projectId: Long): Int?

    @Query("UPDATE Beat SET `order` = `order` - 1 WHERE projectId = :projectId AND `order` > :deletedOrder")
    suspend fun shiftOrderAfterDelete(projectId: Long, deletedOrder: Int)

    @Query("UPDATE Beat SET `order` = :order WHERE beatId = :beatId")
    suspend fun updateOrder(beatId: String, order: Int)

    @Query("SELECT * FROM Beat WHERE `order` > (SELECT `order` FROM Beat WHERE beatId = :beatId) ORDER BY `order` ASC LIMIT 1")
    suspend fun getNextBeat(beatId: String): Beat?
}
```

- [ ] **Step 3: Create CharacterDao**

```kotlin
// app/src/main/java/com/aivoice/input/db/CharacterDao.kt
package com.aivoice.input.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aivoice.input.model.Character
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM Character WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun getByProject(projectId: Long): Flow<List<Character>>

    @Query("SELECT * FROM Character WHERE charId = :charId")
    suspend fun getByCharId(charId: String): Character?

    @Insert
    suspend fun insert(character: Character): Long

    @Query("UPDATE Character SET charId = :charId WHERE id = :id")
    suspend fun updateCharId(id: Long, charId: String)

    @Query("UPDATE Character SET content = :content, updatedAt = :updatedAt WHERE charId = :charId")
    suspend fun updateContent(charId: String, content: String, updatedAt: Long)

    @Query("DELETE FROM Character WHERE charId = :charId")
    suspend fun deleteByCharId(charId: String)
}
```

- [ ] **Step 4: Create OutlineDao**

```kotlin
// app/src/main/java/com/aivoice/input/db/OutlineDao.kt
package com.aivoice.input.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aivoice.input.model.Outline
import kotlinx.coroutines.flow.Flow

@Dao
interface OutlineDao {
    @Query("SELECT * FROM Outline WHERE beatId = :beatId AND isActive = 1 LIMIT 1")
    suspend fun getActiveByBeat(beatId: String): Outline?

    @Insert
    suspend fun insert(outline: Outline)

    @Query("UPDATE Outline SET isActive = 0 WHERE beatId = :beatId")
    suspend fun deactivateByBeat(beatId: String)

    @Query("DELETE FROM Outline WHERE beatId = :beatId")
    suspend fun deleteByBeatId(beatId: String)

    @Query("SELECT MAX(version) FROM Outline WHERE beatId = :beatId")
    suspend fun getMaxVersion(beatId: String): Int?

    @Query("SELECT * FROM Outline WHERE projectId = :projectId AND isActive = 1 ORDER BY updatedAt DESC")
    fun getByProject(projectId: Long): Flow<List<Outline>>
}
```

- [ ] **Step 5: Create WorldRuleDao**

```kotlin
// app/src/main/java/com/aivoice/input/db/WorldRuleDao.kt
package com.aivoice.input.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aivoice.input.model.WorldRule
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldRuleDao {
    @Query("SELECT * FROM WorldRule WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun getByProject(projectId: Long): Flow<List<WorldRule>>

    @Query("SELECT * FROM WorldRule WHERE ruleId = :ruleId")
    suspend fun getByRuleId(ruleId: String): WorldRule?

    @Insert
    suspend fun insert(rule: WorldRule): Long

    @Query("UPDATE WorldRule SET ruleId = :ruleId WHERE id = :id")
    suspend fun updateRuleId(id: Long, ruleId: String)

    @Query("UPDATE WorldRule SET content = :content, updatedAt = :updatedAt WHERE ruleId = :ruleId")
    suspend fun updateContent(ruleId: String, content: String, updatedAt: Long)

    @Query("DELETE FROM WorldRule WHERE ruleId = :ruleId")
    suspend fun deleteByRuleId(ruleId: String)
}
```

- [ ] **Step 6: Create BeatMappingDao**

```kotlin
// app/src/main/java/com/aivoice/input/db/BeatMappingDao.kt
package com.aivoice.input.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aivoice.input.model.BeatMapping
import com.aivoice.input.model.enums.SettingType

@Dao
interface BeatMappingDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<BeatMapping>)

    @Query("DELETE FROM BeatMapping WHERE beatId = :beatId")
    suspend fun deleteByBeatId(beatId: String)

    @Query("DELETE FROM BeatMapping WHERE settingId = :settingId AND settingType = :settingType")
    suspend fun deleteBySettingId(settingId: String, settingType: SettingType)

    @Query("SELECT * FROM BeatMapping WHERE beatId = :beatId")
    suspend fun getByBeat(beatId: String): List<BeatMapping>

    @Query("""
        SELECT c.*, m.contextType, m.contextNote, m.isActive
        FROM Character c
        INNER JOIN BeatMapping m ON m.settingId = c.charId
        WHERE m.beatId = :beatId AND m.settingType = 'CHARACTER'
        ORDER BY m.isActive DESC
    """)
    suspend fun getCharactersForBeat(beatId: String): List<CharacterWithContext>

    @Query("""
        SELECT w.*, m.contextType, m.contextNote, m.isActive
        FROM WorldRule w
        INNER JOIN BeatMapping m ON m.settingId = w.ruleId
        WHERE m.beatId = :beatId AND m.settingType = 'WORLD_RULE'
        ORDER BY m.isActive DESC
    """)
    suspend fun getWorldRulesForBeat(beatId: String): List<WorldRuleWithContext>

    @Query("UPDATE BeatMapping SET isActive = :isActive WHERE beatId = :beatId AND settingId = :settingId AND settingType = :settingType")
    suspend fun updateActiveState(beatId: String, settingId: String, settingType: SettingType, isActive: Boolean)
}

// Helper data classes for queries
data class CharacterWithContext(
    val id: Long,
    val projectId: Long,
    val charId: String,
    val name: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val contextType: com.aivoice.input.model.enums.ContextType,
    val contextNote: String,
    val isActive: Boolean
)

data class WorldRuleWithContext(
    val id: Long,
    val projectId: Long,
    val ruleId: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val contextType: com.aivoice.input.model.enums.ContextType,
    val contextNote: String,
    val isActive: Boolean
)
```

- [ ] **Step 7: Create GlossaryDao**

```kotlin
// app/src/main/java/com/aivoice/input/db/GlossaryDao.kt
package com.aivoice.input.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aivoice.input.model.Glossary
import kotlinx.coroutines.flow.Flow

@Dao
interface GlossaryDao {
    @Query("SELECT * FROM Glossary WHERE projectId = :projectId ORDER BY priority DESC, word ASC")
    fun getByProject(projectId: Long): Flow<List<Glossary>>

    @Query("SELECT * FROM Glossary WHERE projectId = :projectId ORDER BY priority DESC, word ASC")
    suspend fun getByProjectOnce(projectId: Long): List<Glossary>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(item: Glossary)

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<Glossary>)

    @Query("DELETE FROM Glossary WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long)

    @Query("DELETE FROM Glossary WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)
}
```

- [ ] **Step 8: Commit DAOs**

```bash
git add app/src/main/java/com/aivoice/input/db/ProjectDao.kt
git add app/src/main/java/com/aivoice/input/db/BeatDao.kt
git add app/src/main/java/com/aivoice/input/db/CharacterDao.kt
git add app/src/main/java/com/aivoice/input/db/OutlineDao.kt
git add app/src/main/java/com/aivoice/input/db/WorldRuleDao.kt
git add app/src/main/java/com/aivoice/input/db/BeatMappingDao.kt
git add app/src/main/java/com/aivoice/input/db/GlossaryDao.kt
git commit -m "feat: add DAOs for WriterPad entities

- ProjectDao: CRUD operations
- BeatDao: order management, next beat query
- CharacterDao: CRUD with charId
- OutlineDao: versioning support
- WorldRuleDao: CRUD with ruleId
- BeatMappingDao: context queries
- GlossaryDao: batch insert support

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 5: Update AppDatabase and Converters

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/db/AppDatabase.kt`

- [ ] **Step 1: Update AppDatabase with new entities and migration**

```kotlin
// app/src/main/java/com/aivoice/input/db/AppDatabase.kt
package com.aivoice.input.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aivoice.input.model.*

@Database(
    entities = [
        HistoryItem::class,
        DictionaryEntry::class,
        Project::class,
        Beat::class,
        Character::class,
        Outline::class,
        WorldRule::class,
        BeatMapping::class,
        Glossary::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun projectDao(): ProjectDao
    abstract fun beatDao(): BeatDao
    abstract fun characterDao(): CharacterDao
    abstract fun outlineDao(): OutlineDao
    abstract fun worldRuleDao(): WorldRuleDao
    abstract fun beatMappingDao(): BeatMappingDao
    abstract fun glossaryDao(): GlossaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "padwriter.db"
            )
            .addMigrations(MIGRATION_1_2)
            .build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Project table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS Project (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        premise TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_Project_updatedAt ON Project(updatedAt)")

                // Beat table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS Beat (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        beatId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        type TEXT NOT NULL,
                        `order` INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_Beat_projectId ON Beat(projectId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Beat_beatId ON Beat(beatId)")

                // Character table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS Character (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        charId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_Character_projectId ON Character(projectId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Character_charId ON Character(charId)")

                // Outline table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS Outline (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        beatId TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_Outline_beatId ON Outline(beatId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_Outline_projectId ON Outline(projectId)")

                // WorldRule table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS WorldRule (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        ruleId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_WorldRule_projectId ON WorldRule(projectId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_WorldRule_ruleId ON WorldRule(ruleId)")

                // BeatMapping table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS BeatMapping (
                        projectId INTEGER NOT NULL,
                        beatId TEXT NOT NULL,
                        settingType TEXT NOT NULL,
                        settingId TEXT NOT NULL,
                        contextType TEXT NOT NULL,
                        contextNote TEXT NOT NULL,
                        isActive INTEGER NOT NULL,
                        PRIMARY KEY(beatId, settingId, settingType)
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_BeatMapping_beatId ON BeatMapping(beatId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_BeatMapping_settingId ON BeatMapping(settingId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_BeatMapping_settingType ON BeatMapping(settingType)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_BeatMapping_projectId ON BeatMapping(projectId)")

                // Glossary table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS Glossary (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        word TEXT NOT NULL,
                        type TEXT NOT NULL,
                        sourceId TEXT NOT NULL,
                        priority TEXT NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_Glossary_projectId ON Glossary(projectId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_Glossary_word ON Glossary(word)")
            }
        }
    }
}
```

- [ ] **Step 2: Update Converters with new enums**

```kotlin
// Add to Converters class in AppDatabase.kt

class Converters {
    @androidx.room.TypeConverter
    fun fromPolishStyle(style: PolishStyle): String = style.name

    @androidx.room.TypeConverter
    fun toPolishStyle(value: String): PolishStyle = PolishStyle.valueOf(value)

    @androidx.room.TypeConverter
    fun fromBeatType(type: com.aivoice.input.model.enums.BeatType): String = type.name

    @androidx.room.TypeConverter
    fun toBeatType(value: String): com.aivoice.input.model.enums.BeatType =
        com.aivoice.input.model.enums.BeatType.valueOf(value)

    @androidx.room.TypeConverter
    fun fromSettingType(type: com.aivoice.input.model.enums.SettingType): String = type.name

    @androidx.room.TypeConverter
    fun toSettingType(value: String): com.aivoice.input.model.enums.SettingType =
        com.aivoice.input.model.enums.SettingType.valueOf(value)

    @androidx.room.TypeConverter
    fun fromContextType(type: com.aivoice.input.model.enums.ContextType): String = type.name

    @androidx.room.TypeConverter
    fun toContextType(value: String): com.aivoice.input.model.enums.ContextType =
        com.aivoice.input.model.enums.ContextType.valueOf(value)

    @androidx.room.TypeConverter
    fun fromGlossaryType(type: com.aivoice.input.model.enums.GlossaryType): String = type.name

    @androidx.room.TypeConverter
    fun toGlossaryType(value: String): com.aivoice.input.model.enums.GlossaryType =
        com.aivoice.input.model.enums.GlossaryType.valueOf(value)

    @androidx.room.TypeConverter
    fun fromGlossaryPriority(priority: com.aivoice.input.model.enums.GlossaryPriority): String = priority.name

    @androidx.room.TypeConverter
    fun toGlossaryPriority(value: String): com.aivoice.input.model.enums.GlossaryPriority =
        com.aivoice.input.model.enums.GlossaryPriority.valueOf(value)
}
```

- [ ] **Step 3: Commit database update**

```bash
git add app/src/main/java/com/aivoice/input/db/AppDatabase.kt
git commit -m "feat: update AppDatabase with WriterPad entities

- Add 7 new entities to database
- Add migration from version 1 to 2
- Add type converters for all enums
- Add new DAOs to database

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 6: Repository Implementations

**Files:**
- Create: `app/src/main/java/com/aivoice/input/repository/ProjectRepository.kt`
- Create: `app/src/main/java/com/aivoice/input/repository/BeatRepository.kt`
- Create: `app/src/main/java/com/aivoice/input/repository/CharacterRepository.kt`
- Create: `app/src/main/java/com/aivoice/input/repository/OutlineRepository.kt`
- Create: `app/src/main/java/com/aivoice/input/repository/WorldRuleRepository.kt`
- Create: `app/src/main/java/com/aivoice/input/repository/MappingRepository.kt`
- Create: `app/src/main/java/com/aivoice/input/repository/GlossaryRepository.kt`
- Create: `app/src/main/java/com/aivoice/input/repository/BeatContextService.kt`

- [ ] **Step 1: Create ProjectRepository**

```kotlin
// app/src/main/java/com/aivoice/input/repository/ProjectRepository.kt
package com.aivoice.input.repository

import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.Project
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val database: AppDatabase) {
    fun getAllProjects(): Flow<List<Project>> =
        database.projectDao().getAll()

    suspend fun getProjectById(id: Long): Project? =
        database.projectDao().getById(id)

    suspend fun createProject(name: String, premise: String): Project {
        val project = Project(
            name = name,
            premise = premise,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val id = database.projectDao().insert(project)
        return project.copy(id = id)
    }

    suspend fun updateProject(project: Project) {
        database.projectDao().update(
            project.copy(updatedAt = System.currentTimeMillis())
        )
    }
}
```

- [ ] **Step 2: Create BeatRepository**

```kotlin
// app/src/main/java/com/aivoice/input/repository/BeatRepository.kt
package com.aivoice.input.repository

import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.Beat
import com.aivoice.input.model.enums.BeatType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BeatRepository(private val database: AppDatabase) {
    fun getBeatsForProject(projectId: Long): Flow<List<Beat>> =
        database.beatDao().getByProject(projectId)

    suspend fun createBeat(
        projectId: Long,
        title: String,
        summary: String,
        type: BeatType
    ): Beat {
        return database.withTransaction {
            val maxOrder = database.beatDao().getMaxOrder(projectId) ?: 0
            val beat = Beat(
                projectId = projectId,
                beatId = UUID.randomUUID().toString().take(8),
                title = title,
                summary = summary,
                type = type,
                order = maxOrder + 1,
                createdAt = System.currentTimeMillis()
            )
            val id = database.beatDao().insert(beat)
            beat.copy(id = id)
        }
    }

    suspend fun deleteBeat(beatId: String) {
        database.withTransaction {
            val beat = database.beatDao().getByBeatId(beatId) ?: return@withTransaction
            val projectId = beat.projectId

            database.beatDao().deleteByBeatId(beatId)
            database.beatMappingDao().deleteByBeatId(beatId)
            database.outlineDao().deleteByBeatId(beatId)
            database.beatDao().shiftOrderAfterDelete(projectId, beat.order)
        }
    }

    suspend fun getNextBeat(currentBeatId: String): Beat? =
        database.beatDao().getNextBeat(currentBeatId)

    suspend fun reorderBeats(projectId: Long, beatIds: List<String>) {
        database.withTransaction {
            beatIds.forEachIndexed { index, beatId ->
                database.beatDao().updateOrder(beatId, index + 1)
            }
        }
    }
}
```

- [ ] **Step 3: Create CharacterRepository**

```kotlin
// app/src/main/java/com/aivoice/input/repository/CharacterRepository.kt
package com.aivoice.input.repository

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
```

- [ ] **Step 4: Create OutlineRepository**

```kotlin
// app/src/main/java/com/aivoice/input/repository/OutlineRepository.kt
package com.aivoice.input.repository

import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.Outline
import com.aivoice.input.model.draft.OutlineDraft
import kotlinx.coroutines.flow.Flow

class OutlineRepository(private val database: AppDatabase) {
    suspend fun getActiveOutline(beatId: String): Outline? =
        database.outlineDao().getActiveByBeat(beatId)

    fun getOutlinesForProject(projectId: Long): Flow<List<Outline>> =
        database.outlineDao().getByProject(projectId)

    suspend fun createOrUpdateOutline(projectId: Long, draft: OutlineDraft) {
        database.withTransaction {
            database.outlineDao().deactivateByBeat(draft.beatId)
            val currentVersion = database.outlineDao().getMaxVersion(draft.beatId) ?: 0

            val outline = Outline(
                projectId = projectId,
                beatId = draft.beatId,
                version = currentVersion + 1,
                isActive = true,
                content = draft.content,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            database.outlineDao().insert(outline)
        }
    }
}
```

- [ ] **Step 5: Create WorldRuleRepository**

```kotlin
// app/src/main/java/com/aivoice/input/repository/WorldRuleRepository.kt
package com.aivoice.input.repository

import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.WorldRule
import com.aivoice.input.model.draft.WorldRuleDraft
import com.aivoice.input.model.enums.SettingType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class WorldRuleRepository(private val database: AppDatabase) {
    fun getWorldRulesForProject(projectId: Long): Flow<List<WorldRule>> =
        database.worldRuleDao().getByProject(projectId)

    suspend fun createWorldRule(projectId: Long, draft: WorldRuleDraft): WorldRule {
        val rule = WorldRule(
            projectId = projectId,
            ruleId = UUID.randomUUID().toString().take(8),
            title = draft.title,
            content = draft.content,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val id = database.worldRuleDao().insert(rule)
        return rule.copy(id = id)
    }

    suspend fun updateWorldRule(ruleId: String, content: String) {
        database.worldRuleDao().updateContent(
            ruleId = ruleId,
            content = content,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun deleteWorldRule(ruleId: String) {
        database.withTransaction {
            database.worldRuleDao().deleteByRuleId(ruleId)
            database.beatMappingDao().deleteBySettingId(ruleId, SettingType.WORLD_RULE)
            database.glossaryDao().deleteBySourceId(ruleId)
        }
    }
}
```

- [ ] **Step 6: Create MappingRepository**

```kotlin
// app/src/main/java/com/aivoice/input/repository/MappingRepository.kt
package com.aivoice.input.repository

import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.BeatMapping
import com.aivoice.input.model.draft.MappingDraft

class MappingRepository(private val database: AppDatabase) {
    suspend fun saveMappings(projectId: Long, mappings: List<MappingDraft>) {
        val entities = mappings.map { draft ->
            BeatMapping(
                projectId = projectId,
                beatId = draft.beatId,
                settingType = draft.settingType,
                settingId = draft.settingId,
                contextType = draft.contextType,
                contextNote = draft.contextNote
            )
        }
        database.beatMappingDao().insertAll(entities)
    }

    suspend fun getMappingsForBeat(beatId: String): List<BeatMapping> =
        database.beatMappingDao().getByBeat(beatId)

    suspend fun deleteMappingsForBeat(beatId: String) {
        database.beatMappingDao().deleteByBeatId(beatId)
    }
}
```

- [ ] **Step 7: Create GlossaryRepository**

```kotlin
// app/src/main/java/com/aivoice/input/repository/GlossaryRepository.kt
package com.aivoice.input.repository

import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.DictionaryEntry
import com.aivoice.input.model.Glossary
import com.aivoice.input.model.draft.GlossaryDraft
import com.aivoice.input.model.enums.GlossaryPriority
import kotlinx.coroutines.flow.Flow

class GlossaryRepository(private val database: AppDatabase) {
    fun getGlossaryForProject(projectId: Long): Flow<List<Glossary>> =
        database.glossaryDao().getByProject(projectId)

    suspend fun saveGlossary(projectId: Long, items: List<GlossaryDraft>) {
        database.withTransaction {
            items.forEach { draft ->
                database.glossaryDao().insertIgnore(
                    Glossary(
                        projectId = projectId,
                        word = draft.word,
                        type = draft.type,
                        sourceId = draft.sourceId,
                        priority = draft.priority
                    )
                )
            }
        }
    }

    suspend fun syncToDictionary(projectId: Long) {
        database.withTransaction {
            val glossary = database.glossaryDao().getByProjectOnce(projectId)
            val entries = glossary.map { item ->
                DictionaryEntry(
                    word = item.word,
                    replacement = item.word,
                    priority = when (item.priority) {
                        GlossaryPriority.HIGH -> 100
                        GlossaryPriority.MEDIUM -> 50
                        GlossaryPriority.LOW -> 10
                    }
                )
            }
            database.dictionaryDao().insertAll(entries)
        }
    }

    suspend fun deleteGlossaryForProject(projectId: Long) {
        database.glossaryDao().deleteByProject(projectId)
    }
}
```

- [ ] **Step 8: Create BeatContextService**

```kotlin
// app/src/main/java/com/aivoice/input/repository/BeatContextService.kt
package com.aivoice.input.repository

import com.aivoice.input.db.AppDatabase
import com.aivoice.input.db.CharacterWithContext
import com.aivoice.input.db.WorldRuleWithContext
import com.aivoice.input.model.Outline
import com.aivoice.input.model.draft.ClassificationResult

data class BeatContext(
    val beatId: String,
    val characters: List<CharacterWithContext>,
    val worldRules: List<WorldRuleWithContext>,
    val outline: Outline?
)

class BeatContextService(
    private val database: AppDatabase,
    private val characterRepository: CharacterRepository,
    private val worldRuleRepository: WorldRuleRepository,
    private val outlineRepository: OutlineRepository,
    private val mappingRepository: MappingRepository
) {
    suspend fun loadBeatContext(beatId: String): BeatContext {
        return database.withTransaction {
            val characters = database.beatMappingDao().getCharactersForBeat(beatId)
            val worldRules = database.beatMappingDao().getWorldRulesForBeat(beatId)
            val outline = database.outlineDao().getActiveByBeat(beatId)

            BeatContext(
                beatId = beatId,
                characters = characters,
                worldRules = worldRules,
                outline = outline
            )
        }
    }

    suspend fun saveClassificationResult(
        projectId: Long,
        result: ClassificationResult,
        currentBeatId: String
    ) {
        database.withTransaction {
            result.characters.forEach { draft ->
                when (draft.action) {
                    com.aivoice.input.model.enums.DraftAction.CREATE -> {
                        characterRepository.createCharacter(projectId, draft)
                    }
                    com.aivoice.input.model.enums.DraftAction.UPDATE -> {
                        characterRepository.updateCharacter(draft.targetId, draft.content)
                    }
                }
            }

            result.worldRules.forEach { draft ->
                when (draft.action) {
                    com.aivoice.input.model.enums.DraftAction.CREATE -> {
                        worldRuleRepository.createWorldRule(projectId, draft)
                    }
                    com.aivoice.input.model.enums.DraftAction.UPDATE -> {
                        worldRuleRepository.updateWorldRule(draft.targetId, draft.content)
                    }
                }
            }

            result.outline?.let {
                outlineRepository.createOrUpdateOutline(projectId, it)
            }

            if (result.mappings.isNotEmpty()) {
                mappingRepository.saveMappings(projectId, result.mappings)
            }
        }
    }
}
```

- [ ] **Step 9: Commit repositories**

```bash
git add app/src/main/java/com/aivoice/input/repository/
git commit -m "feat: add repositories for WriterPad module

- ProjectRepository: project CRUD
- BeatRepository: UUID generation, order management
- CharacterRepository: CRUD with mapping cleanup
- OutlineRepository: versioning support
- WorldRuleRepository: CRUD with mapping cleanup
- MappingRepository: batch operations
- GlossaryRepository: sync to dictionary
- BeatContextService: aggregation layer

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 7: Update DictionaryDao

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/db/DictionaryDao.kt`

- [ ] **Step 1: Add batch insert to DictionaryDao**

Read the current DictionaryDao and add the insertAll method:

```kotlin
// Add to DictionaryDao interface
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertAll(entries: List<DictionaryEntry>)
```

- [ ] **Step 2: Commit DictionaryDao update**

```bash
git add app/src/main/java/com/aivoice/input/db/DictionaryDao.kt
git commit -m "feat: add batch insert to DictionaryDao

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Summary

This plan covers Phase 1 of the WriterPad module implementation:

1. **Enums** - 6 enum classes for type safety
2. **Draft Models** - 7 data classes for AI results
3. **Entities** - 7 Room entities with proper indices
4. **DAOs** - 7 DAO interfaces with queries
5. **Database** - Migration and type converters
6. **Repositories** - 7 repositories + 1 aggregation service

**Total files created:** 28
**Total files modified:** 2

After completing this plan, proceed to Phase 2 (AI Engine) implementation.
