package app.enso.meditation

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

/** Foreground-only selection ticks, separate from meditation gongs and interval vibration. */
class AdjustmentFeedback(context: Context) {
    private val context = context.applicationContext
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    @Suppress("DEPRECATION")
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private var pool: SoundPool? = null
    private var sound = 0
    private var loaded = false
    private var active = false

    fun prepare() {
        active = true
        if (pool != null) return
        try {
            val next = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attributes).build()
            pool = next
            next.setOnLoadCompleteListener { source, _, status ->
                if (pool === source) loaded = status == 0
            }
            sound = next.load(context, R.raw.adjustment_tick, 1)
        } catch (e: RuntimeException) {
            Log.w("Enso", "Selection sound unavailable", e)
            pool?.release()
            pool = null
            loaded = false
        }
    }

    @Suppress("DEPRECATION")
    fun tick() {
        if (!active) return
        val activePool = pool
        try {
            if (loaded) activePool?.play(sound, .35f, .35f, 1, 0, 1f)
        } catch (e: RuntimeException) {
            Log.w("Enso", "Selection sound unavailable", e)
        }
        try {
            if (!vibrator.hasVibrator()) return
            when {
                Build.VERSION.SDK_INT >= 29 -> vibrator.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK), attributes)
                Build.VERSION.SDK_INT >= 26 -> vibrator.vibrate(
                    VibrationEffect.createOneShot(8, 45), attributes)
                else -> vibrator.vibrate(8, attributes)
            }
        } catch (e: RuntimeException) {
            Log.w("Enso", "Selection vibration unavailable", e)
        }
    }

    fun release() {
        active = false
        val old = pool
        pool = null
        loaded = false
        sound = 0
        old?.release()
    }
}
