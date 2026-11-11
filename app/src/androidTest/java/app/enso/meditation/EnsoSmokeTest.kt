package app.enso.meditation

import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.cos
import kotlin.math.sin
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
            repeat(2) { compose.onNodeWithContentDescription("Decrease repetitions").performClick() }
            awaitState { it.continuous && it.totalMs == null }
            compose.onNodeWithContentDescription("Unlimited duration").assertDoesNotExist()
            compose.onNodeWithContentDescription("Unlimited repetitions (0)").assertIsDisplayed()
            repeat(2) { compose.onNodeWithContentDescription("Increase repetitions").performClick() }
            awaitState { !it.continuous && it.totalMs == 2 * it.selectedMs }
            assertEquals(original.settings, state.settings)
            val saved = runBlocking { EnsoStore(compose.activity).load() }
            assertEquals(2, saved.repeatCount)
            assertFalse(saved.repeatForever)
            compose.onNodeWithContentDescription("Start").performClick()
            awaitState { it.phase == TimerPhase.Running }
            compose.onNodeWithContentDescription("Increase repetitions").assertDoesNotExist()
            compose.onNodeWithContentDescription("Timer circle").assertDoesNotExist()
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
            compose.onNodeWithContentDescription("Reset").performClick()
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
            controller.changeSettings { it.copy(showSaying = true, rotationStepMinutes = 5, showTime = true, hapticFeedback = true) }
            awaitState { it.settings.showSaying && it.settings.showTime && it.settings.hapticFeedback && it.settings.rotationStepMinutes == 5 && it.saying.isNotEmpty() }
            compose.waitUntil(5_000) {
                runCatching { compose.onNodeWithText(state.saying).assertIsDisplayed() }.isSuccess
            }
            val direction = if (state.selectedMs == MAX_DURATION) -1 else 1
            val forward = if (direction > 0) "Increase" else "Decrease"
            val backward = if (direction > 0) "Decrease" else "Increase"
            adjustDurationAccessibly(forward)
            awaitState { it.selectedMs == original.selectedMs + direction * DURATION_STEP }
            adjustDurationAccessibly(backward)
            awaitState { it.selectedMs == original.selectedMs }

            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Minutes per step").assertIsDisplayed()
            compose.onNodeWithContentDescription("Smaller minute step").performClick()
            awaitState { it.settings.rotationStepMinutes == 2 }
            assertEquals(2, runBlocking { EnsoStore(compose.activity).load() }.settings.rotationStepMinutes)
            compose.onNodeWithText("Haptic Feedback").performScrollTo().performClick()
            awaitState { !it.settings.hapticFeedback }
            assertFalse(runBlocking { EnsoStore(compose.activity).load() }.settings.hapticFeedback)
            compose.activityRule.scenario.recreate()
            awaitState { !it.settings.hapticFeedback }
            compose.onNodeWithText("Haptic Feedback").performScrollTo().performClick()
            awaitState { it.settings.hapticFeedback }
            compose.onNodeWithContentDescription("2 minutes per rotation step").assertIsDisplayed()
            compose.onNodeWithContentDescription("Larger minute step").performClick()
            awaitState { it.settings.rotationStepMinutes == 5 }
            compose.onNodeWithText("Vibration").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close settings").assertIsDisplayed()
            // Tap the label itself; the merged row's center overlaps the sound-preview control.
            compose.onNodeWithText("Repeat Gong", useUnmergedTree = true).performClick()
            awaitState { it.settings.repeatGong != original.settings.repeatGong }
            assertEquals(original.repeatCount, state.repeatCount)
            assertEquals(original.continuous, state.continuous)
            compose.onNodeWithText("Show Time").performScrollTo().performClick()
            awaitState { !it.settings.showTime }
            assertFalse(runBlocking { EnsoStore(compose.activity).load() }.settings.showTime)
            compose.activityRule.scenario.recreate()
            awaitState { !it.settings.showTime }
            compose.onNodeWithContentDescription("Close settings").performClick()
            compose.onNodeWithContentDescription("${formatTime(state.selectedMs)} selected").assertDoesNotExist()
            compose.onNodeWithContentDescription(if (state.continuous) "Unlimited repetitions (0)"
                else "${state.repeatCount} repetitions").assertIsDisplayed()
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Show Time").performScrollTo().performClick()
            awaitState { it.settings.showTime }
            compose.onNodeWithText("Show Saying").performScrollTo().performClick()
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
            compose.onNodeWithContentDescription("Timer circle").assertDoesNotExist()
            compose.activityRule.scenario.recreate()
            awaitState { it.phase == TimerPhase.Running && it.remainingMs < running }
            assertEquals(original.selectedMs, state.selectedMs)
            assertEquals(runningSaying, state.saying)

            compose.onNodeWithContentDescription("Pause").performClick()
            awaitState { it.phase == TimerPhase.Paused }
            assertEquals(runningSaying, state.saying)
            val paused = state.remainingMs
            compose.onNodeWithText("Paused").assertDoesNotExist()
            compose.onNodeWithContentDescription("Session progress").assertDoesNotExist()
            val timeDescription = if (state.totalMs == null)
                "${formatTime(state.elapsedMs / 1000 * 1000)} elapsed, unlimited duration"
                else "${formatTime(state.elapsedMs / 1000 * 1000)} elapsed, ${formatTime(state.totalMs!!)} total duration"
            if (state.continuous) compose.onNodeWithContentDescription(timeDescription).assertDoesNotExist()
            else compose.onNodeWithContentDescription(timeDescription).assertIsDisplayed()
            compose.onNodeWithContentDescription("Timer circle").assertDoesNotExist()
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
            compose.onNodeWithContentDescription("Reset").performClick()
            awaitState { it.phase == TimerPhase.Idle }
            assertEquals(original.selectedMs, state.remainingMs)
            compose.onNodeWithContentDescription("Start").assertIsDisplayed()
            compose.onNodeWithContentDescription("Reset").assertDoesNotExist()
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

    private fun adjustDurationAccessibly(direction: String) {
        val actions = compose.onNodeWithContentDescription("Timer circle").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            assertTrue(actions.first { it.label == "$direction meditation duration by 5 minutes" }.action())
        }
    }

    @Test fun circleDistinguishesRotationTapAndHold() {
        awaitState { it.ready }
        val original = state
        val direction = if (original.selectedMs > MAX_DURATION / 2) -1 else 1
        try {
            controller.changeSettings { it.copy(rotationStepMinutes = 5) }
            awaitState { it.settings.rotationStepMinutes == 5 }
            val circle = compose.onNodeWithContentDescription("Timer circle")
            circle.performTouchInput {
                val radius = width * .4f
                fun point(degrees: Float): Offset {
                    val angle = Math.toRadians(degrees.toDouble())
                    return center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
                }
                val start = if (direction > 0) -120f else -60f
                down(point(start))
                for (step in 1..13) moveTo(point(start + step * 5f * direction), 25)
                up()
            }
            awaitState { it.selectedMs == original.selectedMs + direction * 4 * DURATION_STEP }
            assertEquals(original.repeatCount, state.repeatCount)
            assertEquals(original.continuous, state.continuous)
            val rotated = state.selectedMs
            circle.performTouchInput { down(centerLeft + Offset(width * .2f, 0f)); advanceEventTime(800); up() }
            compose.waitForIdle()
            assertEquals(rotated, state.selectedMs)
            assertEquals(original.repeatCount, state.repeatCount)
            circle.performTouchInput { click(centerRight - Offset(width * .2f, 0f)) }
            awaitState { !it.continuous && it.repeatCount ==
                (if (original.continuous) 1 else (original.repeatCount + 1).coerceAtMost(MAX_REPETITIONS)) }
            assertEquals(rotated, state.selectedMs)
            circle.performTouchInput { click(centerLeft + Offset(width * .2f, 0f)) }
            awaitState { if (original.continuous) it.continuous else it.repeatCount ==
                (if (original.repeatCount == MAX_REPETITIONS) MAX_REPETITIONS - 1 else original.repeatCount) }
            circle.performTouchInput {
                val radius = width * .4f
                fun point(degrees: Float): Offset {
                    val angle = Math.toRadians(degrees.toDouble())
                    return center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
                }
                val start = if (direction > 0) -60f else -120f
                down(point(start))
                for (step in 1..13) moveTo(point(start - step * 5f * direction), 25)
                up()
            }
            awaitState { it.selectedMs == rotated - direction * 4 * DURATION_STEP }
            while (state.selectedMs > DURATION_STEP) {
                val before = state.selectedMs
                controller.adjustDuration(-1)
                awaitState { it.selectedMs < before }
            }
            val atMinimum = circle.captureToImage().asAndroidBitmap()
            circle.performTouchInput {
                val radius = width * .4f
                fun point(degrees: Float): Offset {
                    val angle = Math.toRadians(degrees.toDouble())
                    return center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
                }
                down(point(-60f))
                for (step in 1..13) moveTo(point(-60f - step * 5f), 25)
                up()
            }
            compose.waitForIdle()
            assertEquals(DURATION_STEP, state.selectedMs)
            assertTrue("The Enso must stay still when decreasing at minimum duration",
                atMinimum.sameAs(circle.captureToImage().asAndroidBitmap()))
        } finally {
            controller.changeSettings { it.copy(rotationStepMinutes = 1) }
            awaitState { it.settings.rotationStepMinutes == 1 }
            while (state.selectedMs != original.selectedMs) {
                val before = state.selectedMs
                controller.adjustDuration(if (before > original.selectedMs) -1 else 1)
                awaitState { it.selectedMs != before }
            }
            controller.setRepeatForever(false)
            awaitState { !it.continuous }
            while (state.repeatCount != original.repeatCount) {
                val before = state.repeatCount
                controller.adjustRepetitions(if (before > original.repeatCount) -1 else 1)
                awaitState { it.repeatCount != before }
            }
            controller.setRepeatForever(original.continuous)
            controller.changeSettings { original.settings }
            awaitState { it.continuous == original.continuous && it.settings == original.settings }
        }
    }
    @Test fun lockScreenNotificationShowsOneCountdownAndOffersWorkingControls() {
        awaitState { it.ready }
        val original = state
        val manager = compose.activity.getSystemService(android.app.NotificationManager::class.java)
        fun active() = manager.activeNotifications.firstOrNull { it.id == 1 }?.notification
        try {
            controller.changeSettings { it.copy(startGong = false, endGong = false, repeatGong = false,
                vibration = false, keepScreenAwake = false) }
            awaitState { !it.settings.startGong && !it.settings.endGong && !it.settings.repeatGong }
            compose.onNodeWithContentDescription("Start").performClick()
            awaitState { it.phase == TimerPhase.Running }
            compose.waitUntil(5_000) { active()?.actions?.firstOrNull()?.title?.toString() == "Pause" }
            val notification = active()!!
            assertEquals(android.app.Notification.VISIBILITY_PUBLIC, notification.visibility)
            assertEquals(android.app.Notification.CATEGORY_ALARM, notification.category)
            assertFalse(notification.extras.getBoolean(android.app.Notification.EXTRA_SHOW_CHRONOMETER))
            assertFalse(notification.extras.getBoolean(android.app.Notification.EXTRA_SHOW_WHEN))
            assertTrue(notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString().contains(":"))
            assertEquals("Pause", notification.actions.first().title.toString())
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val channel = manager.getNotificationChannel(notification.channelId)
                assertEquals(android.app.NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
                assertNull(channel.sound)
                assertFalse(channel.shouldVibrate())
            }
            notification.actions.first().actionIntent.send()
            awaitState { it.phase == TimerPhase.Paused }
            compose.waitUntil(5_000) { active()?.actions?.firstOrNull()?.title?.toString() == "Resume" }
            assertTrue(active()!!.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString().contains("Paused"))
            assertEquals("Resume", active()!!.actions.first().title.toString())
            active()!!.actions.first().actionIntent.send()
            awaitState { it.phase == TimerPhase.Running }
            compose.waitUntil(5_000) { active()?.actions?.firstOrNull()?.title?.toString() == "Pause" }
            active()!!.actions[1].actionIntent.send()
            awaitState { it.phase == TimerPhase.Idle }
            compose.waitUntil(5_000) { active() == null }
        } finally {
            MeditationService.send(compose.activity, SessionAction.Stop)
            awaitState { it.phase == TimerPhase.Idle }
            controller.changeSettings { original.settings }
            awaitState { it.settings == original.settings }
        }
    }

    @Test fun startupEnsoSharesTheTimerArtworkPositionAndSize() {
        awaitState { it.ready }
        val original = state
        val bounds = compose.onNodeWithContentDescription("Timer circle").fetchSemanticsNode().boundsInRoot
        try {
            compose.runOnUiThread {
                compose.activity.setContent {
                    app.enso.meditation.ui.theme.EnsoTheme {
                        app.enso.meditation.ui.EnsoScreen(original.copy(ready = false), {}, {}, {}, {}, {})
                    }
                }
            }
            val splash = compose.onNodeWithTag("Enso splash ring")
            splash.assertIsDisplayed()
            val splashBounds = splash.fetchSemanticsNode().boundsInRoot
            assertEquals(bounds.left, splashBounds.left, 1f)
            assertEquals(bounds.top, splashBounds.top, 1f)
            assertEquals(bounds.width, splashBounds.width, 1f)
            assertEquals(bounds.height, splashBounds.height, 1f)
            compose.onNodeWithContentDescription("Start").assertDoesNotExist()
        } finally { compose.activityRule.scenario.recreate() }
    }

    @Test fun sumiBrushKeepsDepositsFixedAndUsesUpItsInk() {
        val stroke = app.enso.meditation.ui.SumiStroke(1f, 500f, seed = 4)
        stroke.append(Offset.Zero, 0)
        for (i in 1..20) stroke.append(Offset(i * 5f, 0f), i * 16L)
        val deposited = stroke.dabs.toList()
        val initialInk = deposited.drop(5).map { it.opacity }.average()
        for (i in 21..100) stroke.append(Offset(i * 5f, 0f), i * 16L)
        assertEquals("Extending a stroke must not move or restyle earlier deposits",
            deposited, stroke.dabs.take(deposited.size))
        assertTrue("The brush must leave less wet ink towards the end",
            stroke.dabs.takeLast(20).map { it.opacity }.average() < initialInk * .25)
        val beforeLift = stroke.dabs.toList()
        stroke.finish()
        assertEquals(beforeLift, stroke.dabs.take(beforeLift.size))
        assertTrue(stroke.dabs.last().width < beforeLift.last().width * .2f)
        val adjusted = app.enso.meditation.ui.SumiStroke(1f, 500f, seed = 4,
            settings = app.enso.meditation.ui.BrushSettings(width = 70f, inkReach = 2f, drag = 0f, smoothing = 0f))
        adjusted.append(Offset.Zero, 0)
        for (i in 1..100) adjusted.append(Offset(i * 5f, 0f), i * 16L)
        assertTrue("Width control must change the deposited marks", adjusted.dabs[40].width > stroke.dabs[40].width)
        assertTrue("More ink reach must keep later marks wetter", adjusted.dabs.takeLast(20).map { it.opacity }.average() >
            beforeLift.takeLast(20).map { it.opacity }.average())
        val finished = stroke.dabs.toList()
        stroke.finish()
        stroke.append(Offset(700f, 0f), 2000)
        assertEquals("A completed stroke must remain immutable", finished, stroke.dabs)
    }

    @Test fun brushControlsPersistAndCanBeCopiedWithoutSavingTheDrawing() {
        awaitState { it.ready }
        val store = app.enso.meditation.ui.BrushSettingsStore(compose.activity)
        val original = store.load()
        try {
            compose.onNodeWithContentDescription("Paint an Enso").performClick()
            compose.onNodeWithContentDescription("Brush settings").performClick()
            compose.onNodeWithText("Brush").assertDoesNotExist()
            compose.onNodeWithText("Changes apply to your next stroke.").assertDoesNotExist()
            compose.onNodeWithContentDescription("Brush Width").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) {
                assertTrue(it(70f))
            }
            compose.onNodeWithContentDescription("Brush Ink reach").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) {
                assertTrue(it(2f))
            }
            compose.runOnIdle {
                assertEquals(70f, store.load().width, .001f)
                assertEquals(2f, store.load().inkReach, .001f)
            }
            compose.onNodeWithContentDescription("Copy brush values").performScrollTo().performClick()
            compose.runOnIdle {
                val clipboard = compose.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                assertEquals(store.load().description(), clipboard.primaryClip!!.getItemAt(0).text.toString())
            }
            compose.onNodeWithContentDescription("Close brush settings").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Enso painting canvas").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Empty canvas"))
            compose.onNodeWithContentDescription("Close painting").performClick()
            compose.activityRule.scenario.recreate()
            awaitState { it.ready }
            compose.onNodeWithContentDescription("Paint an Enso").performClick()
            compose.onNodeWithContentDescription("Brush settings").performClick()
            compose.onNodeWithContentDescription("Brush Width").assert(SemanticsMatcher("Retained width") {
                it.config[SemanticsProperties.ProgressBarRangeInfo].current == 70f
            })
            compose.onNodeWithText("Defaults").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(app.enso.meditation.ui.BrushSettings(), store.load()) }
            compose.onNodeWithContentDescription("Close brush settings").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Close painting").performClick()
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithContentDescription("App version ${BuildConfig.VERSION_NAME}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Close settings").performScrollTo().performClick()
        } finally { store.save(original) }
    }

    @Test fun paintedEnsoMatchesTimerBoundsAndCanReplaceAndRestoreArtwork() {
        awaitState { it.ready }
        val imageStore = app.enso.meditation.ui.EnsoImageStore(compose.activity)
        val originalImage = imageStore.load()
        val original = state
        try {
            val bounds = compose.onNodeWithContentDescription("Timer circle").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithContentDescription("Paint an Enso").performClick()
            val canvas = compose.onNodeWithContentDescription("Enso painting canvas")
            val paintBounds = canvas.fetchSemanticsNode().boundsInRoot
            assertEquals(bounds.left, paintBounds.left, 1f)
            assertEquals(bounds.top, paintBounds.top, 1f)
            assertEquals(bounds.width, paintBounds.width, 1f)
            assertEquals(bounds.height, paintBounds.height, 1f)
            compose.onNodeWithContentDescription("Save and use painted Enso").assertIsNotEnabled()
            canvas.performTouchInput {
                val radius = width * app.enso.meditation.ui.ENSO_GUIDE_RADIUS
                fun point(degrees: Int): Offset {
                    val angle = Math.toRadians(degrees.toDouble())
                    return center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
                }
                down(point(-120))
                for (degrees in -115..200 step 5) moveTo(point(degrees), 16)
                up()
            }
            compose.onNodeWithContentDescription("Save and use painted Enso").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("Custom Enso artwork").fetchSemanticsNodes().isNotEmpty() }
            val first = imageStore.load()!!
            assertEquals(bounds.width.toInt(), first.width)
            assertEquals(first.width, first.height)
            assertEquals("Paper and controls must not be saved", 0, android.graphics.Color.alpha(first.getPixel(0, 0)))
            assertEquals("The center must remain transparent", 0,
                android.graphics.Color.alpha(first.getPixel(first.width / 2, first.height / 2)))
            assertTrue("The saved image must contain ink", (0 until first.width).any { x ->
                android.graphics.Color.alpha(first.getPixel(x, first.height / 2)) > 0
            })
            compose.activityRule.scenario.recreate()
            awaitState { it.ready }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("Custom Enso artwork").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(first.sameAs(imageStore.load()))
            compose.onNodeWithContentDescription("Paint an Enso").performClick()
            canvas.performTouchInput { click(center) }
            compose.onNodeWithContentDescription("Save and use painted Enso").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("Custom Enso artwork").fetchSemanticsNodes().isNotEmpty() }
            assertFalse("Saving again must replace the single image", first.sameAs(imageStore.load()))
            compose.onNodeWithContentDescription("Paint an Enso").performClick()
            compose.onNodeWithContentDescription("Use standard Enso").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("Standard Enso artwork").fetchSemanticsNodes().isNotEmpty() }
            assertNull(imageStore.load())
            assertEquals(original.selectedMs, state.selectedMs)
            assertEquals(original.settings, state.settings)
            assertEquals(original.repeatCount, state.repeatCount)
            assertEquals(original.continuous, state.continuous)
        } finally {
            if (originalImage != null) imageStore.save(originalImage) else imageStore.useStandard()
        }
    }

    @Test fun paintingIsTemporaryAndResetClearsOnlyTheDrawing() {
        awaitState { it.ready }
        val original = state
        compose.onNodeWithContentDescription("Paint an Enso").performClick()
        val canvas = compose.onNodeWithContentDescription("Enso painting canvas")
        canvas.assertIsDisplayed()
        compose.onNodeWithContentDescription("Start").assertDoesNotExist()
        val blank = canvas.captureToImage().asAndroidBitmap()
        canvas.performTouchInput {
            val radius = minOf(width, height) * .34f
            fun point(degrees: Int): Offset {
                val angle = Math.toRadians(degrees.toDouble())
                return center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
            }
            down(point(-120))
            for (degrees in -115..200 step 5) moveTo(point(degrees), 16)
            up()
        }
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 brush stroke"))
        assertFalse("Finger painting must produce ink on the paper", blank.sameAs(canvas.captureToImage().asAndroidBitmap()))
        compose.onNodeWithContentDescription("Reset painting").performClick()
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Empty canvas"))
        assertTrue("Reset must leave the original paper", blank.sameAs(canvas.captureToImage().asAndroidBitmap()))
        canvas.performTouchInput { click(center) }
        compose.onNodeWithContentDescription("Close painting").performClick()
        compose.onNodeWithContentDescription("Enso painting canvas").assertDoesNotExist()
        assertEquals(original.selectedMs, state.selectedMs)
        assertEquals(original.repeatCount, state.repeatCount)
        assertEquals(original.continuous, state.continuous)
        assertEquals(original.settings, state.settings)
        compose.onNodeWithContentDescription("Paint an Enso").performClick()
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Empty canvas"))
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("Enso painting canvas").assertDoesNotExist()
    }

}
