package app.enso.meditation

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.flow.first

private val Context.ensoStore by preferencesDataStore(
    name = "enso",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** One atomic local record for preferences and reconstructable session anchors. */
class EnsoStore(private val context: Context) {
    private val duration = longPreferencesKey("duration_ms")
    private val start = booleanPreferencesKey("start_gong")
    private val end = booleanPreferencesKey("end_gong")
    private val continuous = booleanPreferencesKey("continuous_gong")
    private val awake = booleanPreferencesKey("keep_screen_awake")
    private val vibration = booleanPreferencesKey("vibration")
    private val interval = longPreferencesKey("session_interval_ms")
    private val deadline = longPreferencesKey("session_deadline_ms")
    private val repeating = booleanPreferencesKey("session_continuous")
    private val paused = longPreferencesKey("session_paused_ms")
    private val boot = intPreferencesKey("session_boot")

    private fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    suspend fun load(): TimerSnapshot {
        val p = context.ensoStore.data.first()
        val currentBoot = bootCount()
        val session = if (currentBoot >= 0 && p[boot] == currentBoot && p[interval] != null && p[deadline] != null)
            Session(p[interval]!!, p[deadline]!!, p[repeating] ?: false, p[paused]) else null
        return TimerSnapshot(p[duration] ?: DEFAULT_DURATION,
            EnsoSettings(p[start] ?: true, p[end] ?: true, p[continuous] ?: false,
                p[awake] ?: true, p[vibration] ?: false), session)
    }

    suspend fun save(snapshot: TimerSnapshot) {
        context.ensoStore.edit { p ->
            p[duration] = snapshot.selectedMs
            p[start] = snapshot.settings.startGong
            p[end] = snapshot.settings.endGong
            p[continuous] = snapshot.settings.continuousGong
            p[awake] = snapshot.settings.keepScreenAwake
            p[vibration] = snapshot.settings.vibration
            val session = snapshot.session
            if (session == null) {
                p.remove(interval); p.remove(deadline); p.remove(repeating); p.remove(paused); p.remove(boot)
            } else {
                p[interval] = session.intervalMs
                p[deadline] = session.deadlineMs
                p[repeating] = session.continuous
                p[boot] = bootCount()
                session.pausedRemainingMs?.let { p[paused] = it } ?: p.remove(paused)
            }
        }
    }
}
