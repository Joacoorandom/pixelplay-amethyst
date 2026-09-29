package gg.pixelplay.android.game

import gg.pixelplay.android.data.ApiException
import gg.pixelplay.android.data.Http
import gg.pixelplay.android.data.Modrinth
import gg.pixelplay.android.data.mapObjects
import gg.pixelplay.android.data.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile

data class PackManifest(
    val id: String,
    val name: String,
    val description: String,
    val gameVersion: String,
    val loader: String,
    val version: String,
    val updaterUrl: String?,
    val autojoin: String?,
    val mods: List<PackMod>,
)

data class PackMod(
    val id: String,
    val type: String,
    val slug: String,
    val identifier: String,
    val filename: String,
    val groupId: String = "",
    val artifactId: String = "",
    val repoRef: String = "",
)

/** Installs `.ppack` files exactly like the desktop `internal/packs` (mods + overrides into the game dir). */
object PackInstaller {
    private const val STATE_FILE = ".pixelpack.json"

    fun readManifest(ppack: File): Pair<PackManifest, Map<String, String>> = ZipFile(ppack).use { zip ->
        val entry = zip.getEntry("manifest.json") ?: throw ApiException("El pack no tiene manifest.json")
        val o = JSONObject(zip.getInputStream(entry).bufferedReader().readText())
        val gv = o.str("game_version")
        val loader = o.str("loader").lowercase()
        val repos = mutableMapOf<String, String>()
        o.optJSONObject("repositories")?.let { r -> r.keys().forEach { k -> repos[k] = r.optString(k) } }
        o.optJSONArray("repositories")?.mapObjects { r -> repos[r.str("id", "name")] = r.str("url"); null }
        val mods = o.optJSONArray("mods").mapObjects { m ->
            val src = m.optJSONObject("source") ?: return@mapObjects null
            val target = m.optJSONObject("compatibility")?.optJSONObject(gv)?.optJSONObject(loader) ?: return@mapObjects null
            PackMod(
                id = m.str("id"),
                type = src.str("type").lowercase(),
                slug = src.str("projectSlug", "projectId"),
                identifier = target.str("identifier"),
                filename = target.str("filename"),
                groupId = src.str("groupId"),
                artifactId = src.str("artifactId"),
                repoRef = src.str("repositoryRef"),
            )
        }
        val policy = o.optJSONObject("policy")
        PackManifest(
            id = o.str("id"),
            name = o.str("display_name", "id"),
            description = o.str("description"),
            gameVersion = gv,
            loader = loader,
            version = o.str("pack_version"),
            updaterUrl = o.optJSONObject("updater")?.str("url")?.ifBlank { null },
            autojoin = policy?.str("autojoin_server")?.ifBlank { null },
            mods = mods,
        ) to repos
    }

    /** Latest ppack URL from the pack's version.json, or null when it can't be checked. */
    suspend fun latest(updaterUrl: String): Pair<String, String>? = runCatching {
        val v = Http.getJson(updaterUrl)
        v.str("version") to v.str("url")
    }.getOrNull()

    suspend fun install(
        ppack: File,
        gameDir: File,
        progress: (String, Float?) -> Unit,
    ): PackManifest = withContext(Dispatchers.IO) {
        val (manifest, repos) = readManifest(ppack)
        val modsDir = File(gameDir, "mods").apply { mkdirs() }
        val stateFile = File(gameDir, STATE_FILE)
        val previous = runCatching {
            JSONObject(stateFile.readText()).optJSONArray("files")?.let { a -> (0 until a.length()).map(a::getString) }
        }.getOrNull().orEmpty()
        val wanted = manifest.mods.map { it.filename }.toSet()
        previous.filterNot(wanted::contains).forEach { File(modsDir, it).delete() }

        val total = manifest.mods.size
        val done = AtomicInteger(0)
        val gate = Semaphore(6)
        coroutineScope {
            manifest.mods.map { mod ->
                async {
                    gate.withPermit {
                        val dest = File(modsDir, mod.filename)
                        if (!dest.exists() || dest.length() == 0L) downloadMod(mod, repos, dest)
                        val n = done.incrementAndGet()
                        progress("Mods $n/$total", n.toFloat() / total.coerceAtLeast(1))
                    }
                }
            }.awaitAll()
        }

        progress("Aplicando configuración", null)
        ZipFile(ppack).use { zip ->
            val root = gameDir.canonicalPath + File.separator
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("overrides/") && !it.name.endsWith("README.txt") && !it.name.endsWith(".keep") }
                .forEach { e ->
                    val rel = e.name.removePrefix("overrides/")
                    val out = File(gameDir, rel)
                    if (!out.canonicalPath.startsWith(root)) return@forEach
                    if (rel == "options.txt" && out.exists()) return@forEach
                    out.parentFile?.mkdirs()
                    zip.getInputStream(e).use { input -> out.outputStream().use(input::copyTo) }
                }
        }
        stateFile.writeText(
            JSONObject().put("pack", manifest.id).put("version", manifest.version).put("files", JSONArray(wanted.toList())).toString(),
        )
        manifest
    }

    private suspend fun downloadMod(mod: PackMod, repos: Map<String, String>, dest: File) {
        when (mod.type) {
            "modrinth" -> {
                val maven = "https://api.modrinth.com/maven/maven/modrinth/${mod.slug}/${mod.identifier}/${mod.filename}"
                try {
                    Http.download(maven, dest)
                } catch (_: Exception) {
                    val f = Modrinth.version(mod.identifier)
                    Http.download(f.url, dest)
                    f.sha1?.let { verifySha1(dest, it) }
                }
            }
            "url" -> Http.download(mod.identifier, dest)
            "maven" -> {
                val repo = repos[mod.repoRef]?.trimEnd('/').takeUnless { it.isNullOrBlank() } ?: "https://maven.fabricmc.net"
                Http.download("$repo/${mod.groupId.replace('.', '/')}/${mod.artifactId}/${mod.identifier}/${mod.filename}", dest)
            }
            else -> if (mod.identifier.startsWith("http")) Http.download(mod.identifier, dest)
            else throw ApiException("Fuente ${mod.type} no soportada (${mod.id})")
        }
    }

    private fun verifySha1(file: File, expected: String) {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        val got = md.digest().joinToString("") { "%02x".format(it) }
        if (!got.equals(expected, true)) {
            file.delete()
            throw ApiException("Hash inválido: ${file.name}")
        }
    }
}
