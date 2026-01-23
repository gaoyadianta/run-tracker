package com.sdevprem.runtrack.domain.model

data class RunAiAnnotationPoint(
    val timeOffsetMs: Long,
    val latitude: Double,
    val longitude: Double,
    val text: String
)
