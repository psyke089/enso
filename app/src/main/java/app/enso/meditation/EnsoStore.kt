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

private object Keys {
    val rotationStep = intPreferencesKey("rotation_step_minutes")
    val duration = longPreferencesKey("duration_ms")
    val start = booleanPreferencesKey("start_gong")
    val end = booleanPreferencesKey("end_gong")
    val legacyContinuous = booleanPreferencesKey("continuous_gong")
    val repeatGong = booleanPreferencesKey("repeat_gong")
    val repeatCount = intPreferencesKey("repeat_count")
    val repeatForever = booleanPreferencesKey("repeat_forever")
    val awake = booleanPreferencesKey("keep_screen_awake")
    val vibration = booleanPreferencesKey("vibration")
    val startSound = stringPreferencesKey("start_gong_sound")
    val endSound = stringPreferencesKey("end_gong_sound")
    val middleSound = stringPreferencesKey("middle_gong_sound")
    val hapticFeedback = booleanPreferencesKey("haptic_feedback")
    val showTime = booleanPreferencesKey("show_time")
    val showSaying = booleanPreferencesKey("show_saying")
    val hardcorePending = booleanPreferencesKey("hardcore_pending")
    val hardcore = booleanPreferencesKey("hardcore_mode")
    val sayingIndex = intPreferencesKey("saying_index")
    val interval = longPreferencesKey("session_interval_ms")
    val deadline = longPreferencesKey("session_deadline_ms")
    val repeating = booleanPreferencesKey("session_continuous")
    val paused = longPreferencesKey("session_paused_ms")
    val sessionRepeatCount = intPreferencesKey("session_repeat_count")
    val completedIntervals = longPreferencesKey("session_completed_intervals")
    val boot = intPreferencesKey("session_boot")
}

/** One defaults source: an empty preferences file must load exactly `EnsoSettings()`. */
internal fun settingsFrom(p: Preferences): EnsoSettings {
    val defaults = EnsoSettings()
    return EnsoSettings(
        startGong = p[Keys.start] ?: defaults.startGong,
        endGong = p[Keys.end] ?: defaults.endGong,
        repeatGong = p[Keys.repeatGong] ?: p[Keys.end] ?: defaults.repeatGong,
        keepScreenAwake = p[Keys.awake] ?: defaults.keepScreenAwake,
        vibration = p[Keys.vibration] ?: defaults.vibration,
        startGongSound = GongChoice.fromName(p[Keys.startSound]) ?: defaults.startGongSound,
        endGongSound = GongChoice.fromName(p[Keys.endSound]) ?: defaults.endGongSound,
        middleGongSound = GongChoice.fromName(p[Keys.middleSound]) ?: defaults.middleGongSound,
        showSaying = p[Keys.showSaying] ?: defaults.showSaying,
        rotationStepMinutes = p[Keys.rotationStep]?.takeIf { it in ROTATION_STEP_OPTIONS }
            ?: defaults.rotationStepMinutes,
        showTime = p[Keys.showTime] ?: defaults.showTime,
        hapticFeedback = p[Keys.hapticFeedback] ?: defaults.hapticFeedback,
        hardcoreMode = p[Keys.hardcore] ?: defaults.hardcoreMode,
        hardcorePending = p[Keys.hardcorePending] ?: defaults.hardcorePending,
    )
}

/** One atomic local record for preferences and reconstructable session anchors. */
class EnsoStore(private val context: Context) {

    private fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    suspend fun load(): TimerSnapshot {
        val p = context.ensoStore.data.first()
        val currentBoot = bootCount()
        val session = if (currentBoot >= 0 && p[Keys.boot] == currentBoot &&
            p[Keys.interval] != null && p[Keys.deadline] != null)
            Session(p[Keys.interval]!!, p[Keys.deadline]!!, p[Keys.repeating] ?: false, p[Keys.paused],
                p[Keys.sessionRepeatCount] ?: 1, p[Keys.completedIntervals] ?: 0) else null
        return TimerSnapshot(p[Keys.duration] ?: DEFAULT_DURATION,
            settingsFrom(p), session, p[Keys.sayingIndex] ?: -1,
            p[Keys.repeatCount] ?: 1, p[Keys.repeatForever] ?: p[Keys.legacyContinuous] ?: false)
    }

    suspend fun save(snapshot: TimerSnapshot) {
        context.ensoStore.edit { p ->
            p[Keys.rotationStep] = snapshot.settings.rotationStepMinutes
            p[Keys.duration] = snapshot.selectedMs
            p[Keys.start] = snapshot.settings.startGong
            p[Keys.end] = snapshot.settings.endGong
            p[Keys.repeatGong] = snapshot.settings.repeatGong
            p[Keys.repeatCount] = snapshot.repeatCount
            p[Keys.repeatForever] = snapshot.repeatForever
            p.remove(Keys.legacyContinuous)
            p[Keys.awake] = snapshot.settings.keepScreenAwake
            p[Keys.vibration] = snapshot.settings.vibration
            p[Keys.startSound] = snapshot.settings.startGongSound.name
            p[Keys.endSound] = snapshot.settings.endGongSound.name
            p[Keys.middleSound] = snapshot.settings.middleGongSound.name
            p[Keys.hapticFeedback] = snapshot.settings.hapticFeedback
            p[Keys.showTime] = snapshot.settings.showTime
            p[Keys.showSaying] = snapshot.settings.showSaying
            p[Keys.hardcore] = snapshot.settings.hardcoreMode
            p[Keys.hardcorePending] = snapshot.settings.hardcorePending
            p[Keys.sayingIndex] = snapshot.sayingIndex
            val session = snapshot.session
            if (session == null) {
                p.remove(Keys.interval); p.remove(Keys.deadline); p.remove(Keys.repeating)
                p.remove(Keys.paused); p.remove(Keys.boot)
                p.remove(Keys.sessionRepeatCount); p.remove(Keys.completedIntervals)
            } else {
                p[Keys.interval] = session.intervalMs
                p[Keys.deadline] = session.deadlineMs
                p[Keys.repeating] = session.continuous
                p[Keys.sessionRepeatCount] = session.repeatCount
                p[Keys.completedIntervals] = session.completedIntervals
                p[Keys.boot] = bootCount()
                session.pausedRemainingMs?.let { p[Keys.paused] = it } ?: p.remove(Keys.paused)
            }
        }
    }
}
