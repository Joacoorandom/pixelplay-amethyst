package gg.pixelplay.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gg.pixelplay.android.social.Notifications
import gg.pixelplay.android.ui.PixelApp
import gg.pixelplay.android.ui.theme.PixelTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        setContent {
            val accent by vm.accent.collectAsStateWithLifecycle()
            PixelTheme(accent) { PixelApp(vm) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
        vm.repo.engine?.init(this)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(Notifications.EXTRA_OPEN_CHAT)?.let {
            vm.select(Tab.Friends)
            vm.openChat(it)
        }
        when (intent.getStringExtra(Notifications.EXTRA_CALL_ACTION)) {
            "accept" -> {
                Notifications.cancelIncoming(this)
                window.decorView.post { PixelGraph.pendingAccept?.invoke() ?: vm.social.accept() }
            }
            "decline" -> {
                Notifications.cancelIncoming(this)
                vm.social.hangup()
            }
        }
        intent.removeExtra(Notifications.EXTRA_OPEN_CHAT)
        intent.removeExtra(Notifications.EXTRA_CALL_ACTION)
    }
}
