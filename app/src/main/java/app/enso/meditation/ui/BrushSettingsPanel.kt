package app.enso.meditation.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.enso.meditation.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.enso.meditation.ui.theme.FadedInk
import app.enso.meditation.ui.theme.Ink
import app.enso.meditation.ui.theme.Paper
import java.util.Locale
import kotlin.math.roundToInt

/** A stable full-screen workspace: no sheet anchors, drag gesture or nested scrolling. */
@Composable
internal fun BrushSettingsPanel(settings: BrushSettings, onChange: (BrushSettings) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val copyLabel = stringResource(R.string.brush_copy_values)
    val appName = stringResource(R.string.app_name)
    val copiedLabel = stringResource(R.string.brush_copied)
    BackHandler(onBack = onClose)
    fun change(next: BrushSettings) { copied = false; onChange(next.normalized()) }
    val width = stringResource(R.string.brush_width)
    val inkFlow = stringResource(R.string.brush_ink_flow)
    val inkReach = stringResource(R.string.brush_ink_reach)
    val bristles = stringResource(R.string.brush_bristles)
    val drag = stringResource(R.string.brush_drag)
    val speed = stringResource(R.string.brush_speed)
    val smoothing = stringResource(R.string.brush_smoothing)
    val numberLocale = LocalConfiguration.current.locales[0]
    val controls = listOf(
        BrushControl(width, stringResource(R.string.brush_dp, settings.width.roundToInt()),
            settings.width, 16f..80f, 63) {
            change(settings.copy(width = it.roundToInt().toFloat()))
        },
        BrushControl(inkFlow, stringResource(R.string.brush_percent, (settings.flow * 100).roundToInt()),
            settings.flow, .2f..1.8f, 31) {
            change(settings.copy(flow = (it * 20).roundToInt() / 20f))
        },
        BrushControl(inkReach, stringResource(R.string.brush_circles, formatNumber(settings.inkReach, "%.2f", numberLocale)),
            settings.inkReach, .25f..3f, 54) {
            change(settings.copy(inkReach = (it * 20).roundToInt() / 20f))
        },
        BrushControl(bristles, settings.bristles.toString(), settings.bristles.toFloat(), 24f..144f, 19) {
            change(settings.copy(bristles = (it / 6).roundToInt() * 6))
        },
        BrushControl(drag, stringResource(R.string.brush_percent, (settings.drag * 100).roundToInt()),
            settings.drag, 0f..2f, 19) {
            change(settings.copy(drag = (it * 10).roundToInt() / 10f))
        },
        BrushControl(speed, stringResource(R.string.brush_percent, (settings.speed * 100).roundToInt()),
            settings.speed, 0f..2f, 19) {
            change(settings.copy(speed = (it * 10).roundToInt() / 10f))
        },
        BrushControl(smoothing, stringResource(R.string.brush_dp_text, formatNumber(settings.smoothing, "%.1f", numberLocale)),
            settings.smoothing, 0f..10f, 49) {
            change(settings.copy(smoothing = (it * 5).roundToInt() / 5f))
        },
    )
    Box(Modifier.fillMaxSize().background(Paper).pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed })
        }
    }) {
        WashiBackground(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                InkPainterIcon(PainterSymbol.Close, stringResource(R.string.close_brush_settings), onClick = onClose)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (maxWidth > maxHeight) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        for (part in listOf(controls.take(4), controls.drop(4))) {
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                for (control in part) BrushSlider(control, Modifier.weight(1f))
                            }
                        }
                    }
                } else Column(Modifier.fillMaxSize()) {
                    for (control in controls) BrushSlider(control, Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { change(BrushSettings()) }) { Text(stringResource(R.string.brush_defaults)) }
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(appName,
                        settings.description(context)))
                    copied = true
                }, modifier = Modifier.semantics { contentDescription = copyLabel }) {
                    Text(if (copied) copiedLabel else copyLabel)
                }
            }
        }
    }
}

private fun formatNumber(value: Float, pattern: String, locale: Locale): String =
    String.format(locale, pattern, value)

private data class BrushControl(val label: String, val valueLabel: String, val value: Float,
    val range: ClosedFloatingPointRange<Float>, val steps: Int, val change: (Float) -> Unit)

@Composable
private fun BrushSlider(control: BrushControl, modifier: Modifier) {
    val description = stringResource(R.string.brush_slider, control.label)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(control.label, color = Ink, fontFamily = FontFamily.Serif, fontSize = 15.sp)
            Text(control.valueLabel, color = FadedInk, fontSize = 13.sp)
        }
        Slider(value = control.value, onValueChange = control.change, valueRange = control.range, steps = control.steps,
            colors = SliderDefaults.colors(thumbColor = Ink,
                activeTrackColor = FadedInk.copy(alpha = .65f),
                inactiveTrackColor = FadedInk.copy(alpha = .16f),
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().height(38.dp).semantics { contentDescription = description })
    }
}
