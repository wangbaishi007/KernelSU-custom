package me.weishu.kernelsu.monitor

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.weishu.kernelsu.auth.portal.PortalLoginPrefs
import me.weishu.kernelsu.ksuApp
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val LOG_TAG = "KernelSU"
private const val PREF_MONITOR = "monitor"
private const val KEY_SEKIRO_CLIENT_ID = "sekiro_client_id"

/**
 * Minimal Sekiro-compatible WebSocket client (same JSON protocol as official JS web SDK).
 * Registration is done via URL `group` + `clientId`; keep the socket open and reply to invoke messages.
 */
object SekiroWebSocketClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handlers: ConcurrentHashMap<String, (JSONObject) -> Any?> = ConcurrentHashMap()

    @Volatile
    private var started = false

    fun start(app: Application) {
        if (!MonitorConfig.ENABLE_SEKIRO) {
            Log.d(LOG_TAG, "[Sekiro] disabled (ENABLE_SEKIRO=false)")
            return
        }
        synchronized(this) {
            if (started) return
            started = true
            registerDefaultHandlers()
        }
        Log.i(LOG_TAG, "[Sekiro] start")
        scope.launch {
            while (isActive) {
                runCatching {
                    connectOnce(app)
                }.onFailure { e ->
                    Log.e(LOG_TAG, "[Sekiro] connect error: ${e.message}", e)
                }
                delay(MonitorConfig.SEKIRO_RECONNECT_MS)
            }
        }
    }

    fun registerAction(name: String, handler: (JSONObject) -> Any?) {
        handlers[name] = handler
    }

    private fun registerDefaultHandlers() {
        handlers["ping"] = { "pong" }
        handlers["echo"] = { req -> req.optString("msg", req.toString()) }
    }

    private suspend fun connectOnce(app: Application) {
        val url = buildWebSocketUrl(app)
        Log.i(LOG_TAG, "[Sekiro] connecting $url")
        val done = CompletableDeferred<Unit>()
        val client = ksuApp.okhttpClient
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(LOG_TAG, "[Sekiro] websocket open http=${response.code} (group should be visible on server)")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { handleSekiroRequest(webSocket, text) }
                    .onFailure { e -> Log.e(LOG_TAG, "[Sekiro] onMessage: ${e.message}", e) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(LOG_TAG, "[Sekiro] closed code=$code reason=$reason")
                done.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val http = response?.code?.toString() ?: "-"
                Log.e(
                    LOG_TAG,
                    "[Sekiro] failure http=$http msg=${response?.message} err=${t.message}",
                    t,
                )
                done.complete(Unit)
            }
        }
        val ws = client.newWebSocket(Request.Builder().url(url).build(), listener)
        try {
            done.await()
        } finally {
            runCatching { ws.cancel() }
        }
    }

    private fun buildWebSocketUrl(app: Application): String {
        val base = MonitorConfig.BASE_URL.trimEnd('/')
        val wsOrigin = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
            else -> "ws://$base"
        }
        val path = MonitorConfig.SEKIRO_REGISTER_PATH.trim().let { p ->
            if (p.startsWith("/")) p else "/$p"
        }
        val groupEnc = enc("group", MonitorConfig.GROUP)
        val idEnc = enc("clientId", clientId(app))
        return "$wsOrigin$path?$groupEnc&$idEnc"
    }

    private fun enc(k: String, v: String): String =
        "${URLEncoder.encode(k, StandardCharsets.UTF_8.name())}=" +
            URLEncoder.encode(v, StandardCharsets.UTF_8.name())

    /**
     * After portal login, uses saved username as Sekiro `clientId` (same as oss-uploader intent).
     * Otherwise persists a random UUID in `monitor` prefs.
     */
    private fun clientId(app: Application): String {
        if (MonitorConfig.ENABLE_PORTAL_LOGIN) {
            val username =
                app.getSharedPreferences(PortalLoginPrefs.PREFS_NAME, Application.MODE_PRIVATE)
                    .getString(PortalLoginPrefs.KEY_LAST_USERNAME, null)
                    ?.trim()
                    .orEmpty()
            if (username.isNotEmpty()) {
                return username
            }
        }
        val sp = app.getSharedPreferences(PREF_MONITOR, Application.MODE_PRIVATE)
        sp.getString(KEY_SEKIRO_CLIENT_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        sp.edit().putString(KEY_SEKIRO_CLIENT_ID, id).apply()
        return id
    }

    private fun handleSekiroRequest(webSocket: WebSocket, text: String) {
        val json = JSONObject(text)
        if (!json.has("__sekiro_seq__")) {
            return
        }
        val seq = json.opt("__sekiro_seq__").let { raw ->
            when (raw) {
                null, JSONObject.NULL -> return
                is Number -> raw.toLong()
                else -> raw.toString().toLongOrNull() ?: return
            }
        }
        val action = json.optString("action", "")
        if (action.isEmpty()) {
            sendFailed(webSocket, seq, "need request param {action}")
            return
        }
        val handler = handlers[action]
        if (handler == null) {
            sendFailed(webSocket, seq, "no action handler: $action defined")
            return
        }
        try {
            val result = handler(json)
            sendSuccess(webSocket, seq, result)
        } catch (e: Exception) {
            sendFailed(webSocket, seq, e.message ?: "handler error")
        }
    }

    private fun sendSuccess(webSocket: WebSocket, seq: Long, result: Any?) {
        val o = JSONObject()
        o.put("__sekiro_seq__", seq)
        o.put("code", 0)
        o.put("status", 0)
        when (result) {
            is JSONObject -> o.put("data", result)
            is org.json.JSONArray -> o.put("data", result)
            null -> o.put("data", JSONObject.NULL)
            is String ->
                runCatching { o.put("data", JSONObject(result)) }
                    .getOrElse { o.put("data", result) }
            else -> o.put("data", result.toString())
        }
        webSocket.send(o.toString())
    }

    private fun sendFailed(webSocket: WebSocket, seq: Long, message: String) {
        val o = JSONObject()
        o.put("__sekiro_seq__", seq)
        o.put("status", -1)
        o.put("message", message)
        webSocket.send(o.toString())
    }
}
