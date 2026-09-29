package gg.pixelplay.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gg.pixelplay.android.AppViewModel
import gg.pixelplay.android.data.CallDirection
import gg.pixelplay.android.data.CallPhase
import gg.pixelplay.android.ui.components.PixelButton
import gg.pixelplay.android.ui.components.PixelVariant
import gg.pixelplay.android.ui.components.PlayerHead
import gg.pixelplay.android.ui.theme.LocalAccent
import gg.pixelplay.android.ui.theme.PixelColors
import kotlinx.coroutines.delay

@Composable
fun CallOverlay(vm: AppViewModel, onAccept: () -> Unit, modifier: Modifier = Modifier) {
    val call by vm.social.call.collectAsStateWithLifecycle()
    val accent = LocalAccent.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var level by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(call?.phase) {
        while (call?.phase == CallPhase.Active) {
            now = System.currentTimeMillis()
            level = vm.social.micLevel()
            delay(100)
        }
    }

    AnimatedVisibility(
        call != null,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val c = call ?: return@AnimatedVisibility
        val incoming = c.phase == CallPhase.Ringing && c.direction == CallDirection.In
        val border = if (incoming) Color(0xFF34D399) else accent.value
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (incoming) Color(0xF2064E3B) else Color(0xF2000000))
                .border(2.dp, border, RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerHead(c.peer, 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        incoming -> "llamada de ${c.peer}"
                        c.phase == CallPhase.Ringing -> "llamando a ${c.peer}"
                        else -> "con ${c.peer}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                )
                val status = when {
                    incoming -> "Voz Pixel"
                    c.phase == CallPhase.Ringing -> "Sonando…"
                    c.audioError != null -> "Sin audio: ${c.audioError}"
                    else -> {
                        val s = ((now - c.since) / 1000).coerceAtLeast(0)
                        "%d:%02d".format(s / 60, s % 60) + if (c.peerMuted) " · ${c.peer} silenciado" else ""
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = if (c.audioError != null) PixelColors.Danger else PixelColors.TextMuted)
                    if (c.phase == CallPhase.Active && !c.muted) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.1f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth((level * 4f).coerceIn(0f, 1f)).background(accent.light))
                        }
                    }
                }
            }
            val pad = PaddingValues(10.dp)
            if (incoming) {
                PixelButton(null, vm.social::hangup, icon = Icons.Rounded.CallEnd, variant = PixelVariant.Destructive, depth = 3.dp, contentPadding = pad)
                Spacer(Modifier.width(8.dp))
                PixelButton(null, onAccept, icon = Icons.Rounded.Call, depth = 3.dp, contentPadding = pad)
            } else {
                if (c.phase == CallPhase.Active) {
                    PixelButton(null, { vm.social.setMuted(!c.muted) }, icon = if (c.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        variant = if (c.muted) PixelVariant.Destructive else PixelVariant.Ghost, depth = 0.dp, contentPadding = pad)
                    Spacer(Modifier.width(4.dp))
                    PixelButton(null, { vm.social.setSpeaker(!c.speaker) }, icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        variant = if (c.speaker) PixelVariant.Default else PixelVariant.Ghost, depth = 0.dp, contentPadding = pad)
                    Spacer(Modifier.width(4.dp))
                }
                PixelButton(null, vm.social::hangup, icon = Icons.Rounded.CallEnd, variant = PixelVariant.Destructive, depth = 3.dp, contentPadding = pad)
            }
        }
    }
}
