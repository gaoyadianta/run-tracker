package com.sdevprem.runtrack.ai.news.model

data class NewsBriefRequest(
    val keyword: String,
    val language: String,
    val limit: Int = DEFAULT_LIMIT,
    val maxAgeHours: Int = DEFAULT_MAX_AGE_HOURS
) {
    val normalizedLimit: Int
        get() = limit.coerceIn(1, MAX_LIMIT)

    val normalizedMaxAgeHours: Int
        get() = maxAgeHours.coerceIn(1, 168)

    companion object {
        const val DEFAULT_LIMIT = 5
        const val DEFAULT_MAX_AGE_HOURS = 24
        const val MAX_LIMIT = 10
    }
}

data class NewsBrief(
    val id: String,
    val title: String,
    val sourceName: String,
    val publishedAtEpochMs: Long?,
    val articleUrl: String,
    val spokenText: String
)

data class NewsBriefBatch(
    val briefs: List<NewsBrief>,
    val fetchedAtEpochMs: Long = System.currentTimeMillis()
)
