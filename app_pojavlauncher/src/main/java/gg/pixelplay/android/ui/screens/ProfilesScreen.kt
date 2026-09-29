package gg.pixelplay.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gg.pixelplay.android.AppViewModel
import gg.pixelplay.android.Tab
import gg.pixelplay.android.game.GameVersions
import gg.pixelplay.android.ui.components.PixelButton
import gg.pixelplay.android.ui.components.PixelCard
import gg.pixelplay.android.ui.components.PixelTextField
import gg.pixelplay.android.ui.components.PixelVariant
import gg.pixelplay.android.ui.components.SectionTitle
import gg.pixelplay.android.ui.components.SegmentChip
import gg.pixelplay.android.ui.theme.LocalAccent
import gg.pixelplay.android.ui.theme.PixelColors

@Composable
fun ProfilesScreen(vm: AppViewModel) {
    val profiles by vm.repo.profiles.collectAsStateWithLifecycle()
    val selected by vm.selectedProfile.collectAsStateWithLifecycle()
    val accent = LocalAccent.current
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionTitle("Perfiles") {
                PixelButton("Nuevo", onClick = { creating = true }, icon = Icons.Rounded.Add, variant = PixelVariant.Ghost, depth = 0.dp)
            }
        }
        if (profiles.isEmpty()) item {
            Text("Cargando el perfil de Pixel Play…", style = MaterialTheme.typography.bodyMedium, color = PixelColors.TextFaint)
        }
        items(profiles, key = { it.id }) { p ->
            val isSelected = p.id == selected || (selected.isBlank() && p == profiles.first())
            PixelCard(
                Modifier.fillMaxWidth(),
                highlighted = isSelected,
                onClick = {
                    vm.selectProfile(p.id)
                    vm.select(Tab.Play)
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(accent.value.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(p.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1)
                            if (p.featured) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Rounded.Star, "Oficial", tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(
                            "${p.loader} ${p.gameVersion} · ${p.mods} mods" + (p.serverAddress?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = PixelColors.TextFaint,
                        )
                    }
                    if (isSelected) Icon(Icons.Rounded.CheckCircle, "Seleccionado", tint = accent.light)
                    if (!p.featured) {
                        PixelButton(null, { confirmDelete = p.id }, icon = Icons.Rounded.Delete, variant = PixelVariant.Ghost, depth = 0.dp, contentPadding = PaddingValues(8.dp))
                    }
                }
            }
        }
    }

    if (creating) CreateProfileDialog(vm) { creating = false }
    confirmDelete?.let { id ->
        val name = profiles.firstOrNull { it.id == id }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Color(0xFF111114),
            title = { Text("Borrar $name", color = Color.White) },
            text = { Text("Se borran sus mods, mundos y configuración.", color = PixelColors.TextMuted) },
            confirmButton = { PixelButton("Borrar", { vm.deleteProfile(id); confirmDelete = null }, variant = PixelVariant.Destructive) },
            dismissButton = { PixelButton("Cancelar", { confirmDelete = null }, variant = PixelVariant.Ghost, depth = 0.dp) },
        )
    }
}

private val Loaders = listOf("vanilla", "fabric", "neoforge", "forge", "quilt")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateProfileDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var loader by remember { mutableStateOf("fabric") }
    var versions by remember { mutableStateOf(listOf("1.21.1")) }
    var version by remember { mutableStateOf("1.21.1") }
    var showAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { GameVersions.releases() }.onSuccess { if (it.isNotEmpty()) versions = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111114),
        title = { Text("nuevo perfil", style = MaterialTheme.typography.titleLarge, color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelTextField(name, { name = it.take(32) }, "Nombre", Modifier.fillMaxWidth())
                Text("loader", style = MaterialTheme.typography.labelMedium, color = PixelColors.TextFaint)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Loaders.forEach { l -> SegmentChip(l, loader == l) { loader = l } }
                }
                Text("versión", style = MaterialTheme.typography.labelMedium, color = PixelColors.TextFaint)
                FlowRow(
                    Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (if (showAll) versions else versions.take(12)).forEach { v -> SegmentChip(v, version == v) { version = v } }
                    if (!showAll && versions.size > 12) SegmentChip("más…", false) { showAll = true }
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            PixelButton("Crear", { vm.createProfile(name, loader, version, onDismiss) }, enabled = !busy)
        },
        dismissButton = { PixelButton("Cancelar", onDismiss, variant = PixelVariant.Ghost, depth = 0.dp) },
    )
}
