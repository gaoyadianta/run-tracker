package com.sdevprem.runtrack.ai.news.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.sdevprem.runtrack.ai.news.model.NewsFailure
import com.sdevprem.runtrack.di.MainDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
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
    private val initMutex = Mutex()
    private val isReady = MutableStateFlow(false)
    private val eventFlow = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 32)
    private var textToSpeech: TextToSpeech? = null

    override val events: Flow<SpeechEvent> = eventFlow

    init {
        scope.launch { initializeIfNeeded() }
    }

    override suspend fun awaitReady(): Boolean {
        initializeIfNeeded()
        return isReady.value
    }

    override suspend fun setLanguage(language: String): Result<Unit> {
        if (!awaitReady()) return Result.failure(NewsFailure.TtsUnavailable)
        val locale = if (language.startsWith("en", ignoreCase = true)) {
            Locale.US
        } else {
            Locale.SIMPLIFIED_CHINESE
        }
        return withContext(mainDispatcher) {
            val result = textToSpeech?.setLanguage(locale) ?: TextToSpeech.ERROR
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Result.failure(NewsFailure.LanguageUnsupported)
            } else if (result == TextToSpeech.ERROR) {
                Result.failure(NewsFailure.TtsUnavailable)
            } else {
                Result.success(Unit)
            }
        }
    }

    override fun speak(text: String, utteranceId: String): Boolean {
        val tts = textToSpeech ?: return false
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            eventFlow.tryEmit(SpeechEvent.Error(utteranceId))
            return false
        }
        return true
    }

    override fun stop() {
        scope.launch {
            runCatching { textToSpeech?.stop() }
                .onFailure { Timber.w(it, "LocalTts stop failed") }
        }
    }

    override fun shutdown() {
        scope.launch {
            initMutex.withLock {
                runCatching { textToSpeech?.stop() }
                runCatching { textToSpeech?.shutdown() }
                textToSpeech = null
                isReady.value = false
            }
        }
    }

    private suspend fun initializeIfNeeded() {
        if (isReady.value) return
        initMutex.withLock {
            if (isReady.value) return
            withContext(mainDispatcher) {
                if (isReady.value) return@withContext
                val initResult = CompletableDeferred<Int>()
                val instance = TextToSpeech(context) { status ->
                    if (!initResult.isCompleted) initResult.complete(status)
                }
                instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { eventFlow.tryEmit(SpeechEvent.Completed(it)) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { eventFlow.tryEmit(SpeechEvent.Error(it)) }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utteranceId?.let { eventFlow.tryEmit(SpeechEvent.Error(it, errorCode)) }
                    }
                })

                val status = withTimeoutOrNull(2_500L) { initResult.await() } ?: TextToSpeech.ERROR
                if (status == TextToSpeech.SUCCESS) {
                    instance.setSpeechRate(1.0f)
                    textToSpeech = instance
                    isReady.value = true
                } else {
                    Timber.w("LocalTts init failed: status=$status")
                    runCatching { instance.shutdown() }
                    textToSpeech = null
                    isReady.value = false
                }
            }
        }
    }
}
