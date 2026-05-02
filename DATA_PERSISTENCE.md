# 数据持久化与保护 (Data Persistence)

作为一个处理用户语音数据和转录记录的安卓应用，数据的安全性、隐私保护和持久化是最关键的考量点。

## 1. 本地存储架构

### 1.1 存储类型概览

| 存储类型 | 用途 | 技术 | 特点 |
|----------|------|------|------|
| Room 数据库 | 结构化数据 | SQLite | 转录记录、词库 |
| SharedPreferences | 配置项 | Android SDK | 用户设置、偏好 |
| 文件存储 | 音频文件 | 内部存储 | 临时录音文件 |
| MMKV（可选） | 高性能 KV | 腾讯 MMKV | 敏感配置加密存储 |

### 1.2 Room 数据库设计

#### 1.2.1 数据库配置

```kotlin
@Database(
    entities = [
        RecordEntity::class,
        DictionaryEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aivoice_input_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

#### 1.2.2 表结构设计

**转录记录表 (records)**

```sql
CREATE TABLE records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    original_text TEXT NOT NULL,           -- 原始识别文本
    polished_text TEXT,                    -- 润色后文本
    style TEXT NOT NULL,                   -- 润色风格：RAW/FORMAL/CONCISE
    audio_path TEXT,                       -- 音频文件路径（可选保留）
    duration INTEGER NOT NULL,             -- 录音时长（毫秒）
    app_package TEXT,                      -- 输入目标应用包名
    created_at INTEGER NOT NULL            -- 创建时间戳
);

CREATE INDEX idx_records_created_at ON records(created_at);
CREATE INDEX idx_records_style ON records(style);
```

**个人词库表 (dictionary)**

```sql
CREATE TABLE dictionary (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    word TEXT NOT NULL UNIQUE,             -- 词汇
    category TEXT NOT NULL,                -- 分类：人名/地名/术语/其他
    pronunciation TEXT,                    -- 发音提示（可选）
    created_at INTEGER NOT NULL            -- 创建时间戳
);

CREATE INDEX idx_dictionary_category ON dictionary(category);
```

**审计日志表 (audit_logs)**

```sql
CREATE TABLE audit_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    action TEXT NOT NULL,                  -- 操作类型
    target TEXT,                           -- 操作目标
    details TEXT,                          -- 操作详情（JSON）
    created_at INTEGER NOT NULL            -- 操作时间戳
);

CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
```

### 1.3 SharedPreferences 配置项

```kotlin
/**
 * 用户配置项
 */
object PreferenceKeys {
    const val PREFS_NAME = "aivoice_input_prefs"

    // 功能开关
    const val KEY_FLOATING_BALL_ENABLED = "floating_ball_enabled"
    const val KEY_SOUND_EFFECT_ENABLED = "sound_effect_enabled"
    const val KEY_VIBRATION_ENABLED = "vibration_enabled"
    const val KEY_AUTO_START = "auto_start"

    // 默认设置
    const val KEY_DEFAULT_STYLE = "default_style"           // 默认润色风格
    const val KEY_MAX_RECORD_DURATION = "max_record_duration" // 最大录音时长
    const val KEY_AUTO_DELETE_DAYS = "auto_delete_days"     // 自动删除天数

    // 隐私设置
    const val KEY_SAVE_AUDIO = "save_audio"                 // 是否保存音频
    const val KEY_SAVE_HISTORY = "save_history"             // 是否保存历史
}
```

## 2. 数据安全保护

### 2.1 敏感数据处理原则

| 数据类型 | 存储策略 | 加密要求 |
|----------|----------|----------|
| API Key | BuildConfig + 不日志输出 | 禁止明文日志 |
| 用户转录内容 | Room 数据库 | 可选加密（用户敏感场景） |
| 音频文件 | 内部存储 | 不保留（默认）/ 可选保留 |
| 个人词库 | Room 数据库 | 无需加密 |

### 2.2 API Key 安全管理

```kotlin
/**
 * API 配置管理
 * 敏感信息通过 BuildConfig 注入，禁止硬编码
 */
object ApiConfig {
    val ASR_API_KEY: String = BuildConfig.ASR_API_KEY
    val AI_API_KEY: String = BuildConfig.AI_API_KEY
    val AI_API_ENDPOINT: String = BuildConfig.AI_API_ENDPOINT

    /**
     * 获取带认证的请求头
     * 注意：不要在日志中输出完整的 API Key
     */
    fun getAuthHeaders(): Map<String, String> {
        return mapOf(
            "Authorization" to "Bearer ${AI_API_KEY.take(8)}...",
            "Content-Type" to "application/json"
        )
    }
}
```

### 2.3 音频文件管理

```kotlin
/**
 * 音频文件管理器
 * 负责录音文件的创建、清理和安全管理
 */
object AudioManager {
    private const val AUDIO_DIR = "audio_cache"
    private const val MAX_CACHE_SIZE = 50 * 1024 * 1024L // 50MB

    /**
     * 创建临时录音文件
     */
    fun createTempAudioFile(context: Context): File {
        val audioDir = File(context.cacheDir, AUDIO_DIR)
        if (!audioDir.exists()) audioDir.mkdirs()

        val fileName = "record_${System.currentTimeMillis()}.pcm"
        return File(audioDir, fileName)
    }

    /**
     * 清理过期音频缓存
     * @param context 上下文
     * @param maxAgeMs 最大保留时间（毫秒）
     */
    fun cleanExpiredCache(context: Context, maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        val audioDir = File(context.cacheDir, AUDIO_DIR)
        if (!audioDir.exists()) return

        val now = System.currentTimeMillis()
        audioDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > maxAgeMs) {
                file.delete()
            }
        }
    }

    /**
     * 检查并清理超出大小限制的缓存
     */
    fun checkAndCleanOverflow(context: Context) {
        val audioDir = File(context.cacheDir, AUDIO_DIR)
        if (!audioDir.exists()) return

        val totalSize = audioDir.listFiles()?.sumOf { it.length() } ?: 0L
        if (totalSize > MAX_CACHE_SIZE) {
            // 按修改时间排序，删除最旧的文件
            audioDir.listFiles()
                ?.sortedBy { it.lastModified() }
                ?.forEach { file ->
                    file.delete()
                    val newSize = audioDir.listFiles()?.sumOf { it.length() } ?: 0L
                    if (newSize <= MAX_CACHE_SIZE * 0.8) return
                }
        }
    }
}
```

## 3. 数据备份与恢复

### 3.1 数据导出功能

```kotlin
/**
 * 数据导出工具
 * 支持导出转录记录和个人词库
 */
class DataExporter(private val context: Context) {

    /**
     * 导出数据为 JSON 文件
     */
    suspend fun exportToJson(): File = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val records = db.recordDao().getAllSync()
        val dictionary = db.dictionaryDao().getAllSync()

        val exportData = ExportData(
            exportTime = System.currentTimeMillis(),
            records = records,
            dictionary = dictionary
        )

        val json = Gson().toJson(exportData)
        val exportDir = File(context.getExternalFilesDir(null), "export")
        if (!exportDir.exists()) exportDir.mkdirs()

        val exportFile = File(exportDir, "backup_${System.currentTimeMillis()}.json")
        exportFile.writeText(json)
        exportFile
    }

    /**
     * 从 JSON 文件导入数据
     */
    suspend fun importFromJson(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = file.readText()
            val exportData = Gson().fromJson(json, ExportData::class.java)

            val db = AppDatabase.getDatabase(context)
            db.runInTransaction {
                exportData.records.forEach { db.recordDao().insert(it) }
                exportData.dictionary.forEach { db.dictionaryDao().insert(it) }
            }
            true
        } catch (e: Exception) {
            Logger.e("导入数据失败", e)
            false
        }
    }
}

data class ExportData(
    val exportTime: Long,
    val records: List<RecordEntity>,
    val dictionary: List<DictionaryEntity>
)
```

### 3.2 自动备份策略

```kotlin
/**
 * 自动数据清理任务
 * 在应用启动时执行，清理过期数据
 */
object DataCleanupTask {

    /**
     * 执行数据清理
     * @param context 上下文
     * @param retentionDays 数据保留天数
     */
    suspend fun execute(context: Context, retentionDays: Int = 30) {
        withContext(Dispatchers.IO) {
            val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)

            // 清理过期记录
            val db = AppDatabase.getDatabase(context)
            db.recordDao().deleteOldRecords(cutoffTime)

            // 清理音频缓存
            AudioManager.cleanExpiredCache(context)

            // 检查缓存大小
            AudioManager.checkAndCleanOverflow(context)
        }
    }
}
```

## 4. 隐私保护措施

### 4.1 数据脱敏

```kotlin
/**
 * 日志脱敏工具
 * 确保日志输出不包含敏感信息
 */
object LogSanitizer {

    /**
     * 脱敏文本内容（用于日志输出）
     * 保留前 20 个字符，其余用 * 替代
     */
    fun sanitizeText(text: String, maxLength: Int = 20): String {
        if (text.length <= maxLength) return text
        return text.take(maxLength) + "..."
    }

    /**
     * 脱敏 API Key
     */
    fun sanitizeApiKey(key: String): String {
        if (key.length <= 8) return "****"
        return key.take(4) + "****" + key.takeLast(4)
    }
}
```

### 4.2 用户隐私设置

```kotlin
/**
 * 隐私设置管理
 */
class PrivacyManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 是否允许保存历史记录
     */
    var saveHistory: Boolean
        get() = prefs.getBoolean(PreferenceKeys.KEY_SAVE_HISTORY, true)
        set(value) = prefs.edit().putBoolean(PreferenceKeys.KEY_SAVE_HISTORY, value).apply()

    /**
     * 是否允许保存音频文件
     */
    var saveAudio: Boolean
        get() = prefs.getBoolean(PreferenceKeys.KEY_SAVE_AUDIO, false)
        set(value) = prefs.edit().putBoolean(PreferenceKeys.KEY_SAVE_AUDIO, value).apply()

    /**
     * 清除所有用户数据
     */
    suspend fun clearAllData() {
        withContext(Dispatchers.IO) {
            // 清除数据库
            val db = AppDatabase.getDatabase(context)
            db.clearAllTables()

            // 清除音频缓存
            val audioDir = File(context.cacheDir, "audio_cache")
            audioDir.deleteRecursively()

            // 清除 SharedPreferences
            prefs.edit().clear().apply()
        }
    }
}
```

## 5. 数据库迁移策略

### 5.1 版本迁移

```kotlin
/**
 * 数据库迁移定义
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 添加新字段
        database.execSQL("ALTER TABLE records ADD COLUMN app_package TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 创建新表
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                action TEXT NOT NULL,
                target TEXT,
                details TEXT,
                created_at INTEGER NOT NULL
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action)")
    }
}
```

### 5.2 数据恢复保护

```kotlin
/**
 * 数据库备份管理
 * 在应用更新前自动备份
 */
object DatabaseBackup {

    /**
     * 创建数据库备份
     */
    fun backup(context: Context): Boolean {
        return try {
            val dbPath = context.getDatabasePath("aivoice_input_db")
            if (!dbPath.exists()) return false

            val backupDir = File(context.filesDir, "db_backup")
            if (!backupDir.exists()) backupDir.mkdirs()

            val backupFile = File(backupDir, "backup_${System.currentTimeMillis()}.db")
            dbPath.copyTo(backupFile, overwrite = true)
            true
        } catch (e: Exception) {
            Logger.e("数据库备份失败", e)
            false
        }
    }

    /**
     * 恢复最近的备份
     */
    fun restoreLatest(context: Context): Boolean {
        return try {
            val backupDir = File(context.filesDir, "db_backup")
            if (!backupDir.exists()) return false

            val latestBackup = backupDir.listFiles()
                ?.filter { it.extension == "db" }
                ?.maxByOrNull { it.lastModified() }
                ?: return false

            val dbPath = context.getDatabasePath("aivoice_input_db")
            latestBackup.copyTo(dbPath, overwrite = true)
            true
        } catch (e: Exception) {
            Logger.e("数据库恢复失败", e)
            false
        }
    }
}
```
