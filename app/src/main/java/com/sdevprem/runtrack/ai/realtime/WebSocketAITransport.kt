package com.sdevprem.runtrack.ai.realtime

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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketAITransport @Inject constructor() {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var connectDeferred: CompletableDeferred<Result<Unit>>? = null

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<String> = _incomingMessages

    suspend fun connect(config: AIConnectionConfig): Result<Unit> {
        disconnect()

        val deferred = CompletableDeferred<Result<Unit>>()
        connectDeferred = deferred

        val request = Request.Builder()
            .url(config.url)
            .headers(config.headers.toHeaders())
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.d("WebSocket连接成功: ${response.code}")
                connectDeferred?.complete(Result.success(Unit))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _incomingMessages.tryEmit(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.e(t, "WebSocket连接失败")
                connectDeferred?.complete(Result.failure(t))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Timber.d("WebSocket已关闭: $code, $reason")
            }
        })

        return deferred.await()
    }

    fun send(text: String): Boolean {
        return webSocket?.send(text) == true
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }
}
