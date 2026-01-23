package com.sdevprem.runtrack.background.tracking.wakelock

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var wakeLock: PowerManager.WakeLock? = null
    private val powerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) {
            Timber.d("WakeLock already held")
            return
        }

        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RunTrack::LocationTracking"
        ).apply {
            setReferenceCounted(false)
            acquire()
            Timber.d("WakeLock acquired")
        }
    }

    fun releaseWakeLock() {
        wakeLock?.let { wl ->
            if (wl.isHeld) {
                wl.release()
                Timber.d("WakeLock released")
            }
        }
        wakeLock = null
    }

    fun isWakeLockHeld(): Boolean = wakeLock?.isHeld == true
}
