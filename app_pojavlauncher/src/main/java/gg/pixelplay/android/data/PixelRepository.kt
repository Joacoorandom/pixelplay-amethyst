package gg.pixelplay.android.data

import android.app.Activity
import android.content.Context
import gg.pixelplay.android.game.GameEngine
import gg.pixelplay.android.game.GameVersions
import gg.pixelplay.android.game.LaunchAccount
import gg.pixelplay.android.game.PackInstaller
import gg.pixelplay.android.game.ProfileStore
import gg.pixelplay.android.game.SettingsStore
import gg.pixelplay.android.social.Notifications
import gg.pixelplay.android.social.SocialClient
import gg.pixelplay.android.social.SocialSession
import gg.pixelplay.android.social.VoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

class PixelRepository(private val context: Context, private val scope: CoroutineScope) {
    val api = PixelApi(context)
    val settings = SettingsStore(context)
    val engine: GameEngine? = GameEngine.load()
    private val accountStore = AccountStore(context)
    private val profileStore = ProfileStore(context)
    private val tokenLock = Mutex()

    private val _accounts = MutableStateFlow(accountStore.list())
    val accounts = _accounts.asStateFlow()
    private val _account = MutableStateFlow<PixelAccount?>(null)
    val account = _account.asStateFlow()

    private val _friends = MutableStateFlow<List<PixelFriend>>(emptyList())
    val friends = _friends.asStateFlow()
    private val _incoming = MutableStateFlow<List<PixelFriend>>(emptyList())
    val incoming = _incoming.asStateFlow()
    private val _outgoing = MutableStateFlow<List<PixelFriend>>(emptyList())
    val outgoing = _outgoing.asStateFlow()
    private val _friendsError = MutableStateFlow<String?>(null)
    val friendsError = _friendsError.asStateFlow()

    private val _profiles = MutableStateFlow(profileStore.list())
    val profiles = _profiles.asStateFlow()
    private val _launch = MutableStateFlow<LaunchState>(LaunchState.Idle)
    val launch = _launch.asStateFlow()
    private val _featured = MutableStateFlow<FeaturedPack?>(null)
    val featured = _featured.asStateFlow()

    val social = SocialClient(scope, VoiceEngine(context)) { activeSession() }

    @Volatile var visible = true
    private var loops: Job? = null

    init {
        refreshAccountView()
    }

    fun start() {
        if (loops != null) return
        social.start()
        loops = scope.launch {
            launch { runCatching { api.loadRanks(); refreshAccountView() } }
            launch { ensureFeaturedProfile() }
            launch {
                while (isActive) {
                    if (visible) refreshFriends()
                    delay(if (visible) 15_000 else 60_000)
                }
            }
            launch {
                while (isActive) {
                    val status = if (engine?.isRunning(context) == true) "ingame" else if (visible) "online" else null
                    if (status != null) runCatching { pixelToken()?.let { api.presence(it, status) } }
                    delay(20_000)
                }
            }
            launch { runCatching { refreshMe() } }
        }
    }

    // ---- accounts ----

    private fun refreshAccountView() {
        val list = accountStore.list()
        _accounts.value = list
        _account.value = list.firstOrNull { it.active }?.let(::toView) ?: list.firstOrNull()?.let(::toView)
    }

    fun accountViews(): List<PixelAccount> = _accounts.value.map(::toView)

    private fun toView(a: StoredAccount): PixelAccount {
        val meta = api.rankMeta(a.rank)
        return PixelAccount(a.id, a.username, a.kind, meta.key, meta.label, meta.color, a.active)
    }

    private fun activeStored(): StoredAccount? = accountStore.active()

    private suspend fun activeSession(): SocialSession? {
        val tok = pixelToken() ?: return null
        return SocialSession(tok, activeStored()?.username ?: return null)
    }

    /** Valid Pixel JWT for the active account, refreshing it (and the Minecraft token) when needed. */
    suspend fun pixelToken(): String? = tokenLock.withLock {
        var acc = activeStored() ?: return null
        if (acc.pixelToken.isNotBlank() && Jwt.fresh(acc.pixelToken) &&
            Jwt.claims(acc.pixelToken)?.str("username")?.equals(acc.username, true) != false
        ) return acc.pixelToken
        if (acc.kind != AccountKind.Microsoft) return acc.pixelToken.ifBlank { null }
        runCatching {
            acc = refreshMicrosoft(acc)
            val tok = api.pixelTokenForMicrosoft(acc.mcAccessToken, acc.id, acc.username)
            val rank = Jwt.claims(tok)?.str("rank")?.ifBlank { null } ?: acc.rank
            acc = acc.copy(pixelToken = tok, rank = rank)
            accountStore.upsert(acc)
            refreshAccountView()
            tok
        }.getOrNull()
    }

    private suspend fun refreshMicrosoft(acc: StoredAccount): StoredAccount {
        if (acc.mcAccessToken.isNotBlank() && acc.mcExpiresAt - 300_000 > System.currentTimeMillis()) return acc
        val s = MicrosoftAuth.refresh(acc.msRefreshToken)
        val next = acc.copy(mcAccessToken = s.mcToken, msRefreshToken = s.refreshToken, mcExpiresAt = s.expiresAt)
        accountStore.upsert(next)
        return next
    }

    suspend fun lookupPixel(username: String) = api.lookup(username)

    suspend fun loginPixel(username: String, password: String, create: Boolean) {
        val l = if (create) api.reserve(username, password) else api.login(username, password)
        accountStore.upsert(StoredAccount(l.uuid, l.username, AccountKind.Pixel, pixelToken = l.token, rank = l.rank), makeActive = true)
        onAccountChanged()
    }

    suspend fun beginMicrosoft(): MicrosoftAuth.Device = MicrosoftAuth.begin()

    suspend fun finishMicrosoft(dev: MicrosoftAuth.Device) {
        val s = MicrosoftAuth.await(dev)
        var acc = StoredAccount(
            s.uuid, s.name, AccountKind.Microsoft,
            mcAccessToken = s.mcToken, msRefreshToken = s.refreshToken, mcExpiresAt = s.expiresAt,
        )
        runCatching {
            val tok = api.pixelTokenForMicrosoft(s.mcToken, s.uuid, s.name)
            acc = acc.copy(pixelToken = tok, rank = Jwt.claims(tok)?.str("rank")?.ifBlank { null } ?: "usuario")
        }
        accountStore.upsert(acc, makeActive = true)
        onAccountChanged()
    }

    fun setActiveAccount(id: String) {
        accountStore.setActive(id)
        onAccountChanged()
    }

    fun removeAccount(id: String) {
        accountStore.remove(id)
        onAccountChanged()
    }

    private fun onAccountChanged() {
        refreshAccountView()
        _friends.value = emptyList()
        _incoming.value = emptyList()
        _outgoing.value = emptyList()
        social.reconnect()
        scope.launch { refreshMe(); refreshFriends() }
    }

    private suspend fun refreshMe() {
        val tok = pixelToken() ?: return
        val me = runCatching { api.me(tok) }.getOrNull() ?: return
        val rank = me.str("rank").ifBlank { "usuario" }
        activeStored()?.let { acc ->
            if (!acc.rank.equals(rank, true)) {
                accountStore.upsert(acc.copy(rank = rank))
                refreshAccountView()
            }
        }
    }

    // ---- friends ----

    suspend fun refreshFriends() {
        val tok = pixelToken() ?: return
        runCatching { api.friends(tok) }
            .onSuccess {
                _friends.value = it.friends
                _incoming.value = it.incoming
                _outgoing.value = it.outgoing
                _friendsError.value = null
            }
            .onFailure { _friendsError.value = it.message }
    }

    private suspend fun friendOp(block: suspend (String) -> Unit): Result<Unit> = runCatching {
        val tok = pixelToken() ?: throw ApiException("Necesitás una cuenta Pixel activa")
        block(tok)
        refreshFriends()
    }

    suspend fun addFriend(username: String) = friendOp { api.addFriend(it, username) }
    suspend fun acceptFriend(username: String) = friendOp { api.acceptFriend(it, username) }
    suspend fun declineFriend(username: String) = friendOp { api.declineFriend(it, username) }
    suspend fun cancelFriend(username: String) = friendOp { api.cancelFriend(it, username) }
    suspend fun removeFriend(username: String) = friendOp { api.removeFriend(it, username) }

    // ---- profiles & packs ----

    fun gameDir(p: GameProfile): File {
        val root = engine?.instancesRoot(context) ?: File(context.getExternalFilesDir(null), "instances")
        return File(root, p.id).apply { mkdirs() }
    }

    private suspend fun ensureFeaturedProfile() {
        val f = runCatching { api.featured() }.getOrNull() ?: return
        _featured.value = f
        val existing = _profiles.value.firstOrNull { it.packId == f.packId }
        if (existing == null) {
            val p = GameProfile(
                id = f.packId, name = f.name, loader = f.loader, loaderVersion = f.loaderVersion,
                gameVersion = f.gameVersion, mods = 0, featured = true, packId = f.packId, serverAddress = f.address,
            )
            _profiles.value = profileStore.upsert(p)
        } else if (existing.loaderVersion != f.loaderVersion) {
            _profiles.value = profileStore.upsert(existing.copy(loaderVersion = f.loaderVersion))
        }
        if (settings.selectedProfile.isBlank()) settings.selectedProfile = f.packId
    }

    suspend fun createProfile(name: String, loader: String, gameVersion: String): GameProfile {
        val lv = if (loader == "vanilla") null else GameVersions.loaderVersion(loader, gameVersion)
            ?: throw ApiException("No hay $loader para $gameVersion")
        val p = GameProfile(
            id = "p-" + UUID.randomUUID().toString().take(8),
            name = name.ifBlank { "$loader $gameVersion" }, loader = loader, loaderVersion = lv,
            gameVersion = gameVersion, mods = 0,
        )
        _profiles.value = profileStore.upsert(p)
        return p
    }

    fun deleteProfile(id: String) {
        _profiles.value.firstOrNull { it.id == id }?.let { gameDir(it).deleteRecursively() }
        _profiles.value = profileStore.remove(id)
    }

    suspend fun installMod(profile: GameProfile, mod: ModResult): Result<Unit> = runCatching {
        val f = Modrinth.latestFile(mod.id, profile.gameVersion, profile.loader)
        val modsDir = File(gameDir(profile), "mods")
        profile.installedMods.firstOrNull { it.projectId == mod.id }?.let { File(modsDir, it.filename).delete() }
        Http.download(f.url, File(modsDir, f.filename))
        val next = profile.copy(
            installedMods = profile.installedMods.filterNot { it.projectId == mod.id } + InstalledMod(mod.id, f.versionId, mod.title, f.filename),
        )
        _profiles.value = profileStore.upsert(next.copy(mods = next.installedMods.size + packModCount(next)))
    }

    fun uninstallMod(profile: GameProfile, projectId: String) {
        val m = profile.installedMods.firstOrNull { it.projectId == projectId } ?: return
        File(gameDir(profile), "mods/${m.filename}").delete()
        val next = profile.copy(installedMods = profile.installedMods - m)
        _profiles.value = profileStore.upsert(next.copy(mods = next.installedMods.size + packModCount(next)))
    }

    private fun packModCount(p: GameProfile): Int = if (p.packId == null) 0 else
        runCatching { org.json.JSONObject(File(gameDir(p), ".pixelpack.json").readText()).optJSONArray("files")?.length() ?: 0 }.getOrDefault(0)

    suspend fun searchMods(query: String, profile: GameProfile?): List<ModResult> {
        val p = profile?.takeIf { it.loader != "vanilla" }
        return Modrinth.search(query, p?.gameVersion ?: "1.21.1", p?.loader ?: "neoforge")
    }

    private fun progress(step: String, value: Float?) {
        _launch.value = LaunchState.Working(step, value)
        Notifications.launchProgress(context, "Preparando Minecraft", step, value ?: -1f)
    }

    /** Syncs the pack (if any), installs game + loader through the engine and starts Minecraft. */
    fun launch(activity: Activity, profileId: String) {
        if (_launch.value is LaunchState.Working) return
        val profile = _profiles.value.firstOrNull { it.id == profileId } ?: return
        val eng = engine
        scope.launch {
            try {
                val acc = activeStored() ?: throw ApiException("Agregá una cuenta para jugar")
                var p = profile
                val dir = gameDir(p)
                if (p.packId != null) p = syncPack(p, dir)
                if (eng == null) throw ApiException("Este build no incluye el motor de Minecraft (Amethyst)")
                progress("Iniciando sesión", null)
                val launchAcc = if (acc.kind == AccountKind.Microsoft) {
                    val fresh = refreshMicrosoft(acc)
                    LaunchAccount(fresh.username, fresh.id, fresh.mcAccessToken, microsoft = true)
                } else {
                    pixelToken()
                    LaunchAccount(acc.username, acc.id, "0", microsoft = false)
                }
                eng.prepare(activity, p, dir, launchAcc, ::progress)
                Notifications.cancelLaunch(context)
                _launch.value = LaunchState.Running
                val server = if (settings.autoJoin) p.serverAddress else null
                activity.runOnUiThread { eng.launch(activity, p, dir, launchAcc, server) }
                runCatching { pixelToken()?.let { api.presence(it, "ingame") } }
                delay(3_000)
                _launch.value = LaunchState.Idle
            } catch (e: Exception) {
                Notifications.cancelLaunch(context)
                _launch.value = LaunchState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun clearLaunchError() {
        if (_launch.value is LaunchState.Failed) _launch.value = LaunchState.Idle
    }

    private suspend fun syncPack(p: GameProfile, dir: File): GameProfile {
        val f = _featured.value ?: runCatching { api.featured() }.getOrNull()
        val cache = File(context.cacheDir, "ppacks/${p.packId}.ppack")
        progress("Buscando actualizaciones del pack", null)
        var url = f?.takeIf { it.packId == p.packId }?.downloadUrl
        var version: String? = null
        if (cache.exists()) {
            runCatching { PackInstaller.readManifest(cache).first }.getOrNull()?.updaterUrl?.let { upd ->
                PackInstaller.latest(upd)?.let { (v, u) -> version = v; if (u.isNotBlank()) url = u }
            }
        }
        val needDownload = !cache.exists() || (version != null && version != p.packVersion)
        if (needDownload) {
            url ?: throw ApiException("No se encontró el pack ${p.packId}")
            progress("Descargando pack", null)
            Http.download(url!!, cache) { done, total -> if (total > 0) progress("Descargando pack", done.toFloat() / total) }
        }
        val manifest = PackInstaller.install(cache, dir, ::progress)
        val next = p.copy(
            name = if (p.featured) manifest.name.ifBlank { p.name } else p.name,
            packVersion = manifest.version,
            mods = manifest.mods.size + p.installedMods.size,
            gameVersion = manifest.gameVersion.ifBlank { p.gameVersion },
            loader = manifest.loader.ifBlank { p.loader },
            serverAddress = manifest.autojoin ?: p.serverAddress,
        )
        _profiles.value = profileStore.upsert(next)
        return next
    }
}
