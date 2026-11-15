package ai.hey.phone.notify

import ai.hey.phone.data.ApiClient
import ai.hey.phone.data.ApiException
import ai.hey.phone.data.PushFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException

/**
 * Keeps `/v1/inbox/stream` open while the app process lives: a fresh one-time ticket per socket, reconnect with backoff
 * (2 s .. 60 s). A 401/403 means the token is dead: [onUnauthorized] and stop. The 15-minute WorkManager poll covers the rest.
 */
class InboxStream(
    private val api: ApiClient,
    private val scope: CoroutineScope,
    private val onFrame: (PushFrame) -> Unit,
    private val onUnauthorized: () -> Unit,
) {
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var wait = 2_000L
            while (true) {
                try {
                    val connected = connectOnce()
                    if (connected) wait = 2_000L
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    if (e.unauthorized) { onUnauthorized(); return@launch }
                } catch (_: IOException) {
                }
                delay(wait)
                wait = (wait * 2).coerceAtMost(60_000L)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    /** One socket lifetime; true when it opened. Returns when the socket closed or failed. */
    private suspend fun connectOnce(): Boolean {
        val ticket = api.phoneSession().wsTicket
        var opened = false
        val closed = callbackFlow {
            val ws = api.wsClient().newWebSocket(api.wsRequest("v1/inbox/stream", ticket), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { opened = true }
                override fun onMessage(webSocket: WebSocket, text: String) { PushFrame.parse(text)?.let(onFrame) }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null); trySend(Unit) }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { trySend(Unit) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { trySend(Unit) }
            })
            awaitClose { ws.cancel() }
        }
        closed.first()
        return opened
    }
}
