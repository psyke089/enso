package app.enso.meditation.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** Image, scale and drawing date share one atomic record, including the standard-artwork state. */
internal class EnsoImageStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "painted_enso.dat"))
    private val legacyFile = AtomicFile(File(context.filesDir, "painted_enso.png"))
    private val preferences = context.applicationContext.getSharedPreferences("enso_artwork", Context.MODE_PRIVATE)

    private data class Metadata(val scale: Float, val drawnAt: Long, val hasImage: Boolean)

    private fun legacyMetadata() = Metadata(
        normalizeScale(preferences.getFloat("frame_scale", 1f)),
        preferences.getLong("last_drawn_at", legacyFile.baseFile.lastModified()),
        legacyFile.baseFile.exists(),
    )

    private fun readHeader(input: DataInputStream): Metadata {
        if (input.readInt() != MAGIC || input.readInt() != VERSION) throw IOException("Invalid artwork record")
        return Metadata(normalizeScale(input.readFloat()), input.readLong(), input.readBoolean())
    }

    private fun metadata(): Metadata = try {
        file.openRead().use { readHeader(DataInputStream(it)) }
    } catch (_: FileNotFoundException) { legacyMetadata() }
    catch (_: IOException) { Metadata(1f, 0L, false) }

    fun wasDrawnToday(now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        sameLocalDay(metadata().drawnAt, now)
    }

    fun shouldPromptToday(now: Long = System.currentTimeMillis()): Boolean =
        !wasDrawnToday(now) && !sameLocalDay(preferences.getLong("last_dismissed_at", 0L), now)

    /** Hardcore mode has no dismissal: the one canvas of the day opens until it is saved. */
    fun shouldOpenPainter(hardcore: Boolean, now: Long = System.currentTimeMillis(),
        enabledAt: Long = 0L): Boolean = synchronized(lock) {
        if (hardcore) metadata().let { !sameLocalDay(it.drawnAt, now) || it.drawnAt < enabledAt }
        else shouldPromptToday(now)
    }

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

    fun frameScale(): Float = synchronized(lock) { metadata().scale }

    fun load(): Bitmap? = synchronized(lock) {
        try {
            file.openRead().use { stream ->
                val input = DataInputStream(stream)
                if (readHeader(input).hasImage) BitmapFactory.decodeStream(input) else null
            }
        } catch (_: FileNotFoundException) {
            try { legacyFile.openRead().use { BitmapFactory.decodeStream(it) } }
            catch (_: IOException) { null }
        } catch (_: IOException) { null }
    }

    fun save(bitmap: Bitmap, frameScale: Float = 1f) = synchronized(lock) {
        write(Metadata(normalizeScale(frameScale), System.currentTimeMillis(), true), bitmap)
    }

    fun useStandard() = synchronized(lock) {
        // Keep the completed day's drawing even when removing its image. No second preference write.
        write(Metadata(1f, metadata().drawnAt, false), null)
    }

    private fun write(metadata: Metadata, bitmap: Bitmap?) {
        val stream = file.startWrite()
        try {
            val output = DataOutputStream(stream)
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeFloat(metadata.scale)
            output.writeLong(metadata.drawnAt)
            output.writeBoolean(metadata.hasImage)
            if (bitmap != null) check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.flush()
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
        // Only retire the migration input after its replacement is committed.
        legacyFile.delete()
    }

    private fun normalizeScale(scale: Float) = if (scale.isFinite()) scale.coerceIn(1f, 16f) else 1f

    companion object {
        private const val MAGIC = 0x454E534F
        private const val VERSION = 1
        // All instances in this process share AtomicFile's required external serialization.
        private val lock = Any()
    }
}
