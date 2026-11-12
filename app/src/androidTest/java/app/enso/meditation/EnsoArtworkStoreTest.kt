package app.enso.meditation

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.enso.meditation.ui.EnsoImageStore
import java.io.DataOutputStream
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android bitmap and AtomicFile behavior, isolated from the user's artwork. */
@RunWith(AndroidJUnit4::class)
class EnsoArtworkStoreTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var preferenceName: String
    private lateinit var base: Context

    @Before fun isolateStore() {
        base = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        directory = File(base.cacheDir, "artwork-test-$id").apply { check(mkdirs()) }
        preferenceName = "artwork-test-$id"
        context = object : ContextWrapper(base) {
            override fun getFilesDir() = directory
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(preferenceName, mode)
        }
    }

    @After fun cleanUp() {
        directory.deleteRecursively()
        base.deleteSharedPreferences(preferenceName)
    }

    private fun image(color: Int) = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    private fun store() = EnsoImageStore(context)

    @Test fun everyGongHasABundledRecording() {
        for (choice in GongChoice.entries) {
            assertTrue("Missing ${choice.name}", choice.soundRes != 0)
            base.resources.openRawResourceFd(choice.soundRes).use { assertTrue(it.length > 0) }
        }
    }

    @Test fun imageScaleAndDaySurviveReloadAndStandardWithoutDependingOnPreferences() {
        val bitmap = image(Color.BLACK)
        store().save(bitmap, 1.75f)
        // Stale old-format values must never override the new committed record.
        context.getSharedPreferences("enso_artwork", Context.MODE_PRIVATE).edit()
            .putFloat("frame_scale", 9f).putLong("last_drawn_at", 0L).commit()
        assertTrue(bitmap.sameAs(store().load()))
        assertEquals(1.75f, store().frameScale(), 0f)
        assertTrue(store().wasDrawnToday())
        assertFalse(store().shouldOpenPainter(true))
        store().useStandard()
        assertNull(store().load())
        assertEquals(1f, store().frameScale(), 0f)
        assertTrue(store().wasDrawnToday())
        assertFalse(store().shouldOpenPainter(true))
    }

    @Test fun legacyPngAndPreferencesMigrateTogetherOnNextWrite() {
        val bitmap = image(Color.BLUE)
        File(directory, "painted_enso.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        context.getSharedPreferences("enso_artwork", Context.MODE_PRIVATE).edit()
            .putFloat("frame_scale", 1.6f).putLong("last_drawn_at", System.currentTimeMillis()).commit()
        assertTrue(bitmap.sameAs(store().load()))
        assertEquals(1.6f, store().frameScale(), 0f)
        assertTrue(store().wasDrawnToday())
        store().useStandard()
        assertFalse(File(directory, "painted_enso.png").exists())
        assertTrue(store().wasDrawnToday())
        assertNull(store().load())
    }

    @Test fun failedEncodingRollsBackImageAndMetadataTogether() {
        val original = image(Color.BLACK)
        store().save(original, 1.8f)
        val replacement = image(Color.RED).apply { recycle() }
        try {
            store().save(replacement, 3f)
            fail("Encoding a recycled bitmap should fail")
        } catch (_: IllegalStateException) { /* Actual write failure after the new header. */ }
        assertTrue(original.sameAs(store().load()))
        assertEquals(1.8f, store().frameScale(), 0f)
        assertTrue(store().wasDrawnToday())
    }

    @Test fun interruptedReplacementKeepsThePreviousImageScaleAndDay() {
        val original = image(Color.BLACK)
        store().save(original, 1.8f)
        val file = AtomicFile(File(directory, "painted_enso.dat"))
        val output = file.startWrite()
        try {
            // A new header has reached disk, but the PNG and atomic commit have not.
            DataOutputStream(output).apply {
                writeInt(0x454E534F); writeInt(1); writeFloat(4f); writeLong(1L); writeBoolean(true); flush()
            }
            output.fd.sync()
            assertTrue(original.sameAs(store().load()))
            assertEquals(1.8f, store().frameScale(), 0f)
            assertTrue(store().wasDrawnToday())
        } finally { file.failWrite(output) }
        assertTrue(original.sameAs(store().load()))
        assertEquals(1.8f, store().frameScale(), 0f)
        assertTrue(store().wasDrawnToday())
    }
}
