package app.enso.meditation.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Image, scale and drawing date share one atomic record, including the standard-artwork state.
 * Metadata is cached in memory after the first read, so the main thread never parses the file.
 */
internal class EnsoImageStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "painted_enso.dat"))
    private val legacyFile = AtomicFile(File(context.filesDir, "painted_enso.png"))
    private val preferences = context.applicationContext.getSharedPreferences("enso_artwork", Context.MODE_PRIVATE)

    private data class Metadata(val scale: Float, val drawnAt: Long, val hasImage: Boolean)
    private data class Record(val metadata: Metadata, val image: ByteArray?)

    @Volatile private var cached: Metadata? = null

    /** Loads the metadata and legacy preferences once, off the main thread. */
    fun warmUp() {
        metadata()
        preferences.getLong("last_dismissed_at", 0L)
        preferences.getLong("last_drawn_at", 0L)
    }

    /** Re-reads the record after external file changes; metadata is otherwise cached. */
    fun refresh() {
        cached = readMetadata()
    }

    private fun legacyMetadata() = Metadata(
        normalizeScale(preferences.getFloat("frame_scale", 1f)),
        preferences.getLong("last_drawn_at", legacyFile.baseFile.lastModified()),
        legacyFile.baseFile.exists(),
    )

    private fun readHeader(input: DataInputStream): Metadata {
        if (input.readInt() != MAGIC || input.readInt() != VERSION) throw IOException("Invalid artwork record")
        return Metadata(normalizeScale(input.readFloat()), input.readLong(), input.readBoolean())
    }

    private fun readRecord(): Record? = try {
        val input = DataInputStream(ByteArrayInputStream(file.readFully()))
        val metadata = readHeader(input)
        Record(metadata, if (metadata.hasImage) input.readBytes() else null)
    } catch (_: FileNotFoundException) { null }
    catch (_: IOException) { null }

    private fun readMetadata(): Metadata = readRecord()?.metadata ?: try {
        if (legacyFile.baseFile.exists()) legacyMetadata() else Metadata(1f, 0L, false)
    } catch (_: IOException) { Metadata(1f, 0L, false) }

    private fun metadata(): Metadata = cached ?: readMetadata().also { cached = it }

    fun wasDrawnToday(now: Long = System.currentTimeMillis()): Boolean =
        sameLocalDay(metadata().drawnAt, now)

    fun shouldPromptToday(now: Long = System.currentTimeMillis()): Boolean =
        !wasDrawnToday(now) && !sameLocalDay(preferences.getLong("last_dismissed_at", 0L), now)

    /** Hardcore mode has no dismissal: the one canvas of the day opens until it is saved. */
    fun shouldOpenPainter(hardcore: Boolean, pending: Boolean = false,
        now: Long = System.currentTimeMillis()): Boolean =
        if (hardcore) pending || !sameLocalDay(metadata().drawnAt, now) else shouldPromptToday(now)

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

    fun frameScale(): Float = metadata().scale

    fun load(): Bitmap? {
        val record = synchronized(ioLock) { readRecord() }
        if (record != null) {
            cached = record.metadata
            val image = record.image ?: return null
            return BitmapFactory.decodeByteArray(image, 0, image.size)
        }
        val legacy = synchronized(ioLock) {
            try { legacyFile.readFully() } catch (_: IOException) { null }
        } ?: return null
        return BitmapFactory.decodeByteArray(legacy, 0, legacy.size)
    }

    fun save(bitmap: Bitmap, frameScale: Float = 1f) {
        // Encoding happens outside the lock; a failure never touches the committed record.
        val image = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        write(Metadata(normalizeScale(frameScale), System.currentTimeMillis(), true), image)
    }

    fun useStandard() = write(metadata().copy(scale = 1f, hasImage = false), null)

    private fun write(metadata: Metadata, image: ByteArray?) = synchronized(ioLock) {
        val stream = file.startWrite()
        try {
            val output = DataOutputStream(stream)
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeFloat(metadata.scale)
            output.writeLong(metadata.drawnAt)
            output.writeBoolean(metadata.hasImage)
            image?.let { output.write(it) }
            output.flush()
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
        // Only retire the migration input after its replacement is committed.
        legacyFile.delete()
        cached = metadata
    }

    private fun normalizeScale(scale: Float) = if (scale.isFinite()) scale.coerceIn(1f, 16f) else 1f

    companion object {
        private const val MAGIC = 0x454E534F
        private const val VERSION = 1
        // AtomicFile requires a single writer; readers/decoders intentionally stay outside.
        private val ioLock = Any()
    }
}
