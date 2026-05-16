package com.aivoice.input.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_createsAllNewTables() {
        // Create database with version 1 schema
        val db = testHelper.createDatabase(TEST_DB_1_2, 1)

        // Insert test data into v1 tables (history and dictionary)
        db.execSQL(
            "INSERT INTO history (originalText, polishedText, style, timestamp) " +
                "VALUES ('hello', 'Hello', 'FORMAL', 1000)"
        )
        db.execSQL(
            "INSERT INTO dictionary (original, replacement, enabled) " +
                "VALUES ('btw', 'by the way', 1)"
        )

        // Verify data was inserted
        val cursorBefore = db.query("SELECT COUNT(*) FROM history")
        cursorBefore.moveToFirst()
        assertEquals(1, cursorBefore.getInt(0))
        cursorBefore.close()

        db.close()

        // Run migration 1->2 and validate schema against 2.json
        val migratedDb = testHelper.runMigrationsAndValidate(
            TEST_DB_1_2, 2, true, AppDatabase.MIGRATION_1_2
        )

        // Verify v1 data preserved
        val historyCursor = migratedDb.query("SELECT originalText FROM history")
        historyCursor.moveToFirst()
        assertEquals("hello", historyCursor.getString(0))
        historyCursor.close()

        val dictCursor = migratedDb.query("SELECT original FROM dictionary")
        dictCursor.moveToFirst()
        assertEquals("btw", dictCursor.getString(0))
        dictCursor.close()

        // Verify all 7 new tables exist
        val tableCursor = migratedDb.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'"
        )
        val tables = mutableListOf<String>()
        while (tableCursor.moveToNext()) {
            tables.add(tableCursor.getString(0))
        }
        tableCursor.close()

        val expectedTables = listOf(
            "history", "dictionary", "Project", "Beat", "Character",
            "Outline", "WorldRule", "BeatMapping", "Glossary"
        )
        for (table in expectedTables) {
            assertTrue("Table $table should exist after migration", tables.contains(table))
        }

        migratedDb.close()
    }

    @Test
    fun migrate2To3_addsAliasesColumn() {
        // Create database with version 2 schema
        val db = testHelper.createDatabase(TEST_DB_2_3, 2)

        // Insert a Glossary row (v2 schema has no aliases column)
        db.execSQL(
            "INSERT INTO Glossary (projectId, word, type, sourceId, priority) " +
                "VALUES (1, 'test-word', 'TERM', 'source-1', 'HIGH')"
        )
        db.close()

        // Run migration 2->3 and validate schema against 3.json
        val migratedDb = testHelper.runMigrationsAndValidate(
            TEST_DB_2_3, 3, true, AppDatabase.MIGRATION_2_3
        )

        // Verify Glossary data preserved
        val cursor = migratedDb.query("SELECT word, aliases FROM Glossary")
        cursor.moveToFirst()
        assertEquals("test-word", cursor.getString(0))
        assertEquals("", cursor.getString(1)) // default value for aliases
        cursor.close()

        migratedDb.close()
    }

    @Test
    fun migrate1To3_fullChain() {
        // Create database with version 1 schema
        val db = testHelper.createDatabase(TEST_DB_1_3, 1)

        // Insert test data into v1 tables
        db.execSQL(
            "INSERT INTO history (originalText, polishedText, style, timestamp) " +
                "VALUES ('raw text', 'Polished Text', 'CASUAL', 2000)"
        )
        db.execSQL(
            "INSERT INTO dictionary (original, replacement, enabled) " +
                "VALUES ('omg', 'oh my god', 1)"
        )
        db.close()

        // Run both migrations and validate schema against 3.json
        val migratedDb = testHelper.runMigrationsAndValidate(
            TEST_DB_1_3, 3, true,
            AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3
        )

        // Verify v1 data preserved through full chain
        val historyCursor = migratedDb.query("SELECT polishedText FROM history")
        historyCursor.moveToFirst()
        assertEquals("Polished Text", historyCursor.getString(0))
        historyCursor.close()

        val dictCursor = migratedDb.query("SELECT replacement FROM dictionary")
        dictCursor.moveToFirst()
        assertEquals("oh my god", dictCursor.getString(0))
        dictCursor.close()

        // Verify v3 schema: all 9 tables exist
        val tableCursor = migratedDb.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'"
        )
        val tables = mutableListOf<String>()
        while (tableCursor.moveToNext()) {
            tables.add(tableCursor.getString(0))
        }
        tableCursor.close()

        val expectedTables = listOf(
            "history", "dictionary", "Project", "Beat", "Character",
            "Outline", "WorldRule", "BeatMapping", "Glossary"
        )
        for (table in expectedTables) {
            assertTrue("Table $table should exist after full migration", tables.contains(table))
        }

        // Verify Glossary has aliases column (v3 feature)
        val glossaryCursor = migratedDb.query("PRAGMA table_info(Glossary)")
        val columns = mutableListOf<String>()
        while (glossaryCursor.moveToNext()) {
            columns.add(glossaryCursor.getString(glossaryCursor.getColumnIndex("name")))
        }
        glossaryCursor.close()
        assertTrue("Glossary should have aliases column", columns.contains("aliases"))

        migratedDb.close()
    }

    companion object {
        private const val TEST_DB_1_2 = "migration-test-1-2"
        private const val TEST_DB_2_3 = "migration-test-2-3"
        private const val TEST_DB_1_3 = "migration-test-1-3"
    }
}