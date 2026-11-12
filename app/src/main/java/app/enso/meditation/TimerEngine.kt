package app.enso.meditation

import java.util.Locale

val ROTATION_STEP_OPTIONS = listOf(1, 2, 5, 10, 15)
const val DEFAULT_ROTATION_STEP_MINUTES = 5

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
    val rotationStepMinutes: Int = DEFAULT_ROTATION_STEP_MINUTES,
    val showTime: Boolean = true,
    val hapticFeedback: Boolean = true,
    val hardcoreMode: Boolean = false,
    val hardcorePending: Boolean = false,
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
    val sayingIndex: Int = -1,
    val repeatCount: Int = 1,
    val completedIntervals: Long = 0,
    val totalMs: Long? = DEFAULT_DURATION,
    val elapsedMs: Long = 0,
    val progress: Float = 0f,
)

/** What changes on the 250 ms clock; everything else lives in [TimerUiState]. */
data class TimerTick(
    val remainingMs: Long = DEFAULT_DURATION,
    val elapsedMs: Long = 0,
    val completedIntervals: Long = 0,
)

/** Layout state must not change identity on every tick, or the whole screen recomposes. */
fun TimerUiState.withoutTick() = copy(remainingMs = 0, elapsedMs = 0,
    progress = 0f, completedIntervals = 0)

/** Text and tallies only care about whole seconds and completed boundaries. */
fun TimerUiState.tick() = TimerTick(
    remainingMs = (remainingMs + 999) / 1000 * 1000,
    elapsedMs = elapsedMs / 1000 * 1000,
    completedIntervals = completedIntervals,
)

sealed interface TimerEvent {
    data object SelectionTick : TimerEvent
    data object StartGong : TimerEvent
    data class IntervalCompleted(val gong: Boolean, val vibrate: Boolean) : TimerEvent
}

/** Pure state machine. Its owner serializes calls and supplies a monotonic clock. */
class TimerEngine(snapshot: TimerSnapshot = TimerSnapshot(), nowMs: Long = 0) {
    var snapshot: TimerSnapshot = snapshot.copy(selectedMs = normalizeDuration(snapshot.selectedMs),
        repeatCount = snapshot.repeatCount.coerceIn(1, MAX_REPETITIONS),
        settings = normalizeSettings(snapshot.settings))
        private set

    init {
        val session = snapshot.session
        if (session != null && (session.intervalMs !in DURATION_STEP..MAX_DURATION ||
                session.intervalMs % 60_000L != 0L || session.deadlineMs < 0 ||
                session.repeatCount !in 1..MAX_REPETITIONS || session.completedIntervals < 0 ||
                (!session.continuous && session.completedIntervals >= session.repeatCount) ||
                (session.pausedRemainingMs == null && session.deadlineMs - nowMs > session.intervalMs) ||
                session.pausedRemainingMs?.let { it !in 1..session.intervalMs } == true)) {
            this.snapshot = this.snapshot.copy(session = null)
        }
        // Restoration consumes old boundaries silently, including an expired one-shot session.
        advance(nowMs, emit = false)
    }

    fun adjustDuration(direction: Int): List<TimerEvent> {
        if (snapshot.session != null) return emptyList()
        val selected = (snapshot.selectedMs + direction.coerceIn(-1, 1) * snapshot.settings.rotationStepMinutes * 60_000L)
            .coerceIn(DURATION_STEP, MAX_DURATION)
        if (selected == snapshot.selectedMs) return emptyList()
        snapshot = snapshot.copy(selectedMs = selected)
        return selectionFeedback()
    }

    fun updateSettings(settings: EnsoSettings) {
        snapshot = snapshot.copy(settings = normalizeSettings(settings))
    }

    fun adjustRepetitions(direction: Int): List<TimerEvent> {
        if (snapshot.session != null || direction == 0) return emptyList()
        val count = if (snapshot.repeatForever) 0 else snapshot.repeatCount
        val next = (count + direction.coerceIn(-1, 1)).coerceIn(0, MAX_REPETITIONS)
        if (next == count) return emptyList()
        snapshot = snapshot.copy(repeatCount = if (next == 0) snapshot.repeatCount else next,
            repeatForever = next == 0)
        return selectionFeedback()
    }

    private fun selectionFeedback(): List<TimerEvent> =
        if (snapshot.settings.hapticFeedback) listOf(TimerEvent.SelectionTick) else emptyList()

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
            sayingIndex = snapshot.sayingIndex,
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
    (value.coerceIn(DURATION_STEP, MAX_DURATION) / 60_000L) * 60_000L

fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0) + 999) / 1000
    return if (seconds >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
}

private fun normalizeSettings(settings: EnsoSettings) = settings.copy(
    rotationStepMinutes = settings.rotationStepMinutes.takeIf { it in ROTATION_STEP_OPTIONS }
        ?: DEFAULT_ROTATION_STEP_MINUTES,
)
