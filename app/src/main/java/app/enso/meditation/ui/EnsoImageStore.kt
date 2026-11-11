package app.enso.meditation.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.File

/** A single private transparent PNG; atomic replacement preserves the old image on failure. */
internal class EnsoImageStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "painted_enso.png"))

    fun load(): Bitmap? = try {
        file.openRead().use { BitmapFactory.decodeStream(it) }
    } catch (_: java.io.IOException) { null }

    fun save(bitmap: Bitmap) {
        val stream = file.startWrite()
        try {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }

    fun useStandard() {
        file.delete()
        check(!file.baseFile.exists()) { "Could not remove custom Enso" }
    }
}
