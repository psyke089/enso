package app.enso.meditation

import java.util.Locale

const val DURATION_STEP = 5 * 60_000L
const val MAX_DURATION = 24 * 60 * 60_000L
const val DEFAULT_DURATION = 10 * 60_000L

data class EnsoSettings(
    val startGong: Boolean = true,
    val endGong: Boolean = true,
    val continuousGong: Boolean = false,
    val keepScreenAwake: Boolean = true,
    val vibration: Boolean = false,
)

enum class TimerPhase { Idle, Running, Paused }

/** Deadline advances from the previous boundary, never from a UI tick or audio callback. */
data class Session(
    val intervalMs: Long,
    val deadlineMs: Long,
    val continuous: Boolean,
    val pausedRemainingMs: Long? = null,
)

data class TimerSnapshot(
    val selectedMs: Long = DEFAULT_DURATION,
    val settings: EnsoSettings = EnsoSettings(),
    val session: Session? = null,
)

data class TimerUiState(
    val ready: Boolean = false,
    val selectedMs: Long = DEFAULT_DURATION,
    val remainingMs: Long = DEFAULT_DURATION,
    val phase: TimerPhase = TimerPhase.Idle,
    val settings: EnsoSettings = EnsoSettings(),
    val continuous: Boolean = false,
    val storageWarning: Boolean = false,
)

sealed interface TimerEvent {
    data object StartGong : TimerEvent
    data class IntervalCompleted(val gong: Boolean, val vibrate: Boolean) : TimerEvent
}

/** Pure state machine. Its owner serializes calls and supplies a monotonic clock. */
class TimerEngine(snapshot: TimerSnapshot = TimerSnapshot(), nowMs: Long = 0) {
    var snapshot: TimerSnapshot = snapshot.copy(selectedMs = normalizeDuration(snapshot.selectedMs))
        private set

    init {
        val session = snapshot.session
        if (session != null && (session.intervalMs !in DURATION_STEP..MAX_DURATION ||
                session.intervalMs % DURATION_STEP != 0L || session.deadlineMs < 0 ||
                (session.pausedRemainingMs == null && session.deadlineMs - nowMs > session.intervalMs) ||
                session.pausedRemainingMs?.let { it !in 1..session.intervalMs } == true)) {
            this.snapshot = this.snapshot.copy(session = null)
        }
        // Restoration consumes old boundaries silently, including an expired one-shot session.
        advance(nowMs, emit = false)
    }

    fun adjustDuration(direction: Int) {
        if (snapshot.session != null) return
        snapshot = snapshot.copy(selectedMs =
            (snapshot.selectedMs + direction.coerceIn(-1, 1) * DURATION_STEP)
                .coerceIn(DURATION_STEP, MAX_DURATION))
    }

    fun updateSettings(settings: EnsoSettings) {
        // Repetition is captured at Start; other preferences apply immediately.
        snapshot = snapshot.copy(settings = settings)
    }

    fun start(nowMs: Long): List<TimerEvent> {
        if (snapshot.session != null) return emptyList()
        snapshot = snapshot.copy(session = Session(snapshot.selectedMs,
            nowMs + snapshot.selectedMs, snapshot.settings.continuousGong))
        return if (snapshot.settings.startGong) listOf(TimerEvent.StartGong) else emptyList()
    }

    fun pause(nowMs: Long): List<TimerEvent> {
        val events = advance(nowMs)
        val session = snapshot.session ?: return events
        if (session.pausedRemainingMs == null) {
            snapshot = snapshot.copy(session = session.copy(
                pausedRemainingMs = (session.deadlineMs - nowMs).coerceAtLeast(1)))
        }
        return events
    }

    fun resume(nowMs: Long) {
        val session = snapshot.session ?: return
        val remaining = session.pausedRemainingMs ?: return
        snapshot = snapshot.copy(session = session.copy(
            deadlineMs = nowMs + remaining, pausedRemainingMs = null))
    }

    fun stop() { snapshot = snapshot.copy(session = null) }

    fun advance(nowMs: Long, emit: Boolean = true): List<TimerEvent> {
        val session = snapshot.session ?: return emptyList()
        if (session.pausedRemainingMs != null || nowMs < session.deadlineMs) return emptyList()
        snapshot = if (session.continuous) {
            val crossed = (nowMs - session.deadlineMs) / session.intervalMs + 1
            snapshot.copy(session = session.copy(deadlineMs = session.deadlineMs + crossed * session.intervalMs))
        } else snapshot.copy(session = null)
        // A delayed live wake may signal once; never play a catch-up burst.
        return if (emit) listOf(TimerEvent.IntervalCompleted(
            snapshot.settings.endGong, snapshot.settings.vibration)) else emptyList()
    }

    fun uiState(nowMs: Long): TimerUiState {
        val session = snapshot.session
        return TimerUiState(
            ready = true,
            selectedMs = snapshot.selectedMs,
            remainingMs = session?.pausedRemainingMs ?: session?.let {
                (it.deadlineMs - nowMs).coerceAtLeast(0)
            } ?: snapshot.selectedMs,
            phase = when { session == null -> TimerPhase.Idle
                session.pausedRemainingMs != null -> TimerPhase.Paused
                else -> TimerPhase.Running },
            settings = snapshot.settings,
            continuous = session?.continuous ?: snapshot.settings.continuousGong,
        )
    }
}

fun normalizeDuration(value: Long): Long =
    (value.coerceIn(DURATION_STEP, MAX_DURATION) / DURATION_STEP) * DURATION_STEP

fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0) + 999) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}

/** One press = one step, followed by increasingly frequent five-minute steps. */
class DurationHold(private val direction: Int) {
    private var active = false
    private var nextMs = 0L
    private var startedMs = 0L

    fun begin(nowMs: Long): Int {
        active = true
        startedMs = nowMs
        nextMs = nowMs + 450
        return direction.coerceIn(-1, 1)
    }

    fun repeat(nowMs: Long): Int {
        if (!active || nowMs < nextMs) return 0
        val held = nowMs - startedMs
        nextMs = nowMs + when { held < 1500 -> 280; held < 3000 -> 140; else -> 65 }
        return direction.coerceIn(-1, 1)
    }

    fun end() { active = false }
}
