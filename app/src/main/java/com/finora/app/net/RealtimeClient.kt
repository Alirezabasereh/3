package com.finora.app.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * WebSocket connection to Supabase Realtime. Reconnects with back-off, re-joins with a fresh token
 * every 50 minutes, and reports changes of the signed-in user's `user_data` row.
 */
class RealtimeClient(
    private val scope: CoroutineScope,
    private val baseUrl: String,
    private val anonKey: String,
    private val sessionProvider: suspend () -> Session?,
    private val onChange: (Long?) -> Unit,
    private val onConnected: (Boolean) -> Unit
) : RealtimeLink {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch { loop() }
    }

    override fun stop() {
        job?.cancel()
        job = null
        onConnected(false)
    }

    private suspend fun loop() {
        val attempt = AtomicInteger(0)
        val backoff = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000)
        while (scope.isActive) {
            val s = try { sessionProvider() } catch (e: Exception) { null }
            if (s == null) { delay(5_000); continue }

            val closed = CompletableDeferred<Unit>()
            val topic = RealtimeProtocol.topic(s.userId)
            val wsUrl = baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
                "/realtime/v1/websocket?apikey=" + anonKey + "&vsn=1.0.0"
            var heartbeat: Job? = null

            val socket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(RealtimeProtocol.joinMessage(s.userId, s.accessToken))
                    heartbeat = scope.launch {
                        var n = 2
                        while (isActive) {
                            delay(25_000)
                            webSocket.send(RealtimeProtocol.heartbeat(n++))
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    when (val ev = RealtimeProtocol.parse(text, topic)) {
                        is RtEvent.JoinOk -> { attempt.set(0); scope.launch { onConnected(true) } }
                        is RtEvent.JoinError -> { scope.launch { onConnected(false) }; webSocket.close(1000, null); closed.complete(Unit) }
                        is RtEvent.Change -> scope.launch { onChange(ev.updatedAtMillis) }
                        is RtEvent.Ignore -> { }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    closed.complete(Unit)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed.complete(Unit)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    closed.complete(Unit)
                }
            })

            try {
                withTimeoutOrNull(50 * 60 * 1000L) { closed.await() }
            } finally {
                heartbeat?.cancel()
                socket.cancel()
                onConnected(false)
            }
            delay(backoff[minOf(attempt.getAndIncrement(), backoff.size - 1)])
        }
    }
}
