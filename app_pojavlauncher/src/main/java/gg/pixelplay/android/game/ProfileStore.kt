package gg.pixelplay.android.game

import android.content.Context
import gg.pixelplay.android.data.GameProfile
import gg.pixelplay.android.data.Http
import gg.pixelplay.android.data.InstalledMod
import gg.pixelplay.android.data.mapObjects
import gg.pixelplay.android.data.str
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProfileStore(context: Context) {
    private val file = File(context.filesDir, "profiles.json")

    fun list(): List<GameProfile> = runCatching {
        JSONArray(file.readText()).mapObjects(::parse)
    }.getOrDefault(emptyList())

    fun save(list: List<GameProfile>) {
        file.writeText(JSONArray(list.map(::toJson)).toString())
    }

    fun upsert(p: GameProfile): List<GameProfile> {
        val all = list()
        val next = if (all.any { it.id == p.id }) all.map { if (it.id == p.id) p else it } else all + p
        save(next)
        return next
    }

    fun remove(id: String): List<GameProfile> = list().filterNot { it.id == id }.also(::save)

    private fun parse(o: JSONObject) = GameProfile(
        id = o.str("id"),
        name = o.str("name"),
        loader = o.str("loader").ifBlank { "vanilla" },
        loaderVersion = o.str("loaderVersion").ifBlank { null },
        gameVersion = o.str("gameVersion"),
        mods = o.optInt("mods"),
        featured = o.optBoolean("featured"),
        packId = o.str("packId").ifBlank { null },
        packVersion = o.str("packVersion").ifBlank { null },
        serverAddress = o.str("serverAddress").ifBlank { null },
        installedMods = o.optJSONArray("installedMods").mapObjects { m ->
            InstalledMod(m.str("projectId"), m.str("versionId"), m.str("title"), m.str("filename"))
        },
    )

    private fun toJson(p: GameProfile) = JSONObject()
        .put("id", p.id).put("name", p.name).put("loader", p.loader).put("loaderVersion", p.loaderVersion ?: "")
        .put("gameVersion", p.gameVersion).put("mods", p.mods).put("featured", p.featured)
        .put("packId", p.packId ?: "").put("packVersion", p.packVersion ?: "").put("serverAddress", p.serverAddress ?: "")
        .put(
            "installedMods",
            JSONArray(p.installedMods.map { JSONObject().put("projectId", it.projectId).put("versionId", it.versionId).put("title", it.title).put("filename", it.filename) }),
        )
}

object GameVersions {
    suspend fun releases(): List<String> =
        Http.getJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
            .optJSONArray("versions").mapObjects { v -> if (v.str("type") == "release") v.str("id") else null }

    suspend fun loaderVersion(loader: String, gameVersion: String): String? = when (loader) {
        "fabric" -> Http.getArray("https://meta.fabricmc.net/v2/versions/loader/$gameVersion")
            .optJSONObject(0)?.optJSONObject("loader")?.str("version")
        "quilt" -> Http.getArray("https://meta.quiltmc.org/v3/versions/loader/$gameVersion")
            .optJSONObject(0)?.optJSONObject("loader")?.str("version")
        "neoforge" -> {
            val parts = gameVersion.split('.')
            val prefix = "${parts.getOrElse(1) { "0" }}.${parts.getOrElse(2) { "0" }}."
            Http.getJson("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge")
                .optJSONArray("versions")?.let { a -> (0 until a.length()).map(a::getString) }
                ?.lastOrNull { it.startsWith(prefix) && !it.contains("beta") }
        }
        "forge" -> Http.getJson("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json")
            .optJSONObject("promos")?.let { it.str("$gameVersion-recommended", "$gameVersion-latest") }
        else -> null
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("pixel_settings", Context.MODE_PRIVATE)

    var accent: String
        get() = prefs.getString("accent", "emerald") ?: "emerald"
        set(v) = prefs.edit().putString("accent", v).apply()
    var background: String
        get() = prefs.getString("background", "retro-grid") ?: "retro-grid"
        set(v) = prefs.edit().putString("background", v).apply()
    var animations: Boolean
        get() = prefs.getBoolean("animations", true)
        set(v) = prefs.edit().putBoolean("animations", v).apply()
    var selectedProfile: String
        get() = prefs.getString("selectedProfile", "") ?: ""
        set(v) = prefs.edit().putString("selectedProfile", v).apply()
    var ramMb: Int
        get() = prefs.getInt("ramMb", 0)
        set(v) = prefs.edit().putInt("ramMb", v).apply()
    var autoJoin: Boolean
        get() = prefs.getBoolean("autoJoin", true)
        set(v) = prefs.edit().putBoolean("autoJoin", v).apply()
}
