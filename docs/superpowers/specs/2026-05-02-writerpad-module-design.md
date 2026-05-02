# WriterPad 模块设计文档

> 网文创作辅助模块 - 节拍器 + 设定管理 + AI 引导

---

## 一、概述

### 1.1 项目背景

WriterPad 是在现有语音输入悬浮球工具（padwriter）基础上扩展的网文创作辅助模块。核心功能：

- **节拍器系统**：故事骨架管理，推进/回溯交互
- **设定管理**：人设、大纲、世界观模块化存储
- **AI 引导**：三大 Skill（脉络梳理、设定归类、词库生成）
- **语音输入集成**：基于节拍上下文的精准 AI 润色

### 1.2 设计原则

1. **渐进式扩展**：在现有架构基础上增量添加，保持一致性
2. **单向数据流**：MVI 模式，StateFlow 驱动 UI
3. **最小变更**：复用现有 MiniMaxClient、词库系统
4. **事务安全**：多表操作使用 `database.withTransaction`

---

## 二、架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                          UI Layer                               │
│  WriterPadActivity → ViewModel → Intent/State/Reducer          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Repository Layer                          │
│  ProjectRepository, BeatRepository, CharacterRepository,        │
│  OutlineRepository, WorldRuleRepository, MappingRepository,     │
│  GlossaryRepository, BeatContextService                         │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Database Layer                           │
│  AppDatabase → DAOs → Entities                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        AI Engine Layer                          │
│  AIGuideEngine → GuidePromptBuilder → MiniMaxClient             │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        Pipeline Layer                           │
│  StreamingPipeline → PromptEngine → GlossaryManager             │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 文件结构

```
app/src/main/java/com/aivoice/input/
├── model/                                # 数据模型
│   ├── Project.kt                        # 小说项目
│   ├── Beat.kt                           # 节拍
│   ├── Character.kt                      # 人设
│   ├── Outline.kt                        # 大纲
│   ├── WorldRule.kt                      # 世界观
│   ├── BeatMapping.kt                    # 索引映射
│   ├── Glossary.kt                       # 专属词库
│   ├── enums/                            # 枚举定义
│   └── draft/                            # AI 草稿模型
│
├── db/                                   # 数据库层
│   ├── AppDatabase.kt                    # 数据库（扩展）
│   ├── Converters.kt                     # 类型转换器
│   └── *Dao.kt                           # 各实体 DAO
│
├── repository/                           # 数据仓库层
│   ├── ProjectRepository.kt
│   ├── BeatRepository.kt
│   ├── CharacterRepository.kt
│   ├── OutlineRepository.kt
│   ├── WorldRuleRepository.kt
│   ├── MappingRepository.kt
│   ├── GlossaryRepository.kt
│   └── BeatContextService.kt             # 聚合服务
│
├── injection/                            # 依赖注入
│   ├── AppModule.kt                      # Database, Repository 提供
│   └── ViewModelModule.kt                # ViewModel 工厂
│
├── ui/writer/                            # 创作模块
│   ├── WriterPadActivity.kt
│   ├── WriterPadViewModel.kt
│   ├── mvi/                              # MVI 架构
│   │   ├── WriterPadState.kt
│   │   ├── WriterPadIntent.kt
│   │   └── WriterPadReducer.kt
│   ├── components/                       # UI 组件
│   └── settings/                         # 设定管理
│
├── ai/                                   # AI 引导引擎
│   ├── AIGuideEngine.kt
│   ├── GuidePromptBuilder.kt
│   └── GuideEvent.kt
│
└── pipeline/                             # 流水线层
    ├── PromptEngine.kt                   # 扩展
    └── GlossaryManager.kt                # 词库管理器
```

---

## 三、数据模型

### 3.1 实体定义

#### Project（小说项目）

```kotlin
@Entity(indices = [Index("updatedAt")])
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val premise: String,                 // 故事前提/脑洞
    val createdAt: Long,
    val updatedAt: Long
)
```

#### Beat（节拍）

```kotlin
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
    val beatId: String,                  // UUID
    val title: String,
    val summary: String,
    val type: BeatType,                  // 起承转合伏笔高潮
    val order: Int,                      // 独立顺序字段
    val createdAt: Long
)

enum class BeatType {
    OPENING,      // 起
    DEVELOPMENT,  // 承
    TWIST,        // 转
    CLOSING,      // 合
    FORESHADOW,   // 伏笔
    CLIMAX        // 高潮
}
```

#### Character（人设）

```kotlin
@Entity(
    indices = [
        Index("projectId"),
        Index("charId", unique = true)
    ]
)
data class Character(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val charId: String,                  // UUID
    val name: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)
```

#### Outline（大纲 - 版本化）

```kotlin
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

#### WorldRule（世界观）

```kotlin
@Entity(
    indices = [
        Index("projectId"),
        Index("ruleId", unique = true)
    ]
)
data class WorldRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val ruleId: String,                  // UUID
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)
```

#### BeatMapping（索引映射）

```kotlin
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
    val isActive: Boolean = true         // 是否激活（用于 AI 润色）
)

enum class SettingType {
    CHARACTER, OUTLINE, WORLD_RULE
}

enum class ContextType {
    STATE,        // 状态
    RELATION,     // 关系
    EVENT,        // 事件
    CONDITION     // 条件
}
```

#### Glossary（专属词库）

```kotlin
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
    val sourceId: String,                // 关联的 charId/ruleId
    val priority: GlossaryPriority
)

enum class GlossaryType {
    CHARACTER, WORLD, MANUAL
}

enum class GlossaryPriority {
    HIGH, MEDIUM, LOW
}
```

### 3.2 数据关系

```
Project (1) ──→ (N) Beat
Project (1) ──→ (N) Character
Project (1) ──→ (N) WorldRule
Project (1) ──→ (N) Glossary

Beat (1) ──→ (N) Outline        // 版本化
Beat (N) ←──→ (N) Character     // 通过 BeatMapping
Beat (N) ←──→ (N) WorldRule     // 通过 BeatMapping

Character (1) ──→ (N) Glossary
WorldRule (1) ──→ (N) Glossary
```

---

## 四、AI 引导引擎

### 4.1 三大 Skill

#### Skill 1: 故事脉络梳理

**输入**：用户脑洞/前提
**输出**：节拍器节点列表

```kotlin
suspend fun generateBeats(premise: String): Flow<GuideEvent>
```

Prompt 特点：
- Schema + Few-shot 格式
- 自动标注节拍类型（起承转合伏笔高潮）
- 发现逻辑断层时针对性补齐

#### Skill 2: 设定归类 + 索引绑定

**输入**：用户补充内容 + 当前节拍
**输出**：归类结果 + 映射关系

```kotlin
suspend fun classifyAndIndex(
    content: String,
    currentBeat: Beat,
    existingSettings: ExistingSettings
): Flow<GuideEvent>
```

Prompt 特点：
- ID 由系统生成，AI 只返回 `action`（CREATE/UPDATE）
- 自动建立设定与节拍的索引关系
- 标注上下文类型和备注

#### Skill 3: 专属词库生成

**输入**：项目所有人设 + 世界观
**输出**：Glossary 词条列表

```kotlin
suspend fun generateGlossary(
    characters: List<Character>,
    worldRules: List<WorldRule>
): Flow<GuideEvent>
```

Prompt 特点：
- 提取专有名词
- 标注优先级（HIGH/MEDIUM/LOW）
- 不生成拼音（语音转写场景）

### 4.2 Prompt 设计原则

1. **Schema + Few-shot**：比规则描述更稳定
2. **仅输出 JSON**：无其他文字，便于解析
3. **ID 系统生成**：AI 只返回动作，避免冲突
4. **留白原则**：用户未提及的内容全部留白

---

## 五、UI 层架构

### 5.1 MVI 模式

```
Intent → ViewModel → Result → Reducer → State → UI
```

#### WriterPadState

```kotlin
data class WriterPadState(
    val project: Project? = null,
    val isLoading: Boolean = false,
    val currentBeat: Beat? = null,
    val beatList: List<Beat> = emptyList(),
    val characters: List<CharacterWithContext> = emptyList(),
    val worldRules: List<WorldRuleWithContext> = emptyList(),
    val outline: Outline? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isAiProcessing: Boolean = false,
    val isAiPanelOpen: Boolean = false,
    val guideState: GuideState = GuideState.IDLE,
    val error: String? = null
)
```

#### WriterPadIntent

```kotlin
sealed class WriterPadIntent {
    // 项目
    data class LoadProject(val projectId: Long) : WriterPadIntent()
    data class CreateProject(val name: String, val premise: String) : WriterPadIntent()

    // 引导
    object StartGuide : WriterPadIntent()
    data class SubmitPremise(val premise: String) : WriterPadIntent()
    data class ConfirmBeats(val beats: List<BeatDraft>) : WriterPadIntent()

    // 节拍
    object AdvanceBeat : WriterPadIntent()
    data class SelectBeat(val beatId: String) : WriterPadIntent()

    // AI 对话
    object ToggleAiPanel : WriterPadIntent()
    data class SendAiMessage(val text: String) : WriterPadIntent()
}
```

### 5.2 页面结构

```
WriterPadActivity
├── 顶部：项目名称 + 当前节拍显示
├── 中部：内容区域
│   ├── 首次进入 → GuideInputFragment
│   └── 创作中   → 当前节拍内容 + 关联设定卡片
├── 底部：BeatManagerView（节拍器按钮）
│   ├── 显示当前节拍
│   ├── 长按 → 推进下一节拍
│   └── 双击 → BeatListDialog
└── 悬浮：AIGuidePanel（对话面板）
```

### 5.3 交互流程

1. **开屏引导**：输入脑洞 → AI 梳理 → 确认节拍器
2. **创作中**：长按推进、双击回溯
3. **补充设定**：对话面板输入 → AI 归类 → 自动索引
4. **语音润色**：悬浮球录音 → 基于节拍上下文润色

---

## 六、与现有系统集成

### 6.1 PromptEngine 扩展

```kotlin
fun buildWithContext(
    text: String,
    beatContext: BeatContext,
    style: PolishStyle
): String
```

优化策略：
- **分级截断**：角色 200 字、世界观 150 字、大纲 300 字
- **按需同步**：只传 `isActive = true` 的设定

### 6.2 FloatingBallService 扩展

```kotlin
// 设置当前节拍上下文
fun setBeatContext(context: BeatContext?)

companion object {
    fun updateBeatContext(context: BeatContext?)
}
```

### 6.3 数据流

```
ViewModel.uiState
    → BeatContext(beatId, characters, worldRules, outline)
    → FloatingBallService.updateBeatContext(context)
    → 用户录音
    → StreamingPipeline.stopWithContext(style, context)
    → PromptEngine.buildWithContext(text, context, style)
    → MiniMaxClient.chatStream(prompt)
    → 润色结果
```

---

## 七、数据库迁移

### 7.1 版本升级

从 version 1 → version 2

### 7.2 新增表

- Project
- Beat
- Character
- Outline
- WorldRule
- BeatMapping
- Glossary

### 7.3 迁移脚本

```kotlin
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 创建各表及索引
        // ...
    }
}
```

---

## 八、依赖注入

### 8.1 AppModule

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase

    @Provides @Singleton
    fun provideProjectRepository(database: AppDatabase): ProjectRepository

    // ... 其他 Repository

    @Provides @Singleton
    fun provideBeatContextService(...): BeatContextService

    @Provides @Singleton
    fun provideAIGuideEngine(...): AIGuideEngine

    @Provides @Singleton
    fun provideGlossaryManager(database: AppDatabase): GlossaryManager
}
```

### 8.2 ViewModelModule

```kotlin
@Module
@InstallIn(ViewModelComponent::class)
object ViewModelModule {
    @ViewModelScoped
    @Provides
    fun provideWriterPadViewModel(...): WriterPadViewModel
}
```

---

## 九、GlossaryManager

### 9.1 功能

- 构建项目词库 Trie 树
- 高效文本匹配
- 同步到 DictionaryEntry（语音纠错）

### 9.2 Trie 树实现

```kotlin
class GlossaryManager(private val database: AppDatabase) {
    private val glossaryCache = mutableMapOf<Long, TrieNode>()

    suspend fun buildGlossaryTrie(projectId: Long)
    fun matchWords(text: String, projectId: Long): List<MatchResult>
    suspend fun syncProjectGlossary(projectId: Long)
}
```

---

## 十、关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| ID 生成 | UUID | 避免并发冲突，删除后不复用 |
| 节拍顺序 | 独立 order 字段 | 删除后重排，保持连续 |
| Mapping 唯一性 | 复合主键 | 防止重复插入 |
| Glossary 唯一性 | 项目内唯一 | 不同项目可有同名词条 |
| AI Prompt | Schema + Few-shot | 比规则描述更稳定 |
| UI 架构 | MVI | 多状态场景更稳健 |
| 词库匹配 | Trie 树 | O(n) 时间复杂度 |

---

## 十一、Token 成本估算

假设场景：
- 1 个节拍
- 5 个激活角色（每个 200 字截断）
- 3 个激活世界观规则（每个 150 字截断）
- 1 个大纲（300 字截断）

估算：
- 基础模板：~150 tokens
- 角色：5 × 50 = 250 tokens
- 世界观：3 × 40 = 120 tokens
- 大纲：~80 tokens
- 用户输入：~100 tokens
- **总计：~700 tokens**

对比无优化场景（可能 2000+ tokens），节省约 65%。

---

## 十二、后续迭代

1. **Phase 1**：数据模型 + 数据库 + Repository
2. **Phase 2**：AI 引导引擎 + Prompt 设计
3. **Phase 3**：UI 层 + MVI 架构
4. **Phase 4**：与悬浮球集成
5. **Phase 5**：测试 + 优化

---

## 附录：参考文档

- [CLAUDE.md](../../CLAUDE.md) - 项目指令
- [AGENTS.md](../../AGENTS.md) - 编码规范
- [DATA_PERSISTENCE.md](../../DATA_PERSISTENCE.md) - 数据持久化规范
