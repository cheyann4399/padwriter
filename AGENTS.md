# AGENTS.md

> AI 编码协作规范 - AI 速录助手（安卓版）

本文档旨在为参与本项目的 AI 编码 AGENT 提供明确、可执行的指导。所有 AGENT 在开始任何工作前，必须阅读并遵守本文件中的规则。

---

## 1 通用规范

### 1.1 核心原则

- **先理解，后动手**：修改代码前必须阅读相关上下文，充分理解现有架构
- **最小变更**：只改必须改的，不做"顺手优化"
- **保持一致**：严格遵循项目现有的命名、格式、架构风格
- **测试验证**：每次修改后运行相关测试，确保不破坏现有功能

### 1.2 禁止行为

- ❌ 删除或重命名现有的公共 API
- ❌ 引入新依赖（除非明确要求）
- ❌ 在未被要求的文件中进行更改
- ❌ 使用硬编码的 API Key 或敏感信息
- ❌ 忽略或跳过测试用例
- ❌ 提交包含 `TODO`、`FIXME` 的代码

### 1.3 代码质量要求

- 单个函数不超过 50 行
- 复杂度高的逻辑必须添加注释，注释使用中文
- 所有公共函数必须有 KDoc 文档，文档使用中文
- 错误处理必须明确，不吞异常
- 日志输出要有意义，便于调试

### 1.4 协作流程

1. **确认理解**：复述任务要点，确保理解正确
2. **说明方案**：修改前简要说明计划
3. **分步执行**：大改动拆分成小步骤
4. **验证结果**：修改后运行测试并报告结果
5. **承认局限**：遇到不确定的问题，主动询问而非猜测

---

## 2 项目特定规范

### 2.1 项目概述

| 属性 | 值 |
|------|-----|
| 项目名称 | AI 速录助手（安卓版 Typeless） |
| 项目类型 | 安卓应用（全局语音输入工具） |
| 主要语言 | Kotlin |
| 最低 SDK | Android 8.0 (API 26) |
| 目标 SDK | Android 14 (API 34) |
| 架构模式 | MVVM |

### 2.2 核心功能模块

| 模块 | 功能描述 | 技术实现 |
|------|----------|----------|
| 悬浮球模块 | 全局可拖动悬浮球，按住录音 | WindowManager + 触摸监听 |
| 录音管理模块 | 开始、停止、取消、音效反馈 | MediaRecorder / AudioRecord |
| ASR 语音识别 | 语音转文字 | 百度/讯飞语音 API |
| AI 润色模块 | 去口头禅、加标点、修正语句 | 大模型 API |
| 辅助功能服务 | 文字自动输入到其他 App | AccessibilityService |
| 历史记录模块 | 转录记录存储与查询 | Room Database |
| 个人词库模块 | 专业词汇管理 | Room Database |
| 设置页模块 | 权限引导、偏好设置 | SharedPreferences / MMKV |

### 2.3 目录结构

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/aivoice/input/
│   │   │   ├── ui/                    # UI 层：Activity、Fragment、ViewModel
│   │   │   │   ├── floating/          # 悬浮球相关
│   │   │   │   ├── history/           # 历史记录页
│   │   │   │   ├── settings/          # 设置页
│   │   │   │   └── dictionary/        # 词库管理页
│   │   │   ├── service/               # 服务层
│   │   │   │   ├── FloatingBallService.kt      # 悬浮球服务
│   │   │   │   ├── AudioRecordService.kt       # 录音服务
│   │   │   │   └── TextInjectService.kt        # 辅助功能服务
│   │   │   ├── repository/            # 数据仓库层
│   │   │   ├── model/                 # 数据模型
│   │   │   ├── network/               # 网络请求
│   │   │   │   ├── asr/               # ASR 接口
│   │   │   │   └── ai/                # AI 润色接口
│   │   │   ├── db/                    # 数据库
│   │   │   ├── util/                  # 工具类
│   │   │   └── App.kt                 # Application 类
│   │   ├── res/                       # 资源文件
│   │   └── AndroidManifest.xml
│   ├── test/                          # 单元测试
│   └── androidTest/                   # 仪器测试
├── build.gradle.kts
└── proguard-rules.pro
```

### 2.4 代码规范

#### 2.4.1 命名约定

| 类型 | 风格 | 示例 |
|------|------|------|
| 类名 | PascalCase | `FloatingBallService` |
| 函数/方法名 | camelCase | `startRecording()` |
| 变量名 | camelCase | `audioRecord` |
| 常量名 | UPPER_SNAKE_CASE | `MAX_RECORD_DURATION` |
| 私有成员 | _前缀 | `_isRecording` |
| 布局文件 | snake_case | `activity_main.xml` |
| 资源 ID | snake_case | `btn_start_recording` |

#### 2.4.2 Kotlin 代码风格

```kotlin
// 1. 类定义：数据类用于纯数据，普通类用于有逻辑
data class RecordItem(
    val id: Long,
    val content: String,
    val createdAt: Long
)

// 2. 函数定义：使用 KDoc 文档
/**
 * 开始录音
 * @param maxDuration 最大录音时长（毫秒）
 * @return 是否成功开始录音
 */
fun startRecording(maxDuration: Long = 60_000L): Boolean {
    // 实现逻辑
}

// 3. 空安全：使用 ?. 和 ?: 处理空值
val content = record?.content ?: ""

// 4. 协程：使用结构化并发
class MainViewModel : ViewModel() {
    fun processAudio(audioPath: String) {
        viewModelScope.launch {
            try {
                val text = asrRepository.recognize(audioPath)
                val polished = aiRepository.polish(text)
                _result.value = polished
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}

// 5. Flow：用于响应式数据流
val records: Flow<List<RecordItem>> = recordDao.getAll()
```

#### 2.4.3 错误处理模式

```kotlin
// 1. 定义统一的错误类型
sealed class AppError {
    data class NetworkError(val message: String) : AppError()
    data class PermissionError(val permission: String) : AppError()
    data class ServiceError(val code: Int, val message: String) : AppError()
    data class UnknownError(val throwable: Throwable) : AppError()
}

// 2. 使用 Result 封装操作结果
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Failure(val error: AppError) : Result<Nothing>()
}

// 3. 业务层返回 Result
suspend fun recognizeAudio(audioPath: String): Result<String> {
    return try {
        val response = asrApi.recognize(audioPath)
        if (response.isSuccess) {
            Result.Success(response.text)
        } else {
            Result.Failure(AppError.ServiceError(response.code, response.message))
        }
    } catch (e: IOException) {
        Result.Failure(AppError.NetworkError("网络连接失败"))
    } catch (e: Exception) {
        Result.Failure(AppError.UnknownError(e))
    }
}
```

### 2.5 API 接口规范

#### 2.5.1 ASR 语音识别接口

```kotlin
/**
 * ASR 语音识别请求
 */
data class AsrRequest(
    val audio: ByteArray,           // 音频数据（PCM/AMR格式）
    val format: String = "pcm",     // 音频格式
    val rate: Int = 16000,          // 采样率
    val devPid: Int = 1537          // 语言模型（1537=普通话）
)

/**
 * ASR 语音识别响应
 */
data class AsrResponse(
    val isSuccess: Boolean,
    val text: String?,              // 识别结果
    val code: Int,                  // 错误码
    val message: String             // 错误信息
)
```

#### 2.5.2 AI 润色接口

```kotlin
/**
 * AI 润色请求
 */
data class AiPolishRequest(
    val text: String,               // 原始文本
    val style: PolishStyle = PolishStyle.FORMAL  // 润色风格
)

/**
 * 润色风格枚举
 */
enum class PolishStyle(val prompt: String) {
    RAW("请保持原文不变，仅添加标点符号"),
    FORMAL("请将文本整理为正式风格，适合工作邮件、周报"),
    CONCISE("请删除冗余内容，保留核心信息")
}

/**
 * AI 润色响应
 */
data class AiPolishResponse(
    val isSuccess: Boolean,
    val originalText: String,       // 原始文本
    val polishedText: String?,      // 润色后文本
    val message: String
)
```

### 2.6 数据库规范

#### 2.6.1 Room 数据库配置

```kotlin
// 1. 数据库定义
@Database(
    entities = [RecordEntity::class, DictionaryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun dictionaryDao(): DictionaryDao
}

// 2. 实体类定义
@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "original_text")
    val originalText: String,           // 原始识别文本
    @ColumnInfo(name = "polished_text")
    val polishedText: String?,          // 润色后文本
    @ColumnInfo(name = "style")
    val style: String,                  // 润色风格
    @ColumnInfo(name = "audio_path")
    val audioPath: String?,             // 音频文件路径
    @ColumnInfo(name = "duration")
    val duration: Long,                 // 录音时长（毫秒）
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "dictionary")
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "word")
    val word: String,                   // 词汇
    @ColumnInfo(name = "category")
    val category: String,               // 分类（人名、地名、术语等）
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
```

#### 2.6.2 数据库命名规范

| 对象类型 | 命名规范 | 示例 |
|----------|----------|------|
| 表名 | 小写+下划线，复数形式 | `records`, `dictionary` |
| 字段名 | 小写+下划线 | `original_text`, `created_at` |
| 主键 | `id` | `id` |
| 索引 | `idx_字段名` | `idx_created_at` |

### 2.7 权限管理规范

#### 2.7.1 必需权限

| 权限 | 用途 | 申请时机 |
|------|------|----------|
| SYSTEM_ALERT_WINDOW | 悬浮窗显示 | 首次启动时引导开启 |
| RECORD_AUDIO | 麦克风录音 | 首次按住悬浮球时申请 |
| BIND_ACCESSIBILITY_SERVICE | 辅助功能服务 | 设置页引导开启 |
| FOREGROUND_SERVICE | 前台服务保活 | 启动悬浮球服务时 |
| POST_NOTIFICATIONS | 通知（Android 13+） | 首次启动时申请 |

#### 2.7.2 权限检查工具类

```kotlin
object PermissionHelper {
    /**
     * 检查悬浮窗权限
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * 检查辅助功能权限
     */
    fun hasAccessibilityPermission(context: Context): Boolean {
        val service = ComponentName(context, TextInjectService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(service.flattenToString())
    }

    /**
     * 检查录音权限
     */
    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
```

### 2.8 日志规范

#### 2.8.1 日志级别使用

| 级别 | 使用场景 | 示例 |
|------|----------|------|
| VERBOSE | 详细调试信息 | 音量波形数据 |
| DEBUG | 调试信息 | 录音开始/结束 |
| INFO | 关键业务流程 | 语音识别完成 |
| WARN | 警告信息 | 权限未授予 |
| ERROR | 错误信息 | API 调用失败 |

#### 2.8.2 日志格式

```kotlin
// 使用统一的日志工具类
object Logger {
    private const val TAG = "AIVoiceInput"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}

// 使用示例
Logger.i("录音开始: duration=${duration}ms")
Logger.e("ASR识别失败: code=${code}, message=${message}")
```

### 2.9 测试规范

#### 2.9.1 测试分类

| 测试类型 | 位置 | 内容 |
|----------|------|------|
| 单元测试 | `src/test/` | ViewModel、Repository、工具类逻辑 |
| 仪器测试 | `src/androidTest/` | 数据库操作、Service 功能 |

#### 2.9.2 单元测试示例

```kotlin
// 使用 JUnit 5 + MockK
class MainViewModelTest {
    private lateinit var viewModel: MainViewModel
    private val asrRepository: AsrRepository = mockk()
    private val aiRepository: AiRepository = mockk()

    @BeforeEach
    fun setup() {
        viewModel = MainViewModel(asrRepository, aiRepository)
    }

    @Test
    fun `processAudio should return polished text on success`() = runTest {
        // Given
        val audioPath = "/path/to/audio.pcm"
        val originalText = "今天天气不错"
        val polishedText = "今天天气不错。"
        coEvery { asrRepository.recognize(audioPath) } returns Result.Success(originalText)
        coEvery { aiRepository.polish(originalText, PolishStyle.FORMAL) } returns Result.Success(polishedText)

        // When
        viewModel.processAudio(audioPath)
        advanceUntilIdle()

        // Then
        assertEquals(polishedText, viewModel.result.value)
    }

    @Test
    fun `processAudio should handle network error`() = runTest {
        // Given
        val audioPath = "/path/to/audio.pcm"
        coEvery { asrRepository.recognize(audioPath) } returns Result.Failure(AppError.NetworkError("网络错误"))

        // When
        viewModel.processAudio(audioPath)
        advanceUntilIdle()

        // Then
        assertNotNull(viewModel.error.value)
    }
}
```

### 2.10 Git 提交规范

```
<type>(<scope>): <subject>

类型 (type):
- feat:     新功能
- fix:      修复 bug
- docs:     文档更新
- style:    代码格式（不影响功能）
- refactor: 重构
- test:     测试相关
- chore:    构建/工具相关

示例:
feat(floating): add drag-to-edge animation
fix(audio): handle recording permission denial
docs(readme): update installation instructions
```

### 2.11 环境配置

#### 2.11.1 配置文件

敏感信息（API Key、密钥）必须使用 `local.properties` 或环境变量，禁止硬编码：

```properties
# local.properties（不提交到 Git）
ASR_API_KEY=your_asr_api_key
AI_API_KEY=your_ai_api_key
AI_API_ENDPOINT=https://api.example.com/v1
```

#### 2.11.2 BuildConfig 配置

```kotlin
// build.gradle.kts
android {
    defaultConfig {
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }

        buildConfigField("String", "ASR_API_KEY", "\"${localProperties.getProperty("ASR_API_KEY", "")}\"")
        buildConfigField("String", "AI_API_KEY", "\"${localProperties.getProperty("AI_API_KEY", "")}\"")
        buildConfigField("String", "AI_API_ENDPOINT", "\"${localProperties.getProperty("AI_API_ENDPOINT", "")}\"")
    }
}
```

---

## 3 部署与验收要求

### 3.1 构建要求

- 支持 Debug 和 Release 两种构建类型
- Release 版本必须开启代码混淆（ProGuard/R8）
- 签名配置使用环境变量，不硬编码密钥

### 3.2 验收标准

1. **功能完整性**
   - 悬浮球在任意界面正常显示和拖动
   - 按住录音、松开结束功能正常
   - 语音识别准确率满足基本需求
   - AI 润色输出符合预期风格
   - 文字自动输入到其他 App 正常工作

2. **权限引导**
   - 首次启动时引导用户开启必要权限
   - 权限被拒绝时提供友好的提示和重试入口

3. **稳定性**
   - 无崩溃、无 ANR
   - 内存占用合理（< 100MB）
   - 后台运行不被系统杀死

---

## 4 紧急联系

遇到以下情况时，**停止操作并询问人类**：

1. 涉及删除用户数据操作
2. 涉及安全敏感代码（权限、加密、隐私数据）
3. 需要修改核心架构
4. 任务描述模糊或存在多种理解
5. 发现现有代码有严重 bug（先报告，不要擅自修复）

---
