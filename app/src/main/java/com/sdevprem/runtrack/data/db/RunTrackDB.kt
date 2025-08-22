package com.sdevprem.runtrack.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sdevprem.runtrack.data.db.dao.RunDao
import com.sdevprem.runtrack.data.db.mapper.DBConverters
import com.sdevprem.runtrack.data.model.Run

@Database(
    entities = [Run::class],
    version = 2,
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
    }

    abstract fun getRunDao(): RunDao

}