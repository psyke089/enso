package app.enso.meditation.ui

import android.content.Context
import app.enso.meditation.R

/** Experimental brush controls, separate from timer preferences and temporary drawings. */
internal data class BrushSettings(
    val width: Float = 65f,
    val flow: Float = 1.8f,
    val inkReach: Float = .7f,
    val bristles: Int = 108,
    val drag: Float = 0f,
    val speed: Float = 2f,
    val smoothing: Float = 5f,
) {
    fun normalized() = copy(
        width = width.safe(16f..80f, 65f), flow = flow.safe(.2f..1.8f, 1.8f),
        inkReach = inkReach.safe(.25f..3f, .7f), bristles = bristles.coerceIn(24, 144),
        drag = drag.safe(0f..2f, 0f), speed = speed.safe(0f..2f, 2f),
        smoothing = smoothing.safe(0f..10f, 5f),
    )

    fun description(context: Context): String = context.getString(R.string.brush_description,
        format(context, "%.0f", width), format(context, "%.0f", flow * 100),
        format(context, "%.2f", inkReach), bristles.toString(),
        format(context, "%.0f", drag * 100), format(context, "%.0f", speed * 100),
        format(context, "%.1f", smoothing))
}

private fun format(context: Context, pattern: String, value: Float): String =
    String.format(context.resources.configuration.locales[0], pattern, value)

private fun Float.safe(range: ClosedFloatingPointRange<Float>, fallback: Float) =
    if (isFinite()) coerceIn(range) else fallback

internal class BrushSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("brush_experiment", Context.MODE_PRIVATE)

    fun warmUp() {
        load()
    }

    fun load(): BrushSettings {
        val defaults = BrushSettings()
        val loaded = BrushSettings(
            width = preferences.getFloat("width", defaults.width), flow = preferences.getFloat("flow", defaults.flow),
            inkReach = preferences.getFloat("ink_reach", defaults.inkReach), bristles = preferences.getInt("bristles", defaults.bristles),
            drag = preferences.getFloat("drag", defaults.drag), speed = preferences.getFloat("speed", defaults.speed),
            smoothing = preferences.getFloat("smoothing", defaults.smoothing),
        ).normalized()
        // Adopt the new preset for untouched installations; retain individually tuned values.
        val previousDefaults = BrushSettings(46f, 1f, 1f, 72, 1f, 1f, 2.8f)
        return if (loaded == previousDefaults) defaults.also(::save) else loaded
    }

    fun save(settings: BrushSettings) {
        val s = settings.normalized()
        preferences.edit().putFloat("width", s.width).putFloat("flow", s.flow)
            .putFloat("ink_reach", s.inkReach).putInt("bristles", s.bristles)
            .putFloat("drag", s.drag).putFloat("speed", s.speed)
            .putFloat("smoothing", s.smoothing).apply()
    }
}
