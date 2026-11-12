package app.enso.meditation.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.File

/** A single private transparent PNG; atomic replacement preserves the old image on failure. */
internal class EnsoImageStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "painted_enso.png"))

    private val preferences = context.applicationContext.getSharedPreferences("enso_artwork", Context.MODE_PRIVATE)

    fun wasDrawnToday(now: Long = System.currentTimeMillis()): Boolean =
        sameLocalDay(preferences.getLong("last_drawn_at", file.baseFile.lastModified()), now)

    fun shouldPromptToday(now: Long = System.currentTimeMillis()): Boolean =
        !wasDrawnToday(now) && !sameLocalDay(preferences.getLong("last_dismissed_at", 0L), now)

    /** Hardcore mode has no dismissal: the one canvas of the day opens until it is saved. */
    fun shouldOpenPainter(hardcore: Boolean, now: Long = System.currentTimeMillis()): Boolean =
        if (hardcore) !wasDrawnToday(now) else shouldPromptToday(now)

    fun dismissForToday() {
        preferences.edit().putLong("last_dismissed_at", System.currentTimeMillis()).apply()
    }

    private fun sameLocalDay(timestamp: Long, now: Long): Boolean {
        if (timestamp == 0L) return false
        val today = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val other = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return today.get(java.util.Calendar.ERA) == other.get(java.util.Calendar.ERA) &&
            today.get(java.util.Calendar.YEAR) == other.get(java.util.Calendar.YEAR) &&
            today.get(java.util.Calendar.DAY_OF_YEAR) == other.get(java.util.Calendar.DAY_OF_YEAR)
    }

    fun frameScale(): Float = preferences.getFloat("frame_scale", 1f).let {
        if (it.isFinite()) it.coerceIn(1f, 16f) else 1f
    }

    fun load(): Bitmap? = try {
        file.openRead().use { BitmapFactory.decodeStream(it) }
    } catch (_: java.io.IOException) { null }

    fun save(bitmap: Bitmap, frameScale: Float = 1f) {
        val stream = file.startWrite()
        try {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            file.finishWrite(stream)
            preferences.edit().putFloat("frame_scale", frameScale.coerceIn(1f, 16f))
                .putLong("last_drawn_at", System.currentTimeMillis()).apply()
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }

    fun useStandard() {
        file.delete()
        check(!file.baseFile.exists()) { "Could not remove custom Enso" }
        preferences.edit().remove("frame_scale").apply()
    }
}
