package app.enso.meditation

import java.util.Locale

const val DURATION_STEP = 5 * 60_000L
const val MAX_DURATION = 24 * 60 * 60_000L
const val DEFAULT_DURATION = 10 * 60_000L
const val MAX_REPETITIONS = 99

data class EnsoSettings(
    val startGong: Boolean = true,
    val endGong: Boolean = true,
    val repeatGong: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val vibration: Boolean = false,
    val startGongSound: GongChoice = GongChoice.Root,
    val endGongSound: GongChoice = GongChoice.Bell,
    val middleGongSound: GongChoice = GongChoice.Heart,
    val showSaying: Boolean = true,
)

enum class TimerPhase { Idle, Running, Paused }

/** Deadline advances from the previous boundary, never from a UI tick or audio callback. */
data class Session(
    val intervalMs: Long,
    val deadlineMs: Long,
    val continuous: Boolean,
    val pausedRemainingMs: Long? = null,
    val repeatCount: Int = 1,
    val completedIntervals: Long = 0,
)

data class TimerSnapshot(
    val selectedMs: Long = DEFAULT_DURATION,
    val settings: EnsoSettings = EnsoSettings(),
    val session: Session? = null,
    val sayingIndex: Int = -1,
    val repeatCount: Int = 1,
    val repeatForever: Boolean = false,
)

data class TimerUiState(
    val ready: Boolean = false,
    val selectedMs: Long = DEFAULT_DURATION,
    val remainingMs: Long = DEFAULT_DURATION,
    val phase: TimerPhase = TimerPhase.Idle,
    val settings: EnsoSettings = EnsoSettings(),
    val continuous: Boolean = false,
    val storageWarning: Boolean = false,
    val saying: String = "",
    val repeatCount: Int = 1,
    val completedIntervals: Long = 0,
    val totalMs: Long? = DEFAULT_DURATION,
    val elapsedMs: Long = 0,
    val progress: Float = 0f,
)

sealed interface TimerEvent {
    data object StartGong : TimerEvent
    data class IntervalCompleted(val gong: Boolean, val vibrate: Boolean) : TimerEvent
}

/** Pure state machine. Its owner serializes calls and supplies a monotonic clock. */
class TimerEngine(snapshot: TimerSnapshot = TimerSnapshot(), nowMs: Long = 0) {
    var snapshot: TimerSnapshot = snapshot.copy(selectedMs = normalizeDuration(snapshot.selectedMs),
        repeatCount = snapshot.repeatCount.coerceIn(1, MAX_REPETITIONS))
        private set

    init {
        val session = snapshot.session
        if (session != null && (session.intervalMs !in DURATION_STEP..MAX_DURATION ||
                session.intervalMs % DURATION_STEP != 0L || session.deadlineMs < 0 ||
                session.repeatCount !in 1..MAX_REPETITIONS || session.completedIntervals < 0 ||
                (!session.continuous && session.completedIntervals >= session.repeatCount) ||
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
        snapshot = snapshot.copy(settings = settings)
    }

    fun adjustRepetitions(direction: Int) {
        if (snapshot.session != null) return
        snapshot = snapshot.copy(repeatCount =
            (snapshot.repeatCount + direction.coerceIn(-1, 1)).coerceIn(1, MAX_REPETITIONS),
            repeatForever = false)
    }

    fun setRepeatForever(forever: Boolean) {
        if (snapshot.session == null) snapshot = snapshot.copy(repeatForever = forever)
    }

    fun refreshSaying() {
        snapshot = snapshot.copy(sayingIndex = Sayings.next(snapshot.sayingIndex))
    }

    fun start(nowMs: Long): List<TimerEvent> {
        if (snapshot.session != null) return emptyList()
        refreshSaying()
        snapshot = snapshot.copy(session = Session(snapshot.selectedMs,
            nowMs + snapshot.selectedMs, snapshot.repeatForever, repeatCount = snapshot.repeatCount))
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
        val crossed = (nowMs - session.deadlineMs) / session.intervalMs + 1
        val finished = !session.continuous && crossed >= session.repeatCount - session.completedIntervals
        snapshot = if (finished) snapshot.copy(session = null) else snapshot.copy(
            session = session.copy(deadlineMs = session.deadlineMs + crossed * session.intervalMs,
                completedIntervals = session.completedIntervals + crossed))
        if (!finished && emit) refreshSaying()
        // A delayed live wake may signal once; never play a catch-up burst.
        return if (emit) listOf(TimerEvent.IntervalCompleted(
            if (finished) snapshot.settings.endGong else snapshot.settings.repeatGong,
            snapshot.settings.vibration)) else emptyList()
    }

    fun uiState(nowMs: Long): TimerUiState {
        val session = snapshot.session
        val interval = session?.intervalMs ?: snapshot.selectedMs
        val remaining = (session?.pausedRemainingMs ?: session?.let {
            it.deadlineMs - nowMs
        } ?: interval).coerceIn(0, interval)
        val count = session?.repeatCount ?: snapshot.repeatCount
        val continuous = session?.continuous ?: snapshot.repeatForever
        val total = if (continuous) null else interval * count
        val elapsed = if (session == null) 0 else session.completedIntervals * interval + interval - remaining
        return TimerUiState(
            ready = true,
            selectedMs = snapshot.selectedMs,
            remainingMs = remaining,
            phase = when { session == null -> TimerPhase.Idle
                session.pausedRemainingMs != null -> TimerPhase.Paused
                else -> TimerPhase.Running },
            settings = snapshot.settings,
            continuous = continuous,
            saying = Sayings.all.getOrNull(snapshot.sayingIndex).orEmpty(),
            repeatCount = count,
            completedIntervals = session?.completedIntervals ?: 0,
            totalMs = total,
            elapsedMs = elapsed,
            progress = if (session == null) 0f else if (total != null) {
                (elapsed.toDouble() / total).toFloat().coerceIn(0f, 1f)
            } else (1f - remaining.toFloat() / interval).coerceIn(0f, 1f),
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
