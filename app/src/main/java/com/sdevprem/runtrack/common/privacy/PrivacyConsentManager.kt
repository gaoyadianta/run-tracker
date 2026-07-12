package com.sdevprem.runtrack.common.privacy

import android.content.Context
import com.amap.api.maps.MapsInitializer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivacyConsentManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val preferences by lazy {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    val isAccepted: Boolean
        get() = preferences.getBoolean(KEY_ACCEPTED, false)

    fun initializeMapPrivacyState() {
        MapsInitializer.updatePrivacyShow(context, true, true)
        MapsInitializer.updatePrivacyAgree(context, isAccepted)
    }

    fun accept() {
        preferences.edit().putBoolean(KEY_ACCEPTED, true).apply()
        MapsInitializer.updatePrivacyAgree(context, true)
    }

    private companion object {
        const val PREFERENCES_NAME = "privacy_consent"
        const val KEY_ACCEPTED = "privacy_policy_accepted"
    }
}
