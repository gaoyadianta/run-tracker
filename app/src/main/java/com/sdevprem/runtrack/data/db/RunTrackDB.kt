package com.sdevprem.runtrack.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sdevprem.runtrack.data.db.dao.RunAiDao
import com.sdevprem.runtrack.data.db.dao.RunMetricsDao
import com.sdevprem.runtrack.data.db.dao.RunDao
import com.sdevprem.runtrack.data.db.dao.RunNewsHistoryDao
import com.sdevprem.runtrack.data.db.mapper.DBConverters
import com.sdevprem.runtrack.data.model.RunAiArtifact
import com.sdevprem.runtrack.data.model.RunMetricsEntity
import com.sdevprem.runtrack.data.model.Run
import com.sdevprem.runtrack.data.model.RunNewsHistoryEntity

@Database(
    entities = [Run::class, RunAiArtifact::class, RunMetricsEntity::class, RunNewsHistoryEntity::class],
    version = 8,
    exportSchema = true
)

@TypeConverters(DBConverters::class)
abstract class RunTrackDB : RoomDatabase() {
    companion object {
        const val RUN_TRACK_DB_NAME = "run_track_db"
        
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE running_table ADD COLUMN totalSteps INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE running_table ADD COLUMN avgStepsPerMinute REAL NOT NULL DEFAULT 0.0"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS run_ai_artifact (" +
                        "runId INTEGER NOT NULL, " +
                        "oneLiner TEXT, " +
                        "summary TEXT, " +
                        "traceAnnotationsJson TEXT, " +
                        "updatedAtEpochMs INTEGER NOT NULL, " +
                        "PRIMARY KEY(runId), " +
                        "FOREIGN KEY(runId) REFERENCES running_table(id) ON DELETE CASCADE" +
                        ")"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE running_table ADD COLUMN routePoints TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS run_metrics (" +
                        "runId INTEGER NOT NULL, " +
                        "paceSeries TEXT NOT NULL, " +
                        "heartRateSeries TEXT NOT NULL, " +
                        "elevationSeries TEXT NOT NULL, " +
                        "splits TEXT NOT NULL, " +
                        "updatedAtEpochMs INTEGER NOT NULL, " +
                        "PRIMARY KEY(runId), " +
                        "FOREIGN KEY(runId) REFERENCES running_table(id) ON DELETE CASCADE" +
                        ")"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE run_metrics ADD COLUMN cadenceSeries TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE run_metrics ADD COLUMN strideLengthSeries TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS run_news_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "runId INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "source TEXT NOT NULL, " +
                        "publishedAtEpochMs INTEGER, " +
                        "articleUrl TEXT NOT NULL, " +
                        "playedAtEpochMs INTEGER NOT NULL, " +
                        "FOREIGN KEY(runId) REFERENCES running_table(id) ON DELETE CASCADE" +
                        ")"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_run_news_history_runId ON run_news_history(runId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_run_news_history_playedAtEpochMs ON run_news_history(playedAtEpochMs)"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE running_table ADD COLUMN imagePath TEXT"
                )
            }
        }
    }

    abstract fun getRunDao(): RunDao
    abstract fun getRunAiDao(): RunAiDao
    abstract fun getRunMetricsDao(): RunMetricsDao
    abstract fun getRunNewsHistoryDao(): RunNewsHistoryDao

}
