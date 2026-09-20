package app.enso.meditation

import org.junit.Assert.*
import org.junit.Test

class DurationHoldTest {
    @Test fun tapMakesExactlyOneStep() {
        val hold = DurationHold(1)
        assertEquals(1, hold.begin(0))
        assertEquals(0, hold.repeat(100))
        hold.end()
        assertEquals(0, hold.repeat(1_000))
    }

    @Test fun holdAcceleratesFrequencyButNeverStepSize() {
        val hold = DurationHold(1)
        val times = mutableListOf<Long>()
        hold.begin(0)
        for (time in 1L..5_000) {
            val step = hold.repeat(time)
            if (step != 0) { assertEquals(1, step); times += time }
        }
        assertEquals(450L, times.first())
        assertEquals(280L, times[1] - times[0])
        assertEquals(65L, times.last() - times[times.lastIndex - 1])
    }

    @Test fun repeatedIncreasesAndDecreasesStayInFiveMinuteSteps() {
        for (direction in listOf(-1, 1)) {
            val engine = TimerEngine(TimerSnapshot(selectedMs = 12 * 60 * 60_000L))
            val hold = DurationHold(direction)
            engine.adjustDuration(hold.begin(0))
            for (time in 1L..3_000) {
                val before = engine.snapshot.selectedMs
                val step = hold.repeat(time)
                engine.adjustDuration(step)
                assertEquals(step * DURATION_STEP, engine.snapshot.selectedMs - before)
            }
            hold.end()
            val released = engine.snapshot.selectedMs
            engine.adjustDuration(hold.repeat(5_000))
            assertEquals(released, engine.snapshot.selectedMs)
        }
    }

    @Test fun heldAdjustmentRespectsBothLimits() {
        for (direction in listOf(-1, 1)) {
            val limit = if (direction < 0) DURATION_STEP else MAX_DURATION
            val engine = TimerEngine(TimerSnapshot(selectedMs = limit))
            val hold = DurationHold(direction)
            engine.adjustDuration(hold.begin(0))
            for (time in 1L..8_000) engine.adjustDuration(hold.repeat(time))
            assertEquals(limit, engine.snapshot.selectedMs)
        }
    }
}
