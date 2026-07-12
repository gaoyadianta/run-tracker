package com.sdevprem.runtrack.data.tracking.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sdevprem.runtrack.common.utils.RouteEncodingUtils
import com.sdevprem.runtrack.domain.tracking.model.CurrentRunState
import com.sdevprem.runtrack.domain.tracking.session.TrackingSessionCheckpoint
import com.sdevprem.runtrack.domain.tracking.session.TrackingSessionCheckpointStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreTrackingSessionCheckpointStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : TrackingSessionCheckpointStore {
    override suspend fun load(): TrackingSessionCheckpoint? {
        val preferences = dataStore.data.first()
        if (preferences[ACTIVE] != true) return null
        return TrackingSessionCheckpoint(
            runState = CurrentRunState(
                distanceInMeters = preferences[DISTANCE_METERS] ?: 0,
                speedInKMH = preferences[SPEED_KMH] ?: 0f,
                isTracking = preferences[IS_TRACKING] ?: false,
                pathPoints = RouteEncodingUtils.decodeToPathPoints(preferences[ROUTE] ?: ""),
                totalSteps = preferences[TOTAL_STEPS] ?: 0,
                stepsPerMinute = preferences[CADENCE] ?: 0f,
                isStepSensorAvailable = preferences[STEP_SENSOR_AVAILABLE] ?: true
            ),
            durationMs = preferences[DURATION_MS] ?: 0L
        )
    }

    override suspend fun save(checkpoint: TrackingSessionCheckpoint) {
        dataStore.edit { preferences ->
            preferences[ACTIVE] = true
            preferences[DISTANCE_METERS] = checkpoint.runState.distanceInMeters
            preferences[SPEED_KMH] = checkpoint.runState.speedInKMH
            preferences[IS_TRACKING] = checkpoint.runState.isTracking
            preferences[ROUTE] = RouteEncodingUtils.encodePathPoints(checkpoint.runState.pathPoints)
            preferences[TOTAL_STEPS] = checkpoint.runState.totalSteps
            preferences[CADENCE] = checkpoint.runState.stepsPerMinute
            preferences[STEP_SENSOR_AVAILABLE] = checkpoint.runState.isStepSensorAvailable
            preferences[DURATION_MS] = checkpoint.durationMs
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(ACTIVE)
            preferences.remove(DISTANCE_METERS)
            preferences.remove(SPEED_KMH)
            preferences.remove(IS_TRACKING)
            preferences.remove(ROUTE)
            preferences.remove(TOTAL_STEPS)
            preferences.remove(CADENCE)
            preferences.remove(STEP_SENSOR_AVAILABLE)
            preferences.remove(DURATION_MS)
        }
    }

    private companion object {
        val ACTIVE = booleanPreferencesKey("active_run_checkpoint")
        val DISTANCE_METERS = intPreferencesKey("active_run_distance_meters")
        val SPEED_KMH = floatPreferencesKey("active_run_speed_kmh")
        val IS_TRACKING = booleanPreferencesKey("active_run_is_tracking")
        val ROUTE = stringPreferencesKey("active_run_route")
        val TOTAL_STEPS = intPreferencesKey("active_run_total_steps")
        val CADENCE = floatPreferencesKey("active_run_cadence")
        val STEP_SENSOR_AVAILABLE = booleanPreferencesKey("active_run_step_sensor_available")
        val DURATION_MS = longPreferencesKey("active_run_duration_ms")
    }
}
