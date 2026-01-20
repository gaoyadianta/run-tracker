package com.sdevprem.runtrack.ai.realtime.provider

import com.sdevprem.runtrack.ai.config.VolcanoV3Config
import com.sdevprem.runtrack.ai.realtime.AIProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIRealtimeProviderFactory @Inject constructor(
    private val volcanoProvider: VolcanoRealtimeProvider,
    private val volcanoV3Provider: VolcanoV3RealtimeProvider,
    private val bailianProvider: BailianRealtimeProvider,
    private val volcanoV3Config: VolcanoV3Config
) {
    fun get(provider: AIProvider): AIRealtimeProvider {
        return when (provider) {
            AIProvider.VOLCANO -> {
                if (volcanoV3Config.wsMode == VolcanoV3Config.VolcanoWsMode.V3) {
                    volcanoV3Provider
                } else {
                    volcanoProvider
                }
            }
            AIProvider.BAILIAN -> bailianProvider
        }
    }
}
