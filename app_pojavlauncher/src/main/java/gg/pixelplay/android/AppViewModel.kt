package gg.pixelplay.android

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gg.pixelplay.android.data.DeviceCodePrompt
import gg.pixelplay.android.data.GameProfile
import gg.pixelplay.android.data.ModResult
import gg.pixelplay.android.data.PixelRepository
import gg.pixelplay.android.ui.backgrounds.BackgroundEffect
import gg.pixelplay.android.ui.theme.AccentColor
import gg.pixelplay.android.ui.theme.AccentColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Tab { Play, Profiles, Mods, Friends, Skins, Settings, Accounts }

class AppViewModel : ViewModel() {
    val repo: PixelRepository = PixelGraph.repo
    val social get() = repo.social

    private val _accent = MutableStateFlow(AccentColors.firstOrNull { it.id == repo.settings.accent } ?: AccentColors.first())
    val accent = _accent.asStateFlow()
    private val _background = MutableStateFlow(BackgroundEffect.fromId(repo.settings.background))
    val background = _background.asStateFlow()
    private val _animations = MutableStateFlow(repo.settings.animations)
    val animations = _animations.asStateFlow()
    private val _autoJoin = MutableStateFlow(repo.settings.autoJoin)
    val autoJoin = _autoJoin.asStateFlow()

    private val _tab = MutableStateFlow(if (repo.account.value == null) Tab.Accounts else Tab.Play)
    val tab = _tab.asStateFlow()
    private val _openChat = MutableStateFlow<String?>(null)
    val openChat = _openChat.asStateFlow()
    private val _selectedProfile = MutableStateFlow(repo.settings.selectedProfile)
    val selectedProfile = _selectedProfile.asStateFlow()

    private val _mods = MutableStateFlow<List<ModResult>>(emptyList())
    val mods = _mods.asStateFlow()
    private val _modsLoading = MutableStateFlow(false)
    val modsLoading = _modsLoading.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toast = _toast.asSharedFlow()

    private val _msPrompt = MutableStateFlow<DeviceCodePrompt?>(null)
    val msPrompt = _msPrompt.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private var msJob: Job? = null
    private var searchJob: Job? = null

    fun say(text: String) { _toast.tryEmit(text) }

    fun select(tab: Tab) {
        openChat(null)
        _tab.value = tab
    }

    fun setAccent(accent: AccentColor) {
        _accent.value = accent
        repo.settings.accent = accent.id
    }

    fun setBackground(bg: BackgroundEffect) {
        _background.value = bg
        repo.settings.background = bg.id
    }

    fun setAnimations(enabled: Boolean) {
        _animations.value = enabled
        repo.settings.animations = enabled
    }

    fun setAutoJoin(enabled: Boolean) {
        _autoJoin.value = enabled
        repo.settings.autoJoin = enabled
    }

    fun selectProfile(id: String) {
        _selectedProfile.value = id
        repo.settings.selectedProfile = id
    }

    fun currentProfile(): GameProfile? =
        repo.profiles.value.firstOrNull { it.id == _selectedProfile.value } ?: repo.profiles.value.firstOrNull()

    fun openChat(username: String?) {
        _openChat.value = username
        social.openChat = username
        if (username != null) {
            social.markRead(username)
            viewModelScope.launch { runCatching { social.loadHistory(username) }.onFailure { say(it.message ?: "No se pudo cargar el chat") } }
        }
    }

    fun send(to: String, text: String) = social.send(to, text)
    fun retry(to: String, text: String, cid: String) = social.send(to, text, cid)
    fun typing(to: String) = social.typing(to)

    fun call(username: String) {
        if (social.call.value != null) return say("Ya estás en una llamada")
        social.invite(username)
    }

    private fun friendAction(block: suspend () -> Result<Unit>, ok: String? = null) = viewModelScope.launch {
        block().onSuccess { ok?.let(::say) }.onFailure { say(it.message ?: "Error") }
    }

    fun addFriend(username: String) {
        if (username.isBlank()) return
        friendAction({ repo.addFriend(username.trim()) }, "Solicitud enviada a ${username.trim()}")
    }
    fun accept(username: String) = friendAction({ repo.acceptFriend(username) }, "Ahora sos amigo de $username")
    fun decline(username: String) = friendAction({ repo.declineFriend(username) })
    fun cancelRequest(username: String) = friendAction({ repo.cancelFriend(username) })
    fun removeFriend(username: String) = friendAction({ repo.removeFriend(username) }, "$username eliminado")
    fun refreshFriends() = viewModelScope.launch { repo.refreshFriends() }

    // ---- accounts ----

    fun loginPixel(username: String, password: String, create: Boolean) = viewModelScope.launch {
        if (username.isBlank() || password.isBlank()) return@launch say("Completá usuario y clave")
        _busy.value = true
        runCatching { repo.loginPixel(username, password, create) }
            .onSuccess { say("Sesión iniciada como ${repo.account.value?.username}"); _tab.value = Tab.Play }
            .onFailure { say(it.message ?: "No se pudo iniciar sesión") }
        _busy.value = false
    }

    suspend fun pixelExists(username: String): Boolean? = runCatching { repo.lookupPixel(username) }.getOrNull()

    fun startMicrosoft() {
        msJob?.cancel()
        msJob = viewModelScope.launch {
            _busy.value = true
            runCatching {
                val dev = repo.beginMicrosoft()
                _msPrompt.value = dev.prompt
                repo.finishMicrosoft(dev)
            }.onSuccess { say("Cuenta Microsoft agregada"); _tab.value = Tab.Play }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) say(it.message ?: "Error con Microsoft") }
            _msPrompt.value = null
            _busy.value = false
        }
    }

    fun cancelMicrosoft() {
        msJob?.cancel()
        _msPrompt.value = null
        _busy.value = false
    }

    fun setActiveAccount(id: String) = repo.setActiveAccount(id)
    fun removeAccount(id: String) = repo.removeAccount(id)

    // ---- profiles & mods ----

    fun createProfile(name: String, loader: String, version: String, onDone: () -> Unit) = viewModelScope.launch {
        _busy.value = true
        runCatching { repo.createProfile(name, loader, version) }
            .onSuccess { selectProfile(it.id); onDone(); say("Perfil creado") }
            .onFailure { say(it.message ?: "No se pudo crear") }
        _busy.value = false
    }

    fun deleteProfile(id: String) {
        repo.deleteProfile(id)
        if (_selectedProfile.value == id) repo.profiles.value.firstOrNull()?.let { selectProfile(it.id) }
    }

    fun searchMods(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            _modsLoading.value = true
            runCatching { repo.searchMods(query, currentProfile()) }
                .onSuccess { _mods.value = it }
                .onFailure { say(it.message ?: "Modrinth no respondió") }
            _modsLoading.value = false
        }
    }

    fun installMod(mod: ModResult) = viewModelScope.launch {
        val p = currentProfile() ?: return@launch say("Elegí un perfil")
        if (p.loader == "vanilla") return@launch say("El perfil ${p.name} es vanilla: creá uno con Fabric o NeoForge")
        say("Instalando ${mod.title}…")
        repo.installMod(p, mod).onSuccess { say("${mod.title} instalado en ${p.name}") }.onFailure { say(it.message ?: "Error") }
    }

    fun uninstallMod(projectId: String) {
        val p = currentProfile() ?: return
        repo.uninstallMod(p, projectId)
    }

    fun launch(activity: Activity) {
        val p = currentProfile() ?: return say("Creá un perfil primero")
        repo.launch(activity, p.id)
    }

    init {
        searchMods("")
    }
}
