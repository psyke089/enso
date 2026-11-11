package app.enso.meditation.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import app.enso.meditation.ui.theme.Ink
import kotlin.math.*

/** Small mottled nib impressions, not a whole stroke stretched to fit the input. */
internal class SumiBrush {
    private val tips = List(4) { variant ->
        val side = 128
        val pixels = IntArray(side * side)
        for (y in 0 until side) for (x in 0 until side) {
            val across = (x - 63.5f) / 63.5f
            val along = (y - 63.5f) / 63.5f
            val edge = 1f - across * across - along * along
            val fibers = grain(x / 3.7f, y / 19f, variant + 17)
            val flecks = grain(x / 1.1f, y / 2.2f, variant + 51)
            val pooling = grain(x / 17f, y / 23f, variant + 81)
            val coverage = ((edge + (fibers - .5f) * .3f) * 5f).coerceIn(0f, 1f)
            val alpha = (coverage * (.4f + .35f * fibers + .25f * pooling) *
                (.6f + .4f * flecks) * 255).roundToInt().coerceIn(0, 255)
            pixels[y * side + x] = (alpha shl 24) or 0x00211F1B
        }
        Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()

    fun draw(scope: DrawScope, stroke: SumiStroke) = with(scope) {
        @Suppress("UNUSED_VARIABLE") val revision = stroke.revision
        drawIntoCanvas { wrapper ->
            val canvas = wrapper.nativeCanvas
            for (dab in stroke.dabs) {
                paint.alpha = (dab.opacity * 255).roundToInt().coerceIn(0, 255)
                destination.set(-dab.width / 2, -dab.length / 2, dab.width / 2, dab.length / 2)
                canvas.save()
                canvas.translate(dab.center.x, dab.center.y)
                canvas.rotate(dab.angle)
                canvas.drawBitmap(tips[dab.tip], null, destination, paint)
                canvas.restore()
            }
        }
        for (i in stroke.hairs.indices) {
            drawPath(stroke.hairs[i], Ink.copy(alpha = ((.55f + (i / 3) * .075f) * stroke.settings.flow).coerceAtMost(1f)),
                style = Stroke((.35f + (i % 3) * .28f) * stroke.density, cap = StrokeCap.Round))
        }
    }
}

internal data class InkDab(val center: Offset, val width: Float, val length: Float,
    val angle: Float, val opacity: Float, val tip: Int)

/**
 * Fixed-distance deposits and damped individual bristles. Only new marks are appended, so
 * neither texture nor ink depletion shifts backwards when the finger extends the stroke.
 * Speed supplies a gentle pressure proxy for fingers; no pressure-capable hardware is needed.
 */
internal class SumiStroke(val density: Float, private val inkDistance: Float, private val seed: Int,
    val settings: BrushSettings = BrushSettings()) {
    val dabs = ArrayList<InkDab>()
    val hairs = List(18) { Path() }
    var revision by mutableIntStateOf(0)
        private set
    private val bristles = List(settings.bristles) { index ->
        // Uneven small bundles leave channels between hairs, rather than a comb pattern.
        val bundle = index / 6
        val center = (bundle + random(bundle, seed + 13) * .9f) / (settings.bristles / 12f) - 1f
        Bristle(fraction = (center + (index % 6 - 2.5f) * .016f).coerceIn(-1f, 1f),
            drag = (2f + random(index, seed + 7) * 7f) * density,
            size = index % 3, load = .65f + random(bundle, seed + 19) * .6f)
    }
    private var lastRaw: Offset? = null
    private var filtered = Offset.Zero
    private var deposited = Offset.Zero
    private var lastTime = 0L
    private var distance = 0f
    private var radius = 19f * density * settings.width / 46f
    private var direction = Offset(0f, 1f)
    private var count = 0
    private var finished = false

    fun append(raw: Offset, time: Long) {
        if (finished || !raw.x.isFinite() || !raw.y.isFinite()) return
        val previous = lastRaw
        if (previous == null) {
            lastRaw = raw
            filtered = raw
            deposited = raw
            lastTime = time
            // A light touch-down mark; the first moving sample establishes nib orientation.
            dabs += InkDab(raw, radius * .72f, radius * .68f, 0f, (.45f * settings.flow).coerceAtMost(1f), seed % 4)
            revision++
            return
        }
        val delta = (raw - previous).getDistance()
        if (delta < .35f * density) return
        val speed = delta / (time - lastTime).coerceAtLeast(1) / density
        val targetRadius = (23f - speed * 4f * settings.speed).coerceIn(13f, 23f) * density * settings.width / 46f
        radius += (targetRadius - radius) * .22f
        // Exponential filtering in travelled distance avoids dependence on event frequency.
        filtered += (raw - filtered) * if (settings.smoothing == 0f) 1f else
            (1f - exp(-delta / (settings.smoothing * density)))
        depositTo(filtered)
        lastRaw = raw
        lastTime = time
        revision++
    }

    private fun depositTo(target: Offset) {
        val step = 1.25f * density
        var delta = target - deposited
        var length = delta.getDistance()
        while (length >= step) {
            val tangent = delta / length
            deposited += tangent * step
            distance += step
            deposit(deposited, tangent, 1f)
            delta = target - deposited
            length = delta.getDistance()
        }
    }

    private fun deposit(center: Offset, tangent: Offset, lift: Float) {
        direction = tangent
        val normal = Offset(-tangent.y, tangent.x)
        val reach = (inkDistance * settings.inkReach).coerceAtLeast(density)
        val wet = exp(-distance / reach * 1.8f)
        val spread = (1f + (1f - wet) * .16f) * lift
        val head = (.4f + distance / (16f * density)).coerceAtMost(1f)
        val uneven = .93f + grain(distance / (18f * density), 0f, seed + 93) * .14f
        val width = radius * spread * head * uneven
        val angle = Math.toDegrees(atan2(tangent.y, tangent.x).toDouble()).toFloat() - 90f
        dabs += InkDab(center, width * 2, radius * .8f * lift,
            angle + (grain(distance / (21f * density), 0f, seed) - .5f) * 8f,
            (.8f * wet.pow(2.2f) * lift * settings.flow).coerceAtMost(1f), (count + seed) % 4)
        for ((index, bristle) in bristles.withIndex()) {
            val wander = (grain(distance / (24f * density), index.toFloat(), seed + 31) - .5f) * .08f
            val target = center + normal * (width * (bristle.fraction + wander)) - tangent * (bristle.drag * settings.drag)
            val previous = bristle.position
            val next = previous?.let { it + (target - it) *
                (1f - exp(-1.25f * density / (bristle.drag * settings.drag * .5f).coerceAtLeast(.01f * density))) } ?: target
            val edgeDryness = abs(bristle.fraction).pow(2f)
            val remaining = (1f - distance / reach * bristle.load).coerceIn(0f, 1f)
            val ink = remaining * (1f - .32f * edgeDryness)
            val paper = grain(distance / (4.5f * density), index * .73f, seed + 61)
            val contact = (ink * .8f + .16f - paper * (.25f + edgeDryness * .4f)) * lift
            if (previous != null && contact > .07f) {
                val opacity = ((contact * 5 + .5f).toInt()).coerceIn(0, 5)
                hairs[opacity * 3 + bristle.size].apply {
                    moveTo(previous.x, previous.y)
                    lineTo(next.x, next.y)
                }
            }
            bristle.position = next
        }
        count++
    }

    fun finish() {
        if (finished) return
        if (count > 0) {
            // Lift the nib over a few pixels, letting lagging bristles converge naturally.
            for (i in 1..8) {
                deposited += direction * (.75f * density)
                distance += .75f * density
                deposit(deposited, direction, 1f - i / 9f)
            }
        }
        finished = true
        revision++
    }

    private class Bristle(val fraction: Float, val drag: Float, val size: Int, val load: Float,
        var position: Offset? = null)
}

/** Smooth, seeded paper variation: reproducible and independent of redraws. */
private fun grain(x: Float, y: Float, seed: Int): Float {
    val ix = floor(x).toInt()
    val iy = floor(y).toInt()
    val fx = x - ix
    val fy = y - iy
    val tx = fx * fx * (3 - 2 * fx)
    val ty = fy * fy * (3 - 2 * fy)
    fun value(dx: Int, dy: Int) = random((ix + dx) * 7349 xor ((iy + dy) * 1931), seed)
    val top = value(0, 0) * (1 - tx) + value(1, 0) * tx
    val bottom = value(0, 1) * (1 - tx) + value(1, 1) * tx
    return top * (1 - ty) + bottom * ty
}

private fun random(index: Int, seed: Int): Float {
    var bits = index * 73856093 xor (seed * 19349663)
    bits = (bits xor (bits ushr 13)) * 1274126177
    return ((bits ushr 8) and 65535) / 65535f
}

/** Render just the ink. Paper, guide and controls never become part of the saved artwork. */
internal fun renderSumiImage(strokes: List<SumiStroke>, side: Int, density: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val brush = SumiBrush()
    CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()),
        Size(side.toFloat(), side.toFloat())) {
        for (stroke in strokes) brush.draw(this, stroke)
    }
    return bitmap
}
