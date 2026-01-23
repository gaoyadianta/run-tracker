package com.sdevprem.runtrack.ai.summary

import com.sdevprem.runtrack.common.utils.DateTimeUtils
import com.sdevprem.runtrack.common.utils.RunUtils
import com.sdevprem.runtrack.data.model.Run
import com.sdevprem.runtrack.data.model.RunAiArtifact
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalRunSummaryGenerator @Inject constructor() {

    fun generate(runId: Int, run: Run): RunAiArtifact {
        val distanceKm = run.distanceInMeters / 1000f
        val distanceLabel = String.format(Locale.US, "%.1f", distanceKm)
        val paceLabel = RunUtils.formatPace(RunUtils.convertSpeedToPace(run.avgSpeedInKMH))
        val durationLabel = DateTimeUtils.getFormattedStopwatchTime(run.durationInMillis)

        val intensity = when {
            run.avgSpeedInKMH >= 10f -> "strong"
            run.avgSpeedInKMH >= 7f -> "steady"
            else -> "easy"
        }

        val oneLiner = when (intensity) {
            "strong" -> "Strong pace, great focus."
            "steady" -> "Steady pace, solid control."
            else -> "Easy effort, smooth strides."
        }

        val effortNote = when (intensity) {
            "strong" -> "high"
            "steady" -> "moderate"
            else -> "relaxed"
        }

        val suggestion = when (intensity) {
            "strong" -> "add a short cooldown jog"
            "steady" -> "start a touch easier for the first kilometer"
            else -> "finish with a few relaxed strides"
        }

        val summary = listOf(
            "This was a $intensity ${distanceLabel} km run.",
            "You averaged $paceLabel/km over $durationLabel.",
            "The effort looked $effortNote based on your pace.",
            "Next time, try $suggestion."
        ).joinToString(separator = "\n")

        return RunAiArtifact(
            runId = runId,
            oneLiner = oneLiner,
            summary = summary
        )
    }
}
