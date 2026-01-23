package com.sdevprem.runtrack.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class RunWithAi(
    @Embedded val run: Run,
    @ColumnInfo(name = "oneLiner") val oneLiner: String? = null
)
