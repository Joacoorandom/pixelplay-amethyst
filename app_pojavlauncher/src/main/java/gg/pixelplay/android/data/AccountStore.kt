package gg.pixelplay.android.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

data class StoredAccount(
    val id: String,
    val username: String,
    val kind: AccountKind,
    val pixelToken: String = "",
    val mcAccessToken: String = "",
    val msRefreshToken: String = "",
    val mcExpiresAt: Long = 0,
    val rank: String = "usuario",
    val active: Boolean = false,
) {
    fun toJson() = JSONObject()
        .put("id", id).put("username", username).put("kind", kind.name)
        .put("pixelToken", pixelToken).put("mcAccessToken", mcAccessToken)
        .put("msRefreshToken", msRefreshToken).put("mcExpiresAt", mcExpiresAt)
        .put("rank", rank).put("active", active)

    companion object {
        fun fromJson(o: JSONObject) = StoredAccount(
            id = o.str("id"),
            username = o.str("username"),
            kind = runCatching { AccountKind.valueOf(o.str("kind")) }.getOrDefault(AccountKind.Pixel),
            pixelToken = o.str("pixelToken"),
            mcAccessToken = o.str("mcAccessToken"),
            msRefreshToken = o.str("msRefreshToken"),
            mcExpiresAt = o.optLong("mcExpiresAt"),
            rank = o.str("rank").ifBlank { "usuario" },
            active = o.optBoolean("active"),
        )
    }
}

/** Accounts live in app-private storage, same fields the desktop keeps in accounts.json. */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("pixel_accounts", Context.MODE_PRIVATE)

    fun list(): List<StoredAccount> = runCatching {
        JSONArray(prefs.getString("accounts", "[]")).mapObjects(StoredAccount::fromJson)
    }.getOrDefault(emptyList())

    fun active(): StoredAccount? = list().let { all -> all.firstOrNull { it.active } ?: all.firstOrNull() }

    fun upsert(acc: StoredAccount, makeActive: Boolean = acc.active): List<StoredAccount> {
        val rest = list().filterNot { sameId(it.id, acc.id) }
        val next = (rest.map { if (makeActive) it.copy(active = false) else it } + acc.copy(active = makeActive || acc.active))
        save(next)
        return next
    }

    fun setActive(id: String): List<StoredAccount> = list().map { it.copy(active = sameId(it.id, id)) }.also(::save)

    fun remove(id: String): List<StoredAccount> {
        val next = list().filterNot { sameId(it.id, id) }.toMutableList()
        if (next.isNotEmpty() && next.none { it.active }) next[0] = next[0].copy(active = true)
        save(next)
        return next
    }

    private fun save(list: List<StoredAccount>) {
        prefs.edit().putString("accounts", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    companion object {
        fun sameId(a: String, b: String) = a.replace("-", "").equals(b.replace("-", ""), ignoreCase = true)
    }
}

object Jwt {
    fun claims(token: String): JSONObject? = runCatching {
        val part = token.split('.').getOrNull(1) ?: return null
        JSONObject(String(Base64.decode(part, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
    }.getOrNull()

    fun fresh(token: String, skewMs: Long = 120_000): Boolean {
        val exp = claims(token)?.optLong("exp") ?: return false
        return exp == 0L || exp * 1000 - skewMs > System.currentTimeMillis()
    }
}
