package gg.pixelplay.android.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import gg.pixelplay.android.data.PresenceStatus
import gg.pixelplay.android.ui.theme.LocalAccent
import gg.pixelplay.android.ui.theme.PixelColors

enum class PixelVariant { Default, Secondary, Destructive, Ghost }

private val PixelShape = RoundedCornerShape(4.dp)

/** 3D press button matching the desktop `Button variant="3d"` look. */
@Composable
fun PixelButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    variant: PixelVariant = PixelVariant.Default,
    enabled: Boolean = true,
    depth: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge,
) {
    val accent = LocalAccent.current
    val (main, dark) = when (variant) {
        PixelVariant.Default -> accent.value to accent.dark
        PixelVariant.Secondary -> PixelColors.Secondary to PixelColors.SecondaryDark
        PixelVariant.Destructive -> PixelColors.Danger to PixelColors.DangerDark
        PixelVariant.Ghost -> Color.Transparent to Color.Transparent
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val travel by animateDpAsState(if (pressed && enabled) depth else 0.dp, tween(70), label = "press")
    val alpha = if (enabled) 1f else 0.45f

    Box(
        modifier = modifier
            .padding(bottom = depth)
            .drawBehind {
                if (variant != PixelVariant.Ghost) {
                    drawRoundRect(
                        color = dark.copy(alpha = alpha),
                        topLeft = Offset(0f, depth.toPx()),
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    )
                }
            }
            .offset(y = travel)
            .clip(PixelShape)
            .background(
                if (variant == PixelVariant.Ghost) Color.White.copy(alpha = 0.06f) else main.copy(alpha = alpha),
            )
            .border(1.dp, Color.White.copy(alpha = if (variant == PixelVariant.Ghost) 0.12f else 0.18f), PixelShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, contentDescription = text, tint = Color.White, modifier = Modifier.size(18.dp))
            if (text != null) Text(text.uppercase(), style = textStyle, color = Color.White.copy(alpha = alpha))
        }
    }
}

@Composable
fun PixelCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = LocalAccent.current
    val border = if (highlighted) accent.value else accent.value.copy(alpha = 0.25f)
    Column(
        modifier = modifier
            .clip(PixelShape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.35f)),
                ),
            )
            .border(BorderStroke(if (highlighted) 2.dp else 1.dp, border), PixelShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text.lowercase(), style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun PlayerHead(name: String?, size: Dp, modifier: Modifier = Modifier) {
    val px = (size.value * 2).toInt().coerceIn(32, 256)
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data("https://mc-heads.net/avatar/${name ?: "MHF_Steve"}/$px")
            .crossfade(false)
            .build(),
        contentDescription = name,
        filterQuality = FilterQuality.None,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size).clip(RoundedCornerShape(3.dp)),
    )
}

@Composable
fun PlayerBody(name: String?, height: Dp, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data("https://mc-heads.net/body/${name ?: "MHF_Steve"}/${(height.value * 2).toInt()}")
            .crossfade(true)
            .build(),
        contentDescription = name,
        filterQuality = FilterQuality.None,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
fun StatusDot(status: PresenceStatus, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    val color = when (status) {
        PresenceStatus.Online -> PixelColors.Online
        PresenceStatus.InGame -> PixelColors.InGame
        PresenceStatus.Offline -> PixelColors.Offline
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, Color.Black, CircleShape),
    )
}

@Composable
fun RankTag(label: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun PixelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    onSubmit: (() -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
) {
    val accent = LocalAccent.current
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(accent.light),
        visualTransformation = visualTransformation,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (onSubmit != null) androidx.compose.ui.text.input.ImeAction.Send else androidx.compose.ui.text.input.ImeAction.Search,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onAny = { onSubmit?.invoke() }),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .clip(PixelShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, accent.value.copy(alpha = 0.45f), PixelShape)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = PixelColors.TextFaint)
                inner()
            }
        },
    )
}

@Composable
fun SegmentChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Text(
        label.lowercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = if (selected) 1f else 0.6f),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) accent.value else Color.White.copy(alpha = 0.05f))
            .border(1.dp, accent.value.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(PixelColors.Danger)
            .border(1.dp, Color(0xFFF87171), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 9) "9+" else "$count", style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
