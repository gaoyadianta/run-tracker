package com.sdevprem.runtrack.ai.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioFocusCoordinator @Inject constructor(
    @ApplicationContext context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var onNewsFocusChange: ((Int) -> Unit)? = null
    private var onCompanionFocusChange: ((Int) -> Unit)? = null

    private var newsFocusRequest: AudioFocusRequest? = null
    private var companionFocusRequest: AudioFocusRequest? = null

    private val newsFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        onNewsFocusChange?.invoke(change)
    }

    private val companionFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        onCompanionFocusChange?.invoke(change)
    }

    fun setNewsFocusChangeListener(listener: ((Int) -> Unit)?) {
        onNewsFocusChange = listener
    }

    fun setCompanionFocusChangeListener(listener: ((Int) -> Unit)?) {
        onCompanionFocusChange = listener
    }

    fun requestNewsFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = newsFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(newsFocusListener)
                .setAcceptsDelayedFocusGain(false)
                .build()
                .also { newsFocusRequest = it }
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                newsFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandonNewsFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            newsFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(newsFocusListener)
        }
    }

    fun requestCompanionFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = companionFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(companionFocusListener)
                .setAcceptsDelayedFocusGain(false)
                .build()
                .also { companionFocusRequest = it }
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                companionFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandonCompanionFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            companionFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(companionFocusListener)
        }
    }

    fun abandonAll() {
        abandonCompanionFocus()
        abandonNewsFocus()
    }
}
