package app.enso.meditation.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enso.meditation.R
import app.enso.meditation.ui.theme.Ink
import app.enso.meditation.ui.theme.Paper
import kotlin.math.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Draw at the timer artwork bounds; only an explicit Save & use replaces its image. */
@Composable
internal fun EnsoPainter(artworkBounds: Rect, guideBounds: Rect, modifier: Modifier, transitioning: Boolean, closing: Boolean, hardcore: Boolean = false, onSave: suspend (Bitmap) -> Unit,
    onUseStandard: suspend () -> Unit, onClose: () -> Unit) {
    val strokes = remember { mutableStateListOf<SumiStroke>() }
    val density = LocalDensity.current.density
    val brush = remember { SumiBrush() }
    val inkLayer = remember(artworkBounds.width, artworkBounds.height, density) {
        InkLayer(artworkBounds.width.roundToInt().coerceAtLeast(1), density)
    }
    val context = LocalContext.current
    val saveError = stringResource(R.string.save_error)
    val store = remember(context) { BrushSettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    val currentSettings by rememberUpdatedState(settings)
    var brushSettingsOpen by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    val guidePresence by animateFloatAsState(
        if (drawing) .2f else if (strokes.isEmpty()) 1f else .45f,
        tween(550), label = "paper guide")
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val busy = saving || transitioning || closing
    val currentSaving by rememberUpdatedState(busy)
    val scope = rememberCoroutineScope()
    fun persist(useStandard: Boolean) {
        saving = true
        error = null
        val snapshot = strokes.toList()
        scope.launch {
            try {
                if (useStandard) onUseStandard() else {
                    val bitmap = withContext(Dispatchers.Default) {
                        renderSumiImage(snapshot, artworkBounds.width.roundToInt(), density).also { it.prepareToDraw() }
                    }
                    onSave(bitmap)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = saveError }
            finally { saving = false }
        }
    }
    // Hardcore mode has exactly one exit: saving the painting.
    BackHandler { if (!busy && !hardcore) onClose() }
    Box(Modifier.fillMaxSize().then(modifier).background(Paper)
        .then(if (brushSettingsOpen) Modifier.clearAndSetSemantics {} else Modifier).pointerInput(Unit) {
        // Own hit testing even on the margins; touches must not reach the timer underneath.
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed })
        }
    }) {
        WashiBackground(Modifier.matchParentSize())
        if (!hardcore) Row(Modifier.zIndex(1f).fillMaxWidth().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            InkPainterIcon(PainterSymbol.Close, stringResource(R.string.close_painting), enabled = !busy, onClick = onClose)
            InkPainterIcon(PainterSymbol.Brush, stringResource(R.string.brush_settings), enabled = !busy) { brushSettingsOpen = true }
            InkAction(stringResource(R.string.reset_painting), R.drawable.ink_reset, enabled = strokes.isNotEmpty() && !busy && !drawing,
                secondary = true) { strokes.clear(); inkLayer.reset() }
        }
        val canvasDescription = stringResource(R.string.painting_canvas)
        val strokesDescription = if (strokes.isEmpty()) stringResource(R.string.empty_canvas)
            else pluralStringResource(R.plurals.brush_strokes, strokes.size, strokes.size)
        val canvasFrame = Modifier.offset { IntOffset(artworkBounds.left.roundToInt(), artworkBounds.top.roundToInt()) }
            .wrapContentSize(Alignment.TopStart, unbounded = true).size(with(LocalDensity.current) { artworkBounds.width.toDp() },
                with(LocalDensity.current) { artworkBounds.height.toDp() })
        DrawingPaper(canvasFrame, guideBounds.center - artworkBounds.topLeft,
            guideBounds.width * ENSO_GUIDE_RADIUS) { guidePresence }
        Canvas(canvasFrame.clipToBounds()
            .semantics {
                contentDescription = canvasDescription
                stateDescription = strokesDescription
            }
            .pointerInput(density) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    if (currentSaving) return@awaitEachGesture
                    drawing = true
                    val stroke = SumiStroke(density,
                        guideBounds.width * ENSO_GUIDE_RADIUS * 2f * PI.toFloat(),
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
            if (!transitioning) {
                val finished = if (drawing) strokes.size - 1 else strokes.size
                inkLayer.append(strokes, finished)
                drawImage(inkLayer.image)
                if (drawing) strokes.lastOrNull()?.let { brush.draw(this, it) }
            }
        }
        Column(Modifier.zIndex(1f).align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            error?.let { Text(it, color = Ink, fontSize = 13.sp) }
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = if (hardcore) Arrangement.Center else Arrangement.SpaceBetween) {
                if (!hardcore) InkPainterIcon(PainterSymbol.Trash, stringResource(R.string.use_standard_enso), enabled = !busy && !drawing) { persist(true) }
                InkPainterIcon(PainterSymbol.Apply, stringResource(R.string.save_painted_enso),
                    enabled = !busy && !drawing && strokes.isNotEmpty()) { persist(false) }
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
internal fun InkPaintButton(drawnToday: Boolean = false, onClick: () -> Unit) {
    val paintDescription = stringResource(R.string.paint_enso)
    val drawnTodayDescription = stringResource(R.string.enso_drawn_today)
    Box(Modifier.size(44.dp).clip(CircleShape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClick = onClick).semantics {
                contentDescription = paintDescription
                if (drawnToday) stateDescription = drawnTodayDescription
                role = Role.Button
            }, contentAlignment = Alignment.Center) {
        if (drawnToday) Image(painterResource(R.drawable.enso_brush), null,
            Modifier.size(29.dp).align(Alignment.TopStart).padding(3.dp).rotate(154f), alpha = .7f)
        BrushMark(if (drawnToday) Modifier.size(23.dp).align(Alignment.BottomEnd).padding(2.dp)
            else Modifier.size(24.dp))
    }
}

/** Shared brush silhouette for the main action and its settings control. */
@Composable
internal fun BrushMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
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
