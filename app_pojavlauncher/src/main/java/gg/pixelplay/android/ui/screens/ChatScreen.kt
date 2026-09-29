package gg.pixelplay.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CallMissed
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gg.pixelplay.android.AppViewModel
import gg.pixelplay.android.data.ChatMessage
import gg.pixelplay.android.data.MessageKind
import gg.pixelplay.android.ui.components.PixelButton
import gg.pixelplay.android.ui.components.PixelTextField
import gg.pixelplay.android.ui.components.PlayerHead
import gg.pixelplay.android.ui.components.StatusDot
import gg.pixelplay.android.ui.theme.LocalAccent
import gg.pixelplay.android.ui.theme.PixelColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(vm: AppViewModel, username: String, onCall: (String) -> Unit) {
    val chats by vm.social.chats.collectAsStateWithLifecycle()
    val typing by vm.social.typing.collectAsStateWithLifecycle()
    val connected by vm.social.connected.collectAsStateWithLifecycle()
    val friends by vm.repo.friends.collectAsStateWithLifecycle()
    val friend = friends.firstOrNull { it.username.equals(username, true) }
    val messages = chats[username.lowercase()].orEmpty()
    val listState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }
    val accent = LocalAccent.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val typingAt = typing[username.lowercase()]
    val peerTyping = typingAt != null && now - typingAt < 4_000

    LaunchedEffect(typingAt) {
        now = System.currentTimeMillis()
        if (typingAt != null) {
            delay(4_100)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(messages.size, peerTyping) {
        val last = messages.size + (if (peerTyping) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    val send = {
        vm.send(username, draft)
        draft = ""
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).clickable { vm.openChat(null) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = Color.White)
            }
            Box {
                PlayerHead(username, 34.dp)
                friend?.let { StatusDot(it.status, Modifier.align(Alignment.BottomEnd)) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(username, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                Text(
                    when {
                        peerTyping -> "escribiendo…"
                        !connected -> "reconectando…"
                        else -> friend?.rankLabel ?: ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (peerTyping) accent.light else PixelColors.TextFaint,
                )
            }
            PixelButton(null, onClick = { onCall(username) }, icon = Icons.Rounded.Call, depth = 3.dp, contentPadding = PaddingValues(10.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(accent.value.copy(alpha = 0.25f)))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Todavía no hay mensajes. ¡Saludá a $username!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PixelColors.TextFaint,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
            }
            items(messages, key = { it.cid ?: it.id }) { m ->
                if (m.kind == MessageKind.Text) Bubble(m) { vm.retry(username, m.text, m.cid ?: m.id) } else CallLog(m)
            }
            if (peerTyping) {
                item(key = "typing") {
                    Text("$username está escribiendo…", style = MaterialTheme.typography.bodySmall, color = PixelColors.TextFaint)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PixelTextField(
                draft,
                {
                    draft = it.take(2000)
                    if (it.isNotBlank()) vm.typing(username)
                },
                "Escribí un mensaje…",
                Modifier.weight(1f),
                onSubmit = send,
            )
            Spacer(Modifier.width(8.dp))
            PixelButton(
                null,
                onClick = send,
                icon = Icons.AutoMirrored.Rounded.Send,
                enabled = draft.isNotBlank(),
                depth = 3.dp,
                contentPadding = PaddingValues(12.dp),
            )
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@Composable
private fun CallLog(m: ChatMessage) {
    val icon = when (m.kind) {
        MessageKind.CallEnded -> Icons.Rounded.Call
        MessageKind.CallDeclined -> Icons.Rounded.CallEnd
        else -> Icons.Rounded.CallMissed
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (m.kind == MessageKind.CallEnded) PixelColors.TextMuted else PixelColors.Danger, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("${m.text} · ${timeFormat.format(Date(m.timestamp))}", style = MaterialTheme.typography.labelSmall, color = PixelColors.TextMuted)
        }
    }
}

@Composable
private fun Bubble(msg: ChatMessage, onRetry: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 8.dp,
        bottomStart = if (msg.mine) 8.dp else 2.dp,
        bottomEnd = if (msg.mine) 2.dp else 8.dp,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(if (msg.mine) accent.value.copy(alpha = if (msg.pending) 0.5f else 0.85f) else Color.White.copy(alpha = 0.08f))
                .border(1.dp, if (msg.failed) PixelColors.Danger else if (msg.mine) accent.light.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f), shape)
                .then(if (msg.failed) Modifier.clickable(onClick = onRetry) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(msg.text, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (msg.failed) {
                    Text("No enviado · tocá para reintentar", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFCA5A5))
                } else {
                    Text(timeFormat.format(Date(msg.timestamp)), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
                    if (msg.mine) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            if (msg.pending) Icons.Rounded.Schedule else Icons.Rounded.DoneAll,
                            null,
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}
