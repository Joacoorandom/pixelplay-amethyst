package gg.pixelplay.android.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class RankMeta(val key: String, val label: String, val color: Color, val aliases: List<String>)

data class FriendsPayload(
    val friends: List<PixelFriend>,
    val incoming: List<PixelFriend>,
    val outgoing: List<PixelFriend>,
)

data class PixelLogin(val token: String, val uuid: String, val username: String, val rank: String)

data class FeaturedPack(
    val packId: String,
    val name: String,
    val description: String,
    val address: String,
    val gameVersion: String,
    val loader: String,
    val loaderVersion: String,
    val downloadUrl: String,
)

/** Same endpoints as the desktop Go backend (`internal/auth`). */
class PixelApi(private val context: Context) {
    companion object {
        const val BASE = "https://api.pixelplay.gg/api/v1"
        private val DEFAULT_RANKS = listOf(
            RankMeta("usuario", "Usuario", Color(0xFF059669), listOf("user")),
            RankMeta("owner", "Owner", Color(0xFFEF4444), emptyList()),
        )

        fun parseColor(hex: String?, fallback: Color = Color(0xFF9CA3AF)): Color = runCatching {
            val h = hex!!.trim().removePrefix("#")
            val v = h.toLong(16)
            when (h.length) {
                6 -> Color(0xFF000000 or v)
                8 -> Color(v)
                else -> fallback
            }
        }.getOrDefault(fallback)

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    }

    @Volatile private var ranks: List<RankMeta> = DEFAULT_RANKS

    fun rankMeta(rank: String?): RankMeta {
        val key = rank?.trim()?.lowercase().orEmpty().ifBlank { "usuario" }
        return ranks.firstOrNull { it.key == key || key in it.aliases }
            ?: RankMeta(key, key.replaceFirstChar { it.uppercase() }, Color(0xFF9CA3AF), emptyList())
    }

    suspend fun loadRanks() {
        val o = Http.getJson("$BASE/ranks")
        val parsed = o.optJSONArray("ranks").mapObjects { r ->
            val aliases = r.optJSONArray("aliases")
            RankMeta(
                r.str("key").lowercase(),
                r.str("label"),
                parseColor(r.str("color")),
                (0 until (aliases?.length() ?: 0)).map { aliases!!.optString(it).lowercase() },
            )
        }
        if (parsed.isNotEmpty()) ranks = parsed
    }

    private fun parseLogin(o: JSONObject, fallbackName: String): PixelLogin {
        val token = o.str("value", "token")
        if (token.isBlank()) throw ApiException("Respuesta Pixel inválida")
        val acc = o.optJSONObject("account") ?: JSONObject()
        val name = acc.str("username").ifBlank { fallbackName }
        return PixelLogin(token, acc.str("uuid").ifBlank { name }, name, acc.str("rank").ifBlank { "usuario" })
    }

    suspend fun login(username: String, password: String): PixelLogin {
        val o = Http.postJson("$BASE/auth/pixel/login", JSONObject().put("username", username.trim()).put("password", password))
        return parseLogin(o, username.trim())
    }

    suspend fun reserve(username: String, password: String): PixelLogin {
        val o = Http.postJson("$BASE/auth/pixel/reserve", JSONObject().put("username", username.trim()).put("password", password))
        return parseLogin(o, username.trim())
    }

    /** Returns true when the name is already a Pixel account (so the UI shows "login" instead of "crear"). */
    suspend fun lookup(username: String): Boolean =
        Http.getJson("$BASE/auth/pixel/lookup?username=${enc(username.trim())}").optBoolean("exists")

    suspend fun me(token: String): JSONObject = Http.getJson("$BASE/auth/pixel/me", token)

    private fun friend(o: JSONObject): PixelFriend? {
        val name = o.str("username")
        if (name.isBlank()) return null
        val meta = rankMeta(o.str("rank"))
        return PixelFriend(
            uuid = o.str("uuid").ifBlank { name },
            username = name,
            rankLabel = o.str("rankLabel").ifBlank { meta.label },
            rankColor = o.str("rankColor").takeIf { it.isNotBlank() }?.let { parseColor(it, meta.color) } ?: meta.color,
            status = PresenceStatus.parse(o.str("status")),
        )
    }

    suspend fun friends(token: String): FriendsPayload {
        val o = Http.getJson("$BASE/friends", token)
        return FriendsPayload(
            o.optJSONArray("friends").mapObjects(::friend),
            o.optJSONArray("incoming").mapObjects(::friend),
            o.optJSONArray("outgoing").mapObjects(::friend),
        )
    }

    private suspend fun friendAction(path: String, token: String, username: String) =
        Http.postJson("$BASE$path", JSONObject().put("username", username.trim()), token)

    suspend fun addFriend(token: String, username: String) = friendAction("/friends", token, username)
    suspend fun acceptFriend(token: String, username: String) = friendAction("/friends/accept", token, username)
    suspend fun declineFriend(token: String, username: String) = friendAction("/friends/decline", token, username)
    suspend fun cancelFriend(token: String, username: String) = friendAction("/friends/cancel", token, username)
    suspend fun removeFriend(token: String, username: String) = friendAction("/friends/remove", token, username)

    suspend fun presence(token: String, status: String) =
        Http.postJson("$BASE/presence", JSONObject().put("status", status), token)

    suspend fun featured(): FeaturedPack {
        val o = runCatching { Http.getJson("$BASE/launcher/featured") }.getOrDefault(JSONObject())
        return FeaturedPack(
            packId = o.str("packId").ifBlank { "pixelplay-survival-1211" },
            name = o.str("name").ifBlank { "Pixel Play" },
            description = o.str("description").ifBlank { "Survival oficial NeoForge 1.21.1" },
            address = o.str("address").ifBlank { "mc.pixelplay.gg" },
            gameVersion = o.str("gameVersion").ifBlank { "1.21.1" },
            loader = o.str("loader").ifBlank { "neoforge" },
            loaderVersion = o.str("loaderVersion").ifBlank { "21.1.248" },
            downloadUrl = o.str("downloadUrl").ifBlank { "$BASE/launcher/packs/pixelplay-survival-1211.ppack" },
        )
    }

    /** Microsoft accounts get their Pixel JWT through the Mojang join handshake, like the desktop. */
    suspend fun pixelTokenForMicrosoft(mcToken: String, uuid: String, username: String): String {
        val serverId = Http.postJson("$BASE/launcher/auth/request-server-id", null).str("serverId", "server_id")
        if (!serverId.startsWith("nrc-")) throw ApiException("server id inválido")
        Http.postJson(
            "https://sessionserver.mojang.com/session/minecraft/join",
            JSONObject().put("accessToken", mcToken).put("selectedProfile", uuid.replace("-", "")).put("serverId", serverId),
        )
        val raw = Http.call(
            "POST",
            "$BASE/launcher/auth/validate/v2?force=true&hwid=${enc(hwid())}&username=${enc(username)}&server_id=${enc(serverId)}",
        )
        return runCatching { JSONObject(raw).str("value") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: raw.trim().trim('"').takeIf { it.count { c -> c == '.' } == 2 }
            ?: throw ApiException("Pixel no devolvió token")
    }

    @SuppressLint("HardwareIds")
    fun hwid(): String {
        val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec("NRC".toByteArray(), "HmacSHA256")) }
        val h = mac.doFinal(raw.toByteArray()).toHex()
        return MessageDigest.getInstance("SHA-256").digest("pixelplay-device-salt$h".toByteArray()).toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}

object Modrinth {
    private const val BASE = "https://api.modrinth.com/v2"

    suspend fun search(query: String, gameVersion: String, loader: String, limit: Int = 30): List<ModResult> {
        val facets = """[["project_type:mod"],["versions:$gameVersion"],["categories:$loader"]]"""
        val url = "$BASE/search?limit=$limit&index=${if (query.isBlank()) "downloads" else "relevance"}" +
            "&query=${URLEncoder.encode(query, "UTF-8")}&facets=${URLEncoder.encode(facets, "UTF-8")}"
        return Http.getJson(url).optJSONArray("hits").mapObjects { h ->
            ModResult(
                id = h.str("project_id"),
                slug = h.str("slug"),
                title = h.str("title"),
                author = h.str("author"),
                downloads = h.optLong("downloads"),
                description = h.str("description"),
                iconUrl = h.str("icon_url").ifBlank { null },
            )
        }
    }

    data class FileRef(val versionId: String, val url: String, val filename: String, val sha1: String?)

    suspend fun latestFile(projectId: String, gameVersion: String, loader: String): FileRef {
        val url = "$BASE/project/$projectId/version?loaders=${URLEncoder.encode("[\"$loader\"]", "UTF-8")}" +
            "&game_versions=${URLEncoder.encode("[\"$gameVersion\"]", "UTF-8")}"
        val v = Http.getArray(url).optJSONObject(0) ?: throw ApiException("No hay versión para $loader $gameVersion")
        return fileOf(v)
    }

    suspend fun version(versionId: String): FileRef = fileOf(Http.getJson("$BASE/version/$versionId"))

    private fun fileOf(v: JSONObject): FileRef {
        val files = v.optJSONArray("files")
        val f = files.mapObjects { it }.let { list -> list.firstOrNull { it.optBoolean("primary") } ?: list.firstOrNull() }
            ?: throw ApiException("Versión sin archivos")
        return FileRef(v.str("id"), f.str("url"), f.str("filename"), f.optJSONObject("hashes")?.str("sha1"))
    }
}
