package app.enso.meditation

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class EnsoSettingsTest {
    @Test fun emptyPreferencesLoadTheCanonicalDefaults() {
        assertEquals(EnsoSettings(), settingsFrom(emptyPreferences()))
    }

    @Test fun rotationStepSharedDefaultIsSelectable() {
        assertEquals(DEFAULT_ROTATION_STEP_MINUTES, EnsoSettings().rotationStepMinutes)
        assert(DEFAULT_ROTATION_STEP_MINUTES in ROTATION_STEP_OPTIONS)
    }

    @Test fun existingInstallsKeepFullGongVolumeAndStoredVolumeIsBounded() {
        val volume = intPreferencesKey("gong_volume_percent")
        assertEquals(100, settingsFrom(emptyPreferences()).gongVolumePercent)
        assertEquals(35, settingsFrom(preferencesOf(volume to 35)).gongVolumePercent)
        assertEquals(0, settingsFrom(preferencesOf(volume to -10)).gongVolumePercent)
        assertEquals(100, settingsFrom(preferencesOf(volume to 150)).gongVolumePercent)
    }

    @Test fun changingGongVolumePreservesAnActiveSession() {
        val engine = TimerEngine()
        engine.start(1_000L)
        val before = engine.snapshot
        engine.updateSettings(before.settings.copy(gongVolumePercent = -10))
        assertEquals(0, engine.snapshot.settings.gongVolumePercent)
        assertEquals(before.session, engine.snapshot.session)
        assertEquals(before.selectedMs, engine.snapshot.selectedMs)
        engine.updateSettings(engine.snapshot.settings.copy(gongVolumePercent = 150))
        assertEquals(100, engine.snapshot.settings.gongVolumePercent)
        assertEquals(before.session, engine.snapshot.session)
    }
}
