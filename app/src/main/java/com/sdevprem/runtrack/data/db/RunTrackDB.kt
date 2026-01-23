package com.sdevprem.runtrack.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sdevprem.runtrack.data.db.dao.RunAiDao
import com.sdevprem.runtrack.data.db.dao.RunMetricsDao
import com.sdevprem.runtrack.data.db.dao.RunDao
import com.sdevprem.runtrack.data.db.mapper.DBConverters
import com.sdevprem.runtrack.data.model.RunAiArtifact
import com.sdevprem.runtrack.data.model.RunMetricsEntity
import com.sdevprem.runtrack.data.model.Run

@Database(
    entities = [Run::class, RunAiArtifact::class, RunMetricsEntity::class],
    version = 5,
    exportSchema = false
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
    }

    abstract fun getRunDao(): RunDao
    abstract fun getRunAiDao(): RunAiDao
    abstract fun getRunMetricsDao(): RunMetricsDao

}
