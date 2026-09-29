package gg.pixelplay.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import gg.pixelplay.android.AppViewModel
import gg.pixelplay.android.ui.components.PixelButton
import gg.pixelplay.android.ui.components.PixelCard
import gg.pixelplay.android.ui.components.PixelTextField
import gg.pixelplay.android.ui.components.PixelVariant
import gg.pixelplay.android.ui.components.SectionTitle
import gg.pixelplay.android.ui.components.SegmentChip
import gg.pixelplay.android.ui.theme.LocalAccent
import gg.pixelplay.android.ui.theme.PixelColors

@Composable
fun ModsScreen(vm: AppViewModel) {
    val mods by vm.mods.collectAsStateWithLifecycle()
    val loading by vm.modsLoading.collectAsStateWithLifecycle()
    val profiles by vm.repo.profiles.collectAsStateWithLifecycle()
    val selectedId by vm.selectedProfile.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(0) }
    val accent = LocalAccent.current
    val installed = profile?.installedMods.orEmpty()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        SectionTitle("Mods")
        Text(
            profile?.let { "Para ${it.name} · ${it.loader} ${it.gameVersion}" } ?: "Elegí un perfil",
            style = MaterialTheme.typography.bodySmall,
            color = accent.light,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentChip("Modrinth", tab == 0) { tab = 0 }
            SegmentChip("Instalados · ${installed.size}", tab == 1) { tab = 1 }
        }
        Spacer(Modifier.height(10.dp))
        if (tab == 0) {
            PixelTextField(
                value = query,
                onValueChange = {
                    query = it
                    vm.searchMods(it)
                },
                placeholder = "Buscar en Modrinth…",
                modifier = Modifier.fillMaxWidth(),
            )
            if (loading) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(Modifier.size(20.dp).align(Alignment.CenterHorizontally), color = accent.light, strokeWidth = 2.dp)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (tab == 0) {
                items(mods, key = { it.id }) { mod ->
                    val isInstalled = installed.any { it.projectId == mod.id }
                    PixelCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(mod.iconUrl, null, Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(mod.title, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1)
                                Text("por ${mod.author} · ${formatCount(mod.downloads)} descargas", style = MaterialTheme.typography.bodySmall, color = PixelColors.TextFaint)
                                Spacer(Modifier.height(2.dp))
                                Text(mod.description, style = MaterialTheme.typography.bodyMedium, color = PixelColors.TextMuted, maxLines = 2)
                            }
                            Spacer(Modifier.width(6.dp))
                            PixelButton(
                                null,
                                onClick = { vm.installMod(mod) },
                                icon = Icons.Rounded.Download,
                                variant = if (isInstalled) PixelVariant.Secondary else PixelVariant.Default,
                                depth = 3.dp,
                                contentPadding = PaddingValues(10.dp),
                            )
                        }
                    }
                }
            } else {
                if (installed.isEmpty()) item {
                    Text(
                        if (profile?.packId != null) "Los mods del pack se gestionan solos. Acá aparecen los que agregues vos." else "No agregaste mods a este perfil.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PixelColors.TextFaint,
                    )
                }
                items(installed, key = { it.projectId }) { m ->
                    PixelCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                                Text(m.filename, style = MaterialTheme.typography.bodySmall, color = PixelColors.TextFaint, maxLines = 1)
                            }
                            PixelButton(null, { vm.uninstallMod(m.projectId) }, icon = Icons.Rounded.Delete, variant = PixelVariant.Destructive, depth = 3.dp, contentPadding = PaddingValues(10.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000 -> "${n / 1_000}K"
    else -> n.toString()
}
