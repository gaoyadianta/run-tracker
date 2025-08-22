package com.sdevprem.runtrack.data.model

import android.graphics.Bitmap
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "running_table")
data class Run(
    var img: Bitmap,
    var timestamp: Date = Date(),
    var avgSpeedInKMH: Float = 0f,
    var distanceInMeters: Int = 0,
    var durationInMillis: Long = 0L,
    var caloriesBurned: Int = 0,
    // 新增步数相关字段
    var totalSteps: Int = 0,           // 总步数
    var avgStepsPerMinute: Float = 0f, // 平均步频

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0
)
