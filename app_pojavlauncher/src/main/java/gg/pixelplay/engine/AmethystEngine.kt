package gg.pixelplay.engine

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.kdt.mcgui.ProgressLayout
import gg.pixelplay.android.data.ApiException
import gg.pixelplay.android.data.GameProfile
import gg.pixelplay.android.game.GameEngine
import gg.pixelplay.android.game.LaunchAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.kdt.pojavlaunch.JMinecraftVersionList
import net.kdt.pojavlaunch.MainActivity
import net.kdt.pojavlaunch.PojavProfile
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.extra.ExtraConstants
import net.kdt.pojavlaunch.extra.ExtraCore
import net.kdt.pojavlaunch.lifecycle.ContextExecutor
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper
import net.kdt.pojavlaunch.progresskeeper.ProgressListener
import net.kdt.pojavlaunch.tasks.AsyncAssetManager
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader
import net.kdt.pojavlaunch.tasks.AsyncVersionList
import net.kdt.pojavlaunch.tasks.MinecraftDownloader
import net.kdt.pojavlaunch.value.MinecraftAccount
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Runs Minecraft through the bundled Amethyst (PojavLauncher) runtime: JRE, LWJGL, renderers and loaders. */
class AmethystEngine : GameEngine {
    private companion object { const val TAG = "PixelEngine" }

    override val name = "Amethyst"

    @Volatile private var initialized = false

    override fun init(activity: Activity) {
        if (initialized) return
        initialized = true
        val app = activity.application
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) = ContextExecutor.setActivity(a)
            override fun onActivityPaused(a: Activity) = ContextExecutor.clearActivity()
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
        ContextExecutor.setActivity(activity)
        if (!Tools.checkStorageRoot(activity)) return
        if (Tools.DIR_GAME_HOME == null) LauncherPreferences.loadPreferences(activity)
        AsyncAssetManager.unpackComponents(activity)
        AsyncAssetManager.unpackSingleFiles(activity)
        AsyncVersionList().getVersionList({ ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, it) }, false)
    }

    override fun instancesRoot(context: Context): File =
        File(Tools.DIR_GAME_HOME ?: context.getExternalFilesDir(null)!!.absolutePath, "instances")

    override suspend fun prepare(
        activity: Activity,
        profile: GameProfile,
        gameDir: File,
        account: LaunchAccount,
        progress: (String, Float?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (!Tools.checkStorageRoot(activity)) throw ApiException("El almacenamiento no está disponible")
        init(activity)
        progress("Preparando componentes", null)
        awaitIdle(ProgressLayout.EXTRACT_COMPONENTS)
        awaitIdle(ProgressLayout.EXTRACT_SINGLE_FILES)
        progress("Obteniendo versiones de Minecraft", null)
        awaitVersionList()

        val versionId = versionId(profile)
        gameDir.mkdirs()
        select(activity, profile, gameDir, account, profile.gameVersion, server = null)
        download(activity, profile.gameVersion, progress)

        if (versionId != profile.gameVersion) {
            if (!versionJson(versionId).isFile) installLoader(activity, profile, versionId, progress)
            select(activity, profile, gameDir, account, versionId, server = null)
            download(activity, versionId, progress)
        }
    }

    override fun launch(activity: Activity, profile: GameProfile, gameDir: File, account: LaunchAccount, server: String?) {
        val versionId = versionId(profile)
        select(activity, profile, gameDir, account, versionId, server)
        activity.startActivity(
            Intent(activity, MainActivity::class.java)
                .putExtra(MainActivity.INTENT_MINECRAFT_VERSION, versionId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    override fun isRunning(context: Context): Boolean = processAlive(context, ":game")

    private fun versionId(p: GameProfile): String {
        val lv = p.loaderVersion
        return when (p.loader.lowercase()) {
            "vanilla", "" -> p.gameVersion
            "fabric" -> "fabric-loader-${need(lv, p)}-${p.gameVersion}"
            "quilt" -> "quilt-loader-${need(lv, p)}-${p.gameVersion}"
            "forge" -> "${p.gameVersion}-forge-${need(lv, p)}"
            "neoforge" -> "neoforge-${need(lv, p)}"
            else -> throw ApiException("Loader no soportado: ${p.loader}")
        }
    }

    private fun need(lv: String?, p: GameProfile) =
        lv?.takeIf { it.isNotBlank() } ?: throw ApiException("Falta la versión de ${p.loader} para ${p.gameVersion}")

    private fun versionJson(id: String) = File(Tools.DIR_HOME_VERSION, "$id/$id.json")

    /** Writes the Amethyst launcher profile + account the game process reads on start. */
    private fun select(ctx: Context, p: GameProfile, gameDir: File, account: LaunchAccount, versionId: String, server: String?) {
        LauncherProfiles.load()
        val key = UUID.nameUUIDFromBytes("pixelplay:${p.id}".toByteArray()).toString()
        val profiles = LauncherProfiles.mainProfileJson.profiles
        val mp = profiles[key] ?: MinecraftProfile()
        mp.name = p.name
        mp.lastVersionId = versionId
        mp.gameDir = gameDir.relativeToOrNull(File(Tools.DIR_GAME_HOME))?.path ?: gameDir.absolutePath
        mp.pixelServer = server
        profiles[key] = mp
        LauncherProfiles.write()
        LauncherPreferences.DEFAULT_PREF.edit().putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, key).commit()

        val acc = MinecraftAccount().apply {
            username = account.username
            profileId = dashed(account.uuid)
            accessToken = account.accessToken.ifBlank { "0" }
            isMicrosoft = account.microsoft
            selectedVersion = versionId
        }
        acc.save()
        PojavProfile.getPrefs(ctx).edit().putString("file", acc.username).commit()
    }

    private fun dashed(uuid: String): String {
        val h = uuid.replace("-", "")
        if (h.length != 32) return uuid
        return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}"
    }

    private suspend fun awaitVersionList() {
        if (ExtraCore.getValue(ExtraConstants.RELEASE_TABLE) != null) return
        val list = withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine<JMinecraftVersionList?> { cont ->
                AsyncVersionList().getVersionList({ if (cont.isActive) cont.resume(it) }, false)
            }
        }
        if (list != null) ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, list)
    }

    private suspend fun download(activity: Activity, id: String, progress: (String, Float?) -> Unit) {
        var attempt = 0
        while (true) {
            try {
                return downloadOnce(activity, id, progress)
            } catch (e: Exception) {
                Log.w(TAG, "Download of $id failed (attempt ${attempt + 1})", e)
                if (++attempt >= 3) throw e
                progress("Reintentando descarga ($attempt/2)…", null)
                delay(3_000L * attempt)
            }
        }
    }

    private suspend fun downloadOnce(activity: Activity, id: String, progress: (String, Float?) -> Unit) {
        val normalized = AsyncMinecraftDownloader.normalizeVersionId(id)
        val listed = AsyncMinecraftDownloader.getListedVersion(normalized)
        progress("Descargando Minecraft $normalized", null)
        watching(activity, ProgressLayout.DOWNLOAD_MINECRAFT, "Descargando Minecraft", progress) {
            suspendCancellableCoroutine { cont ->
                MinecraftDownloader().forceDownload().start(activity, listed, normalized, object : AsyncMinecraftDownloader.DoneListener {
                    override fun onDownloadDone() { if (cont.isActive) cont.resume(Unit) }
                    override fun onDownloadFailed(throwable: Throwable) { if (cont.isActive) cont.resumeWithException(throwable) }
                })
            }
        }
    }

    private suspend fun installLoader(activity: Activity, p: GameProfile, versionId: String, progress: (String, Float?) -> Unit) {
        val type = when (p.loader.lowercase()) {
            "fabric" -> ModLoader.MOD_LOADER_FABRIC
            "quilt" -> ModLoader.MOD_LOADER_QUILT
            "forge" -> ModLoader.MOD_LOADER_FORGE
            else -> ModLoader.MOD_LOADER_NEOFORGE
        }
        val loader = ModLoader(type, p.loaderVersion, p.gameVersion)
        val label = "Instalando ${p.loader} ${p.loaderVersion}"
        progress(label, null)
        val installer: File? = watching(activity, ProgressLayout.INSTALL_MODPACK, label, progress) {
            suspendCancellableCoroutine { cont ->
                val task = loader.getDownloadTask(object : ModloaderDownloadListener {
                    override fun onDownloadFinished(downloadedFile: File?) { if (cont.isActive) cont.resume(downloadedFile) }
                    override fun onDataNotAvailable() {
                        if (cont.isActive) cont.resumeWithException(ApiException("${p.loader} ${p.loaderVersion} no está disponible para ${p.gameVersion}"))
                    }
                    override fun onDownloadError(e: Exception) { if (cont.isActive) cont.resumeWithException(e) }
                })
                Thread(task, "PixelLoaderDownload").start()
            }
        }
        if (loader.requiresGuiInstallation()) {
            installer ?: throw ApiException("No se pudo descargar el instalador de ${p.loader}")
            progress("Ejecutando instalador de ${p.loader} (no cierres la ventana)", null)
            val intent = loader.getInstallationIntent(activity, installer)
            withContext(Dispatchers.Main) { activity.startActivity(intent) }
            val started = withTimeoutOrNull(60_000) { while (!processAlive(activity, ":gui_installer")) delay(500) } != null
            if (started) withTimeoutOrNull(30 * 60_000L) { while (processAlive(activity, ":gui_installer")) delay(1_000) }
        }
        if (!versionJson(versionId).isFile) throw ApiException("La instalación de ${p.loader} no terminó. Probá de nuevo.")
    }

    /** Mirrors an Amethyst ProgressKeeper record into our progress callback while [block] runs. */
    private suspend fun <T> watching(
        ctx: Context,
        record: String,
        fallback: String,
        progress: (String, Float?) -> Unit,
        block: suspend () -> T,
    ): T {
        val listener = object : ProgressListener {
            override fun onProgressStarted() = Unit
            override fun onProgressEnded() = Unit
            override fun onProgressUpdated(value: Int, resid: Int, vararg va: Any?) {
                val text = runCatching {
                    if (resid != -1) ctx.getString(resid, *va) else va.firstOrNull()?.toString()
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
                progress(text, value.takeIf { it in 0..100 }?.div(100f))
            }
        }
        ProgressKeeper.addListener(record, listener)
        try {
            return block()
        } finally {
            ProgressKeeper.removeListener(record, listener)
        }
    }

    private suspend fun awaitIdle(record: String) {
        withTimeoutOrNull(5 * 60_000L) {
            suspendCancellableCoroutine<Unit> { cont ->
                val listener = object : ProgressListener {
                    override fun onProgressStarted() = Unit
                    override fun onProgressUpdated(progress: Int, resid: Int, vararg va: Any?) = Unit
                    override fun onProgressEnded() {
                        if (cont.isActive) cont.resume(Unit)
                        Tools.runOnUiThread { ProgressKeeper.removeListener(record, this) }
                    }
                }
                ProgressKeeper.addListener(record, listener)
                cont.invokeOnCancellation { ProgressKeeper.removeListener(record, listener) }
            }
        }
    }

    private fun processAlive(ctx: Context, suffix: String): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val target = ctx.packageName + suffix
        return am.runningAppProcesses?.any { it.processName == target } == true
    }
}
