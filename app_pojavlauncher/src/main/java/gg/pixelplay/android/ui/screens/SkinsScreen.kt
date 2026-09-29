package gg.pixelplay.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gg.pixelplay.android.AppViewModel
import gg.pixelplay.android.ui.components.PixelButton
import gg.pixelplay.android.ui.components.PixelCard
import gg.pixelplay.android.ui.components.PixelVariant
import gg.pixelplay.android.ui.components.PlayerBody
import gg.pixelplay.android.ui.components.SectionTitle

private val SampleSkins = listOf("MHF_Steve", "MHF_Alex", "Notch", "jeb_", "Dinnerbone", "Technoblade")

@Composable
fun SkinsScreen(vm: AppViewModel) {
    val account by vm.repo.account.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(account?.username ?: SampleSkins.first()) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                SectionTitle("Skins") {
                    PixelButton("Subir", onClick = {}, icon = Icons.Rounded.Upload, variant = PixelVariant.Ghost, depth = 0.dp)
                }
                Spacer(Modifier.height(12.dp))
                PixelCard(Modifier.fillMaxWidth(), highlighted = true) {
                    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        PlayerBody(selected, 240.dp, Modifier.height(240.dp))
                    }
                    Text(selected, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
        items(SampleSkins) { name ->
            PixelCard(
                Modifier.fillMaxWidth().aspectRatio(0.7f),
                highlighted = name == selected,
                onClick = { selected = name },
            ) {
                PlayerBody(name, 120.dp, Modifier.fillMaxWidth().weight(1f))
                Text(name, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
