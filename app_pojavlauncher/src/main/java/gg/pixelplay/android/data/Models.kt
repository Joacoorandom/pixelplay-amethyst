package gg.pixelplay.android.data

import androidx.compose.ui.graphics.Color

enum class PresenceStatus { Online, InGame, Offline;
    companion object {
        fun parse(raw: String?): PresenceStatus = when (raw?.lowercase()) {
            "online" -> Online
            "ingame", "in_game", "playing" -> InGame
            else -> Offline
        }
    }
}

enum class AccountKind { Pixel, Microsoft }

data class PixelAccount(
    val id: String,
    val username: String,
    val kind: AccountKind,
    val rank: String,
    val rankLabel: String,
    val rankColor: Color,
    val active: Boolean = false,
) {
    val isPixel: Boolean get() = true
}

data class PixelFriend(
    val uuid: String,
    val username: String,
    val rankLabel: String,
    val rankColor: Color,
    val status: PresenceStatus,
    val unread: Int = 0,
)

data class GameProfile(
    val id: String,
    val name: String,
    val loader: String,
    val loaderVersion: String?,
    val gameVersion: String,
    val mods: Int,
    val featured: Boolean = false,
    val packId: String? = null,
    val packVersion: String? = null,
    val serverAddress: String? = null,
    val installedMods: List<InstalledMod> = emptyList(),
)

data class InstalledMod(
    val projectId: String,
    val versionId: String,
    val title: String,
    val filename: String,
)

enum class MessageKind { Text, CallEnded, CallMissed, CallDeclined;
    companion object {
        fun parse(raw: String?): MessageKind = when (raw) {
            "call_ended" -> CallEnded
            "call_missed" -> CallMissed
            "call_declined" -> CallDeclined
            else -> Text
        }
    }
}

data class ChatMessage(
    val id: String,
    val from: String,
    val to: String,
    val text: String,
    val timestamp: Long,
    val mine: Boolean,
    val kind: MessageKind = MessageKind.Text,
    val pending: Boolean = false,
    val failed: Boolean = false,
    val cid: String? = null,
)

data class ModResult(
    val id: String,
    val slug: String,
    val title: String,
    val author: String,
    val downloads: Long,
    val description: String,
    val iconUrl: String?,
)

enum class CallDirection { In, Out }
enum class CallPhase { Ringing, Active }

data class CallState(
    val id: String,
    val peer: String,
    val direction: CallDirection,
    val phase: CallPhase,
    val since: Long = 0,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val peerMuted: Boolean = false,
    val audioError: String? = null,
)

data class DeviceCodePrompt(val userCode: String, val verificationUri: String, val completeUri: String)

sealed interface LaunchState {
    data object Idle : LaunchState
    data class Working(val step: String, val progress: Float?) : LaunchState
    data class Failed(val message: String) : LaunchState
    data object Running : LaunchState
}
