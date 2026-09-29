package gg.pixelplay.android.game

import android.app.Activity
import android.content.Context
import gg.pixelplay.android.data.GameProfile
import java.io.File

data class LaunchAccount(
    val username: String,
    val uuid: String,
    val accessToken: String,
    val microsoft: Boolean,
)

/**
 * Runs Minecraft. The Amethyst fork ships `gg.pixelplay.engine.AmethystEngine`; the standalone
 * UI build has no engine and only manages accounts, social and packs.
 */
interface GameEngine {
    val name: String

    /** Called once from the launcher activity (unpacks runtimes/components). */
    fun init(activity: Activity) {}

    /** Root folder where each profile gets `<root>/<profileId>` as its game directory. */
    fun instancesRoot(context: Context): File

    /** Downloads the Java runtime, vanilla client, libraries, assets and the mod loader for [profile]. */
    suspend fun prepare(
        activity: Activity,
        profile: GameProfile,
        gameDir: File,
        account: LaunchAccount,
        progress: (String, Float?) -> Unit,
    )

    fun launch(activity: Activity, profile: GameProfile, gameDir: File, account: LaunchAccount, server: String?)

    fun isRunning(context: Context): Boolean = false

    companion object {
        fun load(): GameEngine? = runCatching {
            Class.forName("gg.pixelplay.engine.AmethystEngine").getDeclaredConstructor().newInstance() as GameEngine
        }.getOrNull()
    }
}
