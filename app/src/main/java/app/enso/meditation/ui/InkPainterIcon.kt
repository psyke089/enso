package app.enso.meditation.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.enso.meditation.R
import kotlin.math.*

internal enum class PainterSymbol { Close, Brush, Apply, Trash }

/** Small calligraphic controls, deposited with the same textured nib as the drawing tool. */
@Composable
internal fun InkPainterIcon(symbol: PainterSymbol, label: String, enabled: Boolean = true,
    onClick: () -> Unit) {
    val context = LocalContext.current
    val image = remember(symbol) {
        if (symbol == PainterSymbol.Brush) return@remember texturedBrushSettings(context).asImageBitmap()
        val strokes = mutableListOf<SumiStroke>()
        fun line(width: Float, points: List<Offset>) {
            val stroke = SumiStroke(1f, 1000f, strokes.size + 31,
                BrushSettings(width = width, flow = 1.65f, inkReach = 3f, bristles = 54,
                    drag = .12f, speed = 0f, smoothing = .3f))
            var time = 0L
            points.forEachIndexed { i, point ->
                if (i == 0) stroke.append(point, time) else {
                    val start = points[i - 1]
                    val steps = ceil((point - start).getDistance() / 1.4f).toInt().coerceAtLeast(1)
                    for (step in 1..steps) {
                        time += 12
                        stroke.append(start + (point - start) * (step.toFloat() / steps), time)
                    }
                }
            }
            stroke.finish()
            strokes += stroke
        }
        fun p(x: Int, y: Int) = Offset(x.toFloat(), y.toFloat())
        when (symbol) {
            PainterSymbol.Close -> {
                line(9f, listOf(p(34, 33), p(63, 64), p(92, 95)))
                line(8f, listOf(p(94, 32), p(64, 65), p(33, 94)))
            }
            PainterSymbol.Brush -> Unit
            PainterSymbol.Apply -> line(13f, listOf(p(24, 64), p(49, 89), p(99, 34)))
            PainterSymbol.Trash -> {
                line(8f, listOf(p(29, 40), p(100, 40)))
                line(8f, listOf(p(48, 29), p(78, 29)))
                line(8f, listOf(p(36, 49), p(41, 101), p(88, 101), p(93, 49)))
                line(6f, listOf(p(56, 53), p(58, 88)))
                line(6f, listOf(p(75, 53), p(73, 88)))
            }
        }
        renderSumiImage(strokes, 128, 1f).asImageBitmap()
    }
    Box(Modifier.size(48.dp).clip(CircleShape)
        .clickable(enabled = enabled, role = Role.Button,
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Image(image, contentDescription = null, alpha = if (enabled) .88f else .25f,
            modifier = Modifier.size(38.dp))
    }
}

/** Keep the supplied vector silhouette, but deposit it in the same warm, mottled ink. */
private fun texturedBrushSettings(context: Context): Bitmap {
    val side = 256
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val drawable = checkNotNull(ContextCompat.getDrawable(context, R.drawable.ink_brush_settings)).mutate()
    drawable.setBounds(0, 0, side, side)
    drawable.draw(android.graphics.Canvas(bitmap))
    val pixels = IntArray(side * side)
    bitmap.getPixels(pixels, 0, side, 0, 0, side, side)
    for (y in 0 until side) for (x in 0 until side) {
        val index = y * side + x
        val alpha = pixels[index] ushr 24
        if (alpha == 0) continue
        val wash = sumiGrain(x / 21f, y / 29f, 17)
        val fibers = sumiGrain((x + y * .27f) / 2.1f, y / 13f, 51)
        val pores = sumiGrain(x / 1.15f, y / 1.15f, 81)
        val coverage = (.68f + wash * .2f + fibers * .12f) * (.82f + pores * .18f)
        pixels[index] = ((alpha * coverage).roundToInt().coerceIn(0, 255) shl 24) or 0x00292823
    }
    bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
    return bitmap
}
