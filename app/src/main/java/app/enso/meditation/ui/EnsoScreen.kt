package app.enso.meditation.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import app.enso.meditation.*
import app.enso.meditation.ui.theme.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
                    IconButton(onClick = { settingsOpen = true },
                        modifier = Modifier.semantics { contentDescription = "Settings" }) {
                        Canvas(Modifier.size(22.dp)) {
                            for (i in 0..2) {
                                val y = size.height * (.22f + i * .28f)
                                val x = size.width * if (i == 1) .65f else .35f
                                drawLine(FadedInk, Offset(0f, y), Offset(size.width, y), 1.4.dp.toPx())
                                drawCircle(Paper, 3.dp.toPx(), Offset(x, y))
                                drawCircle(FadedInk, 3.dp.toPx(), Offset(x, y),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(topSpace))
                TimerCircle(state, adjustDuration, Modifier.size(circleSize))
                Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                    if (state.continuous) Text("∞  Continuous Gong", color = FadedInk,
                        fontSize = 12.sp, letterSpacing = .5.sp)
                }
                Spacer(Modifier.height(if (compact) 4.dp else 20.dp))
                TextButton(
                    enabled = state.ready,
                    onClick = { action(when (state.phase) {
                        TimerPhase.Idle -> SessionAction.Start
                        TimerPhase.Running -> SessionAction.Pause
                        TimerPhase.Paused -> SessionAction.Resume
                    }) },
                    modifier = Modifier.widthIn(min = 120.dp).heightIn(min = 56.dp),
                ) {
                    Text(when (state.phase) { TimerPhase.Idle -> "Start"; TimerPhase.Running -> "Pause"; TimerPhase.Paused -> "Resume" },
                        fontFamily = FontFamily.Serif, fontSize = 26.sp, fontWeight = FontWeight.Normal,
                        letterSpacing = 1.sp)
                }
                Box(Modifier.height(52.dp), contentAlignment = Alignment.Center) {
                    if (state.phase != TimerPhase.Idle) TextButton(onClick = { action(SessionAction.Stop) },
                        modifier = Modifier.widthIn(min = 96.dp).heightIn(min = 48.dp)) {
                        Text("Stop", color = FadedInk, fontSize = 15.sp)
                    }
                }
                if (state.storageWarning) Text("Changes could not be saved on this device.",
                    color = Vermilion, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 24.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(if (compact) 12.dp else 40.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 28.dp)) {
                    Box(Modifier.size(7.dp).background(Vermilion.copy(alpha = .8f)))
                    Spacer(Modifier.width(12.dp))
                    Text("here and now", fontFamily = FontFamily.Serif, fontSize = 13.sp,
                        color = FadedInk, letterSpacing = 2.sp)
                }
            }
        }
    }
    if (settingsOpen) {
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }, containerColor = Paper,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            contentColor = Ink, scrimColor = Ink.copy(alpha = .22f)) {
            PaperDialogBars()
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 28.dp, end = 28.dp, bottom = 28.dp)) {
                Text("A little intention", fontFamily = FontFamily.Serif, fontSize = 27.sp,
                    modifier = Modifier.padding(bottom = 20.dp))
                Setting("Start Gong", "A sound to begin", state.settings.startGong) { value ->
                    changeSettings { it.copy(startGong = value) }
                }
                Setting("End Gong", "A sound at each interval’s end", state.settings.endGong) { value ->
                    changeSettings { it.copy(endGong = value) }
                }
                Setting("Continuous Gong", if (state.phase == TimerPhase.Idle) "Repeat until you stop"
                    else "Applies to your next meditation", state.settings.continuousGong) { value ->
                    changeSettings { it.copy(continuousGong = value) }
                }
                Setting("Keep Screen Awake", "While the timer is running", state.settings.keepScreenAwake) { value ->
                    changeSettings { it.copy(keepScreenAwake = value) }
                }
                Setting("Vibration", "A gentle touch at each interval’s end", state.settings.vibration) { value ->
                    changeSettings { it.copy(vibration = value) }
                }
                TextButton(onClick = { settingsOpen = false }, modifier = Modifier.align(Alignment.End)) {
                    Text("Done", color = Ink)
                }
            }
        }
    }
}

@Suppress("DEPRECATION") // Android 24–34 dialog navigation bars; newer edge-to-edge windows use the paper surface.
@Composable
private fun PaperDialogBars() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window) {
        val previous = window.navigationBarColor
        window.navigationBarColor = Paper.toArgb()
        onDispose { window.navigationBarColor = previous }
    }
}

@Composable
private fun TimerCircle(state: TimerUiState, adjustDuration: (Int) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        InkCircle(Modifier.fillMaxSize(), state.phase == TimerPhase.Paused)
        val fontScale = LocalDensity.current.fontScale
        val textSize = maxWidth.value * (if (state.remainingMs > 3_599_000) .108f else .157f) /
            (fontScale / 1.25f).coerceAtLeast(1f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(30.dp))
            Text(formatTime(state.remainingMs), fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light, fontSize = textSize.sp, color = Ink,
                maxLines = 1, modifier = Modifier.semantics {
                    contentDescription = "${formatTime(state.remainingMs)} ${if (state.phase == TimerPhase.Idle) "selected" else "remaining"}"
                })
            Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
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

@Composable
private fun Setting(label: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = change)
        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = Ink, fontSize = 16.sp)
            Text(description, color = FadedInk, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(top = 3.dp))
        }
        Switch(checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Paper,
                uncheckedTrackColor = Paper, uncheckedThumbColor = FadedInk,
                uncheckedBorderColor = FadedInk))
    }
}
