package com.sdevprem.runtrack.data.model

/**
 * Immutable persistence payload created from a finished tracking session.
 * Child rows use a temporary runId and are rebound to the inserted run in one transaction.
 */
data class CompletedRunBundle(
    val run: Run,
    val aiArtifact: RunAiArtifact,
    val metrics: RunMetricsEntity,
    val newsHistory: List<RunNewsHistoryEntity>
)
