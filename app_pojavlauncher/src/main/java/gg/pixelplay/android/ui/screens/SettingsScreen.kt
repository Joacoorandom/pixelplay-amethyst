package gg.pixelplay.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gg.pixelplay.android.AppViewModel
import net.kdt.pojavlaunch.BuildConfig
import gg.pixelplay.android.Tab
import gg.pixelplay.android.ui.backgrounds.BackgroundEffect
import gg.pixelplay.android.ui.backgrounds.PixelBackdrop
import gg.pixelplay.android.ui.backgrounds.RetroGridLayer
import gg.pixelplay.android.ui.components.PixelButton
import gg.pixelplay.android.ui.components.PixelCard
import gg.pixelplay.android.ui.components.PixelVariant
import gg.pixelplay.android.ui.components.SectionTitle
import gg.pixelplay.android.ui.theme.AccentColors
import gg.pixelplay.android.ui.theme.LocalAccent
import gg.pixelplay.android.ui.theme.PixelColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val accent = LocalAccent.current
    val animations by vm.animations.collectAsStateWithLifecycle()
    val background by vm.background.collectAsStateWithLifecycle()
    val autoJoin by vm.autoJoin.collectAsStateWithLifecycle()
    val account by vm.repo.account.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Ajustes")

        PixelCard(Modifier.fillMaxWidth(), onClick = { vm.select(Tab.Accounts) }) {
            Text("cuenta", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                account?.let { "${it.username} · ${it.rankLabel}" } ?: "Sin cuenta — tocá para agregar",
                style = MaterialTheme.typography.bodyMedium,
                color = PixelColors.TextMuted,
            )
        }

        PixelCard(Modifier.fillMaxWidth()) {
            Text("color de acento", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentColors.forEach { c ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.value)
                            .border(2.dp, if (c.id == accent.id) Color.White else Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .clickable { vm.setAccent(c) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (c.id == accent.id) Icon(Icons.Rounded.Check, c.name, tint = Color.White)
                    }
                }
            }
        }

        PixelCard(Modifier.fillMaxWidth()) {
            Text("fondo", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("Los mismos efectos que el launcher de PC", style = MaterialTheme.typography.bodySmall, color = PixelColors.TextFaint)
            Spacer(Modifier.height(10.dp))
            val options = BackgroundEffect.entries
            options.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { bg -> BackgroundTile(bg, bg == background, Modifier.weight(1f)) { vm.setBackground(bg) } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        ToggleCard("animaciones", "Fondos animados y transiciones", animations, vm::setAnimations)
        ToggleCard("entrar al server", "Conectar a mc.pixelplay.gg al abrir el pack oficial", autoJoin, vm::setAutoJoin)

        PixelCard(Modifier.fillMaxWidth()) {
            Text("acerca de", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Pixel Play Android v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = PixelColors.TextMuted)
            Text(
                vm.repo.engine?.let { "Motor: ${it.name}" } ?: "Sin motor de juego en este build",
                style = MaterialTheme.typography.bodySmall,
                color = PixelColors.TextFaint,
            )
            Spacer(Modifier.height(8.dp))
            PixelButton("Recargar amigos", { vm.refreshFriends() }, variant = PixelVariant.Ghost, depth = 0.dp)
        }
    }
}

@Composable
private fun BackgroundTile(bg: BackgroundEffect, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.62f)
                .clip(RoundedCornerShape(4.dp))
                .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else accent.value.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
        ) {
            PixelBackdrop(bg, animate = selected)
            if (bg == BackgroundEffect.RetroGrid) RetroGridLayer(animate = selected)
            if (selected) {
                Icon(
                    Icons.Rounded.Check, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(RoundedCornerShape(3.dp)).background(accent.value),
                )
            }
        }
        Text(bg.label.lowercase(), style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else PixelColors.TextMuted, maxLines = 1,
            modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ToggleCard(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    PixelCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PixelColors.TextFaint)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = accent.value, checkedThumbColor = Color.White),
            )
        }
    }
}
