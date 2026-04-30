package com.aivoice.input.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aivoice.input.model.DictionaryEntry
import com.aivoice.input.model.HistoryItem
import com.aivoice.input.model.PolishStyle

@Database(
    entities = [HistoryItem::class, DictionaryEntry::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun dictionaryDao(): DictionaryDao

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
            ).build()
        }
    }
}

class Converters {
    @androidx.room.TypeConverter
    fun fromPolishStyle(style: PolishStyle): String = style.name

    @androidx.room.TypeConverter
    fun toPolishStyle(value: String): PolishStyle = PolishStyle.valueOf(value)
}