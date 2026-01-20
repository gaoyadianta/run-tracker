package com.sdevprem.runtrack.ai.bailian

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber

class BailianWebSocketSession(
    private val url: String,
    private val headers: Map<String, String>
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var connectDeferred: CompletableDeferred<Result<Unit>>? = null

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<String> = _incomingMessages

    suspend fun connect(): Result<Unit> {
        close()

        val deferred = CompletableDeferred<Result<Unit>>()
        connectDeferred = deferred

        val request = Request.Builder()
            .url(url)
            .headers(headers.toHeaders())
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.d("Bailian WebSocket connected: ${response.code}")
                connectDeferred?.complete(Result.success(Unit))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _incomingMessages.tryEmit(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.e(t, "Bailian WebSocket connection failed")
                connectDeferred?.complete(Result.failure(t))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Timber.d("Bailian WebSocket closed: $code, $reason")
            }
        })

        return deferred.await()
    }

    fun send(text: String): Boolean {
        return webSocket?.send(text) == true
    }

    fun close() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }
}
