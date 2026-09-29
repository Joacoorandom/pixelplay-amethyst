package gg.pixelplay.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.R

@Immutable
data class AccentColor(
    val id: String,
    val name: String,
    val value: Color,
    val hover: Color,
    val light: Color,
    val dark: Color,
)

/** Same palette as the desktop launcher (`useThemeStore.ts`). */
val AccentColors = listOf(
    AccentColor("emerald", "Emerald", Color(0xFF059669), Color(0xFF047857), Color(0xFF10B981), Color(0xFF065F46)),
    AccentColor("green", "Green", Color(0xFF10B981), Color(0xFF059669), Color(0xFF34D399), Color(0xFF047857)),
    AccentColor("teal", "Teal", Color(0xFF14B8A6), Color(0xFF0D9488), Color(0xFF2DD4BF), Color(0xFF0F766E)),
    AccentColor("cyan", "Cyan", Color(0xFF06B6D4), Color(0xFF0891B2), Color(0xFF22D3EE), Color(0xFF0E7490)),
    AccentColor("blue", "Blue", Color(0xFF3B82F6), Color(0xFF2563EB), Color(0xFF60A5FA), Color(0xFF1D4ED8)),
    AccentColor("violet", "Violet", Color(0xFF8B5CF6), Color(0xFF7C3AED), Color(0xFFA78BFA), Color(0xFF6D28D9)),
    AccentColor("pink", "Pink", Color(0xFFEC4899), Color(0xFFDB2777), Color(0xFFF472B6), Color(0xFFBE185D)),
    AccentColor("amber", "Amber", Color(0xFFF59E0B), Color(0xFFD97706), Color(0xFFFBBF24), Color(0xFFB45309)),
)

object PixelColors {
    val Background = Color(0xFF0A0A0C)
    val BackgroundTop = Color(0xFF121218)
    val BackgroundBottom = Color(0xFF050507)
    val Surface = Color(0x66000000)
    val Border = Color(0x1AFFFFFF)
    val TextPrimary = Color.White
    val TextMuted = Color(0xB3FFFFFF)
    val TextFaint = Color(0x80FFFFFF)
    val Danger = Color(0xFFEF4444)
    val DangerDark = Color(0xFFDC2626)
    val Secondary = Color(0xFF6B7280)
    val SecondaryDark = Color(0xFF4B5563)
    val Online = Color(0xFF22C55E)
    val InGame = Color(0xFF3B82F6)
    val Offline = Color(0xFF6B7280)
}

val MinecraftFont = FontFamily(Font(R.font.minecraft))
val MinecraftTenFont = FontFamily(Font(R.font.minecraft_ten))

val LocalAccent = staticCompositionLocalOf { AccentColors.first() }

private val PixelTypography = Typography(
    displayLarge = TextStyle(fontFamily = MinecraftFont, fontSize = 56.sp, letterSpacing = 2.sp),
    headlineMedium = TextStyle(fontFamily = MinecraftFont, fontSize = 34.sp, letterSpacing = 1.5.sp),
    titleLarge = TextStyle(fontFamily = MinecraftFont, fontSize = 28.sp, letterSpacing = 1.sp),
    titleMedium = TextStyle(fontFamily = MinecraftFont, fontSize = 22.sp, letterSpacing = 1.sp),
    bodyLarge = TextStyle(fontFamily = MinecraftTenFont, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = MinecraftTenFont, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = MinecraftTenFont, fontSize = 11.sp),
    labelLarge = TextStyle(fontFamily = MinecraftFont, fontSize = 20.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = MinecraftTenFont, fontSize = 11.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = MinecraftTenFont, fontSize = 9.sp, letterSpacing = 0.5.sp),
)

@Composable
fun PixelTheme(accent: AccentColor, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent.value,
        onPrimary = Color.White,
        secondary = accent.light,
        background = PixelColors.Background,
        onBackground = PixelColors.TextPrimary,
        surface = PixelColors.Background,
        onSurface = PixelColors.TextPrimary,
        error = PixelColors.Danger,
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = scheme, typography = PixelTypography, content = content)
    }
}
