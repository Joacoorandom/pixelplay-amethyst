package gg.pixelplay.android

import android.app.Application
import android.os.Build
import java.io.File
import net.kdt.pojavlaunch.PojavApplication
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import gg.pixelplay.android.data.CallDirection
import gg.pixelplay.android.data.CallPhase
import gg.pixelplay.android.data.PixelRepository
import gg.pixelplay.android.social.CallService
import gg.pixelplay.android.social.Notifications
import gg.pixelplay.android.social.SocialClient
import gg.pixelplay.android.social.SocialEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object PixelGraph {
    lateinit var repo: PixelRepository
        private set
    val social: SocialClient get() = repo.social
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Set by the UI so a notification "Aceptar" can go through the mic-permission flow. */
    @Volatile var pendingAccept: (() -> Unit)? = null

    fun init(app: Context) {
        if (::repo.isInitialized) return
        Notifications.ensureChannels(app)
        repo = PixelRepository(app, scope)
        repo.start()

        scope.launch {
            social.events.collect { ev ->
                when (ev) {
                    is SocialEvent.Message -> if (!repo.visible || !social.openChat.equals(ev.from, true)) {
                        Notifications.message(app, ev.from, ev.text)
                    }
                    is SocialEvent.IncomingCall -> Notifications.incomingCall(app, ev.from)
                    else -> Unit
                }
            }
        }
        scope.launch {
            social.call.map { it?.let { c -> Triple(c.id, c.phase, c.direction) } to it?.peer }
                .distinctUntilChanged()
                .collect { (state, peer) ->
                    val phase = state?.second
                    if (phase == null || phase == CallPhase.Active || state.third == CallDirection.Out) {
                        Notifications.cancelIncoming(app)
                    }
                    if (phase == CallPhase.Active || (phase == CallPhase.Ringing && state.third == CallDirection.Out)) {
                        CallService.start(app, peer.orEmpty())
                    } else if (phase == null) {
                        CallService.stop(app)
                    }
                }
        }
    }
}

class PixelApplication : PojavApplication() {
    override fun onCreate() {
        super.onCreate()
        if (!isLauncherProcess()) return
        PixelGraph.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                PixelGraph.repo.visible = true
                PixelGraph.social.appVisible = true
            }

            override fun onStop(owner: LifecycleOwner) {
                PixelGraph.repo.visible = false
                PixelGraph.social.appVisible = false
            }
        })
    }

    private fun isLauncherProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName()
        else runCatching { File("/proc/self/cmdline").readText().trimEnd('\u0000') }.getOrDefault("")
        return name.endsWith(":launcher") || name == packageName
    }
}
