package com.sdevprem.runtrack.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunTrackDBMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RunTrackDB::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate8To9PreservesHistoryAndAddsBriefState() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(
                "INSERT INTO running_table " +
                    "(id, img, timestamp, avgSpeedInKMH, distanceInMeters, durationInMillis, " +
                    "caloriesBurned, totalSteps, avgStepsPerMinute, routePoints, imagePath) " +
                    "VALUES (1, X'', 1, 0, 0, 0, 0, 0, 0, '', NULL)"
            )
            execSQL(
                "INSERT INTO run_news_history " +
                    "(id, runId, title, source, publishedAtEpochMs, articleUrl, playedAtEpochMs) " +
                    "VALUES (1, 1, 'title', 'source', NULL, 'https://example.com/1', 1)"
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 9, true, RunTrackDB.MIGRATION_8_9).use { db ->
            db.query("SELECT briefText, completed FROM run_news_history WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals("", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
        }
    }

    private companion object {
        const val TEST_DB = "run-track-migration-test"
    }
}
