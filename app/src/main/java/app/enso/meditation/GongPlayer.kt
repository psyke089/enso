package app.enso.meditation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

enum class GongSound(val resourceName: String) { Start("start_gong"), End("end_gong") }

/** Optional local recordings; missing audio never prevents a meditation. */
class GongPlayer(private val context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stop() }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change < 0) stop()
    }

    @Suppress("DEPRECATION")
    fun play(sound: GongSound) {
        val id = findResource(sound.resourceName).takeIf { it != 0 } ?: findResource("gong")
        if (id == 0) return
        stop()
        try {
            val granted = if (Build.VERSION.SDK_INT >= 26) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes).setOnAudioFocusChangeListener(focusListener).build()
                focus = request
                manager.requestAudioFocus(request)
            } else manager.requestAudioFocus(focusListener, AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { stop(); return }
            val next = MediaPlayer()
            player = next
            next.setAudioAttributes(attributes)
            next.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            context.resources.openRawResourceFd(id).use { asset ->
                next.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
            }
            next.setOnPreparedListener { if (player === it) it.start() }
            next.setOnCompletionListener { if (player === it) stop() }
            next.setOnErrorListener { _, what, extra ->
                Log.w("Enso", "Gong playback failed: $what / $extra")
                stop(); true
            }
            next.prepareAsync()
            handler.postDelayed(timeout, 60_000)
        } catch (e: Exception) {
            Log.w("Enso", "Gong unavailable", e)
            stop()
        }
    }

    @Suppress("DiscouragedApi")
    private fun findResource(name: String) = context.resources.getIdentifier(name, "raw", context.packageName)

    @Suppress("DEPRECATION")
    fun stop() {
        handler.removeCallbacks(timeout)
        player?.release()
        player = null
        if (Build.VERSION.SDK_INT >= 26) focus?.let { manager.abandonAudioFocusRequest(it) }
        else manager.abandonAudioFocus(focusListener)
        focus = null
    }

    @Suppress("DEPRECATION")
    fun vibrate() {
        val vibrator = context.getSystemService(Vibrator::class.java)
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(70, 60), attributes)
        else vibrator.vibrate(70, attributes)
    }
}
