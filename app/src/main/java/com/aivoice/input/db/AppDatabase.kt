package com.aivoice.input.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aivoice.input.model.Beat
import com.aivoice.input.model.BeatMapping
import com.aivoice.input.model.Character
import com.aivoice.input.model.DictionaryEntry
import com.aivoice.input.model.Glossary
import com.aivoice.input.model.HistoryItem
import com.aivoice.input.model.Outline
import com.aivoice.input.model.PolishStyle
import com.aivoice.input.model.Project
import com.aivoice.input.model.WorldRule
import com.aivoice.input.model.enums.BeatType
import com.aivoice.input.model.enums.ContextType
import com.aivoice.input.model.enums.GlossaryPriority
import com.aivoice.input.model.enums.GlossaryType
import com.aivoice.input.model.enums.SettingType

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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create Project table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS Project (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        premise TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Project_updatedAt ON Project(updatedAt)")

                // Create Beat table
                db.execSQL(
                    """
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
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Beat_projectId ON Beat(projectId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Beat_beatId ON Beat(beatId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Beat_order ON Beat(`order`)")

                // Create Character table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS Character (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        charId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Character_projectId ON Character(projectId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Character_charId ON Character(charId)")

                // Create Outline table
                db.execSQL(
                    """
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
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Outline_beatId ON Outline(beatId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Outline_projectId ON Outline(projectId)")

                // Create WorldRule table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS WorldRule (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        ruleId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_WorldRule_projectId ON WorldRule(projectId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_WorldRule_ruleId ON WorldRule(ruleId)")

                // Create BeatMapping table (composite primary key)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS BeatMapping (
                        beatId TEXT NOT NULL,
                        settingId TEXT NOT NULL,
                        settingType TEXT NOT NULL,
                        projectId INTEGER NOT NULL,
                        contextType TEXT NOT NULL,
                        contextNote TEXT NOT NULL,
                        isActive INTEGER NOT NULL,
                        PRIMARY KEY(beatId, settingId, settingType)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_BeatMapping_beatId ON BeatMapping(beatId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_BeatMapping_settingId ON BeatMapping(settingId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_BeatMapping_settingType ON BeatMapping(settingType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_BeatMapping_projectId ON BeatMapping(projectId)")

                // Create Glossary table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS Glossary (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        word TEXT NOT NULL,
                        type TEXT NOT NULL,
                        sourceId TEXT NOT NULL,
                        priority TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Glossary_projectId ON Glossary(projectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Glossary_word ON Glossary(word)")
            }
        }

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
    }
}

class Converters {
    // PolishStyle
    @androidx.room.TypeConverter
    fun fromPolishStyle(style: PolishStyle): String = style.name

    @androidx.room.TypeConverter
    fun toPolishStyle(value: String): PolishStyle = PolishStyle.valueOf(value)

    // BeatType
    @androidx.room.TypeConverter
    fun fromBeatType(type: BeatType): String = type.name

    @androidx.room.TypeConverter
    fun toBeatType(value: String): BeatType = BeatType.valueOf(value)

    // SettingType
    @androidx.room.TypeConverter
    fun fromSettingType(type: SettingType): String = type.name

    @androidx.room.TypeConverter
    fun toSettingType(value: String): SettingType = SettingType.valueOf(value)

    // ContextType
    @androidx.room.TypeConverter
    fun fromContextType(type: ContextType): String = type.name

    @androidx.room.TypeConverter
    fun toContextType(value: String): ContextType = ContextType.valueOf(value)

    // GlossaryType
    @androidx.room.TypeConverter
    fun fromGlossaryType(type: GlossaryType): String = type.name

    @androidx.room.TypeConverter
    fun toGlossaryType(value: String): GlossaryType = GlossaryType.valueOf(value)

    // GlossaryPriority
    @androidx.room.TypeConverter
    fun fromGlossaryPriority(priority: GlossaryPriority): String = priority.name

    @androidx.room.TypeConverter
    fun toGlossaryPriority(value: String): GlossaryPriority = GlossaryPriority.valueOf(value)
}
