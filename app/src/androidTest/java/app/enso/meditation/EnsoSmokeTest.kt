package app.enso.meditation

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real controls and persistence without shortening production timer limits. */
@RunWith(AndroidJUnit4::class)
class EnsoSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val controller get() = (compose.activity.application as EnsoApplication).controller
    private val state get() = controller.state.value
    private fun awaitState(test: (TimerUiState) -> Boolean) = compose.waitUntil(5_000) { test(state) }

    @Test fun repetitionSelectionAndSessionPersistWithoutChangingGongSettings() {
        awaitState { it.ready }
        assertEquals(TimerPhase.Idle, state.phase)
        val original = state
        try {
            controller.setRepeatForever(false)
            awaitState { !it.continuous }
            while (state.repeatCount != 2) {
                val before = state.repeatCount
                controller.adjustRepetitions(if (before > 2) -1 else 1)
                awaitState { it.repeatCount != before }
            }
            compose.onNodeWithContentDescription("Increase repetitions").performClick()
            awaitState { it.repeatCount == 3 }
            compose.onNodeWithContentDescription("Decrease repetitions").performClick()
            awaitState { it.repeatCount == 2 }
            compose.onNodeWithContentDescription("Repeat forever").performClick()
            awaitState { it.continuous && it.totalMs == null }
            compose.onNodeWithContentDescription("Finite repetitions").performClick()
            awaitState { !it.continuous && it.totalMs == 2 * it.selectedMs }
            assertEquals(original.settings, state.settings)
            val saved = runBlocking { EnsoStore(compose.activity).load() }
            assertEquals(2, saved.repeatCount)
            assertFalse(saved.repeatForever)
            compose.onNodeWithContentDescription("Start").performClick()
            awaitState { it.phase == TimerPhase.Running }
            compose.onNodeWithContentDescription("Increase repetitions").assertDoesNotExist()
            compose.onNodeWithContentDescription("Repeat forever").assertDoesNotExist()
            compose.onNodeWithContentDescription("Interval 1 of 2").assertIsDisplayed()
            compose.onNodeWithContentDescription("Pause").performClick()
            awaitState { it.phase == TimerPhase.Paused }
            val paused = state
            val pausedSnapshot = runBlocking { EnsoStore(compose.activity).load() }
            assertEquals(2, pausedSnapshot.session!!.repeatCount)
            assertEquals(0L, pausedSnapshot.session.completedIntervals)
            assertEquals(paused.remainingMs, pausedSnapshot.session.pausedRemainingMs)
            compose.activityRule.scenario.recreate()
            compose.onNodeWithContentDescription("Interval 1 of 2").assertIsDisplayed()
            assertEquals(paused.elapsedMs, state.elapsedMs)
            assertEquals(paused.progress, state.progress, 0f)
            compose.onNodeWithContentDescription("Stop").performClick()
            awaitState { it.phase == TimerPhase.Idle }
            assertEquals(2, state.repeatCount)
            assertNull(runBlocking { EnsoStore(compose.activity).load() }.session)
        } finally {
            compose.runOnIdle { MeditationService.send(compose.activity, SessionAction.Stop) }
            awaitState { it.phase == TimerPhase.Idle }
            while (state.repeatCount != original.repeatCount) {
                val before = state.repeatCount
                controller.adjustRepetitions(if (before > original.repeatCount) -1 else 1)
                awaitState { it.repeatCount != before }
            }
            controller.setRepeatForever(original.continuous)
            awaitState { it.continuous == original.continuous }
        }
    }

    @Test fun durationSettingsAndSessionLifecycle() {
        awaitState { it.ready }
        assertEquals(TimerPhase.Idle, state.phase)
        val original = state
        try {
            controller.changeSettings { it.copy(showSaying = true) }
            awaitState { it.settings.showSaying && it.saying.isNotEmpty() }
            compose.onNodeWithText(state.saying).assertIsDisplayed()
            val direction = if (state.selectedMs == MAX_DURATION) -1 else 1
            val forward = if (direction > 0) "Increase" else "Decrease"
            val backward = if (direction > 0) "Decrease" else "Increase"
            compose.onNodeWithContentDescription("$forward meditation duration by 5 minutes").performClick()
            awaitState { it.selectedMs == original.selectedMs + direction * DURATION_STEP }
            compose.onNodeWithContentDescription("$backward meditation duration by 5 minutes").performClick()
            awaitState { it.selectedMs == original.selectedMs }

            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Vibration").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close settings").assertIsDisplayed()
            // Tap the label itself; the merged row's center overlaps the sound-preview control.
            compose.onNodeWithText("Repeat Gong", useUnmergedTree = true).performClick()
            awaitState { it.settings.repeatGong != original.settings.repeatGong }
            assertEquals(original.repeatCount, state.repeatCount)
            assertEquals(original.continuous, state.continuous)
            compose.onNodeWithText("Show Saying").performClick()
            awaitState { !it.settings.showSaying }
            compose.onNodeWithText("Vibration").performClick()
            awaitState { it.settings.vibration != original.settings.vibration }
            val saved = runBlocking { EnsoStore(compose.activity).load() }
            assertEquals(state.settings, saved.settings)
            assertEquals(state.selectedMs, saved.selectedMs)
            compose.onNodeWithContentDescription("Close settings").performClick()
            compose.onNodeWithText(state.saying).assertDoesNotExist()

            compose.onNodeWithContentDescription("Start").performClick()
            awaitState { it.phase == TimerPhase.Running }
            assertNotEquals(original.saying, state.saying)
            val runningSaying = state.saying
            val running = state.remainingMs
            compose.onNodeWithContentDescription("Increase meditation duration by 5 minutes").assertDoesNotExist()
            compose.activityRule.scenario.recreate()
            awaitState { it.phase == TimerPhase.Running && it.remainingMs < running }
            assertEquals(original.selectedMs, state.selectedMs)
            assertEquals(runningSaying, state.saying)

            compose.onNodeWithContentDescription("Pause").performClick()
            awaitState { it.phase == TimerPhase.Paused }
            assertEquals(runningSaying, state.saying)
            val paused = state.remainingMs
            compose.onNodeWithText("Paused").assertIsDisplayed()
            compose.onNodeWithContentDescription("Decrease meditation duration by 5 minutes").assertDoesNotExist()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(1_100)
            assertEquals(paused, state.remainingMs)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithContentDescription("Resume").performClick()
            awaitState { it.phase == TimerPhase.Running && it.remainingMs < paused }
            assertEquals(runningSaying, state.saying)
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            val beforeBackground = state.remainingMs
            Thread.sleep(1_200)
            assertTrue(state.remainingMs < beforeBackground)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithContentDescription("Stop").performClick()
            awaitState { it.phase == TimerPhase.Idle }
            assertEquals(original.selectedMs, state.remainingMs)
            compose.onNodeWithContentDescription("Start").assertIsDisplayed()
            compose.onNodeWithContentDescription("Stop").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(0, compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            assertNull(runBlocking { EnsoStore(compose.activity).load() }.session)
        } finally {
            compose.runOnIdle { MeditationService.send(compose.activity, SessionAction.Stop) }
            awaitState { it.phase == TimerPhase.Idle }
            controller.changeSettings { original.settings }
            awaitState { it.settings == original.settings }
        }
    }

    @Test fun realHoldRepeatsAndStopsOnRelease() {
        awaitState { it.ready }
        val original = state.selectedMs
        val direction = if (original > MAX_DURATION / 2) -1 else 1
        val label = if (direction > 0) "Increase" else "Decrease"
        try {
            compose.onNodeWithContentDescription("$label meditation duration by 5 minutes")
                .performTouchInput { down(center) }
            // The gesture coroutine uses Compose's test scheduler; the hold policy uses real monotonic time.
            repeat(70) {
                Thread.sleep(16)
                compose.mainClock.advanceTimeBy(16)
            }
            compose.onNodeWithContentDescription("$label meditation duration by 5 minutes")
                .performTouchInput { up() }
            awaitState { kotlin.math.abs(it.selectedMs - original) >= 3 * DURATION_STEP }
            val released = state.selectedMs
            assertEquals(0L, (released - original) % DURATION_STEP)
            Thread.sleep(550)
            assertEquals(released, state.selectedMs)
        } finally {
            while (state.selectedMs != original) {
                val before = state.selectedMs
                controller.adjustDuration(if (before > original) -1 else 1)
                awaitState { it.selectedMs != before }
            }
        }
    }
}
