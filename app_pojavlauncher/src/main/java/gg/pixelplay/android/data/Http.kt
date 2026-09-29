package gg.pixelplay.android.data

import net.kdt.pojavlaunch.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, val status: Int = 0) : IOException(message)

object Http {
    const val USER_AGENT_PREFIX = "PixelPlayAndroid"
    val userAgent = "$USER_AGENT_PREFIX/${BuildConfig.VERSION_NAME}"
    private val jsonType = "application/json".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        .build()

    fun jsonBody(obj: JSONObject): RequestBody = obj.toString().toRequestBody(jsonType)

    fun form(vararg pairs: Pair<String, String>): RequestBody =
        FormBody.Builder().apply { pairs.forEach { add(it.first, it.second) } }.build()

    suspend fun call(
        method: String,
        url: String,
        body: RequestBody? = null,
        bearer: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("Accept", "application/json").apply {
            if (!bearer.isNullOrBlank()) header("Authorization", "Bearer $bearer")
            method(method, body ?: if (method == "POST") ByteArray(0).toRequestBody(null) else null)
        }.build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ApiException(errorText(text, res.code), res.code)
            text
        }
    }

    suspend fun getJson(url: String, bearer: String? = null) = JSONObject(call("GET", url, bearer = bearer).ifBlank { "{}" })
    suspend fun getArray(url: String) = JSONArray(call("GET", url).ifBlank { "[]" })
    suspend fun postJson(url: String, payload: JSONObject?, bearer: String? = null) =
        JSONObject(call("POST", url, payload?.let(::jsonBody), bearer).ifBlank { "{}" })

    suspend fun download(url: String, dest: File, onProgress: ((Long, Long) -> Unit)? = null) = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        client.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw ApiException("descarga ${res.code}: $url", res.code)
            val body = res.body ?: throw ApiException("descarga vacía: $url")
            val total = body.contentLength()
            body.byteStream().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress?.invoke(done, total)
                    }
                }
            }
        }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    private fun errorText(body: String, code: Int): String {
        runCatching {
            val o = JSONObject(body)
            listOf("error", "message", "errorMessage", "error_description").forEach { k ->
                val v = o.optString(k)
                if (v.isNotBlank()) return v
            }
        }
        return "HTTP $code${if (body.isNotBlank()) " — ${body.take(200)}" else ""}"
    }
}

fun JSONObject.str(vararg keys: String): String {
    for (k in keys) {
        val v = optString(k, "")
        if (v.isNotBlank() && v != "null") return v
    }
    return ""
}

inline fun <T> JSONArray?.mapObjects(fn: (JSONObject) -> T?): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        fn(o)?.let(out::add)
    }
    return out
}
