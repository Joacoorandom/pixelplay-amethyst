package gg.pixelplay.android.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gg.pixelplay.android.AppViewModel
import gg.pixelplay.android.data.AccountKind
import gg.pixelplay.android.ui.components.PixelButton
import gg.pixelplay.android.ui.components.PixelCard
import gg.pixelplay.android.ui.components.PixelTextField
import gg.pixelplay.android.ui.components.PixelVariant
import gg.pixelplay.android.ui.components.PlayerHead
import gg.pixelplay.android.ui.components.RankTag
import gg.pixelplay.android.ui.components.SectionTitle
import gg.pixelplay.android.ui.components.SegmentChip
import gg.pixelplay.android.ui.theme.LocalAccent
import gg.pixelplay.android.ui.theme.PixelColors
import kotlinx.coroutines.delay

@Composable
fun AccountsScreen(vm: AppViewModel) {
    val accounts by vm.repo.accounts.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val prompt by vm.msPrompt.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val accent = LocalAccent.current
    var mode by rememberSaveable { mutableStateOf(0) }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var exists by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(user) {
        exists = null
        if (user.trim().length < 3) return@LaunchedEffect
        delay(450)
        exists = vm.pixelExists(user.trim())
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Cuentas")

        accounts.forEach { a ->
            val view = vm.repo.accountViews().firstOrNull { it.id == a.id } ?: return@forEach
            PixelCard(Modifier.fillMaxWidth(), highlighted = a.active, onClick = { vm.setActiveAccount(a.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerHead(a.username, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.username, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                            Spacer(Modifier.width(6.dp))
                            RankTag(view.rankLabel, view.rankColor)
                        }
                        Text(
                            if (a.kind == AccountKind.Microsoft) "Microsoft · premium" else "Cuenta Pixel",
                            style = MaterialTheme.typography.bodySmall,
                            color = PixelColors.TextFaint,
                        )
                    }
                    if (a.active) Icon(Icons.Rounded.CheckCircle, "Activa", tint = accent.light)
                    Spacer(Modifier.width(6.dp))
                    PixelButton(null, { vm.removeAccount(a.id) }, icon = Icons.Rounded.Delete, variant = PixelVariant.Ghost, depth = 0.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp))
                }
            }
        }

        PixelCard(Modifier.fillMaxWidth()) {
            Text("agregar cuenta", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentChip("Pixel", mode == 0) { mode = 0 }
                SegmentChip("Microsoft", mode == 1) { mode = 1 }
            }
            Spacer(Modifier.height(12.dp))
            if (mode == 0) {
                PixelTextField(user, { user = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(16) }, "Usuario", Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PixelTextField(
                    pass, { pass = it }, "Clave", Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                    onSubmit = { vm.loginPixel(user, pass, create = exists == false) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when (exists) {
                        true -> "Esa cuenta existe: ingresá tu clave"
                        false -> "Nombre libre: se va a crear tu cuenta Pixel"
                        null -> "Usá tu cuenta de login.pixelplay.gg"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (exists == false) accent.light else PixelColors.TextFaint,
                )
                Spacer(Modifier.height(10.dp))
                PixelButton(
                    if (exists == false) "Crear cuenta" else "Iniciar sesión",
                    { vm.loginPixel(user, pass, create = exists == false) },
                    Modifier.fillMaxWidth(),
                    enabled = !busy && user.length >= 3 && pass.isNotBlank(),
                )
            } else {
                val p = prompt
                if (p == null) {
                    Text(
                        "Te damos un código para ingresar en microsoft.com/link. Necesitás Minecraft Java comprado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PixelColors.TextMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    PixelButton("Iniciar con Microsoft", vm::startMicrosoft, Modifier.fillMaxWidth(), enabled = !busy)
                } else {
                    Text("Tu código", style = MaterialTheme.typography.bodySmall, color = PixelColors.TextFaint)
                    Text(
                        p.userCode,
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton("Copiar", { copy(ctx, p.userCode); vm.say("Código copiado") }, Modifier.weight(1f), icon = Icons.Rounded.ContentCopy, variant = PixelVariant.Secondary)
                        PixelButton("Abrir", {
                            copy(ctx, p.userCode)
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.completeUri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }, Modifier.weight(1f), icon = Icons.Rounded.OpenInBrowser)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 10.dp).width(18.dp).height(18.dp), color = accent.light, strokeWidth = 2.dp)
                        Text("Esperando que confirmes en Microsoft…", style = MaterialTheme.typography.bodySmall, color = PixelColors.TextMuted, modifier = Modifier.weight(1f))
                        PixelButton("Cancelar", vm::cancelMicrosoft, variant = PixelVariant.Ghost, depth = 0.dp)
                    }
                }
            }
        }
    }
}

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", text))
}