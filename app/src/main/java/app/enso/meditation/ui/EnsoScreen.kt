package app.enso.meditation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enso.meditation.*
import app.enso.meditation.BuildConfig
import app.enso.meditation.R
import app.enso.meditation.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
fun EnsoScreen(
    state: TimerUiState,
    adjustDuration: (Int) -> Unit,
    adjustRepetitions: (Int) -> Unit,
    changeSettings: ((EnsoSettings) -> EnsoSettings) -> Unit,
    previewGong: (GongChoice) -> Unit,
    animateLaunch: Boolean = false,
    splashReleased: Boolean = true,
    action: (SessionAction) -> Unit,
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var paintingOpen by remember { mutableStateOf(false) }
    var circleBounds by remember { mutableStateOf<Rect?>(null) }
    val context = LocalContext.current
    val imageStore = remember(context) { EnsoImageStore(context) }
    var customEnso by remember { mutableStateOf<ImageBitmap?>(null) }
    var customFrameScale by remember { mutableFloatStateOf(1f) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val painterOpacity = remember { Animatable(1f) }
    val launchInkOpacity = remember { Animatable(1f) }
    var foregroundVisit by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) foregroundVisit++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var launchComplete by rememberSaveable { mutableStateOf(!animateLaunch) }
    val launchMotion = remember { Animatable(if (launchComplete) 1f else 0f) }
    val mainOpacity = remember { Animatable(if (launchComplete) 1f else 0f) }
    var artworkLoaded by remember { mutableStateOf(false) }
    var drawnToday by remember { mutableStateOf(imageStore.wasDrawnToday()) }
    LaunchedEffect(imageStore) {
        while (true) {
            drawnToday = imageStore.wasDrawnToday()
            delay(30_000)
        }
    }
    var handingOff by remember { mutableStateOf(false) }
    var painterClosing by remember { mutableStateOf(false) }
    var handoffImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var handoffBounds by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(imageStore) {
        val saved = withContext(Dispatchers.IO) { imageStore.load()?.asImageBitmap() to imageStore.frameScale() }
        customEnso = saved.first
        customFrameScale = saved.second
        artworkLoaded = true
    }
    LaunchedEffect(foregroundVisit, launchComplete, state.ready, circleBounds != null) {
        if (launchComplete && state.ready && circleBounds != null) {
            drawnToday = imageStore.wasDrawnToday()
            if (!paintingOpen && imageStore.shouldPromptToday()) {
                painterOpacity.snapTo(0f)
                paintingOpen = true
                painterOpacity.animateTo(1f, tween(450))
            }
        }
    }
    LaunchedEffect(splashReleased, artworkLoaded, circleBounds != null, state.ready) {
        if (!launchComplete && splashReleased && artworkLoaded && circleBounds != null && state.ready) {
            launchMotion.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
            if (imageStore.shouldPromptToday()) {
                painterOpacity.snapTo(0f)
                paintingOpen = true
                coroutineScope {
                    launch { painterOpacity.animateTo(1f, tween(450)) }
                    launch { launchInkOpacity.animateTo(0f, tween(450)) }
                }
                mainOpacity.snapTo(1f)
            } else mainOpacity.animateTo(1f, tween(450))
            launchComplete = true
        }
    }
    fun closePainter() {
        if (painterClosing) return
        painterClosing = true
        imageStore.dismissForToday()
        scope.launch {
            painterOpacity.animateTo(0f, tween(360))
            paintingOpen = false
            painterOpacity.snapTo(1f)
            painterClosing = false
        }
    }
    Box(Modifier.fillMaxSize().onSizeChanged { viewSize = it }) {
        WashiBackground(Modifier.matchParentSize())
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = mainOpacity.value }
            .then(if (paintingOpen || !launchComplete) Modifier.clearAndSetSemantics {} else Modifier)) {
            WashiBackground(Modifier.matchParentSize())
            SumiLandscape(Modifier.align(Alignment.BottomCenter).fillMaxWidth())
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val compact = maxHeight < 580.dp
                val topSpace = if (compact) 8.dp else (maxHeight * .045f).coerceAtMost(48.dp)
                val circleSize = minOf(maxWidth - 24.dp, if (compact) 290.dp else 380.dp,
                    (maxHeight - topSpace - 400.dp).coerceAtLeast(180.dp))
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        InkPaintButton(drawnToday) {
                            if (circleBounds != null && launchComplete) scope.launch {
                                painterOpacity.snapTo(0f)
                                paintingOpen = true
                                painterOpacity.animateTo(1f, tween(360))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        InkSettingsButton { settingsOpen = true }
                    }
                    Spacer(Modifier.height(topSpace))
                    TimerCircle(state, adjustDuration, adjustRepetitions, customEnso, customFrameScale, !handingOff && launchComplete,
                        Modifier.size(circleSize).testTag("Timer artwork frame").onGloballyPositioned { circleBounds = Rect(it.positionInRoot(), it.size.toSize()) })
                    RepetitionDisplay(state)
                    Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
                    InkAction(
                        label = when (state.phase) { TimerPhase.Idle -> "Start"; TimerPhase.Running -> "Pause"; TimerPhase.Paused -> "Resume" },
                        artwork = if (state.phase == TimerPhase.Running) R.drawable.ink_pause else R.drawable.ink_start,
                        enabled = state.ready,
                        onClick = { action(when (state.phase) {
                            TimerPhase.Idle -> SessionAction.Start
                            TimerPhase.Running -> SessionAction.Pause
                            TimerPhase.Paused -> SessionAction.Resume
                        }) },
                    )
                    Box(Modifier.height(52.dp), contentAlignment = Alignment.Center) {
                        if (state.phase != TimerPhase.Idle) InkAction("Reset", R.drawable.ink_reset,
                            enabled = state.ready, secondary = true) { action(SessionAction.Stop) }
                    }
                    if (state.storageWarning) Text("Changes could not be saved on this device.",
                        color = Vermilion, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 24.dp), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
                    Saying(state.saying, state.ready && state.settings.showSaying)
                }
            }
            AnimatedVisibility(
                visible = settingsOpen,
                enter = fadeIn(tween(360)) + slideInVertically(tween(420)) { it / 20 },
                exit = fadeOut(tween(300)) + slideOutVertically(tween(360)) { it / 20 },
            ) {
                BackHandler { settingsOpen = false }
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .20f))
                        .clickable(interactionSource = remember { MutableInteractionSource() },
                            indication = null) { settingsOpen = false })
                    SettingsPanel(
                        settings = state.settings,
                        changeSettings = changeSettings,
                        previewGong = previewGong,
                        onDone = { settingsOpen = false },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        if (paintingOpen) circleBounds?.let { bounds ->
            val side = viewSize.width.toFloat()
            val paintBounds = Rect(bounds.center.x - side / 2, bounds.center.y - side / 2,
                bounds.center.x + side / 2, bounds.center.y + side / 2)
            EnsoPainter(paintBounds, bounds, Modifier.graphicsLayer { alpha = painterOpacity.value },
                handingOff, painterClosing || !launchComplete, onSave = { bitmap ->
                val scale = side / bounds.width
                withContext(Dispatchers.IO) { imageStore.save(bitmap, scale) }
                mainOpacity.snapTo(0f)
                drawnToday = imageStore.wasDrawnToday()
                customEnso = bitmap.asImageBitmap()
                customFrameScale = scale
                handoffBounds = paintBounds
                handoffImage = customEnso
                handingOff = true
                // The root owns this animation so it continues after the painter is removed.
                scope.launch {
                    coroutineScope {
                        launch { painterOpacity.animateTo(0f, tween(520)) }
                        launch { mainOpacity.animateTo(1f, tween(600, delayMillis = 80)) }
                    }
                    // Defer disposal until both animations have finished.
                    paintingOpen = false
                    handingOff = false
                    handoffImage = null
                    painterOpacity.snapTo(1f)
                }
            }, onUseStandard = {
                withContext(Dispatchers.IO) { imageStore.useStandard() }
                customEnso = null
                customFrameScale = 1f
                closePainter()
            }, onClose = { closePainter() })
        }
        if (!launchComplete) Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown().consume()
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        })
        if (!launchComplete) circleBounds?.let { bounds ->
            val density = LocalDensity.current
            val initialSide = with(density) { 288.dp.toPx() }
            val finalSide = bounds.width * if (customEnso != null) customFrameScale else 1f
            // Transform a cached layer; do not relayout the screen on every animation frame.
            Box(Modifier.size(with(density) { finalSide.toDp() })
                .graphicsLayer {
                    alpha = launchInkOpacity.value
                    val progress = launchMotion.value
                    val side = initialSide + (finalSide - initialSide) * progress
                    scaleX = side / finalSide
                    scaleY = scaleX
                    translationX = viewSize.width / 2f + (bounds.center.x - viewSize.width / 2f) * progress - finalSide / 2f
                    translationY = viewSize.height / 2f + (bounds.center.y - viewSize.height / 2f) * progress - finalSide / 2f
                }) {
                Image(painterResource(R.drawable.native_splash_enso), null,
                    Modifier.fillMaxSize().graphicsLayer { alpha = 1f - launchMotion.value })
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = launchMotion.value }) {
                    EnsoArtwork(Modifier.fillMaxSize(), paused = state.phase == TimerPhase.Paused, customImage = customEnso)
                }
            }
        }
        // One image bridges both screens while their controls fade around it.
        handoffImage?.let { image -> handoffBounds?.let { bounds ->
            Image(image, contentDescription = null, alpha = if (state.phase == TimerPhase.Paused) .55f else .92f,
                modifier = Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .wrapContentSize(Alignment.TopStart, unbounded = true).size(with(LocalDensity.current) { bounds.width.toDp() },
                        with(LocalDensity.current) { bounds.height.toDp() }))
        } }

    }
}

/** A quiet ink tally. Completed intervals fade from the right, leaving the next ones dark. */
@Composable
private fun RepetitionDisplay(state: TimerUiState) {
    val active = state.phase != TimerPhase.Idle
    val completed = if (active) state.completedIntervals.coerceIn(0, state.repeatCount.toLong()).toInt() else 0
    val description = if (active)
        "Interval ${state.completedIntervals + 1} of ${if (state.continuous) "unlimited" else state.repeatCount}"
        else if (state.continuous) "Unlimited repetitions (0)" else "${state.repeatCount} repetitions"
    Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
        if (state.continuous) {
            Image(painterResource(R.drawable.ink_infinity), contentDescription = description,
                alpha = .8f, modifier = Modifier.size(48.dp, 32.dp))
        } else {
            Canvas(Modifier.widthIn(max = 300.dp).fillMaxWidth().height(28.dp).semantics {
                contentDescription = description
                stateDescription = "$completed completed, ${state.repeatCount - completed} remaining"
            }) {
                val spacing = minOf(15.dp.toPx(), size.width / state.repeatCount)
                val start = (size.width - spacing * state.repeatCount) / 2
                repeat(state.repeatCount) { index ->
                    // Fixed variations keep each brush mark organic without changing on redraw.
                    val variant = index % 7
                    val x = start + spacing * (index + .5f)
                    val top = size.height * (.17f + variant % 3 * .035f)
                    val bottom = size.height * (.84f - variant % 2 * .045f)
                    val width = minOf((3.2f + variant % 3 * .35f).dp.toPx(), spacing * .44f)
                    val lean = width * (.35f + variant % 4 * .16f)
                    val ink = if (index >= state.repeatCount - completed) FadedInk.copy(alpha = .24f)
                        else Ink.copy(alpha = .78f + variant % 3 * .055f)
                    val mark = Path().apply {
                        moveTo(x + lean, top)
                        cubicTo(x + lean + width * .65f, top - width * .4f,
                            x + width * .5f, bottom - width * 1.3f, x + width * .15f, bottom)
                        lineTo(x - width * .4f, bottom + width * .25f)
                        cubicTo(x - width * .7f, bottom - width,
                            x - width * .35f, top + width, x + lean, top)
                        close()
                    }
                    drawPath(mark, ink)
                    // A fine dry-brush seam and a thin trailing hair soften the solid silhouette.
                    drawLine(Paper.copy(alpha = .45f), Offset(x + lean * .6f, top + width),
                        Offset(x - width * .08f, bottom - width * .6f),
                        strokeWidth = minOf(.45.dp.toPx(), width * .15f))
                    drawLine(ink.copy(alpha = ink.alpha * .55f),
                        Offset(x + width * .65f, top + width * 1.8f),
                        Offset(x + width * .2f, bottom + width * .8f),
                        strokeWidth = minOf(.5.dp.toPx(), width * .17f), cap = StrokeCap.Round)
                }
            }
        }
    }
}

/** Session time sits quietly beneath the interval countdown inside the Enso. */
@Composable
private fun SessionTime(state: TimerUiState) {
    if (state.continuous) return
    val active = state.phase != TimerPhase.Idle
    val total = state.totalMs?.let(::formatTime) ?: return
    val elapsed = formatTime(state.elapsedMs / 1000 * 1000)
    Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
        Text(if (active) "$elapsed / $total" else total,
            color = FadedInk, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            modifier = Modifier.semantics {
                contentDescription = if (active) "$elapsed elapsed, $total total duration"
                    else "$total total duration"
            })
    }
}

/** Same hand-painted charcoal and transparent paper as the session controls. */
@Composable
private fun InkSettingsButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(Modifier.size(44.dp).clip(CircleShape)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .semantics { contentDescription = "Settings" }, contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ink_settings), contentDescription = null,
            alpha = if (pressed) .45f else .8f, modifier = Modifier.fillMaxSize())
    }
}

/** Generated sumi brush artwork, with a full touch target and spoken action label. */
@Composable
internal fun InkAction(label: String, artwork: Int, enabled: Boolean,
    secondary: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val inkAlpha by animateFloatAsState(
        when { !enabled -> .32f; pressed -> .5f; secondary -> .65f; else -> .95f },
        tween(180), label = "control ink")
    Box(
        modifier = Modifier
            .size(if (secondary) 52.dp else 64.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled,
                role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(artwork), contentDescription = null,
            alpha = inkAlpha, modifier = Modifier.fillMaxSize())
    }
}

/** Fade through clear paper; no sliding, scaling, or overlapping lines of text. */
@Composable
private fun Saying(text: String, visible: Boolean) {
    AnimatedContent(
        targetState = if (visible) text else "",
        transitionSpec = {
            (fadeIn(tween(650, delayMillis = 450)) togetherWith fadeOut(tween(450))).using(null)
        },
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)
            .padding(bottom = 28.dp).heightIn(min = 44.dp),
        label = "saying fade",
    ) { saying ->
        if (saying.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.widthIn(max = 340.dp)) {
                SealAccent(Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text(saying, fontFamily = FontFamily.Serif, fontSize = 14.sp,
                    lineHeight = 22.sp, textAlign = TextAlign.Center,
                    color = FadedInk, letterSpacing = .7.sp,
                    modifier = Modifier.weight(1f, fill = false))
            }
        }
    }
}

@Composable
private fun TimerCircle(state: TimerUiState, adjustDuration: (Int) -> Unit,
    adjustRepetitions: (Int) -> Unit, customEnso: ImageBitmap?, frameScale: Float, showArtwork: Boolean, modifier: Modifier) {
    var brushRotation by rememberSaveable(customEnso) { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (showArtwork) EnsoArtwork(if (customEnso == null) Modifier.fillMaxSize()
            else Modifier.requiredSize(maxWidth * frameScale), paused = state.phase == TimerPhase.Paused,
            rotationDegrees = brushRotation, customImage = customEnso)
        val fontScale = LocalDensity.current.fontScale
        val textSize = maxWidth.value * (if (state.remainingMs > 3_599_000) .108f else .157f) /
            (fontScale / 1.25f).coerceAtLeast(1f)
        if (state.settings.showTime) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(6.dp))
            Text(formatTime(state.remainingMs), fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light, fontSize = textSize.sp, color = Ink,
                maxLines = 1, modifier = Modifier.semantics {
                    contentDescription = "${formatTime(state.remainingMs)} ${if (state.phase == TimerPhase.Idle) "selected" else "remaining"}"
                    if (state.phase == TimerPhase.Paused) stateDescription = "Paused"
                })
            SessionTime(state)
        }
        if (state.ready && state.phase == TimerPhase.Idle) {
            CircleInput(state, adjustDuration, adjustRepetitions) { delta ->
                brushRotation = (brushRotation + delta) % 360f
            }
        }

    }
}

/** One recognizer owns the whole circle, including gestures crossing between halves. */
@Composable
private fun CircleInput(state: TimerUiState, duration: (Int) -> Unit, repetitions: (Int) -> Unit,
    rotateBrush: (Float) -> Unit) {
    val currentDuration by rememberUpdatedState(duration)
    val currentRepetitions by rememberUpdatedState(repetitions)
    val currentRotateBrush by rememberUpdatedState(rotateBrush)
    val currentState by rememberUpdatedState(state)
    Row(Modifier.fillMaxSize().clip(CircleShape)
        .semantics {
            contentDescription = "Timer circle"
            stateDescription = "${state.selectedMs / 60_000} minutes"
            customActions = listOf(
                CustomAccessibilityAction("Increase meditation duration by ${state.settings.rotationStepMinutes} minutes") { currentDuration(1); true },
                CustomAccessibilityAction("Decrease meditation duration by ${state.settings.rotationStepMinutes} minutes") { currentDuration(-1); true },
            )
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = minOf(size.width, size.height) / 2f
                if ((down.position - center).getDistance() > radius) return@awaitEachGesture
                down.consume()
                val rotation = CircleRotation()
                // Predict duration locally while persistence catches up with rapid motion.
                var gestureDuration = currentState.selectedMs
                val stepMs = currentState.settings.rotationStepMinutes * 60_000L
                fun angle(position: Offset): Double? {
                    val relative = position - center
                    // Angles close to the center are unstable. Re-entering the ring starts afresh.
                    return if (hypot(relative.x, relative.y) < radius * .3f) null
                        else atan2(relative.y.toDouble(), relative.x.toDouble())
                }
                rotation.move(angle(down.position))
                var moved = false
                var cancelled = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.any { it.id != down.id && it.pressed } || change.isConsumed) {
                        cancelled = true
                    }
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                    if (!cancelled && moved) {
                        val decreaseBudget = -((gestureDuration - DURATION_STEP + stepMs - 1) / stepMs).toInt()
                        val increaseBudget = ((MAX_DURATION - gestureDuration + stepMs - 1) / stepMs).toInt()
                        val steps = rotation.move(angle(change.position), decreaseBudget, increaseBudget)
                        currentRotateBrush(rotation.deltaDegrees)
                        repeat(kotlin.math.abs(steps)) {
                            val direction = if (steps > 0) 1 else -1
                            gestureDuration = (gestureDuration + direction * stepMs).coerceIn(DURATION_STEP, MAX_DURATION)
                            currentDuration(direction)
                        }
                    }
                    change.consume()
                    if (!change.pressed) {
                        if (!cancelled && !moved &&
                            change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                            currentRepetitions(if (down.position.x < center.x) -1 else 1)
                        }
                        break
                    }
                }
            }
        }) {
        for (direction in listOf(-1, 1)) {
            Box(Modifier.weight(1f).fillMaxHeight().semantics {
                role = Role.Button
                contentDescription = if (direction > 0) "Increase repetitions" else "Decrease repetitions"
                stateDescription = if (state.continuous) "Unlimited repetitions (0)" else "${state.repeatCount} repetitions"
                onClick { currentRepetitions(direction); true }
            }.onKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.Spacebar)) {
                    currentRepetitions(direction); true
                } else false
            }.focusable())
        }
    }
}

/** Bottom sheet of settings painted as a sheet of paper rather than a Material card. */
@Composable
private fun SettingsPanel(
    settings: EnsoSettings,
    changeSettings: ((EnsoSettings) -> EnsoSettings) -> Unit,
    previewGong: (GongChoice) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dragOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dragThreshold = with(LocalDensity.current) { 120.dp.toPx() }
    Column(modifier.offset { IntOffset(0, dragOffset.value.roundToInt()) }
        .fillMaxWidth().background(Paper)
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(start = 28.dp, end = 28.dp, bottom = 26.dp)) {
        Box(Modifier.fillMaxWidth().height(30.dp)
            .draggable(
                state = rememberDraggableState { delta ->
                    scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f)) }
                },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (dragOffset.value > dragThreshold || velocity > 900f) onDone()
                    else dragOffset.animateTo(0f, spring())
                },
            ),
            contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Ink.copy(alpha = .3f)))
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("v${BuildConfig.VERSION_NAME}", color = FadedInk.copy(alpha = .65f), fontFamily = FontFamily.Serif,
                fontSize = 12.sp, modifier = Modifier.weight(1f)
                    .semantics { contentDescription = "App version ${BuildConfig.VERSION_NAME}" })
            Box(Modifier.size(44.dp).offset(x = 7.dp).clip(CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() },
                    indication = null, onClick = onDone)
                .semantics { contentDescription = "Close settings" },
                contentAlignment = Alignment.Center) {
                Text("\u00D7", color = FadedInk.copy(alpha = .65f), fontSize = 24.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Minutes per step", color = Ink, fontSize = 16.sp, fontFamily = FontFamily.Serif,
                modifier = Modifier.weight(1f).padding(end = 12.dp))
            val index = ROTATION_STEP_OPTIONS.indexOf(settings.rotationStepMinutes).coerceAtLeast(0)
            fun select(offset: Int) {
                val next = (index + offset + ROTATION_STEP_OPTIONS.size) % ROTATION_STEP_OPTIONS.size
                changeSettings { it.copy(rotationStepMinutes = ROTATION_STEP_OPTIONS[next]) }
            }
            PickerArrow("‹", "Smaller minute step") { select(-1) }
            Text("${settings.rotationStepMinutes} min", fontFamily = FontFamily.Serif, color = Ink,
                textAlign = TextAlign.Center, modifier = Modifier.width(56.dp).semantics {
                    contentDescription = "${settings.rotationStepMinutes} minutes per rotation step"
                })
            PickerArrow("›", "Larger minute step") { select(1) }
        }
        Setting("Haptic Feedback", settings.hapticFeedback) { value ->
            changeSettings { it.copy(hapticFeedback = value) }
        }
        Setting("Start Gong", settings.startGong,
            gong = settings.startGongSound,
            onGong = { choice ->
                changeSettings { it.copy(startGong = true, startGongSound = choice) }
                previewGong(choice)
            },
            onPreview = previewGong) { value ->
            changeSettings { it.copy(startGong = value) }
        }
        Setting("End Gong", settings.endGong,
            gong = settings.endGongSound,
            onGong = { choice ->
                changeSettings { it.copy(endGong = true, endGongSound = choice) }
                previewGong(choice)
            },
            onPreview = previewGong) { value ->
            changeSettings { it.copy(endGong = value) }
        }
        Setting("Repeat Gong", settings.repeatGong,
            gong = settings.middleGongSound,
            onGong = { choice ->
                changeSettings { it.copy(repeatGong = true, middleGongSound = choice) }
                previewGong(choice)
            },
            onPreview = previewGong) { value ->
            changeSettings { it.copy(repeatGong = value) }
        }
        Setting("Keep Screen Awake", settings.keepScreenAwake) { value ->
            changeSettings { it.copy(keepScreenAwake = value) }
        }
        Setting("Vibration", settings.vibration) { value ->
            changeSettings { it.copy(vibration = value) }
        }
        Setting("Show Time", settings.showTime) { value ->
            changeSettings { it.copy(showTime = value) }
        }
        Setting("Show Saying", settings.showSaying) { value ->
            changeSettings { it.copy(showSaying = value) }
        }
    }
}

@Composable
private fun Setting(
    label: String,
    checked: Boolean,
    gong: GongChoice? = null,
    onGong: ((GongChoice) -> Unit)? = null,
    onPreview: (GongChoice) -> Unit = {},
    change: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = change)
        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, fontSize = 16.sp, fontFamily = FontFamily.Serif,
            modifier = Modifier.weight(1f).padding(end = 12.dp))
        if (gong != null && onGong != null) GongPicker(gong, onGong, onPreview)
        InkToggle(checked)
    }
}

/** Cycles through the available recordings; kept quiet so it never reads as a form control. */
@Composable
private fun GongPicker(
    choice: GongChoice,
    onSelect: (GongChoice) -> Unit,
    onPreview: (GongChoice) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PickerArrow("‹", "Previous gong") { onSelect(choice.previous()) }
        Box(Modifier.width(92.dp).height(40.dp).clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() },
                indication = null) { onPreview(choice) }
            .semantics { contentDescription = "Preview ${choice.label}" },
            contentAlignment = Alignment.Center) {
            Text(choice.label, color = Ink.copy(alpha = .85f), fontFamily = FontFamily.Serif,
                fontSize = 14.sp, letterSpacing = .5.sp, textAlign = TextAlign.Center,
                maxLines = 1)
        }
        PickerArrow("›", "Next gong") { onSelect(choice.next()) }
    }
}

@Composable
private fun PickerArrow(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = FadedInk, fontSize = 20.sp)
    }
}

/** A brush dot inside a faint ring replaces the generic Material switch. */
@Composable
private fun InkToggle(checked: Boolean) {
    val progress by animateFloatAsState(if (checked) 1f else 0f, tween(220), label = "ink toggle")
    Canvas(Modifier.size(30.dp)) {
        drawCircle(FadedInk.copy(alpha = .55f), size.minDimension * .36f, center,
            style = Stroke(1.2.dp.toPx()))
        if (progress > 0f) {
            drawCircle(Ink.copy(alpha = .9f), size.minDimension * .24f * progress, center)
        }
    }
}
