package gg.pixelplay.android.social

import gg.pixelplay.android.data.CallDirection
import gg.pixelplay.android.data.CallPhase
import gg.pixelplay.android.data.CallState
import gg.pixelplay.android.data.ChatMessage
import gg.pixelplay.android.data.Http
import gg.pixelplay.android.data.MessageKind
import gg.pixelplay.android.data.PixelApi
import gg.pixelplay.android.data.mapObjects
import gg.pixelplay.android.data.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import kotlin.coroutines.resume

sealed interface SocialEvent {
    data class Message(val from: String, val text: String) : SocialEvent
    data class IncomingCall(val from: String) : SocialEvent
    data class CallEnded(val reason: String) : SocialEvent
    data class Notice(val text: String) : SocialEvent
}

data class SocialSession(val token: String, val username: String)

/** Client for the pixel-social service (chat + call signaling + audio relay over one WebSocket). */
class SocialClient(
    private val scope: CoroutineScope,
    private val voice: VoiceEngine,
    private val session: suspend () -> SocialSession?,
) {
    private val base = "${PixelApi.BASE}/social"

    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()
    private val _me = MutableStateFlow("")
    val me = _me.asStateFlow()
    private val _chats = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val chats = _chats.asStateFlow()
    private val _unread = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unread = _unread.asStateFlow()
    private val _typing = MutableStateFlow<Map<String, Long>>(emptyMap())
    val typing = _typing.asStateFlow()
    private val _call = MutableStateFlow<CallState?>(null)
    val call = _call.asStateFlow()
    private val _events = MutableSharedFlow<SocialEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    @Volatile private var ws: WebSocket? = null
    @Volatile private var token: String = ""
    private var loop: Job? = null
    @Volatile var openChat: String? = null
    @Volatile var appVisible = true

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            var backoff = 1_000L
            while (isActive) {
                val s = session()
                if (s == null) {
                    _connected.value = false
                    delay(5_000)
                    continue
                }
                if (!s.username.equals(_me.value, true)) {
                    _chats.value = emptyMap()
                    _unread.value = emptyMap()
                }
                token = s.token
                _me.value = s.username
                val started = System.currentTimeMillis()
                runSocket(s)
                _connected.value = false
                endLocalCall()
                backoff = if (System.currentTimeMillis() - started > 30_000) 1_000 else (backoff * 2).coerceAtMost(30_000)
                delay(backoff)
            }
        }
    }

    /** Drop the current socket so the loop reconnects with the (possibly new) active account. */
    fun reconnect() {
        ws?.close(1000, "account")
        if (loop?.isActive != true) start()
    }

    private suspend fun runSocket(s: SocialSession) = suspendCancellableCoroutine { cont ->
        val req = Request.Builder()
            .url("${base.replace("https://", "wss://")}/ws?device=android")
            .header("Authorization", "Bearer ${s.token}")
            .build()
        val socket = Http.client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { handle(JSONObject(text)) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                voice.feed(bytes.toByteArray())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (cont.isActive) cont.resume(Unit)
            }
        })
        ws = socket
        cont.invokeOnCancellation { socket.cancel() }
    }

    private fun sendJson(obj: JSONObject): Boolean = ws?.send(obj.toString()) == true

    private fun key(name: String) = name.lowercase()

    private fun parseMessage(o: JSONObject): ChatMessage {
        val from = o.str("from")
        return ChatMessage(
            id = o.opt("id")?.toString() ?: UUID.randomUUID().toString(),
            from = from,
            to = o.str("to"),
            text = o.str("text"),
            timestamp = o.optLong("ts", System.currentTimeMillis()),
            mine = from.equals(_me.value, true),
            kind = MessageKind.parse(o.optString("kind")),
        )
    }

    private fun addMessage(peer: String, m: ChatMessage, cid: String?) {
        _chats.update { all ->
            val list = all[key(peer)].orEmpty()
            val replaced = if (cid != null && list.any { it.cid == cid }) list.map { if (it.cid == cid) m else it }
            else if (list.any { it.id == m.id }) list
            else list + m
            all + (key(peer) to replaced)
        }
    }

    private fun handle(o: JSONObject) {
        when (o.str("t")) {
            "hello" -> {
                o.optJSONObject("me")?.str("username")?.takeIf { it.isNotBlank() }?.let { _me.value = it }
                o.optJSONObject("unread")?.let { u -> _unread.value = u.keys().asSequence().associate { key(it) to u.optInt(it) } }
            }
            "msg" -> {
                val m = parseMessage(o.optJSONObject("m") ?: return)
                val peer = if (m.mine) m.to else m.from
                addMessage(peer, m, o.str("cid").ifBlank { null })
                if (m.mine) return
                _typing.update { it - key(m.from) }
                if (openChat.equals(m.from, true) && appVisible) {
                    markRead(m.from)
                } else {
                    _unread.update { it + (key(m.from) to (it[key(m.from)] ?: 0) + 1) }
                    if (m.kind == MessageKind.Text) _events.tryEmit(SocialEvent.Message(m.from, m.text))
                }
            }
            "error" -> {
                val cid = o.str("cid")
                if (cid.isNotBlank()) {
                    _chats.update { all -> all.mapValues { (_, l) -> l.map { if (it.cid == cid) it.copy(pending = false, failed = true) else it } } }
                }
                o.str("error").takeIf { it.isNotBlank() }?.let { _events.tryEmit(SocialEvent.Notice(it)) }
            }
            "typing" -> o.str("from").takeIf { it.isNotBlank() }?.let { f -> _typing.update { it + (key(f) to System.currentTimeMillis()) } }
            "read" -> o.str("with").takeIf { it.isNotBlank() }?.let { w -> _unread.update { it - key(w) } }
            "call" -> handleCall(o)
        }
    }

    private fun handleCall(o: JSONObject) {
        val id = o.str("call")
        when (o.str("action")) {
            "ringing" -> {
                val to = o.optJSONObject("to")?.str("username").orEmpty()
                _call.value = CallState(id, to.ifBlank { _call.value?.peer.orEmpty() }, CallDirection.Out, CallPhase.Ringing)
            }
            "invite" -> {
                val from = o.optJSONObject("from")?.str("username").orEmpty()
                if (_call.value == null) {
                    _call.value = CallState(id, from, CallDirection.In, CallPhase.Ringing)
                    _events.tryEmit(SocialEvent.IncomingCall(from))
                }
            }
            "active" -> {
                val peer = o.optJSONObject("peer")?.str("username").orEmpty()
                val prev = _call.value
                val next = CallState(
                    id, peer.ifBlank { prev?.peer.orEmpty() }, prev?.direction ?: CallDirection.Out, CallPhase.Active,
                    since = o.optLong("since", System.currentTimeMillis()), muted = prev?.muted ?: false,
                )
                _call.value = next
                startVoice()
            }
            "peer-mute" -> _call.update { it?.copy(peerMuted = o.optBoolean("muted")) }
            "ended" -> {
                if (_call.value?.id == id || _call.value != null) {
                    endLocalCall()
                    _events.tryEmit(SocialEvent.CallEnded(o.str("reason")))
                }
            }
            "answered-elsewhere" -> if (_call.value?.id == id) endLocalCall()
            "busy" -> { endLocalCall(); _events.tryEmit(SocialEvent.Notice("Está en otra llamada")) }
            "unavailable" -> { endLocalCall(); _events.tryEmit(SocialEvent.Notice("No está conectado — le quedó una llamada perdida")) }
            "failed" -> { endLocalCall(); _events.tryEmit(SocialEvent.Notice(o.str("error").ifBlank { "No se pudo llamar" })) }
        }
    }

    private fun startVoice() {
        try {
            voice.muted = _call.value?.muted ?: false
            voice.start { frame -> ws?.send(frame.toByteString()) }
        } catch (e: Exception) {
            _call.update { it?.copy(audioError = e.message ?: "audio no disponible") }
        }
    }

    private fun endLocalCall() {
        voice.stop()
        _call.value = null
    }

    fun micLevel(): Float = voice.level

    fun send(to: String, text: String, retryCid: String? = null) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val cid = retryCid ?: "a${System.currentTimeMillis()}${(1000..9999).random()}"
        val pending = ChatMessage(cid, _me.value, to, clean, System.currentTimeMillis(), mine = true, pending = true, cid = cid)
        _chats.update { all ->
            val list = all[key(to)].orEmpty().filterNot { it.cid == cid }
            all + (key(to) to list + pending)
        }
        val ok = sendJson(JSONObject().put("t", "msg").put("to", to).put("text", clean).put("cid", cid))
        if (!ok) _chats.update { all -> all.mapValues { (_, l) -> l.map { if (it.cid == cid) it.copy(pending = false, failed = true) else it } } }
    }

    fun markRead(with: String) {
        _unread.update { it - key(with) }
        if (!sendJson(JSONObject().put("t", "read").put("with", with))) {
            scope.launch { runCatching { Http.postJson("$base/read", JSONObject().put("with", with), token) } }
        }
    }

    private var lastTyping = 0L
    fun typing(to: String) {
        val now = System.currentTimeMillis()
        if (now - lastTyping < 2_500) return
        lastTyping = now
        sendJson(JSONObject().put("t", "typing").put("to", to))
    }

    suspend fun loadHistory(with: String) {
        if (token.isBlank()) return
        val res = Http.getJson("$base/history?with=${URLEncoder.encode(with, "UTF-8")}&limit=100", token)
        val msgs = res.optJSONArray("messages").mapObjects(::parseMessage).sortedBy { it.timestamp }
        _chats.update { all ->
            val local = all[key(with)].orEmpty().filter { it.pending || it.failed }
            all + (key(with) to msgs + local)
        }
    }

    fun invite(to: String): Boolean {
        if (_call.value != null) return false
        _call.value = CallState("", to, CallDirection.Out, CallPhase.Ringing)
        if (!sendJson(JSONObject().put("t", "call").put("action", "invite").put("to", to))) {
            _call.value = null
            _events.tryEmit(SocialEvent.Notice("Sin conexión con el servidor de chat"))
            return false
        }
        return true
    }

    fun accept() {
        val c = _call.value ?: return
        sendJson(JSONObject().put("t", "call").put("action", "accept").put("call", c.id))
    }

    fun hangup() {
        val c = _call.value ?: return
        val action = when {
            c.phase == CallPhase.Active -> "end"
            c.direction == CallDirection.In -> "decline"
            else -> "cancel"
        }
        if (c.id.isNotBlank()) sendJson(JSONObject().put("t", "call").put("action", action).put("call", c.id))
        endLocalCall()
    }

    fun setMuted(muted: Boolean) {
        voice.muted = muted
        _call.update { it?.copy(muted = muted) }
        sendJson(JSONObject().put("t", "call").put("action", "mute").put("muted", muted))
    }

    fun setSpeaker(on: Boolean) {
        voice.setSpeaker(on)
        _call.update { it?.copy(speaker = on) }
    }
}
