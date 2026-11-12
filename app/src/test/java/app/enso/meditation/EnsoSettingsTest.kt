package app.enso.meditation

import androidx.datastore.preferences.core.emptyPreferences
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
}
