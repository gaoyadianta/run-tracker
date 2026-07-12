package com.sdevprem.runtrack.ai.news.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.sdevprem.runtrack.di.MainDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalTtsChannel @Inject constructor(
    @ApplicationContext private val context: Context,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher
) : SpeechChannel {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val isReady = MutableStateFlow(false)
    private val doneFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val errorFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private var textToSpeech: TextToSpeech? = null

    override val utteranceDone: Flow<String> = doneFlow
    override val utteranceError: Flow<String> = errorFlow

    init {
        scope.launch {
            initializeIfNeeded()
        }
    }

    override suspend fun awaitReady(): Boolean {
        initializeIfNeeded()
        if (isReady.value) return true
        return withTimeoutOrNull(3_500L) {
            isReady.filter { it }.first()
            true
        } ?: false
    }

    override fun setLanguage(language: String) {
        val locale = if (language.startsWith("en", ignoreCase = true)) {
            Locale.US
        } else {
            Locale.SIMPLIFIED_CHINESE
        }
        scope.launch {
            textToSpeech?.language = locale
        }
    }

    override fun speak(text: String, utteranceId: String): Boolean {
        val tts = textToSpeech ?: return false
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            scope.launch {
                errorFlow.emit(utteranceId)
            }
            return false
        }
        return true
    }

    override fun stop() {
        scope.launch {
            try {
                textToSpeech?.stop()
            } catch (e: Exception) {
                Timber.w(e, "LocalTts stop failed")
            }
        }
    }

    override fun shutdown() {
        scope.launch {
            try {
                textToSpeech?.stop()
            } catch (_: Exception) {
            }
            try {
                textToSpeech?.shutdown()
            } catch (_: Exception) {
            }
            textToSpeech = null
            isReady.value = false
        }
    }

    private suspend fun initializeIfNeeded() {
        if (textToSpeech != null) return
        withContext(mainDispatcher) {
            if (textToSpeech != null) return@withContext
            val initState = MutableStateFlow<Int?>(null)
            lateinit var instance: TextToSpeech
            instance = TextToSpeech(context) { status ->
                initState.value = status
            }
            instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    val safeId = utteranceId ?: return
                    scope.launch {
                        doneFlow.emit(safeId)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    val safeId = utteranceId ?: return
                    scope.launch {
                        errorFlow.emit(safeId)
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    val safeId = utteranceId ?: return
                    Timber.w("LocalTts utterance error: id=$safeId, code=$errorCode")
                    scope.launch {
                        errorFlow.emit(safeId)
                    }
                }
            })

            val status = withTimeoutOrNull(2_500L) {
                initState.filter { it != null }.first()
            } ?: TextToSpeech.ERROR

            if (status == TextToSpeech.SUCCESS) {
                instance.language = Locale.SIMPLIFIED_CHINESE
                instance.setSpeechRate(1.0f)
                textToSpeech = instance
                isReady.value = true
            } else {
                Timber.w("LocalTts init failed: status=$status")
                try {
                    instance.shutdown()
                } catch (_: Exception) {
                }
                textToSpeech = null
                isReady.value = false
            }
        }
    }
}
