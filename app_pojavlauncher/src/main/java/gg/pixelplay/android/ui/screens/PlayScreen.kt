package gg.pixelplay.android.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gg.pixelplay.android.AppViewModel
import gg.pixelplay.android.Tab
import gg.pixelplay.android.data.LaunchState
import gg.pixelplay.android.ui.backgrounds.BackgroundEffect
import gg.pixelplay.android.ui.backgrounds.RetroGridLayer
import gg.pixelplay.android.ui.components.PixelButton
import gg.pixelplay.android.ui.components.PixelCard
import gg.pixelplay.android.ui.components.PixelVariant
import gg.pixelplay.android.ui.components.PlayerBody
import gg.pixelplay.android.ui.components.RankTag
import gg.pixelplay.android.ui.theme.LocalAccent
import gg.pixelplay.android.ui.theme.PixelColors

@Composable
fun PlayScreen(vm: AppViewModel) {
    val account by vm.repo.account.collectAsStateWithLifecycle()
    val profiles by vm.repo.profiles.collectAsStateWithLifecycle()
    val selectedId by vm.selectedProfile.collectAsStateWithLifecycle()
    val launch by vm.repo.launch.collectAsStateWithLifecycle()
    val background by vm.background.collectAsStateWithLifecycle()
    val animations by vm.animations.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    val accent = LocalAccent.current
    val activity = LocalContext.current as? Activity
    val working = launch as? LaunchState.Working

    Box(Modifier.fillMaxSize()) {
        if (background == BackgroundEffect.RetroGrid) RetroGridLayer(animations)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                (profile?.name ?: "Ningún perfil").lowercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            profile?.let {
                Text(
                    "${it.loader} ${it.gameVersion}${it.packVersion?.let { v -> " · pack $v" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent.light,
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PlayerBody(account?.username, 280.dp, Modifier.height(280.dp))
            }

            account?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it.username, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    RankTag(it.rankLabel, it.rankColor)
                }
                Spacer(Modifier.height(12.dp))
            }

            if (profile != null) {
                PixelCard(Modifier.fillMaxWidth(), onClick = { vm.select(Tab.Profiles) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Extension, null, tint = accent.light)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                            Text(
                                if (profile.mods > 0) "${profile.mods} mods" else if (profile.packId != null) "Se instala al jugar" else "Sin mods",
                                style = MaterialTheme.typography.bodySmall,
                                color = PixelColors.TextFaint,
                            )
                        }
                        Icon(Icons.Rounded.ExpandMore, "Cambiar perfil", tint = PixelColors.TextMuted)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            when (val l = launch) {
                is LaunchState.Working -> {
                    Text(l.step, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    val barMod = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    if (l.progress != null) {
                        LinearProgressIndicator({ l.progress }, barMod, color = accent.light, trackColor = Color.White.copy(alpha = 0.1f))
                    } else {
                        LinearProgressIndicator(barMod, color = accent.light, trackColor = Color.White.copy(alpha = 0.1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                is LaunchState.Failed -> {
                    Text(l.message, style = MaterialTheme.typography.bodySmall, color = PixelColors.Danger, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                }
                else -> Unit
            }

            Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton(
                    text = when {
                        account == null -> "Agregar cuenta"
                        working != null -> "Preparando…"
                        launch is LaunchState.Running -> "Iniciando…"
                        else -> "Launch"
                    },
                    onClick = {
                        vm.repo.clearLaunchError()
                        if (account == null) vm.select(Tab.Accounts) else activity?.let(vm::launch)
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    enabled = working == null && profile != null,
                    depth = 6.dp,
                    textStyle = MaterialTheme.typography.headlineMedium,
                )
                PixelButton(
                    text = null,
                    icon = Icons.Rounded.ExpandMore,
                    onClick = { vm.select(Tab.Profiles) },
                    modifier = Modifier.width(58.dp).fillMaxHeight(),
                    variant = PixelVariant.Default,
                    depth = 6.dp,
                )
            }
            if (vm.repo.engine == null) {
                Spacer(Modifier.height(6.dp))
                Text("Build sin motor: instala el pack pero no abre el juego", style = MaterialTheme.typography.labelSmall, color = PixelColors.TextFaint)
            }
        }
    }
}
