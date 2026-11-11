package app.enso.meditation.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enso.meditation.R
import app.enso.meditation.ui.theme.FadedInk
import app.enso.meditation.ui.theme.Ink
import app.enso.meditation.ui.theme.Paper
import kotlin.math.*

/** An ephemeral experiment: no settings, files, timer callbacks, or saved-state drawing. */
@Composable
internal fun EnsoPainter(onClose: () -> Unit) {
    val strokes = remember { mutableStateListOf<InkStroke>() }
    val density = LocalDensity.current.density
    val resources = LocalContext.current.resources
    val brush = remember(resources) { unwrapEnsoInk(resources) }
    val inkPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 235 } }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Paper).pointerInput(Unit) {
        // Own hit testing even on the margins; touches must not reach the timer underneath.
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed })
        }
    }) {
        WashiBackground(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(48.dp).clip(CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = onClose)
                    .semantics { contentDescription = "Close painting" }, contentAlignment = Alignment.Center) {
                    Text("×", color = FadedInk, fontSize = 28.sp)
                }
                InkAction("Reset painting", R.drawable.ink_reset, enabled = strokes.isNotEmpty(),
                    secondary = true) { strokes.clear() }
            }
            Canvas(Modifier.weight(1f).fillMaxWidth().padding(12.dp).clipToBounds()
                .semantics {
                    contentDescription = "Enso painting canvas"
                    stateDescription = when (strokes.size) {
                        0 -> "Empty canvas"
                        1 -> "1 brush stroke"
                        else -> "${strokes.size} brush strokes"
                    }
                }
                .pointerInput(density) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val stroke = InkStroke(density)
                        strokes += stroke
                        stroke.append(down.position, down.uptimeMillis)
                        stroke.updateShape()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                                for (sample in change.historical) stroke.append(sample.position, sample.uptimeMillis)
                                stroke.append(change.position, change.uptimeMillis)
                                stroke.updateShape()
                                change.consume()
                                if (!change.pressed) break
                            }
                        } finally { stroke.finish() }
                    }
                }) {
                // A quiet pencil guide, underneath the ink and independent of the drawn shape.
                drawCircle(FadedInk.copy(alpha = .15f), radius = size.minDimension * .34f,
                    style = Stroke(.7.dp.toPx()))
                for (stroke in strokes) {
                    val shape = stroke.shape
                    if (shape.segments == 0) {
                        shape.dab?.let { drawCircle(Ink.copy(alpha = .88f), it.radius * .4f, it.position) }
                    } else drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawBitmapMesh(brush, 1, shape.segments,
                            shape.vertices, 0, null, 0, inkPaint)
                    }
                }
            }
        }
    }
}

/** A small brush silhouette drawn in the same editable canvas language as the experiment. */
@Composable
internal fun InkPaintButton(onClick: () -> Unit) {
    Canvas(Modifier.size(44.dp).clip(CircleShape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClick = onClick).semantics { contentDescription = "Paint an Enso" }.padding(10.dp)) {
        val w = size.width
        val h = size.height
        drawPath(Path().apply {
            moveTo(w * .43f, h * .51f)
            cubicTo(w * .52f, h * .36f, w * .82f, h * .05f, w * .9f, h * .08f)
            cubicTo(w * .97f, h * .12f, w * .7f, h * .5f, w * .57f, h * .62f)
            close()
        }, Ink.copy(alpha = .7f))
        drawPath(Path().apply {
            moveTo(w * .44f, h * .55f)
            cubicTo(w * .17f, h * .58f, w * .36f, h * .83f, w * .08f, h * .9f)
            cubicTo(w * .4f, h * .99f, w * .68f, h * .81f, w * .55f, h * .66f)
            close()
        }, Ink.copy(alpha = .8f))
        drawLine(Paper.copy(alpha = .5f), Offset(w * .21f, h * .88f), Offset(w * .46f, h * .69f),
            .6.dp.toPx(), cap = StrokeCap.Round)
    }
}

/**
 * Unroll the existing timer artwork into a brush ribbon (across the nib × along the stroke).
 * Its real translucent grain and split bristles survive the warp, including the dry trailing end.
 * This small in-memory texture is shared by all strokes; no generated asset or draft is saved.
 */
private fun unwrapEnsoInk(resources: Resources): Bitmap {
    val source = BitmapFactory.decodeResource(resources, R.drawable.enso_brush,
        BitmapFactory.Options().apply { inScaled = false })
    val sourcePixels = IntArray(source.width * source.height)
    source.getPixels(sourcePixels, 0, source.width, 0, 0, source.width, source.height)
    val width = 256
    val height = 1536
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        // Start at the loaded brush heel; follow the artwork around to the dry hairs.
        val angle = Math.toRadians(82.0 + 346.0 * y / (height - 1))
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        for (x in 0 until width) {
            val radius = source.width * (.23f + .26f * x / (width - 1))
            val sx = (source.width * .5f + radius * dx).roundToInt().coerceIn(0, source.width - 1)
            val sy = (source.height * .5f + radius * dy).roundToInt().coerceIn(0, source.height - 1)
            pixels[y * width + x] = sourcePixels[sy * source.width + sx]
        }
    }
    source.recycle()
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private data class BrushPoint(val position: Offset, val radius: Float)
private data class BrushShape(
    val vertices: FloatArray = FloatArray(0),
    val segments: Int = 0,
    val dab: BrushPoint? = null,
)

/** Rebuild only the active stroke; previously painted strokes keep their cached mesh. */
private class InkStroke(private val density: Float) {
    private val points = mutableListOf<BrushPoint>()
    private var lastTime = 0L
    private var lastRaw = Offset.Zero
    var shape by mutableStateOf(BrushShape())
        private set

    fun append(raw: Offset, time: Long) {
        val previous = points.lastOrNull()
        if (previous != null && (raw - lastRaw).getDistance() < density * 1.5f) return
        val position = previous?.let { it.position + (raw - it.position) * .58f } ?: raw
        val speed = if (previous == null) 0f else (raw - lastRaw).getDistance() /
            (time - lastTime).coerceAtLeast(1) / density
        // The ribbon also includes transparent space around the irregular ink edges.
        val targetRadius = (32f - speed * 5f).coerceIn(23f, 32f) * density
        val radius = previous?.let { it.radius * .7f + targetRadius * .3f } ?: targetRadius
        points += BrushPoint(position, radius)
        lastRaw = raw
        lastTime = time
    }

    fun updateShape() { rebuild(finished = false) }

    fun finish() { rebuild(finished = true) }

    private fun rebuild(finished: Boolean) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            shape = BrushShape(dab = points.first())
            return
        }
        // Quadratic midpoint interpolation rounds corners without forcing a perfect circle.
        val smooth = mutableListOf(points.first())
        for (i in 1 until points.size) {
            val control = points[i - 1]
            val start = smooth.last()
            val end = if (i == points.lastIndex) points[i] else BrushPoint(
                (points[i - 1].position + points[i].position) * .5f,
                (points[i - 1].radius + points[i].radius) * .5f)
            val divisions = ((end.position - start.position).getDistance() / (2f * density)).toInt().coerceIn(2, 32)
            for (step in 1..divisions) {
                val t = step.toFloat() / divisions
                val u = 1 - t
                smooth += BrushPoint(start.position * (u * u) + control.position * (2 * u * t) + end.position * (t * t),
                    start.radius * u + end.radius * t)
            }
        }
        val distances = FloatArray(smooth.size)
        for (i in 1 until smooth.size) distances[i] = distances[i - 1] +
            (smooth[i].position - smooth[i - 1].position).getDistance()
        val total = distances.last()
        if (total < density) {
            shape = BrushShape(dab = points.first())
            return
        }
        // Equal arc-length samples keep texture detail even when input events arrive irregularly.
        val segments = (total / (2f * density)).roundToInt().coerceIn(2, 2048)
        val samples = ArrayList<BrushPoint>(segments + 1)
        var cursor = 1
        for (i in 0..segments) {
            val distance = total * i / segments
            while (cursor < smooth.lastIndex && distances[cursor] < distance) cursor++
            val before = smooth[cursor - 1]
            val after = smooth[cursor]
            val fraction = ((distance - distances[cursor - 1]) /
                (distances[cursor] - distances[cursor - 1]).coerceAtLeast(.001f)).coerceIn(0f, 1f)
            samples += BrushPoint(before.position + (after.position - before.position) * fraction,
                before.radius + (after.radius - before.radius) * fraction)
        }
        val vertices = FloatArray((segments + 1) * 4)
        for (i in samples.indices) {
            val tangent = samples[min(i + 1, segments)].position - samples[max(i - 1, 0)].position
            val length = tangent.getDistance().coerceAtLeast(.001f)
            val normal = Offset(-tangent.y / length, tangent.x / length)
            val distance = total * i / segments
            val head = (.3f + distance / (14f * density)).coerceAtMost(1f)
            val tail = if (finished) (.12f + (total - distance) / (36f * density)).coerceAtMost(1f) else 1f
            val radius = samples[i].radius * min(head, tail)
            val inner = samples[i].position + normal * radius
            val outer = samples[i].position - normal * radius
            vertices[i * 4] = inner.x
            vertices[i * 4 + 1] = inner.y
            vertices[i * 4 + 2] = outer.x
            vertices[i * 4 + 3] = outer.y
        }
        shape = BrushShape(vertices, segments)
    }
}
