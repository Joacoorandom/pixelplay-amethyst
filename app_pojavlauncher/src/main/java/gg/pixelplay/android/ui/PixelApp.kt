package gg.pixelplay.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gg.pixelplay.android.AppViewModel
import net.kdt.pojavlaunch.BuildConfig
import net.kdt.pojavlaunch.R
import gg.pixelplay.android.Tab
import gg.pixelplay.android.PixelGraph
import gg.pixelplay.android.ui.backgrounds.PixelBackdrop
import gg.pixelplay.android.ui.screens.AccountsScreen
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import gg.pixelplay.android.ui.components.PlayerHead
import gg.pixelplay.android.ui.screens.ChatScreen
import gg.pixelplay.android.ui.screens.FriendsScreen
import gg.pixelplay.android.ui.screens.ModsScreen
import gg.pixelplay.android.ui.screens.PlayScreen
import gg.pixelplay.android.ui.screens.ProfilesScreen
import gg.pixelplay.android.ui.screens.SettingsScreen
import gg.pixelplay.android.ui.screens.SkinsScreen
import gg.pixelplay.android.ui.theme.LocalAccent
import gg.pixelplay.android.ui.theme.PixelColors

private data class NavItem(val tab: Tab, val icon: ImageVector, val label: String)

private val NavItems = listOf(
    NavItem(Tab.Play, Icons.Rounded.PlayArrow, "Jugar"),
    NavItem(Tab.Profiles, Icons.Rounded.SportsEsports, "Perfiles"),
    NavItem(Tab.Mods, Icons.Rounded.Widgets, "Mods"),
    NavItem(Tab.Skins, Icons.Rounded.Face, "Skins"),
    NavItem(Tab.Friends, Icons.Rounded.Group, "Amigos"),
)

@Composable
fun PixelApp(vm: AppViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val openChat by vm.openChat.collectAsStateWithLifecycle()
    val account by vm.repo.account.collectAsStateWithLifecycle()
    val unreadMap by vm.social.unread.collectAsStateWithLifecycle()
    val animations by vm.animations.collectAsStateWithLifecycle()
    val background by vm.background.collectAsStateWithLifecycle()
    val unread = unreadMap.values.sum()
    val ctx = LocalContext.current
    var pendingCall by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingCall?.invoke() else vm.say("Sin permiso de micrófono no se puede llamar")
        pendingCall = null
    }
    val withMic: (() -> Unit) -> Unit = { action ->
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) action()
        else {
            pendingCall = action
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val call: (String) -> Unit = { name -> withMic { vm.call(name) } }
    val acceptCall = { withMic { vm.social.accept() } }
    LaunchedEffect(Unit) { PixelGraph.pendingAccept = { acceptCall() } }

    BackHandler(enabled = openChat != null || tab != Tab.Play) {
        if (openChat != null) vm.openChat(null) else vm.select(Tab.Play)
    }

    Box(Modifier.fillMaxSize()) {
        PixelBackdrop(background, animate = animations)
        Column(Modifier.fillMaxSize()) {
            TopBar(
                username = account?.username,
                onAccount = { vm.select(Tab.Accounts) },
                onSettings = { vm.select(Tab.Settings) },
                settingsActive = tab == Tab.Settings,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = openChat?.let { "chat:$it" } ?: tab.name,
                    transitionSpec = {
                        if (animations) fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                        else fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    },
                    label = "screen",
                ) { key ->
                    when {
                        key.startsWith("chat:") -> ChatScreen(vm, key.removePrefix("chat:"), call)
                        key == Tab.Play.name -> PlayScreen(vm)
                        key == Tab.Profiles.name -> ProfilesScreen(vm)
                        key == Tab.Mods.name -> ModsScreen(vm)
                        key == Tab.Skins.name -> SkinsScreen(vm)
                        key == Tab.Friends.name -> FriendsScreen(vm, call)
                        key == Tab.Accounts.name -> AccountsScreen(vm)
                        else -> SettingsScreen(vm)
                    }
                }
            }
            if (openChat == null) BottomNav(active = tab, unread = unread, onSelect = vm::select)
        }
        CallOverlay(vm, onAccept = acceptCall, modifier = Modifier.statusBarsPadding().padding(top = 58.dp))
        ToastHost(vm, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 86.dp))
    }
}

@Composable
private fun ToastHost(vm: AppViewModel, modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        vm.toast.collect {
            message = it
            delay(2_600)
            if (message == it) message = null
        }
    }
    val accent = LocalAccent.current
    AnimatedVisibility(message != null, modifier = modifier.padding(horizontal = 24.dp), enter = fadeIn(), exit = fadeOut()) {
        Text(
            message.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xF0111114))
                .border(1.dp, accent.value.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun TopBar(username: String?, onAccount: () -> Unit, onSettings: () -> Unit, settingsActive: Boolean) {
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.28f))))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.pp_logo), contentDescription = null, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("pixel play", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("v${BuildConfig.VERSION_NAME} · android", style = MaterialTheme.typography.labelSmall, color = PixelColors.TextFaint)
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(accent.value.copy(alpha = 0.18f))
                .border(1.dp, accent.value.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .clickable(onClick = onAccount)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerHead(username, 22.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                username ?: "Añadir cuenta",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (settingsActive) accent.value else Color.White.copy(alpha = 0.06f))
                .border(1.dp, accent.value.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = "Ajustes", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(accent.value.copy(alpha = 0.25f)))
}

@Composable
private fun BottomNav(active: Tab, unread: Int, onSelect: (Tab) -> Unit) {
    val accent = LocalAccent.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(accent.value.copy(alpha = 0.35f)))
    Row(
        Modifier
            .fillMaxWidth()
            .background(accent.dark.copy(alpha = 0.35f))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItems.forEach { item ->
            val selected = item.tab == active
            Column(
                Modifier
                    .weight(1f)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(item.tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (selected) accent.value else Color.Transparent)
                            .then(
                                if (selected) Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                else Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    if (item.tab == Tab.Friends && unread > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(PixelColors.Danger)
                                .border(1.dp, Color(0xFFF87171), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (unread > 9) "9+" else "$unread", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    item.label.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) Color.White else PixelColors.TextFaint,
                )
            }
        }
    }
}
