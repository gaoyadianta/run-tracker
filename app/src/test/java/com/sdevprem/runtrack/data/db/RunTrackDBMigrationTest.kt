package com.sdevprem.runtrack.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class RunTrackDBMigrationTest {
    @Test
    fun `migration 7 to 8 adds nullable image path`() {
        val statements = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") {
                statements += args?.firstOrNull() as String
            }
            defaultValue(method.returnType)
        } as SupportSQLiteDatabase

        RunTrackDB.MIGRATION_7_8.migrate(database)

        assertEquals(
            listOf("ALTER TABLE running_table ADD COLUMN imagePath TEXT"),
            statements
        )
    }

    @Test
    fun `migration 8 to 9 adds brief text and completion flag`() {
        val statements = mutableListOf<String>()
        val database = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") {
                statements += args?.firstOrNull() as String
            }
            defaultValue(method.returnType)
        } as SupportSQLiteDatabase

        RunTrackDB.MIGRATION_8_9.migrate(database)

        assertEquals(
            listOf(
                "ALTER TABLE run_news_history ADD COLUMN briefText TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE run_news_history ADD COLUMN completed INTEGER NOT NULL DEFAULT 0"
            ),
            statements
        )
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        else -> null
    }
}
