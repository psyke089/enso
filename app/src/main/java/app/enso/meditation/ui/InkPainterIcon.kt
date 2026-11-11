package app.enso.meditation.ui

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
import kotlin.math.*

internal enum class PainterSymbol { Close, Brush, Apply, Standard }

/** Small calligraphic controls, deposited with the same textured nib as the drawing tool. */
@Composable
internal fun InkPainterIcon(symbol: PainterSymbol, label: String, enabled: Boolean = true,
    onClick: () -> Unit) {
    val image = remember(symbol) {
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
            PainterSymbol.Brush -> {
                line(10f, listOf(p(57, 63), p(75, 44), p(96, 20)))
                line(19f, listOf(p(52, 71), p(45, 82), p(34, 95), p(23, 101)))
                line(12f, listOf(p(60, 71), p(59, 83), p(47, 96), p(27, 102)))
            }
            PainterSymbol.Apply -> line(13f, listOf(p(24, 64), p(49, 89), p(99, 34)))
            PainterSymbol.Standard -> {
                val ring = (110..425 step 5).map { degrees ->
                    val angle = Math.toRadians(degrees.toDouble())
                    Offset(64f + cos(angle).toFloat() * 34f, 64f + sin(angle).toFloat() * 34f)
                }
                line(11f, ring)
                line(7f, listOf(p(90, 88), p(98, 100), p(109, 86)))
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
