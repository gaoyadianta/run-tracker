package com.sdevprem.runtrack.ai.volcano.v3

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import timber.log.Timber

class VolcanoBinaryWebSocketSession(
    private val url: String,
    private val headers: Map<String, String>
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var connectDeferred: CompletableDeferred<Result<Unit>>? = null

    private val _incomingBinary = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incomingBinary: SharedFlow<ByteArray> = _incomingBinary

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
                Timber.d("Volcano V3 WebSocket connected: ${response.code}")
                connectDeferred?.complete(Result.success(Unit))
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                _incomingBinary.tryEmit(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.e(t, "Volcano V3 WebSocket connection failed")
                connectDeferred?.complete(Result.failure(t))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Timber.d("Volcano V3 WebSocket closed: $code, $reason")
            }
        })

        return deferred.await()
    }

    fun send(bytes: ByteArray): Boolean {
        return webSocket?.send(ByteString.of(*bytes)) == true
    }

    fun close() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }
}
