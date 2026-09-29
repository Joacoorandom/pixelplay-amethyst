package gg.pixelplay.android.data

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** Device-code Microsoft → Xbox → Minecraft flow, identical to the desktop `internal/auth/msa.go`. */
object MicrosoftAuth {
    private const val CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb"
    private const val SCOPE = "XboxLive.signin XboxLive.offline_access offline_access"
    private const val TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"

    data class Device(val prompt: DeviceCodePrompt, val deviceCode: String, val interval: Long, val expiresAt: Long)

    data class Session(val uuid: String, val name: String, val mcToken: String, val refreshToken: String, val expiresAt: Long)

    suspend fun begin(): Device {
        val o = JSONObject(
            Http.call(
                "POST",
                "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
                Http.form("client_id" to CLIENT_ID, "scope" to SCOPE),
            ),
        )
        val code = o.str("user_code")
        val uri = o.str("verification_uri").ifBlank { "https://www.microsoft.com/link" }
        return Device(
            DeviceCodePrompt(code, uri, "https://www.microsoft.com/link?otc=$code"),
            o.str("device_code"),
            o.optLong("interval", 5).coerceAtLeast(2),
            System.currentTimeMillis() + o.optLong("expires_in", 900) * 1000,
        )
    }

    suspend fun await(dev: Device): Session {
        var interval = dev.interval
        while (System.currentTimeMillis() < dev.expiresAt) {
            delay(interval * 1000)
            val res = try {
                JSONObject(
                    Http.call(
                        "POST",
                        TOKEN_URL,
                        Http.form(
                            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                            "client_id" to CLIENT_ID,
                            "device_code" to dev.deviceCode,
                        ),
                    ),
                )
            } catch (e: ApiException) {
                when {
                    e.message?.contains("authorization_pending", true) == true ||
                        e.message?.contains("AADSTS70016") == true -> continue
                    e.message?.contains("slow_down", true) == true -> { interval += 5; continue }
                    else -> throw ApiException("Microsoft: ${e.message}")
                }
            }
            return complete(res.str("access_token"), res.str("refresh_token"))
        }
        throw ApiException("El código de Microsoft expiró, probá de nuevo")
    }

    suspend fun refresh(refreshToken: String): Session {
        val res = JSONObject(
            Http.call(
                "POST",
                TOKEN_URL,
                Http.form(
                    "client_id" to CLIENT_ID,
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                    "scope" to SCOPE,
                ),
            ),
        )
        return complete(res.str("access_token"), res.str("refresh_token").ifBlank { refreshToken })
    }

    private suspend fun complete(msaAccess: String, refresh: String): Session {
        val xbl = Http.postJson(
            "https://user.auth.xboxlive.com/user/authenticate",
            JSONObject()
                .put("Properties", JSONObject().put("AuthMethod", "RPS").put("SiteName", "user.auth.xboxlive.com").put("RpsTicket", "d=$msaAccess"))
                .put("RelyingParty", "http://auth.xboxlive.com")
                .put("TokenType", "JWT"),
        ).str("Token")

        val xsts = try {
            Http.postJson(
                "https://xsts.auth.xboxlive.com/xsts/authorize",
                JSONObject()
                    .put("Properties", JSONObject().put("SandboxId", "RETAIL").put("UserTokens", JSONArray().put(xbl)))
                    .put("RelyingParty", "rp://api.minecraftservices.com/")
                    .put("TokenType", "JWT"),
            )
        } catch (e: ApiException) {
            throw ApiException(
                if (e.message?.contains("2148916233") == true) "Esa cuenta Microsoft no tiene perfil de Xbox" else "Xbox: ${e.message}",
            )
        }
        val uhs = xsts.optJSONObject("DisplayClaims")?.optJSONArray("xui")?.optJSONObject(0)?.str("uhs").orEmpty()
        val mc = Http.postJson(
            "https://api.minecraftservices.com/authentication/login_with_xbox",
            JSONObject().put("identityToken", "XBL3.0 x=$uhs;${xsts.str("Token")}"),
        )
        val mcToken = mc.str("access_token")
        val profile = try {
            Http.getJson("https://api.minecraftservices.com/minecraft/profile", mcToken)
        } catch (e: ApiException) {
            throw ApiException(if (e.status == 404) "Esa cuenta no tiene Minecraft Java comprado" else "Minecraft: ${e.message}")
        }
        return Session(
            uuid = profile.str("id"),
            name = profile.str("name"),
            mcToken = mcToken,
            refreshToken = refresh,
            expiresAt = System.currentTimeMillis() + mc.optLong("expires_in", 86_400) * 1000,
        )
    }
}
