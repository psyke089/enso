package app.enso.meditation.ui

import android.graphics.Bitmap
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enso.meditation.R
import app.enso.meditation.ui.theme.FadedInk
import app.enso.meditation.ui.theme.Ink
import app.enso.meditation.ui.theme.Paper
import kotlin.math.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Draw at the timer artwork bounds; only an explicit Save & use replaces its image. */
@Composable
internal fun EnsoPainter(artworkBounds: Rect, onSave: suspend (Bitmap) -> Unit,
    onUseStandard: suspend () -> Unit, onClose: () -> Unit) {
    val strokes = remember { mutableStateListOf<SumiStroke>() }
    val density = LocalDensity.current.density
    val brush = remember { SumiBrush() }
    val context = LocalContext.current
    val store = remember(context) { BrushSettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    val currentSettings by rememberUpdatedState(settings)
    var brushSettingsOpen by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentSaving by rememberUpdatedState(saving)
    val scope = rememberCoroutineScope()
    fun persist(useStandard: Boolean) {
        saving = true
        error = null
        val snapshot = strokes.toList()
        scope.launch {
            try {
                if (useStandard) onUseStandard() else {
                    val bitmap = withContext(Dispatchers.Default) {
                        renderSumiImage(snapshot, artworkBounds.width.roundToInt(), density)
                    }
                    onSave(bitmap)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Could not save the Enso. Please try again." }
            finally { saving = false }
        }
    }
    BackHandler { if (!saving) onClose() }
    Box(Modifier.fillMaxSize().background(Paper).pointerInput(Unit) {
        // Own hit testing even on the margins; touches must not reach the timer underneath.
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed })
        }
    }) {
        WashiBackground(Modifier.matchParentSize())
        Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(48.dp).clip(CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    enabled = !saving, onClick = onClose)
                .semantics { contentDescription = "Close painting" }, contentAlignment = Alignment.Center) {
                Text("×", color = FadedInk, fontSize = 28.sp)
            }
            TextButton(onClick = { brushSettingsOpen = true }, enabled = !saving,
                modifier = Modifier.semantics { contentDescription = "Brush settings" }) { Text("Brush") }
            InkAction("Reset painting", R.drawable.ink_reset, enabled = strokes.isNotEmpty() && !saving && !drawing,
                secondary = true) { strokes.clear() }
        }
        Canvas(Modifier.offset { IntOffset(artworkBounds.left.roundToInt(), artworkBounds.top.roundToInt()) }
            .size(with(LocalDensity.current) { artworkBounds.width.toDp() },
                with(LocalDensity.current) { artworkBounds.height.toDp() }).clipToBounds()
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
                    if (currentSaving) return@awaitEachGesture
                    drawing = true
                    val stroke = SumiStroke(density,
                        size.width.coerceAtMost(size.height) * ENSO_GUIDE_RADIUS * 2f * PI.toFloat(),
                        seed = strokes.size + 1, settings = currentSettings)
                    strokes += stroke
                    stroke.append(down.position, down.uptimeMillis)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                            for (sample in change.historical) stroke.append(sample.position, sample.uptimeMillis)
                            stroke.append(change.position, change.uptimeMillis)
                            change.consume()
                            if (!change.pressed) break
                        }
                    } finally { stroke.finish(); drawing = false }
                }
            }) {
            // Centerline of the original ink ring, in exactly the same screen-space artwork bounds.
            drawCircle(FadedInk.copy(alpha = .32f), radius = size.minDimension * ENSO_GUIDE_RADIUS,
                style = Stroke(1.dp.toPx()))
            for (stroke in strokes) brush.draw(this, stroke)
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            error?.let { Text(it, color = Ink, fontSize = 13.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { persist(true) }, enabled = !saving && !drawing,
                    modifier = Modifier.semantics { contentDescription = "Use standard Enso" }) { Text("Use standard") }
                TextButton(onClick = { persist(false) }, enabled = !saving && !drawing && strokes.isNotEmpty(),
                    modifier = Modifier.semantics { contentDescription = "Save and use painted Enso" }) {
                    Text(if (saving) "Saving…" else "Save & use")
                }
            }
        }
    }
    if (brushSettingsOpen) BrushSettingsPanel(settings, onChange = {
        settings = it
        store.save(it)
    }, onClose = { brushSettingsOpen = false })
}

internal const val ENSO_GUIDE_RADIUS = .38f

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
