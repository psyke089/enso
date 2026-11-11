package app.enso.meditation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
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
    val image = remember(symbol) {
        if (symbol == PainterSymbol.Brush) return@remember null
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
        if (symbol == PainterSymbol.Brush) {
            Box(Modifier.size(38.dp).graphicsLayer { alpha = if (enabled) .88f else .25f }) {
                BrushMark(Modifier.size(27.dp).align(Alignment.TopStart))
                Image(painterResource(R.drawable.ink_settings), contentDescription = null,
                    modifier = Modifier.size(24.dp).align(Alignment.BottomEnd))
            }
        } else image?.let {
            Image(it, contentDescription = null, alpha = if (enabled) .88f else .25f,
                modifier = Modifier.size(38.dp))
        }
    }
}
