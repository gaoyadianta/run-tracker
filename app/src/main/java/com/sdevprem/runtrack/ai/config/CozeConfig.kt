package com.sdevprem.runtrack.ai.config

import android.content.Context
import com.sdevprem.runtrack.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CozeConfig @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val cozeAccessToken: String by lazy {
        context.getString(R.string.coze_access_token)
    }
    
    val baseURL: String by lazy {
        context.getString(R.string.coze_base_url)
    }
    
    val botID: String by lazy {
        context.getString(R.string.coze_bot_id)
    }
    
    val voiceID: String by lazy {
        context.getString(R.string.coze_voice_id)
    }
    
    fun isConfigured(): Boolean {
        return cozeAccessToken.isNotBlank() && 
               baseURL.isNotBlank() && 
               botID.isNotBlank() && 
               voiceID.isNotBlank()
    }
}