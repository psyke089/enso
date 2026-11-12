package app.enso.meditation

import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith

/** Exercises real controls and persistence without shortening production timer limits. */
@RunWith(AndroidJUnit4::class)
class EnsoSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val controller get() = (compose.activity.application as EnsoApplication).controller
    private val state get() = controller.state.value
    private fun awaitState(test: (TimerUiState) -> Boolean) = compose.waitUntil(5_000) { test(state) }

    private fun str(@StringRes id: Int, vararg args: Any): String =
        compose.activity.getString(id, *args)

    private fun quantity(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        compose.activity.resources.getQuantityString(id, count, *args)

    private fun saying(index: Int): String =
        compose.activity.resources.getStringArray(R.array.sayings).getOrNull(index).orEmpty()

    private var savedArtworkDates: Pair<Long?, Long?>? = null
    private var savedArtworkFiles: Map<String, ByteArray?> = emptyMap()
    private var savedSettings: EnsoSettings? = null

    @Before fun dismissDailyPromptForControlTests() {
        val preferences = compose.activity.getSharedPreferences("enso_artwork", android.content.Context.MODE_PRIVATE)
        savedArtworkFiles = listOf("painted_enso.dat", "painted_enso.png").associateWith { name ->
            try { android.util.AtomicFile(java.io.File(compose.activity.filesDir, name)).readFully() }
            catch (_: java.io.FileNotFoundException) { null }
        }
        savedArtworkDates = (if (preferences.contains("last_drawn_at")) preferences.getLong("last_drawn_at", 0L) else null) to
            (if (preferences.contains("last_dismissed_at")) preferences.getLong("last_dismissed_at", 0L) else null)
        awaitState { it.ready }
        savedSettings = state.settings
        // A crashed hardcore run would otherwise hide every control these tests rely on.
        if (state.settings.hardcoreMode) {
            controller.changeSettings { it.copy(hardcoreMode = false) }
            awaitState { !it.settings.hardcoreMode }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(str(R.string.close_painting)).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithContentDescription(str(R.string.paint_enso)).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithContentDescription(str(R.string.close_painting)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription(str(R.string.close_painting)).performClick()
        }
        compose.waitForIdle()
    }

    @After fun restoreSettings() {
        val saved = savedSettings ?: return
        if (controller.state.value.settings != saved) {
            controller.changeSettings { saved }
            runBlocking {
                withTimeout(5_000) {
                    while (controller.state.value.settings != saved) delay(10)
                }
            }
        }
    }

    @After fun restoreArtworkDates() {
        for ((name, bytes) in savedArtworkFiles) {
            val file = android.util.AtomicFile(java.io.File(compose.activity.filesDir, name))
            if (bytes == null) file.delete() else {
                val output = file.startWrite()
                try { output.write(bytes); file.finishWrite(output) }
                catch (failure: Exception) { file.failWrite(output); throw failure }
            }
        }
        savedArtworkDates?.let { (drawn, dismissed) ->
            val edit = compose.activity.getSharedPreferences("enso_artwork", android.content.Context.MODE_PRIVATE).edit()
            if (drawn == null) edit.remove("last_drawn_at") else edit.putLong("last_drawn_at", drawn)
            if (dismissed == null) edit.remove("last_dismissed_at") else edit.putLong("last_dismissed_at", dismissed)
            edit.commit()
        }
    }

    @Test fun sayingsArraysMatchEngineCountInSupportedLocales() {
        for (language in listOf("en", "de")) {
            val configuration = android.content.res.Configuration(compose.activity.resources.configuration)
                .apply { setLocale(java.util.Locale(language)) }
            val localized = compose.activity.createConfigurationContext(configuration).resources
            val sayings = localized.getStringArray(R.array.sayings)
            assertEquals("Unexpected saying count for $language", Sayings.count, sayings.size)
            assertTrue("Sayings must not be blank for $language", sayings.all { it.isNotBlank() })
        }
    }

    @Test fun dailyDrawingPromptHonorsCloseAndReturnsTheNextDay() {
        val preferences = compose.activity.getSharedPreferences("enso_artwork", android.content.Context.MODE_PRIVATE)
        val store = (compose.activity.application as EnsoApplication).imageStore
        android.util.AtomicFile(java.io.File(compose.activity.filesDir, "painted_enso.dat")).delete()
        preferences.edit().putLong("last_drawn_at", 0L).putLong("last_dismissed_at", 0L).commit()
        store.refresh()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(str(R.string.close_painting)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(str(R.string.close_painting)).performClick()
        compose.waitForIdle()
        assertFalse(store.shouldPromptToday())
        assertFalse(store.wasDrawnToday())
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription(str(R.string.paint_enso)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.close_painting)).assertDoesNotExist()
        val tomorrow = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
        assertTrue(store.shouldPromptToday(tomorrow.timeInMillis))
        val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
        preferences.edit().putLong("last_dismissed_at", yesterday.timeInMillis).commit()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(str(R.string.close_painting)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(str(R.string.close_painting)).performClick()
    }

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
            compose.onNodeWithContentDescription(str(R.string.increase_repetitions)).performClick()
            awaitState { it.repeatCount == 3 }
            compose.onNodeWithContentDescription(str(R.string.decrease_repetitions)).performClick()
            awaitState { it.repeatCount == 2 }
            repeat(2) { compose.onNodeWithContentDescription(str(R.string.decrease_repetitions)).performClick() }
            awaitState { it.continuous && it.totalMs == null }
            compose.onNodeWithContentDescription(str(R.string.unlimited_repetitions)).assertIsDisplayed()
            repeat(2) { compose.onNodeWithContentDescription(str(R.string.increase_repetitions)).performClick() }
            awaitState { !it.continuous && it.totalMs == 2 * it.selectedMs }
            assertEquals(original.settings, state.settings)
            val saved = runBlocking { EnsoStore(compose.activity).load() }
            assertEquals(2, saved.repeatCount)
            assertFalse(saved.repeatForever)
            compose.onNodeWithContentDescription(str(R.string.action_start)).performClick()
            awaitState { it.phase == TimerPhase.Running }
            compose.onNodeWithContentDescription(str(R.string.increase_repetitions)).assertDoesNotExist()
            compose.onNodeWithContentDescription(str(R.string.timer_circle)).assertDoesNotExist()
            compose.onNodeWithContentDescription(str(R.string.interval_of, 1, 2)).assertIsDisplayed()
            compose.onNodeWithContentDescription(str(R.string.action_pause)).performClick()
            awaitState { it.phase == TimerPhase.Paused }
            val paused = state
            val pausedSnapshot = runBlocking { EnsoStore(compose.activity).load() }
            assertEquals(2, pausedSnapshot.session!!.repeatCount)
            assertEquals(0L, pausedSnapshot.session.completedIntervals)
            assertEquals(paused.remainingMs, pausedSnapshot.session.pausedRemainingMs)
            compose.activityRule.scenario.recreate()
            compose.onNodeWithContentDescription(str(R.string.interval_of, 1, 2)).assertIsDisplayed()
            assertEquals(paused.elapsedMs, state.elapsedMs)
            assertEquals(paused.progress, state.progress, 0f)
            compose.onNodeWithContentDescription(str(R.string.action_reset)).performClick()
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
            awaitState { it.settings.showSaying && it.settings.showTime && it.settings.hapticFeedback && it.settings.rotationStepMinutes == 5 && it.sayingIndex >= 0 }
            compose.waitUntil(5_000) {
                runCatching { compose.onNodeWithText(saying(state.sayingIndex)).assertIsDisplayed() }.isSuccess
            }
            val direction = if (state.selectedMs == MAX_DURATION) -1 else 1
            adjustDurationAccessibly(increase = direction > 0)
            awaitState { it.selectedMs == original.selectedMs + direction * DURATION_STEP }
            adjustDurationAccessibly(increase = direction < 0)
            awaitState { it.selectedMs == original.selectedMs }

            compose.onNodeWithContentDescription(str(R.string.settings)).performClick()
            compose.onNodeWithText(str(R.string.minutes_per_step)).assertIsDisplayed()
            compose.onNodeWithContentDescription(str(R.string.smaller_minute_step)).performClick()
            awaitState { it.settings.rotationStepMinutes == 2 }
            assertEquals(2, runBlocking { EnsoStore(compose.activity).load() }.settings.rotationStepMinutes)
            compose.onNodeWithText(str(R.string.haptic_feedback)).performScrollTo().performClick()
            awaitState { !it.settings.hapticFeedback }
            assertFalse(runBlocking { EnsoStore(compose.activity).load() }.settings.hapticFeedback)
            compose.activityRule.scenario.recreate()
            awaitState { !it.settings.hapticFeedback }
            compose.onNodeWithText(str(R.string.haptic_feedback)).performScrollTo().performClick()
            awaitState { it.settings.hapticFeedback }
            compose.onNodeWithContentDescription(quantity(R.plurals.minutes_per_rotation_step, 2, 2)).assertIsDisplayed()
            compose.onNodeWithContentDescription(str(R.string.larger_minute_step)).performClick()
            awaitState { it.settings.rotationStepMinutes == 5 }
            compose.onNodeWithText(str(R.string.vibration)).assertIsDisplayed()
            compose.onNodeWithContentDescription(str(R.string.close_settings)).assertIsDisplayed()
            // Tap the label itself; the merged row's center overlaps the sound-preview control.
            compose.onNodeWithText(str(R.string.repeat_gong), useUnmergedTree = true).performClick()
            awaitState { it.settings.repeatGong != original.settings.repeatGong }
            assertEquals(original.repeatCount, state.repeatCount)
            assertEquals(original.continuous, state.continuous)
            compose.onNodeWithText(str(R.string.show_time)).performScrollTo().performClick()
            awaitState { !it.settings.showTime }
            assertFalse(runBlocking { EnsoStore(compose.activity).load() }.settings.showTime)
            compose.activityRule.scenario.recreate()
            awaitState { !it.settings.showTime }
            compose.onNodeWithContentDescription(str(R.string.close_settings)).performClick()
            compose.onNodeWithContentDescription(str(R.string.time_selected, formatTime(state.selectedMs))).assertDoesNotExist()
            compose.onNodeWithContentDescription(if (state.continuous) str(R.string.unlimited_repetitions)
                else quantity(R.plurals.repetitions, state.repeatCount, state.repeatCount)).assertIsDisplayed()
            compose.onNodeWithContentDescription(str(R.string.settings)).performClick()
            compose.onNodeWithText(str(R.string.show_time)).performScrollTo().performClick()
            awaitState { it.settings.showTime }
            compose.onNodeWithText(str(R.string.show_saying)).performScrollTo().performClick()
            awaitState { !it.settings.showSaying }
            compose.onNodeWithText(str(R.string.vibration)).performClick()
            awaitState { it.settings.vibration != original.settings.vibration }
            val saved = runBlocking { EnsoStore(compose.activity).load() }
            assertEquals(state.settings, saved.settings)
            assertEquals(state.selectedMs, saved.selectedMs)
            compose.onNodeWithContentDescription(str(R.string.close_settings)).performClick()
            compose.onNodeWithText(saying(state.sayingIndex)).assertDoesNotExist()

            compose.onNodeWithContentDescription(str(R.string.action_start)).performClick()
            awaitState { it.phase == TimerPhase.Running }
            assertNotEquals(original.sayingIndex, state.sayingIndex)
            val runningSaying = state.sayingIndex
            val running = state.remainingMs
            compose.onNodeWithContentDescription(str(R.string.timer_circle)).assertDoesNotExist()
            compose.activityRule.scenario.recreate()
            awaitState { it.phase == TimerPhase.Running && it.remainingMs < running }
            assertEquals(original.selectedMs, state.selectedMs)
            assertEquals(runningSaying, state.sayingIndex)

            compose.onNodeWithContentDescription(str(R.string.action_pause)).performClick()
            awaitState { it.phase == TimerPhase.Paused }
            assertEquals(runningSaying, state.sayingIndex)
            val paused = state.remainingMs
            compose.onNodeWithText(str(R.string.paused)).assertDoesNotExist()
            // Continuous sessions show elapsed / ∞; bounded ones show elapsed / total.
            if (state.continuous) {
                compose.onNodeWithText("∞", substring = true).assertIsDisplayed()
            } else state.totalMs?.let { total ->
                compose.onNodeWithContentDescription(str(R.string.elapsed_total,
                    formatTime(state.elapsedMs / 1000 * 1000), formatTime(total))).assertIsDisplayed()
            }
            compose.onNodeWithContentDescription(str(R.string.timer_circle)).assertDoesNotExist()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(1_100)
            assertEquals(paused, state.remainingMs)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithContentDescription(str(R.string.action_resume)).performClick()
            awaitState { it.phase == TimerPhase.Running && it.remainingMs < paused }
            assertEquals(runningSaying, state.sayingIndex)
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            val beforeBackground = state.remainingMs
            Thread.sleep(1_200)
            assertTrue(state.remainingMs < beforeBackground)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithContentDescription(str(R.string.action_reset)).performClick()
            awaitState { it.phase == TimerPhase.Idle }
            assertEquals(original.selectedMs, state.remainingMs)
            compose.onNodeWithContentDescription(str(R.string.action_start)).assertIsDisplayed()
            compose.onNodeWithContentDescription(str(R.string.action_reset)).assertDoesNotExist()
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

    private fun adjustDurationAccessibly(increase: Boolean) {
        val stepMinutes = state.settings.rotationStepMinutes
        val expected = if (increase) quantity(R.plurals.increase_duration, stepMinutes, stepMinutes)
            else quantity(R.plurals.decrease_duration, stepMinutes, stepMinutes)
        val actions = compose.onNodeWithContentDescription(str(R.string.timer_circle))
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            assertTrue(actions.first { it.label == expected }.action())
        }
    }

    @Test fun circleDistinguishesRotationTapAndHold() {
        awaitState { it.ready }
        val original = state
        val direction = if (original.selectedMs > MAX_DURATION / 2) -1 else 1
        try {
            controller.changeSettings { it.copy(rotationStepMinutes = 5) }
            awaitState { it.settings.rotationStepMinutes == 5 }
            val circle = compose.onNodeWithContentDescription(str(R.string.timer_circle))
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
            compose.onNodeWithContentDescription(str(R.string.action_start)).performClick()
            awaitState { it.phase == TimerPhase.Running }
            compose.waitUntil(5_000) { active()?.actions?.firstOrNull()?.title?.toString() == str(R.string.action_pause) }
            val notification = active()!!
            assertEquals(android.app.Notification.VISIBILITY_PUBLIC, notification.visibility)
            assertEquals(android.app.Notification.CATEGORY_ALARM, notification.category)
            assertFalse(notification.extras.getBoolean(android.app.Notification.EXTRA_SHOW_CHRONOMETER))
            assertFalse(notification.extras.getBoolean(android.app.Notification.EXTRA_SHOW_WHEN))
            assertTrue(notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString().contains(":"))
            assertEquals(str(R.string.action_pause), notification.actions.first().title.toString())
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val channel = manager.getNotificationChannel(notification.channelId)
                assertEquals(android.app.NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
                assertNull(channel.sound)
                assertFalse(channel.shouldVibrate())
            }
            notification.actions.first().actionIntent.send()
            awaitState { it.phase == TimerPhase.Paused }
            compose.waitUntil(5_000) { active()?.actions?.firstOrNull()?.title?.toString() == str(R.string.action_resume) }
            assertTrue(active()!!.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
                .contains(str(R.string.paused)))
            assertEquals(str(R.string.action_resume), active()!!.actions.first().title.toString())
            active()!!.actions.first().actionIntent.send()
            awaitState { it.phase == TimerPhase.Running }
            compose.waitUntil(5_000) { active()?.actions?.firstOrNull()?.title?.toString() == str(R.string.action_pause) }
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
            compose.onNodeWithContentDescription(str(R.string.paint_enso)).performClick()
            compose.onNodeWithContentDescription(str(R.string.brush_settings)).performClick()
            compose.onNodeWithText(str(R.string.brush_settings)).assertDoesNotExist()
            compose.onNodeWithContentDescription(str(R.string.brush_slider, str(R.string.brush_width)))
                .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(70f)) }
            compose.onNodeWithContentDescription(str(R.string.brush_slider, str(R.string.brush_ink_reach)))
                .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(2f)) }
            compose.runOnIdle {
                assertEquals(70f, store.load().width, .001f)
                assertEquals(2f, store.load().inkReach, .001f)
            }
            compose.onNodeWithContentDescription(str(R.string.brush_copy_values)).performClick()
            compose.runOnIdle {
                val clipboard = compose.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                assertEquals(store.load().description(compose.activity), clipboard.primaryClip!!.getItemAt(0).text.toString())
            }
            compose.onNodeWithContentDescription(str(R.string.close_brush_settings)).performClick()
            compose.onNodeWithContentDescription(str(R.string.painting_canvas)).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(R.string.empty_canvas)))
            compose.onNodeWithContentDescription(str(R.string.close_painting)).performClick()
            compose.activityRule.scenario.recreate()
            awaitState { it.ready }
            compose.onNodeWithContentDescription(str(R.string.paint_enso)).performClick()
            compose.onNodeWithContentDescription(str(R.string.brush_settings)).performClick()
            compose.onNodeWithContentDescription(str(R.string.brush_slider, str(R.string.brush_width))).assert(SemanticsMatcher("Retained width") {
                it.config[SemanticsProperties.ProgressBarRangeInfo].current == 70f
            })
            compose.onNodeWithText(str(R.string.brush_defaults)).performClick()
            compose.runOnIdle { assertEquals(app.enso.meditation.ui.BrushSettings(), store.load()) }
            compose.onNodeWithContentDescription(str(R.string.close_brush_settings)).performClick()
            compose.onNodeWithContentDescription(str(R.string.close_painting)).performClick()
            compose.onNodeWithContentDescription(str(R.string.settings)).performClick()
            compose.onNodeWithContentDescription(str(R.string.app_version, BuildConfig.VERSION_NAME))
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription(str(R.string.close_settings)).performScrollTo().performClick()
        } finally { store.save(original) }
    }

    @Test fun paintedEnsoMatchesTimerBoundsAndCanReplaceAndRestoreArtwork() {
        awaitState { it.ready }
        val imageStore = (compose.activity.application as EnsoApplication).imageStore
        val originalImage = imageStore.load()
        val originalScale = imageStore.frameScale()
        val artworkPreferences = compose.activity.getSharedPreferences("enso_artwork", android.content.Context.MODE_PRIVATE)
        val originalDrawnAt = artworkPreferences.getLong("last_drawn_at", 0L)
        val original = state
        try {
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("Timer artwork frame").fetchSemanticsNodes().isNotEmpty()
            }
            val bounds = compose.onNodeWithTag("Timer artwork frame").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithContentDescription(str(R.string.paint_enso)).performClick()
            val canvas = compose.onNodeWithContentDescription(str(R.string.painting_canvas))
            val paintBounds = canvas.fetchSemanticsNode().boundsInRoot
            assertEquals(bounds.center.x, paintBounds.center.x, 1f)
            assertEquals(bounds.center.y, paintBounds.center.y, 1f)
            assertTrue(paintBounds.width >= bounds.width)
            assertEquals(paintBounds.width, paintBounds.height, 1f)
            compose.onNodeWithContentDescription(str(R.string.save_painted_enso)).assertIsNotEnabled()
            canvas.performTouchInput {
                val radius = bounds.width * app.enso.meditation.ui.ENSO_GUIDE_RADIUS
                fun point(degrees: Int): Offset {
                    val angle = Math.toRadians(degrees.toDouble())
                    return center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
                }
                down(point(-120))
                for (degrees in -115..200 step 5) moveTo(point(degrees), 16)
                up()
            }
            compose.onNodeWithContentDescription(str(R.string.save_painted_enso)).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("Custom Enso artwork").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(imageStore.wasDrawnToday())
            val tomorrow = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
            assertFalse(imageStore.wasDrawnToday(tomorrow.timeInMillis))
            compose.onNodeWithContentDescription(str(R.string.paint_enso)).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(R.string.enso_drawn_today)))
            val first = imageStore.load()!!
            assertEquals(paintBounds.width.toInt(), first.width)
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
            compose.onNodeWithContentDescription(str(R.string.paint_enso)).performClick()
            canvas.performTouchInput { click(center) }
            compose.onNodeWithContentDescription(str(R.string.save_painted_enso)).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("Custom Enso artwork").fetchSemanticsNodes().isNotEmpty() }
            assertFalse("Saving again must replace the single image", first.sameAs(imageStore.load()))
            compose.onNodeWithContentDescription(str(R.string.paint_enso)).performClick()
            compose.onNodeWithContentDescription(str(R.string.use_standard_enso)).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("Standard Enso artwork").fetchSemanticsNodes().isNotEmpty() }
            assertNull(imageStore.load())
            assertFalse(imageStore.shouldPromptToday())
            assertEquals(original.selectedMs, state.selectedMs)
            assertEquals(original.settings, state.settings)
            assertEquals(original.repeatCount, state.repeatCount)
            assertEquals(original.continuous, state.continuous)
        } finally {
            if (originalImage != null) imageStore.save(originalImage, originalScale) else imageStore.useStandard()
            artworkPreferences.edit().putLong("last_drawn_at", originalDrawnAt).commit()
        }
    }

    @Test fun paintingIsTemporaryAndResetClearsOnlyTheDrawing() {
        awaitState { it.ready }
        val original = state
        compose.onNodeWithContentDescription(str(R.string.paint_enso)).performClick()
        val canvas = compose.onNodeWithContentDescription(str(R.string.painting_canvas))
        canvas.assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.action_start)).assertDoesNotExist()
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
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
            quantity(R.plurals.brush_strokes, 1, 1)))
        assertFalse("Finger painting must produce ink on the paper", blank.sameAs(canvas.captureToImage().asAndroidBitmap()))
        compose.onNodeWithContentDescription(str(R.string.reset_painting)).performClick()
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(R.string.empty_canvas)))
        assertTrue("Reset must leave the original paper", blank.sameAs(canvas.captureToImage().asAndroidBitmap()))
        canvas.performTouchInput { click(center) }
        compose.onNodeWithContentDescription(str(R.string.close_painting)).performClick()
        compose.onNodeWithContentDescription(str(R.string.painting_canvas)).assertDoesNotExist()
        assertEquals(original.selectedMs, state.selectedMs)
        assertEquals(original.repeatCount, state.repeatCount)
        assertEquals(original.continuous, state.continuous)
        assertEquals(original.settings, state.settings)
        compose.onNodeWithContentDescription(str(R.string.paint_enso)).performClick()
        canvas.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(R.string.empty_canvas)))
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription(str(R.string.painting_canvas)).assertDoesNotExist()
    }

    @Test fun hardcoreModeDemandsOnePaintingPerDayAndLeavesNoWayBack() {
        awaitState { it.ready }
        val imageStore = (compose.activity.application as EnsoApplication).imageStore
        val preferences = compose.activity.getSharedPreferences("enso_artwork", android.content.Context.MODE_PRIVATE)
        val original = state.settings
        val originalImage = imageStore.load()
        val originalScale = imageStore.frameScale()
        val originalDrawnAt = preferences.getLong("last_drawn_at", 0L)
        try {
            // Exercise migration dates without changing an already committed artwork record.
            android.util.AtomicFile(java.io.File(compose.activity.filesDir, "painted_enso.dat")).delete()
            // A dismissed invitation still opens the canvas, because hardcore has no dismissal.
            preferences.edit().putLong("last_drawn_at", 0L)
                .putLong("last_dismissed_at", System.currentTimeMillis()).commit()
            imageStore.refresh()
            assertTrue(imageStore.shouldOpenPainter(hardcore = true))
            assertFalse(imageStore.shouldOpenPainter(hardcore = false))

            // An ordinary painting saved today must not swallow a new activation.
            preferences.edit().putLong("last_drawn_at", System.currentTimeMillis() - 1_000L).commit()
            imageStore.refresh()
            assertTrue(imageStore.wasDrawnToday())
            compose.onNodeWithContentDescription(str(R.string.settings)).performClick()
            compose.onNodeWithText(str(R.string.hardcore_mode)).performScrollTo().performClick()
            awaitState { it.settings.hardcoreMode }
            val enabledSettings = runBlocking { EnsoStore(compose.activity).load() }.settings
            assertTrue(enabledSettings.hardcoreMode)
            assertTrue(enabledSettings.hardcorePending)
            assertTrue(imageStore.shouldOpenPainter(true, pending = enabledSettings.hardcorePending))
            compose.onNodeWithContentDescription(str(R.string.close_settings)).performScrollTo().performClick()

            // Closing settings hands straight over to the single canvas of the day.
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription(str(R.string.painting_canvas)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(str(R.string.close_painting)).assertDoesNotExist()
            compose.onNodeWithContentDescription(str(R.string.brush_settings)).assertDoesNotExist()
            compose.onNodeWithContentDescription(str(R.string.use_standard_enso)).assertDoesNotExist()
            compose.onNodeWithContentDescription(str(R.string.reset_painting)).assertDoesNotExist()
            compose.onNodeWithContentDescription(str(R.string.save_painted_enso)).assertIsNotEnabled()

            // Pending activation also survives activity recreation before any drawing is saved.
            compose.activityRule.scenario.recreate()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription(str(R.string.painting_canvas)).fetchSemanticsNodes().isNotEmpty()
            }
            // Back must not leave the canvas; only the checkmark does.
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithContentDescription(str(R.string.painting_canvas)).assertIsDisplayed()
            val canvas = compose.onNodeWithContentDescription(str(R.string.painting_canvas))
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
            compose.onNodeWithContentDescription(str(R.string.save_painted_enso)).assertIsEnabled().performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("Custom Enso artwork").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitUntil(5_000) { !state.settings.hardcorePending }
            assertTrue(imageStore.wasDrawnToday())
            assertFalse(imageStore.shouldOpenPainter(hardcore = true,
                pending = state.settings.hardcorePending))

            // The one attempt is spent: no button, no canvas, also not after a relaunch.
            compose.onNodeWithContentDescription(str(R.string.paint_enso)).assertDoesNotExist()
            compose.onNodeWithContentDescription(str(R.string.painting_canvas)).assertDoesNotExist()
            compose.activityRule.scenario.recreate()
            awaitState { it.ready }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription(str(R.string.settings)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            compose.onNodeWithContentDescription(str(R.string.painting_canvas)).assertDoesNotExist()
            compose.onNodeWithContentDescription(str(R.string.paint_enso)).assertDoesNotExist()

            // Switching hardcore off restores every control.
            compose.onNodeWithContentDescription(str(R.string.settings)).performClick()
            compose.onNodeWithText(str(R.string.hardcore_mode)).performScrollTo().performClick()
            awaitState { !it.settings.hardcoreMode }
            compose.onNodeWithContentDescription(str(R.string.close_settings)).performScrollTo().performClick()
            compose.onNodeWithContentDescription(str(R.string.paint_enso)).assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(str(R.string.close_painting)).assertIsDisplayed()
            compose.onNodeWithContentDescription(str(R.string.brush_settings)).assertIsDisplayed()
            compose.onNodeWithContentDescription(str(R.string.use_standard_enso)).assertIsDisplayed()
            compose.onNodeWithContentDescription(str(R.string.reset_painting)).assertIsDisplayed()
            compose.onNodeWithContentDescription(str(R.string.close_painting)).performClick()
        } finally {
            if (state.settings.hardcoreMode) {
                controller.changeSettings { it.copy(hardcoreMode = false) }
                awaitState { !it.settings.hardcoreMode }
            }
            controller.changeSettings { original }
            awaitState { it.settings == original }
            if (originalImage != null) imageStore.save(originalImage, originalScale) else imageStore.useStandard()
            preferences.edit().putLong("last_drawn_at", originalDrawnAt).commit()
        }
    }

}
