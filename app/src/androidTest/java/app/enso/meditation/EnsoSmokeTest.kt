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

    @Test fun durationSettingsAndSessionLifecycle() {
        awaitState { it.ready }
        assertEquals(TimerPhase.Idle, state.phase)
        val original = state
        try {
            val direction = if (state.selectedMs == MAX_DURATION) -1 else 1
            val forward = if (direction > 0) "Increase" else "Decrease"
            val backward = if (direction > 0) "Decrease" else "Increase"
            compose.onNodeWithContentDescription("$forward meditation duration by 5 minutes").performClick()
            awaitState { it.selectedMs == original.selectedMs + direction * DURATION_STEP }
            compose.onNodeWithContentDescription("$backward meditation duration by 5 minutes").performClick()
            awaitState { it.selectedMs == original.selectedMs }

            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Vibration").assertIsDisplayed()
            compose.onNodeWithText("Done").assertIsDisplayed()
            compose.onNodeWithText("Continuous Gong").performClick()
            awaitState { it.settings.continuousGong != original.settings.continuousGong }
            compose.onNodeWithText("Vibration").performClick()
            awaitState { it.settings.vibration != original.settings.vibration }
            val saved = runBlocking { EnsoStore(compose.activity).load() }
            assertEquals(state.settings, saved.settings)
            assertEquals(state.selectedMs, saved.selectedMs)
            compose.onNodeWithText("Done").performClick()

            compose.onNodeWithText("Start").performClick()
            awaitState { it.phase == TimerPhase.Running }
            val running = state.remainingMs
            compose.onNodeWithContentDescription("Increase meditation duration by 5 minutes").assertDoesNotExist()
            compose.activityRule.scenario.recreate()
            awaitState { it.phase == TimerPhase.Running && it.remainingMs < running }
            assertEquals(original.selectedMs, state.selectedMs)

            compose.onNodeWithText("Pause").performClick()
            awaitState { it.phase == TimerPhase.Paused }
            val paused = state.remainingMs
            compose.onNodeWithText("Paused").assertIsDisplayed()
            compose.onNodeWithContentDescription("Decrease meditation duration by 5 minutes").assertDoesNotExist()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(1_100)
            assertEquals(paused, state.remainingMs)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithText("Resume").performClick()
            awaitState { it.phase == TimerPhase.Running && it.remainingMs < paused }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            val beforeBackground = state.remainingMs
            Thread.sleep(1_200)
            assertTrue(state.remainingMs < beforeBackground)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithText("Stop").performClick()
            awaitState { it.phase == TimerPhase.Idle }
            assertEquals(original.selectedMs, state.remainingMs)
            compose.onNodeWithText("Start").assertIsDisplayed()
            compose.onNodeWithText("Stop").assertDoesNotExist()
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
