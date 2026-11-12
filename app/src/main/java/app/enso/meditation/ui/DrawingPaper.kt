package app.enso.meditation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Decorative practice paper only: neither its fibers nor its guide enter the exported ink. */
@Composable
internal fun DrawingPaper(modifier: Modifier, guideCenter: Offset, guideRadius: Float,
    guidePresence: () -> Float) {
    Box(modifier.drawWithCache {
        val random = Random(73)
        val edgeDepth = 2.8.dp.toPx()
        val step = 3.dp.toPx()
        val steps = (size.width / step).toInt().coerceAtLeast(1)
        val sheet = Path().apply {
            moveTo(0f, edgeDepth * .5f)
            for (i in 1..steps) lineTo(size.width * i / steps, random.nextFloat() * edgeDepth)
            lineTo(size.width, size.height - edgeDepth * .5f)
            for (i in steps - 1 downTo 0) {
                lineTo(size.width * i / steps, size.height - random.nextFloat() * edgeDepth)
            }
            close()
        }
        val fibers = Path().apply {
            repeat((size.width * size.height / (220.dp.toPx() * 1.dp.toPx())).toInt().coerceAtMost(1800)) {
                val x = random.nextFloat() * size.width
                val y = edgeDepth + random.nextFloat() * (size.height - 2 * edgeDepth).coerceAtLeast(0f)
                val length = (2f + random.nextFloat() * 9f).dp.toPx()
                moveTo(x, y)
                quadraticTo(x + length * .45f, y - .6.dp.toPx(), x + length, y + .35.dp.toPx())
            }
        }
        // Broken, softly irregular arcs suggest a pressure mark, not a technical template.
        val guide = Path().apply {
            for (section in 0..3) {
                for (i in 0..80) {
                    val degrees = section * 90f + 5f + i
                    val angle = degrees * PI.toFloat() / 180f
                    val radius = guideRadius + .45.dp.toPx() * sin(angle * 5f)
                    val point = guideCenter + Offset(cos(angle), sin(angle)) * radius
                    if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            }
        }
        val paper = Color(0xFFFFF7E5)
        val umber = Color(0xFF81715A)
        val edgeStroke = Stroke(.65.dp.toPx())
        val fiberStroke = Stroke(.45.dp.toPx(), cap = StrokeCap.Round)
        val softGuide = Stroke(2.8.dp.toPx(), cap = StrokeCap.Round)
        val fineGuide = Stroke(.7.dp.toPx(), cap = StrokeCap.Round)
        onDrawBehind {
            // Several translucent impressions give the deckle a little depth without a card shadow.
            translate(top = 2.dp.toPx()) { drawPath(sheet, umber.copy(alpha = .045f)) }
            translate(top = 1.dp.toPx()) { drawPath(sheet, umber.copy(alpha = .06f)) }
            drawPath(sheet, paper.copy(alpha = .65f))
            drawPath(sheet, paper.copy(alpha = .8f), style = edgeStroke)
            drawPath(fibers, umber.copy(alpha = .065f), style = fiberStroke)
            val presence = guidePresence()
            drawPath(guide, umber.copy(alpha = .035f * presence), style = softGuide)
            translate(top = .7.dp.toPx()) {
                drawPath(guide, Color.White.copy(alpha = .55f * presence), style = fineGuide)
            }
            drawPath(guide, umber.copy(alpha = .24f * presence), style = fineGuide)
        }
    })
}
