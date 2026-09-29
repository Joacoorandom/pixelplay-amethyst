package gg.pixelplay.android.ui.backgrounds

import android.graphics.RuntimeShader
import android.graphics.Typeface
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import gg.pixelplay.android.ui.theme.AccentColor
import gg.pixelplay.android.ui.theme.LocalAccent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Same ids and order as the desktop `BACKGROUND_EFFECTS` enum. */
enum class BackgroundEffect(val id: String, val label: String) {
    RetroGrid("retro-grid", "Retro Grid"),
    MatrixRain("matrix-rain", "Matrix Rain"),
    Enchantment("enchantment-particles", "Encantamientos"),
    NebulaWaves("nebula-waves", "Nebula Waves"),
    NebulaParticles("nebula-particles", "Nebula Particles"),
    NebulaGrid("nebula-grid", "Nebula Grid"),
    NebulaVoxels("nebula-voxels", "Nebula Voxels"),
    NebulaLightning("nebula-lightning", "Lightning"),
    LiquidChrome("nebula-liquid-chrome", "Liquid Chrome"),
    OgBalls("og-balls", "OG Balls"),
    OgBoth("og-both", "OG Both"),
    Plain("plain-background", "Plano"),
    None("none", "Ninguno");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: RetroGrid
    }
}

private const val OPACITY = 0.3f
private const val SPEED = 1f

@Composable
private fun rememberEffectTime(animate: Boolean, fps: Int = 30): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        val interval = 1_000_000_000L / fps
        val offset = (t.floatValue * 1e9).toLong()
        val start = withFrameNanos { it } - offset
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (now - last >= interval) {
                    last = now
                    t.floatValue = (now - start) / 1e9f
                }
            }
        }
    }
    return t
}

/** Full-screen launcher backdrop: base gradient (as AppLayout) plus the selected effect. */
@Composable
fun PixelBackdrop(effect: BackgroundEffect, animate: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Box(modifier.fillMaxSize().drawBehindBase(accent)) {
        val time = rememberEffectTime(
            animate && effect != BackgroundEffect.Plain && effect != BackgroundEffect.None && effect != BackgroundEffect.RetroGrid,
        )
        when (effect) {
            BackgroundEffect.RetroGrid -> Box(Modifier.fillMaxSize().background(accent.value.copy(alpha = 0.05f)))
            BackgroundEffect.MatrixRain -> MatrixRain(accent, time)
            BackgroundEffect.Enchantment -> EnchantmentParticles(accent, time)
            BackgroundEffect.NebulaWaves -> NebulaWaves(accent, time)
            BackgroundEffect.NebulaParticles -> NebulaParticles(accent, time)
            BackgroundEffect.NebulaGrid -> NebulaGrid(accent, time)
            BackgroundEffect.NebulaVoxels -> NebulaVoxels(accent, time)
            BackgroundEffect.NebulaLightning -> ShaderEffect(ShaderKind.Lightning, accent, time)
            BackgroundEffect.LiquidChrome -> ShaderEffect(ShaderKind.Chrome, accent, time)
            BackgroundEffect.OgBalls -> OgBalls(accent, time, center = false)
            BackgroundEffect.OgBoth -> OgBalls(accent, time, center = true)
            BackgroundEffect.Plain -> Box(
                Modifier.fillMaxSize().background(
                    Color(accent.value.red * 0.1f, accent.value.green * 0.1f, accent.value.blue * 0.1f),
                ),
            )
            BackgroundEffect.None -> Unit
        }
    }
}

private fun Modifier.drawBehindBase(accent: AccentColor) = drawBehind {
    drawRect(
        Brush.linearGradient(
            0f to Color(0xFF121218), 0.55f to Color(0xFF07070A), 1f to Color(0xFF050507),
            start = Offset(size.width * 0.35f, 0f), end = Offset(size.width * 0.65f, size.height),
        ),
    )
    val r1 = size.width * 0.95f
    val c1 = Offset(size.width * 0.18f, size.height * 0.35f)
    drawCircle(Brush.radialGradient(listOf(accent.value.copy(alpha = 0.16f), Color.Transparent), c1, r1), r1, c1)
    val r2 = size.width * 0.75f
    val c2 = Offset(size.width * 0.85f, size.height * 0.75f)
    drawCircle(Brush.radialGradient(listOf(accent.value.copy(alpha = 0.09f), Color.Transparent), c2, r2), r2, c2)
}

// ---------- Retro grid: two perspective planes (top + bottom) fading into the middle ----------

/** Play-screen grid, like the desktop `PlayTab` renders `RetroGridEffect renderMode="both"`. */
@Composable
fun RetroGridLayer(animate: Boolean, modifier: Modifier = Modifier) {
    RetroGridEffect(LocalAccent.current, rememberEffectTime(animate), modifier)
}

@Composable
private fun RetroGridEffect(accent: AccentColor, time: State<Float>, modifier: Modifier = Modifier) {
    val line = accent.value.copy(alpha = 0.5f)
    Canvas(modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        val horizon = h * 0.5f
        val cell = 40.dp.toPx()
        val focal = h * 0.5f
        val camH = cell * 2.2f
        val scroll = (t / 10f * 200.dp.toPx() / cell) % 1f
        val stroke = 1.dp.toPx()

        fun plane(sign: Float) {
            fun alphaAt(screenY: Float): Float {
                val d = abs(screenY - horizon) / (h - horizon)
                return ((d - 0.2f) / 0.62f).coerceIn(0f, 1f)
            }
            var i = 0
            while (true) {
                val z = 1f + i - scroll
                if (z <= 0.2f) { i++; continue }
                val y = horizon + sign * focal * camH / (z * cell)
                if (abs(y - horizon) < 2f) break
                if ((sign > 0 && y <= h) || (sign < 0 && y >= 0f)) {
                    val a = alphaAt(y)
                    if (a > 0.01f) drawLine(line.copy(alpha = line.alpha * a), Offset(0f, y), Offset(w, y), stroke)
                } else if (i > 60) break
                i++
                if (i > 200) break
            }
            val zNear = 0.35f
            val zFar = 60f
            val halfCols = 24
            for (c in -halfCols..halfCols) {
                val x = c * cell
                val x0 = w / 2 + focal * x / (zNear * cell)
                val y0 = horizon + sign * focal * camH / (zNear * cell)
                val x1 = w / 2 + focal * x / (zFar * cell)
                val y1 = horizon + sign * focal * camH / (zFar * cell)
                val steps = 8
                for (s in 0 until steps) {
                    val f0 = s / steps.toFloat()
                    val f1 = (s + 1) / steps.toFloat()
                    val ya = y0 + (y1 - y0) * f0
                    val yb = y0 + (y1 - y0) * f1
                    val a = alphaAt((ya + yb) / 2)
                    if (a <= 0.01f) continue
                    drawLine(
                        line.copy(alpha = line.alpha * a),
                        Offset(x0 + (x1 - x0) * f0, ya),
                        Offset(x0 + (x1 - x0) * f1, yb),
                        stroke,
                    )
                }
            }
        }
        plane(1f)
        plane(-1f)
    }
}

// ---------- Matrix rain ----------

private const val MATRIX_CHARS = "ﾊﾐﾋｰｳｼﾅﾓﾆｻﾜﾂｵﾘｱﾎﾃﾏｹﾒｴｶｷﾑﾕﾗｾﾈｽﾀﾇﾍ0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

private class Drop(var x: Float, var y: Float, var trail: Int, var speed: Float, var brightness: Float, var pulse: Float, var pulseFactor: Float)

@Composable
private fun MatrixRain(accent: AccentColor, time: State<Float>) {
    val drops = remember { ArrayList<Drop>() }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE } }
    val last = remember { floatArrayOf(-1f) }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val font = 15.dp.toPx()
        paint.textSize = font
        val cols = (size.width / font).toInt()
        val rows = (size.height / font).toInt()
        if (drops.size != cols) {
            drops.clear()
            repeat(cols) { i -> drops += newDrop(i, font, rows, size.height, initial = true) }
        }
        val dt = if (last[0] < 0) 0f else (t - last[0]).coerceIn(0f, 0.1f)
        last[0] = t
        drawGridLines(accent.value, OPACITY * 0.2f, 40.dp.toPx(), t)
        val canvas = drawContext.canvas.nativeCanvas
        val base = accent.value
        for (i in drops.indices) {
            val d = drops[i]
            d.pulse = (d.pulse + d.pulseFactor * dt * 30f) % 1f
            val pulse = 0.5f + 0.5f * sin(2f * PI.toFloat() * d.pulse)
            val alpha = d.brightness * pulse * OPACITY * 2f
            paint.color = base.copy(alpha = min(1f, alpha * 3f)).toArgb()
            canvas.drawText(MATRIX_CHARS[Random.nextInt(MATRIX_CHARS.length)].toString(), d.x, d.y, paint)
            for (j in 1 until d.trail) {
                val y = d.y - j * font
                if (y < 0) break
                paint.color = base.copy(alpha = (alpha * (1f - j / d.trail.toFloat())).coerceIn(0f, 1f)).toArgb()
                canvas.drawText(MATRIX_CHARS[(i * 31 + j * 7 + (t * 8).toInt()) % MATRIX_CHARS.length].toString(), d.x, y, paint)
            }
            d.y += d.speed * dt * 60f * font / 16f
            if (d.y - d.trail * font > size.height) drops[i] = newDrop(i, font, rows, size.height, initial = false)
        }
    }
}

private fun newDrop(i: Int, font: Float, rows: Int, height: Float, initial: Boolean) = Drop(
    x = i * font,
    y = if (initial) Random.nextFloat() * height else -Random.nextFloat() * 100f,
    trail = (Random.nextFloat() * (rows * 0.45f) + 5).toInt().coerceAtMost(28),
    speed = (Random.nextFloat() + 0.5f) * SPEED,
    brightness = Random.nextFloat() * 0.5f + 0.5f,
    pulse = Random.nextFloat(),
    pulseFactor = Random.nextFloat() * 0.02f + 0.005f,
)

private fun DrawScope.drawGridLines(color: Color, opacity: Float, grid: Float, t: Float) {
    var y = 0f
    while (y < size.height) {
        drawLine(color.copy(alpha = opacity * (0.3f + 0.7f * sin(y * 0.1f + t))), Offset(0f, y), Offset(size.width, y), 1f)
        y += grid
    }
    var x = 0f
    while (x < size.width) {
        drawLine(color.copy(alpha = opacity * (0.3f + 0.7f * sin(x * 0.1f + t))), Offset(x, 0f), Offset(x, size.height), 1f)
        x += grid
    }
}

// ---------- Enchantment table glyphs ----------

private val ENCHANT = listOf("⍑", "⌇", "⎓", "⊣", "⊢", "⋮", "⫎", "⟒", "⟓", "⍊", "⌰", "⏃", "⏚", "⌿", "⍀", "⏁", "⎍", "⎐", "⍙", "⍡", "⊬", "⟊")

private class Glyph(var x: Float, var y: Float, var vx: Float, var vy: Float, val size: Float, val alpha: Float, var life: Float, val maxLife: Float, val ch: String)

@Composable
private fun EnchantmentParticles(accent: AccentColor, time: State<Float>) {
    val glyphs = remember { ArrayList<Glyph>() }
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SERIF
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val last = remember { floatArrayOf(-1f) }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val dt = if (last[0] < 0) 0f else (t - last[0]).coerceIn(0f, 0.1f)
        last[0] = t
        val k = dt * 60f
        val density = this.density
        fun spawn(anywhere: Boolean) {
            glyphs += Glyph(
                x = Random.nextFloat() * size.width,
                y = if (anywhere) Random.nextFloat() * size.height else size.height + 20f,
                vx = (Random.nextFloat() - 0.5f) * 0.5f * SPEED,
                vy = -Random.nextFloat() - 0.5f * SPEED,
                size = (Random.nextFloat() * 12f + 8f) * density,
                alpha = Random.nextFloat() * 0.6f + 0.2f,
                life = 0f,
                maxLife = 200f + Random.nextFloat() * 300f,
                ch = ENCHANT[Random.nextInt(ENCHANT.size)],
            )
        }
        if (glyphs.isEmpty()) repeat(50) { spawn(true) }
        val canvas = drawContext.canvas.nativeCanvas
        val it = glyphs.iterator()
        while (it.hasNext()) {
            val g = it.next()
            g.life += k
            val fadeIn = min(1f, g.life / 20f)
            val fadeOut = max(0f, 1f - (g.life - (g.maxLife - 20f)) / 20f)
            g.x += (g.vx + sin(g.life * 0.05f) * 0.2f * SPEED) * k * density
            g.y += g.vy * k * density
            g.vy *= 0.99f.pow(k)
            if (g.life >= g.maxLife || g.y < -40f) { it.remove(); continue }
            paint.textSize = g.size
            paint.color = accent.value.copy(alpha = (g.alpha * fadeIn * fadeOut * OPACITY * 2.2f).coerceIn(0f, 1f)).toArgb()
            canvas.drawText(g.ch, g.x, g.y, paint)
        }
        while (glyphs.size < 50) spawn(false)
    }
}

private fun Float.pow(e: Float) = Math.pow(this.toDouble(), e.toDouble()).toFloat()

// ---------- Nebula waves ----------

@Composable
private fun NebulaWaves(accent: AccentColor, time: State<Float>) {
    Canvas(Modifier.fillMaxSize()) {
        val ms = time.value * 1000f
        val baseAmp = size.height / 6f
        val step = max(5f, size.width / 100f)
        val waveCount = 3
        val scaleX = 1280f / size.width
        for (i in 0 until waveCount) {
            val amp = baseAmp * (1 - i * 0.2f) * 0.6f
            val freq = (0.005f + i * 0.002f) * scaleX
            val speed = 0.0015f * (i + 1) * SPEED * 0.5f
            val yOff = size.height * 0.5f + i * 20.dp.toPx()
            val path = Path()
            var x = 0f
            while (x <= size.width + step) {
                val y = sin(x * freq + ms * speed) * amp + yOff
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += step
            }
            drawPath(
                path,
                accent.value.copy(alpha = (OPACITY * 1.6f * (1 - i * 0.2f)).coerceIn(0f, 1f)),
                style = Stroke(width = (2f - i * 0.5f).coerceAtLeast(0.6f) * density, cap = StrokeCap.Round),
            )
        }
    }
}

// ---------- Nebula particles ----------

private class Dot(var x: Float, var y: Float, var vx: Float, var vy: Float, val size: Float, val alpha: Float)

@Composable
private fun NebulaParticles(accent: AccentColor, time: State<Float>) {
    val dots = remember { ArrayList<Dot>() }
    val last = remember { floatArrayOf(-1f) }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val dt = if (last[0] < 0) 0f else (t - last[0]).coerceIn(0f, 0.1f)
        last[0] = t
        if (dots.isEmpty()) repeat(45) {
            dots += Dot(
                Random.nextFloat() * size.width, Random.nextFloat() * size.height,
                (Random.nextFloat() - 0.5f) * 0.6f, (Random.nextFloat() - 0.5f) * 0.6f,
                (Random.nextFloat() * 4f + 1f) * density * 0.8f, Random.nextFloat() * 0.5f + 0.3f,
            )
        }
        val k = dt * 60f * SPEED
        for (d in dots) {
            d.x += d.vx * k * density
            d.y += d.vy * k * density
            if (d.x < -10) d.x = size.width + 10 else if (d.x > size.width + 10) d.x = -10f
            if (d.y < -10) d.y = size.height + 10 else if (d.y > size.height + 10) d.y = -10f
            val a = d.alpha * OPACITY * 2f
            if (d.size > 2.5f * density) {
                val glow = d.size * 4f
                drawCircle(
                    Brush.radialGradient(listOf(accent.value.copy(alpha = a * 0.5f), Color.Transparent), Offset(d.x, d.y), glow),
                    glow, Offset(d.x, d.y),
                )
            }
            drawCircle(accent.value.copy(alpha = a.coerceIn(0f, 1f)), d.size, Offset(d.x, d.y))
        }
        val maxD = 110.dp.toPx()
        for (i in dots.indices) for (j in i + 1 until dots.size) {
            val a = dots[i]
            val b = dots[j]
            val dx = a.x - b.x
            val dy = a.y - b.y
            val d2 = dx * dx + dy * dy
            if (d2 < maxD * maxD) {
                val f = 1f - kotlin.math.sqrt(d2) / maxD
                drawLine(accent.value.copy(alpha = OPACITY * 0.5f * f), Offset(a.x, a.y), Offset(b.x, b.y), 1f)
            }
        }
    }
}

// ---------- Nebula grid ----------

@Composable
private fun NebulaGrid(accent: AccentColor, time: State<Float>) {
    Canvas(Modifier.fillMaxSize()) {
        val ms = time.value * 1000f
        val g = 30.dp.toPx()
        val shift = (ms * 0.01f * SPEED * density) % g
        val cols = (size.width / g).toInt() + 2
        val rows = (size.height / g).toInt() + 2
        for (y in 0..rows) {
            val py = y * g - shift
            drawLine(accent.value.copy(alpha = OPACITY * (0.3f + 0.7f * sin(y * 0.1f + ms * 0.001f)).coerceAtLeast(0f)), Offset(0f, py), Offset(size.width, py), 1f)
        }
        for (x in 0..cols) {
            val px = x * g - shift
            drawLine(accent.value.copy(alpha = OPACITY * (0.3f + 0.7f * sin(x * 0.1f + ms * 0.001f)).coerceAtLeast(0f)), Offset(px, 0f), Offset(px, size.height), 1f)
        }
        for (y in 0..rows) for (x in 0..cols) {
            if ((x + y) % 3 != 0) continue
            val pulse = 0.5f + 0.5f * sin(x * 0.5f + y * 0.5f + ms * 0.003f * SPEED)
            drawCircle(accent.value.copy(alpha = OPACITY * pulse * 1.5f), (1f + pulse * 1.5f) * density, Offset(x * g - shift, y * g - shift))
        }
    }
}

// ---------- Nebula voxels ----------

private class Cube(var x: Float, var y: Float, val z: Float, val size: Float, var rx: Float, var ry: Float, var rz: Float, val sx: Float, val sy: Float, val sz: Float, val vy: Float)

private val CUBE_V = arrayOf(
    floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f), floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f),
    floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f),
)
private val CUBE_F = arrayOf(
    intArrayOf(0, 1, 2, 3), intArrayOf(4, 5, 6, 7), intArrayOf(0, 1, 5, 4),
    intArrayOf(2, 3, 7, 6), intArrayOf(0, 3, 7, 4), intArrayOf(1, 2, 6, 5),
)

@Composable
private fun NebulaVoxels(accent: AccentColor, time: State<Float>) {
    val cubes = remember { ArrayList<Cube>() }
    val last = remember { floatArrayOf(-1f) }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val dt = if (last[0] < 0) 0f else (t - last[0]).coerceIn(0f, 0.1f)
        last[0] = t
        if (cubes.isEmpty()) repeat(22) {
            cubes += Cube(
                Random.nextFloat() * size.width, Random.nextFloat() * size.height, Random.nextFloat(),
                (Random.nextFloat() * 30f + 10f) * density * 0.7f,
                Random.nextFloat() * 6.28f, Random.nextFloat() * 6.28f, Random.nextFloat() * 6.28f,
                (Random.nextFloat() - 0.5f) * 0.02f, (Random.nextFloat() - 0.5f) * 0.02f, (Random.nextFloat() - 0.5f) * 0.02f,
                -(Random.nextFloat() * 0.3f + 0.1f),
            )
        }
        val k = dt * 60f * SPEED
        val pts = Array(8) { FloatArray(3) }
        for (c in cubes.sortedBy { it.z }) {
            c.rx = (c.rx + c.sx * k) % 6.2832f
            c.ry = (c.ry + c.sy * k) % 6.2832f
            c.rz = (c.rz + c.sz * k) % 6.2832f
            c.y += c.vy * k * density
            if (c.y < -c.size * 2) { c.y = size.height + c.size * 2; c.x = Random.nextFloat() * size.width }
            val cx = cos(c.rx); val sx = sin(c.rx); val cy = cos(c.ry); val sy = sin(c.ry); val cz = cos(c.rz); val sz = sin(c.rz)
            for (i in 0 until 8) {
                var x = CUBE_V[i][0] * c.size / 2; var y = CUBE_V[i][1] * c.size / 2; var z = CUBE_V[i][2] * c.size / 2
                val y1 = y * cx - z * sx; val z1 = y * sx + z * cx; y = y1; z = z1
                val x2 = x * cy + z * sy; val z2 = -x * sy + z * cy; x = x2; z = z2
                val x3 = x * cz - y * sz; val y3 = x * sz + y * cz
                pts[i][0] = c.x + x3; pts[i][1] = c.y + y3; pts[i][2] = z
            }
            val faceA = OPACITY * (0.15f + c.z * 0.25f)
            for (f in CUBE_F) {
                val p = Path().apply {
                    moveTo(pts[f[0]][0], pts[f[0]][1])
                    for (n in 1 until 4) lineTo(pts[f[n]][0], pts[f[n]][1])
                    close()
                }
                drawPath(p, accent.value.copy(alpha = faceA * 0.5f))
                drawPath(p, accent.value.copy(alpha = (faceA * 1.5f).coerceAtMost(1f)), style = Stroke(1f))
            }
        }
    }
}

// ---------- OG balls ----------

@Composable
private fun OgBalls(accent: AccentColor, time: State<Float>, center: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val ball = size.width * if (center) 1.0f else 1.1f
        fun orbit(period: Float, dir: Float, dx: Float, dy: Float, scaleAmp: Float): Triple<Float, Float, Float> {
            val p = ((t * SPEED) % period) / period
            val a = 2f * PI.toFloat() * p
            return Triple(sin(a) * dx * dir, (1 - cos(a)) / 2f * dy * dir, 1f + sin(a * 2 - PI.toFloat() / 2) * 0 + (1 - cos(a)) / 2f * scaleAmp)
        }
        val (lx, ly, ls) = orbit(16f, 1f, 0.06f * ball, 0.08f * ball, 0.1f)
        val lc = Offset(size.width * -0.12f + ball / 2 + lx, size.height * -0.08f + ball / 2 + ly)
        val lr = ball / 2 * ls
        drawCircle(
            Brush.radialGradient(
                0f to accent.light.copy(alpha = 0.35f), 0.3f to accent.value.copy(alpha = 0.3f), 0.6f to Color.Transparent,
                center = lc + Offset(-lr * 0.4f, -lr * 0.4f), radius = lr * 1.25f,
            ),
            lr * 1.25f, lc, blendMode = BlendMode.Screen,
        )
        val (rx, ry, rs) = orbit(20f, -1f, 0.05f * ball, 0.1f * ball, 0.08f)
        val rc = Offset(size.width * 1.1f - ball / 2 + rx, size.height * 1.12f - ball / 2 + ry)
        val rr = ball / 2 * rs
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFF2752C1).copy(alpha = 0.35f), 0.25f to Color(0xE62752C1).copy(alpha = 0.3f), 0.6f to Color.Transparent,
                center = rc + Offset(rr * 0.2f, rr * 0.2f), radius = rr * 1.25f,
            ),
            rr * 1.25f, rc, blendMode = BlendMode.Screen,
        )
        if (center) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.width * 0.8f
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0x1F4BE08C), 0.3f to Color(0x1F2752C1), 0.6f to Color.Transparent, center = c, radius = r,
                ),
                r, c, blendMode = BlendMode.Screen,
            )
        }
    }
}

// ---------- Shader effects (AGSL port of the desktop GLSL) ----------

private enum class ShaderKind { Chrome, Lightning }

private const val CHROME_SRC = """
uniform float2 uRes;
uniform float uTime;
uniform float3 uBase;
uniform float uAmp;
uniform float uFx;
uniform float uFy;
uniform float uOpacity;
half4 main(float2 fc) {
    float2 uv = (2.0 * fc - uRes) / min(uRes.x, uRes.y);
    for (float i = 1.0; i < 6.0; i += 1.0) {
        uv.x += uAmp / i * cos(i * uFx * uv.y + uTime);
        uv.y += uAmp / i * cos(i * uFy * uv.x + uTime);
    }
    float3 c = uBase / abs(sin(uTime - uv.y - uv.x));
    c = clamp(c, 0.0, 1.0);
    return half4(half3(c * uOpacity), half(uOpacity));
}
"""

private const val LIGHTNING_SRC = """
uniform float2 uRes;
uniform float uTime;
uniform float uHue;
uniform float uSpeed;
uniform float uIntensity;
uniform float uSize;
uniform float uOpacity;
float3 hsv2rgb(float3 c) {
    float3 rgb = clamp(abs(mod(c.x * 6.0 + float3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return c.z * mix(float3(1.0), rgb, c.y);
}
float hash11(float p) { p = fract(p * .1031); p *= p + 33.33; p *= p + p; return fract(p); }
float hash12(float2 p) { float3 p3 = fract(float3(p.xyx) * .1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
float noise(float2 p) {
    float2 ip = floor(p); float2 fp = fract(p);
    float a = hash12(ip); float b = hash12(ip + float2(1.0, 0.0));
    float c = hash12(ip + float2(0.0, 1.0)); float d = hash12(ip + float2(1.0, 1.0));
    float2 t = smoothstep(0.0, 1.0, fp);
    return mix(mix(a, b, t.x), mix(c, d, t.x), t.y);
}
float fbm(float2 p) {
    float v = 0.0; float amp = 0.5;
    float cr = cos(0.45); float sr = sin(0.45);
    for (int i = 0; i < 10; i++) {
        v += amp * noise(p);
        p = float2(cr * p.x - sr * p.y, sr * p.x + cr * p.y) * 2.0;
        amp *= 0.5;
    }
    return v;
}
half4 main(float2 fc) {
    float2 uv = fc / uRes;
    uv = 2.0 * uv - 1.0;
    uv.y = -uv.y;
    uv.x *= uRes.x / uRes.y;
    uv += 2.0 * fbm(uv * uSize + 0.8 * uTime * uSpeed) - 1.0;
    float dist = abs(uv.x);
    float3 base = hsv2rgb(float3(uHue / 360.0, 0.7, 0.8));
    float3 col = base * (mix(0.0, 0.07, hash11(uTime * uSpeed)) / dist) * uIntensity;
    col = clamp(col, 0.0, 1.0);
    float a = clamp(max(col.r, max(col.g, col.b)), 0.0, 1.0) * uOpacity;
    return half4(half3(col * uOpacity), half(a));
}
"""

@Composable
private fun ShaderEffect(kind: ShaderKind, accent: AccentColor, time: State<Float>) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        OgBalls(accent, time, center = kind == ShaderKind.Chrome)
        return
    }
    ShaderCanvas(kind, accent, time)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderCanvas(kind: ShaderKind, accent: AccentColor, time: State<Float>) {
    val shader = remember(kind) { RuntimeShader(if (kind == ShaderKind.Chrome) CHROME_SRC else LIGHTNING_SRC) }
    val brush = remember(shader) { ShaderBrush(shader) }
    val hue = remember(accent) { hueOf(accent.value) }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        shader.setFloatUniform("uRes", size.width, size.height)
        if (kind == ShaderKind.Chrome) {
            shader.setFloatUniform("uTime", t * 0.2f * 0.5f * 2f)
            shader.setFloatUniform("uBase", accent.value.red, accent.value.green, accent.value.blue)
            shader.setFloatUniform("uAmp", 0.25f)
            shader.setFloatUniform("uFx", 3f)
            shader.setFloatUniform("uFy", 2f)
            shader.setFloatUniform("uOpacity", 0.6f)
        } else {
            shader.setFloatUniform("uTime", t)
            shader.setFloatUniform("uHue", hue)
            shader.setFloatUniform("uSpeed", 1f)
            shader.setFloatUniform("uIntensity", 1.2f)
            shader.setFloatUniform("uSize", 1.5f)
            shader.setFloatUniform("uOpacity", 0.6f)
        }
        drawRect(brush, size = Size(size.width, size.height))
    }
}

private fun hueOf(c: Color): Float {
    val r = c.red; val g = c.green; val b = c.blue
    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
    if (mx == mn) return 0f
    val d = mx - mn
    val h = when (mx) {
        r -> ((g - b) / d + (if (g < b) 6 else 0))
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    }
    return h * 60f
}