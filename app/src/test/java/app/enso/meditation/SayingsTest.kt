package app.enso.meditation

import org.junit.Assert.*
import org.junit.Test

class SayingsTest {
    @Test fun selectionNeverImmediatelyRepeatsAndHandlesOldIndices() {
        for (previous in Sayings.all.indices + listOf(-1, Int.MAX_VALUE, Int.MIN_VALUE)) {
            repeat(100) {
                val next = Sayings.next(previous)
                assertTrue(next in Sayings.all.indices)
                assertNotEquals(previous, next)
            }
        }
    }

    @Test fun launchRefreshUsesSavedSayingWithoutChangingSession() {
        val original = TimerEngine()
        original.start(0)
        val saved = original.snapshot
        val restored = TimerEngine(saved, 100)
        restored.refreshSaying()
        assertNotEquals(saved.sayingIndex, restored.snapshot.sayingIndex)
        assertEquals(saved.session, restored.snapshot.session)
    }

    @Test fun silentStartChangesSayingButPauseResumeAndDuplicateStartDoNot() {
        val engine = TimerEngine(TimerSnapshot(settings = EnsoSettings(startGong = false)))
        engine.refreshSaying()
        val launched = engine.uiState(0).saying
        engine.start(0)
        val started = engine.uiState(0).saying
        assertNotEquals(launched, started)
        engine.start(1)
        engine.advance(100)
        engine.pause(100)
        engine.resume(200)
        assertEquals(started, engine.uiState(200).saying)
        engine.stop()
        assertEquals(started, engine.uiState(200).saying)
        engine.start(300)
        assertNotEquals(started, engine.uiState(300).saying)
    }

    @Test fun silentRepeatingBoundariesRotateEvenWhenSayingIsHidden() {
        val engine = TimerEngine(TimerSnapshot(settings = EnsoSettings(
            startGong = false, endGong = false, continuousGong = true, showSaying = false)))
        engine.start(0)
        val started = engine.uiState(0).saying
        engine.advance(DEFAULT_DURATION * 10 + 123)
        val next = engine.uiState(DEFAULT_DURATION * 10 + 123).saying
        assertNotEquals(started, next)
        engine.advance(DEFAULT_DURATION * 10 + 124)
        assertEquals(next, engine.uiState(DEFAULT_DURATION * 10 + 124).saying)
        assertEquals(DEFAULT_DURATION * 11, engine.snapshot.session!!.deadlineMs)
    }

    @Test fun oneShotCompletionKeepsSayingUntilNextStart() {
        val engine = TimerEngine()
        engine.start(0)
        val started = engine.uiState(0).saying
        engine.advance(DEFAULT_DURATION)
        assertEquals(started, engine.uiState(DEFAULT_DURATION).saying)
        engine.start(DEFAULT_DURATION + 1)
        assertNotEquals(started, engine.uiState(DEFAULT_DURATION + 1).saying)
    }
}
