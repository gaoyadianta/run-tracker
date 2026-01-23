package com.sdevprem.runtrack.domain.tracking.model

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val timeMs: Long = 0L
)
