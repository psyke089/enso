package app.enso.meditation.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrushSettingsPanel(settings: BrushSettings, onChange: (BrushSettings) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Paper,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp).padding(bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                InkPainterIcon(PainterSymbol.Close, "Close brush settings", onClick = onClose)
            }
            fun change(next: BrushSettings) { copied = false; onChange(next.normalized()) }
            BrushSlider("Width", "${settings.width.roundToInt()} dp", "Maximum width of the brush",
                settings.width, 16f..80f, 63) { change(settings.copy(width = it.roundToInt().toFloat())) }
            BrushSlider("Ink flow", "${(settings.flow * 100).roundToInt()}%", "Light wash to saturated black",
                settings.flow, .2f..1.8f, 31) { change(settings.copy(flow = (it * 20).roundToInt() / 20f)) }
            BrushSlider("Ink reach", String.format(Locale.ROOT, "%.2f circles", settings.inkReach),
                "How far the ink lasts before the brush runs dry", settings.inkReach, .25f..3f, 54) {
                change(settings.copy(inkReach = (it * 20).roundToInt() / 20f))
            }
            BrushSlider("Bristles", "${settings.bristles}", "Few broad bundles to a dense brush",
                settings.bristles.toFloat(), 24f..144f, 19) { change(settings.copy(bristles = (it / 6).roundToInt() * 6)) }
            BrushSlider("Bristle drag", "${(settings.drag * 100).roundToInt()}%", "How much the hairs trail behind your finger",
                settings.drag, 0f..2f, 19) { change(settings.copy(drag = (it * 10).roundToInt() / 10f)) }
            BrushSlider("Speed influence", "${(settings.speed * 100).roundToInt()}%", "Faster movement makes a thinner stroke",
                settings.speed, 0f..2f, 19) { change(settings.copy(speed = (it * 10).roundToInt() / 10f)) }
            BrushSlider("Smoothing", String.format(Locale.ROOT, "%.1f dp", settings.smoothing),
                "Higher values soften the finger path more", settings.smoothing, 0f..10f, 49) {
                change(settings.copy(smoothing = (it * 5).roundToInt() / 5f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { change(BrushSettings()) }) { Text("Defaults") }
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Enso brush", settings.description()))
                    copied = true
                }, modifier = Modifier.semantics { contentDescription = "Copy brush values" }) {
                    Text(if (copied) "Copied" else "Copy values")
                }
            }
        }
    }
}

@Composable
private fun BrushSlider(label: String, valueLabel: String, hint: String, value: Float,
    range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Ink, fontFamily = FontFamily.Serif, fontSize = 16.sp)
            Text(valueLabel, color = FadedInk, fontSize = 14.sp)
        }
        Text(hint, color = FadedInk, fontSize = 12.sp)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = Ink,
                activeTrackColor = FadedInk.copy(alpha = .65f),
                inactiveTrackColor = FadedInk.copy(alpha = .16f),
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = "Brush $label" })
    }
}
