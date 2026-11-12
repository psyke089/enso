package app.enso.meditation

import android.app.Application
import android.os.SystemClock
import android.util.Log
import app.enso.meditation.ui.BrushSettingsStore
import app.enso.meditation.ui.EnsoImageStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

class EnsoApplication : Application() {
    val controller by lazy { SessionController(this) }
    internal val imageStore by lazy { EnsoImageStore(this) }
    private val warmUp = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // First SharedPreferences/AtomicFile access parses XML and reads disk; never do that in
        // composition. Both stores cache their values in memory afterwards.
        warmUp.launch {
            imageStore.warmUp()
            BrushSettingsStore(this@EnsoApplication).warmUp()
        }
    }
}

enum class SessionAction { Start, Pause, Resume, Stop, Restore }

/** Application-scoped owner; activity recreation never creates a second session. */
class SessionController(application: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = EnsoStore(application)
    private val audio = GongPlayer(application)
    private val adjustmentFeedback = AdjustmentFeedback(application)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(TimerUiState())
    private val mutableLayout = MutableStateFlow(TimerUiState())
    private val mutableTick = MutableStateFlow(TimerTick())
    val state = mutableState.asStateFlow()
    val layoutState = mutableLayout.asStateFlow()
    val tickState = mutableTick.asStateFlow()
    private var warning = false
    private lateinit var engine: TimerEngine
    private val initialized = scope.async {
        val saved = try { store.load() } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e("Enso", "Unable to read local state", e)
            warning = true
            TimerSnapshot()
        }
        engine = TimerEngine(saved, SystemClock.elapsedRealtime())
        try {
            if (engine.snapshot != saved) persist()
        } catch (e: Exception) {
            Log.e("Enso", "Unable to initialize local state", e)
            warning = true
        }
        publish()
    }

    suspend fun awaitReady() { initialized.await() }

    private fun publish() {
        val next = engine.uiState(SystemClock.elapsedRealtime()).copy(storageWarning = warning)
        mutableState.value = next
        mutableLayout.value = next.withoutTick()
        mutableTick.value = next.tick()
    }

    private suspend fun persist() {
        try { store.save(engine.snapshot); warning = false }
        catch (e: IOException) { warning = true; Log.e("Enso", "Unable to save local state", e) }
    }

    private suspend fun mutate(block: TimerEngine.() -> List<TimerEvent>) {
        initialized.await()
        mutex.withLock {
            val before = engine.snapshot
            val events = engine.block()
            // Selection feedback is immediate; boundary gongs still wait for persistence below.
            if (TimerEvent.SelectionTick in events) adjustmentFeedback.tick()
            // Commit the consumed boundary before audio, preventing replay after process death.
            if (before != engine.snapshot) persist()
            publish()
            events.forEach { event ->
                when (event) {
                    TimerEvent.SelectionTick -> Unit
                    TimerEvent.StartGong -> audio.play(engine.snapshot.settings.startGongSound)
                    is TimerEvent.IntervalCompleted -> {
                        if (event.gong) {
                            val settings = engine.snapshot.settings
                            // An interior boundary keeps the session alive; its final boundary ends it.
                            val sound = if (engine.snapshot.session != null) {
                                settings.middleGongSound
                            } else settings.endGongSound
                            audio.play(sound)
                        }
                        if (event.vibrate) audio.vibrate()
                    }
                }
            }
        }
    }

    fun adjustDuration(direction: Int) { scope.launch { mutate { adjustDuration(direction) } } }

    fun adjustRepetitions(direction: Int) { scope.launch { mutate { adjustRepetitions(direction) } } }

    fun setRepeatForever(forever: Boolean) { scope.launch { mutate { setRepeatForever(forever); emptyList() } } }

    fun prepareAdjustmentFeedback() = adjustmentFeedback.prepare()

    fun releaseAdjustmentFeedback() = adjustmentFeedback.release()

    suspend fun onLaunch() = mutate { refreshSaying(); emptyList() }

    /** Plays a single recording so settings can be chosen by ear; never touches session state. */
    fun previewGong(choice: GongChoice) { audio.play(choice) }

    fun changeSettings(change: (EnsoSettings) -> EnsoSettings) {
        scope.launch { mutate {
            val previous = snapshot.settings
            val next = change(previous)
            // Persist the activation together with the toggle, even if today's normal Enso exists.
            updateSettings(when {
                next.hardcoreMode && !previous.hardcoreMode -> next.copy(hardcorePending = true)
                !next.hardcoreMode -> next.copy(hardcorePending = false)
                else -> next
            })
            emptyList()
        } }
    }

    suspend fun command(action: SessionAction) = mutate {
        val now = SystemClock.elapsedRealtime()
        when (action) {
            SessionAction.Start -> start(now)
            SessionAction.Pause -> { audio.stop(); pause(now) }
            SessionAction.Resume -> { resume(now); emptyList() }
            SessionAction.Stop -> { stop(); audio.stop(); emptyList() }
            SessionAction.Restore -> emptyList()
        }
    }

    suspend fun tick() = mutate { advance(SystemClock.elapsedRealtime()) }
}
