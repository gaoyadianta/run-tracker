package com.sdevprem.runtrack.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.sdevprem.runtrack.background.tracking.service.DefaultBackgroundTrackingManager
import com.sdevprem.runtrack.data.db.RunTrackDB
import com.sdevprem.runtrack.data.db.RunTrackDB.Companion.RUN_TRACK_DB_NAME
import com.sdevprem.runtrack.data.tracking.location.DefaultLocationTrackingManager
import com.sdevprem.runtrack.data.tracking.location.LocationUtils
import com.sdevprem.runtrack.data.tracking.step.DefaultStepTrackingManager
import com.sdevprem.runtrack.data.tracking.timer.DefaultTimeTracker
import com.sdevprem.runtrack.domain.tracking.background.BackgroundTrackingManager
import com.sdevprem.runtrack.domain.tracking.location.LocationTrackingManager
import com.sdevprem.runtrack.domain.tracking.step.StepTrackingManager
import com.sdevprem.runtrack.domain.tracking.timer.TimeTracker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    companion object {

        private const val USER_PREFERENCES_FILE_NAME = "user_preferences"

        @Singleton
        @Provides
        fun provideFusedLocationProviderClient(
            @ApplicationContext context: Context
        ) = LocationServices
            .getFusedLocationProviderClient(context)

        @Provides
        @Singleton
        fun provideRunningDB(
            @ApplicationContext context: Context
        ): RunTrackDB = Room.databaseBuilder(
            context,
            RunTrackDB::class.java,
            RUN_TRACK_DB_NAME
        ).addMigrations(
            RunTrackDB.MIGRATION_1_2,
            RunTrackDB.MIGRATION_2_3
        )
        .build()

        @Singleton
        @Provides
        fun provideRunDao(db: RunTrackDB) = db.getRunDao()

        @Singleton
        @Provides
        fun provideRunAiDao(db: RunTrackDB) = db.getRunAiDao()

        @Provides
        @Singleton
        fun providesPreferenceDataStore(
            @ApplicationContext context: Context,
            @ApplicationScope scope: CoroutineScope,
            @IoDispatcher ioDispatcher: CoroutineDispatcher
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler(
                    produceNewData = { emptyPreferences() }
                ),
                produceFile = { context.preferencesDataStoreFile(USER_PREFERENCES_FILE_NAME) },
                scope = scope.plus(ioDispatcher + SupervisorJob())
            )

        @Singleton
        @Provides
        fun provideLocationRequest(): com.google.android.gms.location.LocationRequest {
            return LocationUtils.locationRequestBuilder.build()
        }

        @Singleton
        @Provides
        fun provideLocationTrackingManager(
            locationTrackingManagerFactory: com.sdevprem.runtrack.data.tracking.location.LocationTrackingManagerFactory
        ): LocationTrackingManager {
            return locationTrackingManagerFactory.createLocationTrackingManager()
        }

    }

    @Binds
    @Singleton
    abstract fun provideBackgroundTrackingManager(
        trackingServiceManager: DefaultBackgroundTrackingManager
    ): BackgroundTrackingManager

    @Binds
    @Singleton
    abstract fun provideTimeTracker(
        timeTracker: DefaultTimeTracker
    ): TimeTracker

    @Binds
    @Singleton
    abstract fun provideStepTrackingManager(
        stepTrackingManager: DefaultStepTrackingManager
    ): StepTrackingManager

}
