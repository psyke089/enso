package app.enso.meditation.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enso.meditation.*
import app.enso.meditation.ui.theme.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EnsoScreen(
    state: TimerUiState,
    adjustDuration: (Int) -> Unit,
    changeSettings: ((EnsoSettings) -> EnsoSettings) -> Unit,
    action: (SessionAction) -> Unit,
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        WashiBackground(Modifier.matchParentSize())
        SumiLandscape(Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val compact = maxHeight < 580.dp
            val circleSize = minOf(maxWidth - 24.dp, if (compact) 290.dp else 380.dp)
            val topSpace = if (compact) 8.dp else (maxHeight * .085f).coerceAtMost(90.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("enso", fontFamily = FontFamily.Serif, fontSize = 28.sp,
                        letterSpacing = 5.sp, color = Ink, modifier = Modifier.weight(1f))
                    InkSettingsButton { settingsOpen = true }
                }
                Spacer(Modifier.height(topSpace))
                TimerCircle(state, adjustDuration, Modifier.size(circleSize))
                Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                    if (state.continuous) Text("∞  Continuous Gong", color = FadedInk,
                        fontSize = 12.sp, letterSpacing = .5.sp)
                }
                Spacer(Modifier.height(if (compact) 4.dp else 20.dp))
                InkAction(
                    label = when (state.phase) { TimerPhase.Idle -> "Start"; TimerPhase.Running -> "Pause"; TimerPhase.Paused -> "Resume" },
                    enabled = state.ready,
                    onClick = { action(when (state.phase) {
                        TimerPhase.Idle -> SessionAction.Start
                        TimerPhase.Running -> SessionAction.Pause
                        TimerPhase.Paused -> SessionAction.Resume
                    }) },
                )
                Box(Modifier.height(52.dp), contentAlignment = Alignment.Center) {
                    if (state.phase != TimerPhase.Idle) InkTextAction("Stop", FadedInk, 15.sp) { action(SessionAction.Stop) }
                }
                if (state.storageWarning) Text("Changes could not be saved on this device.",
                    color = Vermilion, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 24.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(if (compact) 12.dp else 40.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 28.dp)) {
                    SealAccent(Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("here and now", fontFamily = FontFamily.Serif, fontSize = 13.sp,
                        color = FadedInk, letterSpacing = 2.sp)
                }
            }
        }
        AnimatedVisibility(
            visible = settingsOpen,
            enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 4 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(200)) { it / 4 },
        ) {
            BackHandler { settingsOpen = false }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Ink.copy(alpha = .20f))
                    .clickable(interactionSource = remember { MutableInteractionSource() },
                        indication = null) { settingsOpen = false })
                SettingsPanel(
                    settings = state.settings,
                    phase = state.phase,
                    changeSettings = changeSettings,
                    onDone = { settingsOpen = false },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/** Discreet settings affordance: three brushed ink strokes, no Material button chrome. */
@Composable
private fun InkSettingsButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Canvas(Modifier.size(44.dp)
        .clip(CircleShape)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .semantics { contentDescription = "Settings" }
        .padding(11.dp)) {
        for (i in 0..2) {
            val y = size.height * (.22f + i * .28f)
            val x = size.width * if (i == 1) .65f else .35f
            drawLine(Ink.copy(alpha = if (pressed) .45f else .7f).let { if (i == 2) it.copy(alpha = it.alpha * .7f) else it },
                Offset(0f, y), Offset(size.width, y), 1.4.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Paper, 3.dp.toPx(), Offset(x, y))
            drawCircle(FadedInk, 3.dp.toPx(), Offset(x, y), style = Stroke(1.2.dp.toPx()))
        }
    }
}

/** Primary timer action rendered as ink and a single brush underline. */
@Composable
private fun InkAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val inkAlpha = when { !enabled -> .32f; pressed -> .6f; else -> 1f }
    Column(
        modifier = Modifier
            .widthIn(min = 132.dp).heightIn(min = 56.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, fontFamily = FontFamily.Serif, fontSize = 26.sp, fontWeight = FontWeight.Normal,
            letterSpacing = 1.sp, color = Ink.copy(alpha = inkAlpha))
        Spacer(Modifier.height(7.dp))
        Canvas(Modifier.width(44.dp).height(2.dp)) {
            drawLine(Ink.copy(alpha = if (enabled) .5f else .2f),
                Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                size.height, cap = StrokeCap.Round)
        }
    }
}

/** Quiet secondary action: a word that behaves like a link painted in ink. */
@Composable
private fun InkTextAction(label: String, color: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.TextUnit, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Text(
        text = label,
        color = color.copy(alpha = if (pressed) .55f else 1f),
        fontSize = size,
        letterSpacing = 1.sp,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .widthIn(min = 96.dp).heightIn(min = 48.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 16.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun TimerCircle(state: TimerUiState, adjustDuration: (Int) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        EnsoArtwork(Modifier.fillMaxSize(), paused = state.phase == TimerPhase.Paused)
        val fontScale = LocalDensity.current.fontScale
        val textSize = maxWidth.value * (if (state.remainingMs > 3_599_000) .108f else .157f) /
            (fontScale / 1.25f).coerceAtLeast(1f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(6.dp))
            Text(formatTime(state.remainingMs), fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light, fontSize = textSize.sp, color = Ink,
                maxLines = 1, modifier = Modifier.semantics {
                    contentDescription = "${formatTime(state.remainingMs)} ${if (state.phase == TimerPhase.Idle) "selected" else "remaining"}"
                })
            Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                if (state.phase == TimerPhase.Paused) Text("Paused", color = FadedInk,
                    fontSize = 13.sp, letterSpacing = 1.sp)
            }
        }
        if (state.ready && state.phase == TimerPhase.Idle) {
            Row(Modifier.fillMaxSize().clip(CircleShape)) {
                DurationZone(-1, state.selectedMs, adjustDuration, Modifier.weight(1f).fillMaxHeight())
                DurationZone(1, state.selectedMs, adjustDuration, Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.fillMaxWidth().offset(y = 52.dp).padding(horizontal = maxWidth * .19f),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("−", fontSize = 18.sp, color = FadedInk.copy(alpha = .7f),
                    modifier = Modifier.clearAndSetSemantics { })
                Text("+", fontSize = 18.sp, color = FadedInk.copy(alpha = .7f),
                    modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}

@Composable
private fun DurationZone(direction: Int, selectedMs: Long, adjust: (Int) -> Unit, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val currentAdjust by rememberUpdatedState(adjust)
    Box(modifier.background(Ink.copy(alpha = if (pressed) .045f else 0f))
        .semantics {
            role = Role.Button
            contentDescription = if (direction < 0) "Decrease meditation duration by 5 minutes"
                else "Increase meditation duration by 5 minutes"
            stateDescription = "${selectedMs / 60_000} minutes"
            onClick { currentAdjust(direction); true }
        }
        .onKeyEvent {
            if (it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.Spacebar)) {
                currentAdjust(direction); true
            } else false
        }.focusable()
        .pointerInput(direction) {
            coroutineScope {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val hold = DurationHold(direction)
                    pressed = true
                    currentAdjust(hold.begin(SystemClock.elapsedRealtime()))
                    val repeats = launch {
                        while (true) {
                            delay(16)
                            val step = hold.repeat(SystemClock.elapsedRealtime())
                            if (step != 0) currentAdjust(step)
                        }
                    }
                    try { waitForUpOrCancellation()?.consume() }
                    finally { hold.end(); repeats.cancel(); pressed = false }
                }
            }
        })
}

/** Bottom sheet of settings painted as a sheet of paper rather than a Material card. */
@Composable
private fun SettingsPanel(
    settings: EnsoSettings,
    phase: TimerPhase,
    changeSettings: ((EnsoSettings) -> EnsoSettings) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(Paper)
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(start = 28.dp, end = 28.dp, bottom = 26.dp)) {
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            drawLine(Ink.copy(alpha = .45f), Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2), 1.4.dp.toPx(), cap = StrokeCap.Round)
        }
        Text("A little intention", fontFamily = FontFamily.Serif, fontSize = 27.sp,
            modifier = Modifier.padding(top = 18.dp, bottom = 14.dp))
        Setting("Start Gong", "A sound to begin", settings.startGong) { value ->
            changeSettings { it.copy(startGong = value) }
        }
        Setting("End Gong", "A sound at each interval’s end", settings.endGong) { value ->
            changeSettings { it.copy(endGong = value) }
        }
        Setting("Continuous Gong", if (phase == TimerPhase.Idle) "Repeat until you stop"
            else "Applies to your next meditation", settings.continuousGong) { value ->
            changeSettings { it.copy(continuousGong = value) }
        }
        Setting("Keep Screen Awake", "While the timer is running", settings.keepScreenAwake) { value ->
            changeSettings { it.copy(keepScreenAwake = value) }
        }
        Setting("Vibration", "A gentle touch at each interval’s end", settings.vibration) { value ->
            changeSettings { it.copy(vibration = value) }
        }
        Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.CenterEnd) {
            InkTextAction("Done", Ink, 16.sp, onDone)
        }
    }
}

@Composable
private fun Setting(label: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = change)
        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = Ink, fontSize = 16.sp, fontFamily = FontFamily.Serif)
            Text(description, color = FadedInk, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(top = 3.dp))
        }
        InkToggle(checked)
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
