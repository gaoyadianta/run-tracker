package com.sdevprem.runtrack.data.tracking.timer

import android.os.SystemClock
import com.sdevprem.runtrack.di.ApplicationScope
import com.sdevprem.runtrack.di.DefaultDispatcher
import com.sdevprem.runtrack.domain.tracking.timer.TimeTracker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

class DefaultTimeTracker @Inject constructor(
    @ApplicationScope private val applicationScope: CoroutineScope,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : TimeTracker {
    private var accumulatedTimeMs = 0L
    private var resumedAtElapsedRealtimeMs = 0L
    private var isRunning = false
    private var callback: ((timeInMillis: Long) -> Unit)? = null
    private var job: Job? = null

    private fun start() {
        if (job != null)
            return
        resumedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
        this.job = applicationScope.launch(defaultDispatcher) {
            while (isRunning && isActive) {
                callback?.invoke(currentElapsedTimeMs())
                delay(1000)
            }
        }
    }

    override fun startResumeTimer(callback: (timeInMillis: Long) -> Unit) {
        if (isRunning)
            return
        this.callback = callback
        isRunning = true
        start()
    }

    override fun stopTimer() {
        pauseTimer()
        accumulatedTimeMs = 0L
    }

    override fun pauseTimer() {
        if (isRunning) {
            accumulatedTimeMs = currentElapsedTimeMs()
        }
        isRunning = false
        job?.cancel()
        job = null
        callback = null
    }

    override fun restoreElapsedTime(elapsedTimeMs: Long) {
        check(!isRunning) { "Timer must be paused before restoring elapsed time" }
        accumulatedTimeMs = elapsedTimeMs.coerceAtLeast(0L)
    }

    private fun currentElapsedTimeMs(): Long {
        if (!isRunning) return accumulatedTimeMs
        return accumulatedTimeMs +
            (SystemClock.elapsedRealtime() - resumedAtElapsedRealtimeMs).coerceAtLeast(0L)
    }

}
