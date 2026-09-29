package gg.pixelplay.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gg.pixelplay.android.AppViewModel
import gg.pixelplay.android.data.PixelFriend
import gg.pixelplay.android.data.PresenceStatus
import gg.pixelplay.android.ui.components.CountBadge
import gg.pixelplay.android.ui.components.PixelButton
import gg.pixelplay.android.ui.components.PixelCard
import gg.pixelplay.android.ui.components.PixelTextField
import gg.pixelplay.android.ui.components.PixelVariant
import gg.pixelplay.android.ui.components.PlayerHead
import gg.pixelplay.android.ui.components.RankTag
import gg.pixelplay.android.ui.components.SectionTitle
import gg.pixelplay.android.ui.components.SegmentChip
import gg.pixelplay.android.ui.components.StatusDot
import gg.pixelplay.android.ui.theme.PixelColors

@Composable
fun FriendsScreen(vm: AppViewModel, onCall: (String) -> Unit) {
    val friends by vm.repo.friends.collectAsStateWithLifecycle()
    val incoming by vm.repo.incoming.collectAsStateWithLifecycle()
    val outgoing by vm.repo.outgoing.collectAsStateWithLifecycle()
    val error by vm.repo.friendsError.collectAsStateWithLifecycle()
    val unread by vm.social.unread.collectAsStateWithLifecycle()
    val connected by vm.social.connected.collectAsStateWithLifecycle()
    val account by vm.repo.account.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var addName by rememberSaveable { mutableStateOf("") }
    val sorted = friends.sortedWith(
        compareByDescending<PixelFriend> { unread[it.username.lowercase()] ?: 0 }
            .thenBy { if (it.status == PresenceStatus.Offline) 1 else 0 }
            .thenBy { it.username.lowercase() },
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        SectionTitle("Amigos") {
            StatusDot(if (connected) PresenceStatus.Online else PresenceStatus.Offline, size = 10.dp)
            Spacer(Modifier.width(6.dp))
            Text(if (connected) "chat en línea" else "conectando…", style = MaterialTheme.typography.labelSmall, color = PixelColors.TextFaint)
        }
        if (account == null) {
            Spacer(Modifier.height(20.dp))
            Text("Agregá una cuenta Pixel para ver a tus amigos.", style = MaterialTheme.typography.bodyMedium, color = PixelColors.TextMuted)
            return@Column
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val submit = {
                vm.addFriend(addName)
                addName = ""
            }
            PixelTextField(addName, { addName = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(16) }, "Agregar por usuario…", Modifier.weight(1f), onSubmit = submit)
            Spacer(Modifier.width(8.dp))
            PixelButton(null, onClick = submit, icon = Icons.Rounded.PersonAdd, enabled = addName.length >= 3, depth = 3.dp, contentPadding = PaddingValues(12.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentChip("Amigos · ${friends.size}", tab == 0) { tab = 0 }
            SegmentChip("Recibidas · ${incoming.size}", tab == 1) { tab = 1 }
            SegmentChip("Enviadas · ${outgoing.size}", tab == 2) { tab = 2 }
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = PixelColors.Danger)
        }
        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                0 -> {
                    if (sorted.isEmpty()) item { Empty("Todavía no tenés amigos. Agregá a alguien por su usuario.") }
                    items(sorted, key = { it.uuid }) { f ->
                        FriendRow(
                            f,
                            unread = unread[f.username.lowercase()] ?: 0,
                            onClick = { vm.openChat(f.username) },
                            onCall = { onCall(f.username) },
                            onRemove = { vm.removeFriend(f.username) },
                        )
                    }
                }
                1 -> {
                    if (incoming.isEmpty()) item { Empty("No tenés solicitudes pendientes") }
                    items(incoming, key = { "in-" + it.uuid }) { f ->
                        RequestRow(f) {
                            PixelButton(null, { vm.accept(f.username) }, icon = Icons.Rounded.Check, depth = 3.dp, contentPadding = PaddingValues(10.dp))
                            Spacer(Modifier.width(6.dp))
                            PixelButton(null, { vm.decline(f.username) }, icon = Icons.Rounded.Close, variant = PixelVariant.Destructive, depth = 3.dp, contentPadding = PaddingValues(10.dp))
                        }
                    }
                }
                else -> {
                    if (outgoing.isEmpty()) item { Empty("No enviaste solicitudes") }
                    items(outgoing, key = { "out-" + it.uuid }) { f ->
                        RequestRow(f, subtitle = "Pendiente") {
                            PixelButton("Cancelar", { vm.cancelRequest(f.username) }, variant = PixelVariant.Ghost, depth = 0.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = PixelColors.TextFaint, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun RequestRow(f: PixelFriend, subtitle: String? = null, actions: @Composable () -> Unit) {
    PixelCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerHead(f.username, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.username, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    Spacer(Modifier.width(6.dp))
                    RankTag(f.rankLabel, f.rankColor)
                }
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFBBF24)) }
            }
            actions()
        }
    }
}

@Composable
private fun FriendRow(f: PixelFriend, unread: Int, onClick: () -> Unit, onCall: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    PixelCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                PlayerHead(f.username, 40.dp)
                StatusDot(f.status, Modifier.align(Alignment.BottomEnd), size = 12.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.username, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    RankTag(f.rankLabel, f.rankColor)
                }
                Text(
                    when (f.status) {
                        PresenceStatus.Online -> "En el launcher"
                        PresenceStatus.InGame -> "Jugando"
                        PresenceStatus.Offline -> "Desconectado"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (f.status) {
                        PresenceStatus.Online -> PixelColors.Online
                        PresenceStatus.InGame -> PixelColors.InGame
                        PresenceStatus.Offline -> PixelColors.TextFaint
                    },
                )
            }
            CountBadge(unread)
            if (unread > 0) Spacer(Modifier.width(8.dp))
            PixelButton(null, onCall, icon = Icons.Rounded.Call, variant = PixelVariant.Ghost, depth = 0.dp, contentPadding = PaddingValues(9.dp))
            Box {
                PixelButton(null, { menu = true }, icon = Icons.Rounded.PersonRemove, variant = PixelVariant.Ghost, depth = 0.dp, contentPadding = PaddingValues(9.dp))
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Eliminar a ${f.username}") }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}
