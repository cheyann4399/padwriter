# WriterPad Phase 2: AI Engine Design

> AI 引导引擎 - 三大 Skill 实现

---

## 一、概述

### 1.1 目标

实现 WriterPad 的 AI 引导引擎，提供三大核心能力：
1. **故事脉络梳理** - 从脑洞生成节拍器骨架
2. **设定归类 + 索引绑定** - 智能分类用户补充内容，自动建立映射
3. **专属词库生成** - 提取专有名词，支持别名消歧

### 1.2 设计原则

- **Schema + Few-shot**：比规则描述更稳定
- **仅输出 JSON**：无其他文字，便于解析
- **ID 系统生成**：AI 只返回动作，避免冲突
- **留白原则**：用户未提及的内容全部留白
- **容错优先**：JSON 修复机制，提高成功率

---

## 二、架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      AIGuideEngine                           │
│  - generateBeats(premise): Flow<GuideEvent>                  │
│  - classifyAndIndex(content, beat, settings): Flow<GuideEvent>│
│  - generateGlossary(chars, rules): Flow<GuideEvent>          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    GuidePromptBuilder                        │
│  - buildBeatPrompt(premise): String                          │
│  - buildClassifyPrompt(content, beat, settings): String      │
│  - buildGlossaryPrompt(chars, rules): String                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      MiniMaxClient                           │
│  (existing - chatStream(prompt): Flow<String>)               │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 文件结构

```
app/src/main/java/com/aivoice/input/ai/
├── AIGuideEngine.kt          # 主引擎，协调 AI 调用
├── GuidePromptBuilder.kt     # Prompt 构建（Schema + Few-shot）
├── GuideEvent.kt             # 事件密封类
├── GuideResponseParser.kt    # JSON 解析 + 本地修复
└── JsonRepair.kt             # JSON 本地快速修复工具
```

---

## 三、数据模型

### 3.1 GuideEvent（事件流）

```kotlin
sealed class GuideEvent<out T> {
    object Loading : GuideEvent<Nothing>()
    data class Partial<T>(val data: T) : GuideEvent<T>()
    data class Complete<T>(val data: T) : GuideEvent<T>()
    data class Repaired<T>(val data: T, val originalJson: String) : GuideEvent<T>()
    data class Error(val message: String, val rawResponse: String? = null) : GuideEvent<Nothing>()
}
```

### 3.2 ClassificationResult（Skill 2 输出）

```kotlin
data class ClassificationResult(
    val characters: List<CharacterDraft>,
    val outline: OutlineDraft?,
    val worldRules: List<WorldRuleDraft>,
    val mappings: List<MappingDraft>,
    val conflictCheck: ConflictCheck?
)

data class ConflictCheck(
    val hasConflict: Boolean,
    val conflicts: List<ConflictItem>
)

data class ConflictItem(
    val settingId: String,
    val beatRange: Pair<Int, Int>,
    val description: String,
    val severity: ConflictSeverity
)

enum class ConflictSeverity {
    WARNING,  // 轻微违和，可能是有意为之
    ERROR     // 明显矛盾，需要处理
}
```

### 3.3 GlossaryDraft（Skill 3 输出 - 增强）

```kotlin
data class GlossaryDraft(
    val word: String,
    val type: GlossaryType,
    val sourceId: String,
    val priority: GlossaryPriority,
    val aliases: List<String> = emptyList(),  // 别名列表
    val canonicalId: String? = null           // 指向主词条（消歧用）
)
```

### 3.4 ExistingSettings（辅助数据类）

```kotlin
data class ExistingSettings(
    val characters: List<Character>,
    val worldRules: List<WorldRule>,
    val currentBeatId: String
)
```

---

## 四、三大 Skill 设计

### 4.1 Skill 1: 故事脉络梳理

**输入**：用户脑洞/前提
**输出**：`List<BeatDraft>`

Prompt 结构：
```
系统角色：你是一个网文创作顾问，擅长梳理故事脉络。

JSON Schema:
{
  "beats": [
    {
      "title": "节拍标题（2-6字）",
      "summary": "节拍摘要（20-50字）",
      "type": "OPENING|DEVELOPMENT|TWIST|CLOSING|FORESHADOW|CLIMAX"
    }
  ]
}

Few-shot 示例:
输入: "一个少年在废墟中发现神秘石碑，从此踏上修仙之路"
输出: {"beats": [{"title": "废墟觉醒", "summary": "少年李火旺在废墟中发现神秘石碑，获得传承", "type": "OPENING"}, ...]}

用户输入: {premise}

只输出 JSON，不要其他文字。
```

### 4.2 Skill 2: 设定归类 + 索引绑定

**输入**：用户补充内容 + 当前节拍 + 已有设定
**输出**：`ClassificationResult`

Prompt 结构：
```
系统角色：你是一个网文设定管理助手，擅长归类和检测冲突。

JSON Schema:
{
  "characters": [...],      // CharacterDraft 列表
  "outline": {...},         // OutlineDraft 或 null
  "worldRules": [...],      // WorldRuleDraft 列表
  "mappings": [...],        // MappingDraft 列表
  "conflictCheck": {        // 冲突检测
    "hasConflict": false,
    "conflicts": []
  }
}

冲突检测规则：
- 检查同一角色在不同节拍中的状态一致性
- 检查世界观规则是否被违反
- severity: WARNING（轻微违和）/ ERROR（明显矛盾）

当前节拍: {currentBeat}
已有设定: {existingSettings}
用户输入: {content}

只输出 JSON，不要其他文字。
```

### 4.3 Skill 3: 专属词库生成

**输入**：项目所有人设 + 世界观
**输出**：`List<GlossaryDraft>`

Prompt 结构：
```
系统角色：你是一个网文术语提取专家。

JSON Schema:
{
  "glossary": [
    {
      "word": "主词条",
      "type": "CHARACTER|WORLD|MANUAL",
      "sourceId": "关联ID",
      "priority": "HIGH|MEDIUM|LOW",
      "aliases": ["别名1", "别名2"]
    }
  ]
}

提取规则：
- 提取专有名词（人名、地名、功法、组织等）
- 优先级：主角/核心设定 HIGH，配角/次要设定 MEDIUM，其他 LOW
- 别名：包括外号、简称、尊称等

人设列表: {characters}
世界观列表: {worldRules}

只输出 JSON，不要其他文字。
```

---

## 五、JSON 修复机制

### 5.1 修复流程

```
JSON 解析失败
    ↓
尝试本地快速修复（JsonRepair）
    ↓ 成功
返回 Repaired 事件
    ↓ 失败
返回 Error（包含原始响应）
```

### 5.2 本地快速修复规则

```kotlin
object JsonRepair {
    fun repair(json: String): String {
        var result = json

        // 1. 尾随逗号修复
        result = result.replace(Regex(",\\s*]"), "]")
        result = result.replace(Regex(",\\s*}"), "}")

        // 2. 缺失右括号修复
        val openBraces = result.count { it == '{' }
        val closeBraces = result.count { it == '}' }
        if (openBraces > closeBraces) {
            result += "}".repeat(openBraces - closeBraces)
        }

        // 3. 缺失右方括号修复
        val openBrackets = result.count { it == '[' }
        val closeBrackets = result.count { it == ']' }
        if (openBrackets > closeBrackets) {
            result += "]".repeat(openBrackets - closeBrackets)
        }

        // 4. 非法转义序列修复
        result = result.replace(Regex("\\\\(?!['\"\\\\/bfnrt])"), "")

        return result
    }
}
```

---

## 六、AIGuideEngine 接口

```kotlin
class AIGuideEngine(
    private val client: MiniMaxClient,
    private val promptBuilder: GuidePromptBuilder,
    private val parser: GuideResponseParser
) {
    /**
     * Skill 1: 生成节拍
     * @param premise 用户脑洞/前提
     * @return 事件流，最终输出 List<BeatDraft>
     */
    fun generateBeats(premise: String): Flow<GuideEvent<List<BeatDraft>>>

    /**
     * Skill 2: 设定归类 + 冲突检测
     * @param content 用户补充内容
     * @param currentBeat 当前节拍
     * @param existingSettings 已有设定
     * @return 事件流，最终输出 ClassificationResult
     */
    fun classifyAndIndex(
        content: String,
        currentBeat: Beat,
        existingSettings: ExistingSettings
    ): Flow<GuideEvent<ClassificationResult>>

    /**
     * Skill 3: 词库生成（含别名）
     * @param characters 项目所有人设
     * @param worldRules 项目所有世界观
     * @return 事件流，最终输出 List<GlossaryDraft>
     */
    fun generateGlossary(
        characters: List<Character>,
        worldRules: List<WorldRule>
    ): Flow<GuideEvent<List<GlossaryDraft>>>
}
```

---

## 七、Token 优化策略

### 7.1 内容截断

传入已有设定时进行截断：
- 角色：200 字
- 世界观：150 字
- 大纲：300 字

### 7.2 Prompt 精简

- Few-shot 示例控制在 2-3 个
- Schema 定义紧凑，无冗余字段
- 系统角色描述一句话

### 7.3 预估 Token 消耗

| Skill | 输入 | Prompt | 输出 | 总计 |
|-------|------|--------|------|------|
| Skill 1 | ~100 | ~300 | ~200 | ~600 |
| Skill 2 | ~300 | ~400 | ~300 | ~1000 |
| Skill 3 | ~500 | ~300 | ~200 | ~1000 |

---

## 八、依赖注入

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AIGuideModule {

    @Provides
    @Singleton
    fun provideMiniMaxClient(@ApplicationContext context: Context): MiniMaxClient {
        val apiKey = BuildConfig.AI_API_KEY
        return MiniMaxClient(apiKey)
    }

    @Provides
    @Singleton
    fun provideGuidePromptBuilder(): GuidePromptBuilder {
        return GuidePromptBuilder()
    }

    @Provides
    @Singleton
    fun provideGuideResponseParser(): GuideResponseParser {
        return GuideResponseParser()
    }

    @Provides
    @Singleton
    fun provideAIGuideEngine(
        client: MiniMaxClient,
        promptBuilder: GuidePromptBuilder,
        parser: GuideResponseParser
    ): AIGuideEngine {
        return AIGuideEngine(client, promptBuilder, parser)
    }
}
```

---

## 九、测试策略

### 9.1 单元测试

- `GuidePromptBuilder` - 验证 Prompt 生成正确
- `GuideResponseParser` - 验证 JSON 解析和修复
- `JsonRepair` - 验证各种破损 JSON 修复

### 9.2 集成测试

- Mock MiniMaxClient，测试 AIGuideEngine 事件流
- 测试错误处理路径

---

## 十、后续迭代

Phase 2 完成后：
- **Phase 3**: UI 层 + MVI 架构
- **Phase 4**: 与悬浮球集成
