package app.enso.meditation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import app.enso.meditation.ui.theme.Ink
import app.enso.meditation.ui.theme.Paper
import kotlin.math.*
import kotlin.random.Random

/** Original procedural fallback artwork. Replace this composable with a transparent Enso asset. */
@Composable
fun InkCircle(modifier: Modifier = Modifier, paused: Boolean = false) {
    Canvas(modifier.drawWithCache {
        val random = Random(108)
        val scale = size.minDimension / 360f
        val center = Offset(size.width / 2, size.height / 2)
        val paths = List(105) { bristle ->
            val radial = (bristle - 52) / 52f
            val phase = random.nextFloat() * 6f
            val end = 326 + random.nextInt(0, 19)
            val start = random.nextInt(0, 12)
            val path = Path()
            for (degree in start..end) {
                val angle = (degree - 71) * PI / 180
                val fullness = (sin((degree + 18) * PI / 390) * .7 + .3).toFloat()
                val radius = (142 + radial * 16 * fullness +
                    sin(angle * 3 + .7).toFloat() * 3.8f +
                    sin(angle * 7 + phase).toFloat() * .9f) * scale
                val x = center.x + cos(angle).toFloat() * radius
                val y = center.y + sin(angle).toFloat() * radius * 1.025f
                if (degree == start) path.moveTo(x, y) else path.lineTo(x, y)
            }
            Triple(path, (.35f + random.nextFloat() * .62f) * scale,
                if (bristle % 9 == 0) .08f else .28f + random.nextFloat() * .44f)
        }
        onDrawBehind {
            paths.forEach { (path, width, alpha) ->
                drawPath(path, Ink.copy(alpha = alpha * if (paused) .65f else 1f),
                    style = Stroke(width, cap = StrokeCap.Round))
            }
        }
    }) { }
}

/** Seeded fibers and a distant wash stay static across timer recompositions. */
@Composable
fun WashiBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.drawWithCache {
        val random = Random(42)
        val fibers = List(1800) {
            Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
        }
        val hills = List(3) { layer ->
            Path().apply {
                val base = size.height * (.92f + layer * .025f)
                moveTo(size.width * .35f, size.height)
                lineTo(size.width * .35f, base)
                cubicTo(size.width * .53f, base + 15, size.width * .57f, base - 52 - layer * 14, size.width * .69f, base - 28)
                cubicTo(size.width * .81f, base - 9, size.width * .86f, base - 100 + layer * 15, size.width, base - 62)
                lineTo(size.width, size.height)
                close()
            }
        }
        onDrawBehind {
            drawRect(Paper)
            fibers.forEachIndexed { index, point ->
                drawLine(Ink.copy(alpha = if (index % 3 == 0) .035f else .018f), point,
                    point + Offset(if (index % 2 == 0) 2.8f else .7f, 1.6f), strokeWidth = .7f)
            }
            hills.forEach { drawPath(it, Ink.copy(alpha = .027f)) }
        }
    }) { }
}
