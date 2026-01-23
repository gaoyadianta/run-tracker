package com.sdevprem.runtrack.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class RunDetailWithAi(
    @Embedded val run: Run,
    @ColumnInfo(name = "oneLiner") val oneLiner: String? = null,
    @ColumnInfo(name = "summary") val summary: String? = null,
    @ColumnInfo(name = "traceAnnotationsJson") val traceAnnotationsJson: String? = null
)
