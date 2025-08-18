package com.sdevprem.runtrack.ai.manager

import com.coze.openapi.service.auth.TokenAuth
import com.coze.openapi.service.service.CozeAPI
import com.sdevprem.runtrack.ai.config.CozeConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CozeAPIManager @Inject constructor(
    private val cozeConfig: CozeConfig
) {
    private val cozeAPI: CozeAPI by lazy {
        CozeAPI.Builder()
            .auth(TokenAuth(cozeConfig.cozeAccessToken))
            .baseURL(cozeConfig.baseURL)
            .build()
    }

    fun getCozeAPI(): CozeAPI = cozeAPI
}