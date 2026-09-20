package app.enso.meditation

import org.junit.Assert.*
import org.junit.Test

class TimerEngineTest {
    private val interval = DEFAULT_DURATION
    private fun repeating() = TimerEngine(TimerSnapshot(settings = EnsoSettings(continuousGong = true)))

    @Test fun defaultsMatchSpecification() {
        val engine = TimerEngine()
        assertEquals(600_000L, engine.snapshot.selectedMs)
        assertEquals(TimerPhase.Idle, engine.uiState(0).phase)
        assertEquals(EnsoSettings(true, true, false, true, false), engine.snapshot.settings)
    }

    @Test fun durationChangesByExactlyFiveMinutes() {
        val engine = TimerEngine()
        engine.adjustDuration(1)
        assertEquals(900_000L, engine.snapshot.selectedMs)
        engine.adjustDuration(-1)
        assertEquals(600_000L, engine.snapshot.selectedMs)
    }

    @Test fun durationBoundsCannotOverflow() {
        val engine = TimerEngine()
        repeat(400) { engine.adjustDuration(-1) }
        assertEquals(DURATION_STEP, engine.snapshot.selectedMs)
        repeat(400) { engine.adjustDuration(Int.MAX_VALUE) }
        assertEquals(MAX_DURATION, engine.snapshot.selectedMs)
        engine.adjustDuration(Int.MIN_VALUE)
        assertEquals(MAX_DURATION - DURATION_STEP, engine.snapshot.selectedMs)
    }

    @Test fun corruptSelectedDurationsAreNormalized() {
        assertEquals(DURATION_STEP, TimerEngine(TimerSnapshot(Long.MIN_VALUE)).snapshot.selectedMs)
        assertEquals(MAX_DURATION, TimerEngine(TimerSnapshot(Long.MAX_VALUE)).snapshot.selectedMs)
        assertEquals(600_000L, TimerEngine(TimerSnapshot(650_000)).snapshot.selectedMs)
    }

    @Test fun selectedDurationAndSettingsRestore() {
        val original = TimerEngine()
        repeat(5) { original.adjustDuration(1) }
        original.updateSettings(EnsoSettings(false, false, true, false, true))
        val restored = TimerEngine(original.snapshot, 85_000)
        assertEquals(2_100_000L, restored.uiState(85_000).remainingMs)
        assertEquals(original.snapshot.settings, restored.snapshot.settings)
    }

    @Test fun activeAndPausedDurationsAreLocked() {
        val engine = TimerEngine()
        engine.start(0)
        engine.adjustDuration(1)
        assertEquals(interval, engine.snapshot.selectedMs)
        engine.pause(1_000)
        engine.adjustDuration(-1)
        assertEquals(interval, engine.snapshot.selectedMs)
        engine.stop()
        engine.adjustDuration(1)
        assertEquals(interval + DURATION_STEP, engine.snapshot.selectedMs)
    }

    @Test fun countdownUsesElapsedTimeNotTickCount() {
        val engine = TimerEngine()
        engine.start(12_345)
        assertEquals(576_544L, engine.uiState(35_801).remainingMs)
        assertTrue(engine.advance(400_000).isEmpty())
        assertEquals(212_345L, engine.uiState(400_000).remainingMs)
    }

    @Test fun duplicateStartDoesNotResetOrReplay() {
        val engine = TimerEngine()
        assertEquals(listOf(TimerEvent.StartGong), engine.start(500))
        assertTrue(engine.start(2_000).isEmpty())
        assertEquals(600_500L, engine.snapshot.session!!.deadlineMs)
    }

    @Test fun disabledStartGongIsSilent() {
        val engine = TimerEngine(TimerSnapshot(settings = EnsoSettings(startGong = false)))
        assertTrue(engine.start(0).isEmpty())
        assertEquals(TimerPhase.Running, engine.uiState(0).phase)
    }

    @Test fun pauseFreezesExactRemainderWithoutEvents() {
        val engine = TimerEngine()
        engine.start(0)
        assertTrue(engine.pause(123_456).isEmpty())
        assertTrue(engine.advance(9_000_000).isEmpty())
        assertEquals(476_544L, engine.uiState(9_000_000).remainingMs)
        assertEquals(TimerPhase.Paused, engine.uiState(9_000_000).phase)
        engine.pause(9_000_001)
        assertEquals(476_544L, engine.uiState(9_000_001).remainingMs)
    }

    @Test fun resumeContinuesExactRemainder() {
        val engine = TimerEngine()
        engine.start(0)
        engine.pause(123_456)
        engine.resume(1_000_000)
        assertEquals(476_544L, engine.uiState(1_000_000).remainingMs)
        engine.resume(1_000_010)
        assertEquals(476_534L, engine.uiState(1_000_010).remainingMs)
        assertEquals(1, engine.advance(1_476_544).size)
    }

    @Test fun stopReturnsToSelectionAndCancelsFutureBoundaries() {
        val engine = repeating()
        engine.start(0)
        engine.stop()
        assertEquals(TimerPhase.Idle, engine.uiState(500).phase)
        assertEquals(interval, engine.uiState(500).remainingMs)
        assertTrue(engine.advance(interval * 100).isEmpty())
    }

    @Test fun pausedStopAlsoReturnsToIdle() {
        val engine = TimerEngine()
        engine.start(0)
        engine.pause(150)
        engine.stop()
        engine.resume(500)
        assertNull(engine.snapshot.session)
    }

    @Test fun normalCompletionOccursExactlyOnce() {
        val engine = TimerEngine()
        engine.start(0)
        assertTrue(engine.advance(interval - 1).isEmpty())
        assertEquals(listOf(TimerEvent.IntervalCompleted(true, false)), engine.advance(interval))
        assertTrue(engine.advance(interval).isEmpty())
        assertTrue(engine.advance(interval + 10_000).isEmpty())
        assertEquals(TimerPhase.Idle, engine.uiState(interval).phase)
    }

    @Test fun continuousRolloverUsesOriginalBoundary() {
        val engine = repeating()
        engine.start(123)
        assertEquals(listOf(TimerEvent.IntervalCompleted(true, false)), engine.advance(interval + 130))
        assertEquals(interval * 2 + 123, engine.snapshot.session!!.deadlineMs)
        assertEquals(interval - 7, engine.uiState(interval + 130).remainingMs)
        assertTrue(engine.advance(interval + 130).isEmpty())
    }

    @Test fun thousandsOfDelayedTicksDoNotAccumulateIntervalDrift() {
        val engine = repeating()
        engine.start(17)
        repeat(10_000) { index ->
            val boundary = 17 + (index + 1) * interval
            assertEquals(1, engine.advance(boundary + 89).size)
            assertEquals(boundary + interval, engine.snapshot.session!!.deadlineMs)
            assertTrue(engine.advance(boundary + 99).isEmpty())
        }
    }

    @Test fun pauseShiftsAllFutureBoundariesOnlyByPausedTime() {
        val engine = repeating()
        engine.start(0)
        engine.pause(100_000)
        engine.resume(150_000)
        engine.advance(interval + 50_015)
        assertEquals(2 * interval + 50_000, engine.snapshot.session!!.deadlineMs)
        engine.advance(2 * interval + 50_121)
        assertEquals(3 * interval + 50_000, engine.snapshot.session!!.deadlineMs)
    }

    @Test fun eachEligibleBoundaryHasOneGongAndNoNewStartGong() {
        val engine = repeating()
        val events = engine.start(0).toMutableList()
        repeat(10) { index ->
            events += engine.advance((index + 1) * interval)
            events += engine.advance((index + 1) * interval)
        }
        assertEquals(1, events.count { it is TimerEvent.StartGong })
        assertEquals(10, events.count { it is TimerEvent.IntervalCompleted && it.gong })
    }

    @Test fun continuousAndEndGongHaveIndependentResponsibilities() {
        val engine = TimerEngine(TimerSnapshot(settings = EnsoSettings(
            continuousGong = true, endGong = false, vibration = true)))
        engine.start(0)
        assertEquals(listOf(TimerEvent.IntervalCompleted(false, true)), engine.advance(interval))
        assertEquals(TimerPhase.Running, engine.uiState(interval).phase)
    }

    @Test fun settingsApplyImmediatelyExceptSessionRepetition() {
        val engine = repeating()
        engine.start(0)
        engine.updateSettings(EnsoSettings(endGong = false, continuousGong = false, vibration = true))
        assertEquals(listOf(TimerEvent.IntervalCompleted(false, true)), engine.advance(interval))
        assertNotNull(engine.snapshot.session)
        engine.stop()
        engine.start(interval)
        engine.advance(interval * 2)
        assertNull(engine.snapshot.session)
    }

    @Test fun restorationSkipsAllMissedBoundariesWithoutEventsOrDrift() {
        val engine = repeating()
        engine.start(0)
        val restored = TimerEngine(engine.snapshot, interval * 3 + 123)
        assertEquals(interval * 4, restored.snapshot.session!!.deadlineMs)
        assertTrue(restored.advance(interval * 3 + 124).isEmpty())
        assertEquals(listOf(TimerEvent.IntervalCompleted(true, false)), restored.advance(interval * 4))
    }

    @Test fun restorationExactlyOnBoundaryIsSilent() {
        val engine = repeating()
        engine.start(0)
        val restored = TimerEngine(engine.snapshot, interval)
        assertTrue(restored.advance(interval).isEmpty())
        assertEquals(interval * 2, restored.snapshot.session!!.deadlineMs)
    }

    @Test fun expiredOneShotRestoresIdleWithoutGong() {
        val engine = TimerEngine()
        engine.start(0)
        val restored = TimerEngine(engine.snapshot, interval + 1)
        assertNull(restored.snapshot.session)
        assertTrue(restored.advance(interval + 2).isEmpty())
    }

    @Test fun pausedSessionRestoresWithoutAdvancing() {
        val engine = repeating()
        engine.start(0)
        engine.pause(42_123)
        val restored = TimerEngine(engine.snapshot, interval * 10)
        assertEquals(interval - 42_123, restored.uiState(interval * 10).remainingMs)
        assertEquals(TimerPhase.Paused, restored.uiState(interval * 10).phase)
    }

    @Test fun delayedLiveExecutionDoesNotReplayABurst() {
        val engine = repeating()
        engine.start(0)
        assertEquals(1, engine.advance(interval * 30 + 555).size)
        assertEquals(interval * 31, engine.snapshot.session!!.deadlineMs)
    }

    @Test fun malformedSessionIsDiscarded() {
        assertNull(TimerEngine(TimerSnapshot(session = Session(0, 100, true))).snapshot.session)
        assertNull(TimerEngine(TimerSnapshot(session = Session(interval, 100, true, -1))).snapshot.session)
        assertNull(TimerEngine(TimerSnapshot(session = Session(interval, Long.MAX_VALUE, true))).snapshot.session)
    }

    @Test fun timeFormattingIsStableAndRoundsRemainingTimeUp() {
        assertEquals("05:00", formatTime(300_000))
        assertEquals("35:42", formatTime(2_142_000))
        assertEquals("1:00:00", formatTime(3_600_000))
        assertEquals("2:35:17", formatTime(9_317_000))
        assertEquals("24:00:00", formatTime(MAX_DURATION))
        assertEquals("00:01", formatTime(1))
        assertEquals("00:00", formatTime(0))
    }
}
