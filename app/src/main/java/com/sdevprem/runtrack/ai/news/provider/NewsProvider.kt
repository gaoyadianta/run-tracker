package com.sdevprem.runtrack.ai.news.provider

import com.sdevprem.runtrack.ai.news.model.NewsBriefBatch
import com.sdevprem.runtrack.ai.news.model.NewsBriefRequest

interface NewsProvider {
    suspend fun fetchBriefs(request: NewsBriefRequest): Result<NewsBriefBatch>
}
