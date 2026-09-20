package app.enso.meditation

import android.app.Application
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

class EnsoApplication : Application() {
    val controller by lazy { SessionController(this) }
}

enum class SessionAction { Start, Pause, Resume, Stop, Restore }

/** Application-scoped owner; activity recreation never creates a second session. */
class SessionController(application: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = EnsoStore(application)
    private val audio = GongPlayer(application)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(TimerUiState())
    val state = mutableState.asStateFlow()
    private var warning = false
    private lateinit var engine: TimerEngine
    private val initialized = scope.async {
        val saved = try { store.load() } catch (e: IOException) {
            Log.e("Enso", "Unable to read local state", e)
            warning = true
            TimerSnapshot()
        }
        engine = TimerEngine(saved, SystemClock.elapsedRealtime())
        if (engine.snapshot != saved) persist()
        publish()
    }

    suspend fun awaitReady() { initialized.await() }

    private fun publish() {
        mutableState.value = engine.uiState(SystemClock.elapsedRealtime()).copy(storageWarning = warning)
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
            // Commit the consumed boundary before audio, preventing replay after process death.
            if (before != engine.snapshot) persist()
            publish()
            events.forEach { event ->
                when (event) {
                    TimerEvent.StartGong -> audio.play(engine.snapshot.settings.startGongSound)
                    is TimerEvent.IntervalCompleted -> {
                        if (event.gong) {
                            val settings = engine.snapshot.settings
                            // A repeating session keeps its session alive, so its boundaries are
                            // interval gongs; a one-shot session's boundary is the final end gong.
                            val sound = if (engine.snapshot.session?.continuous == true) {
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

    fun adjustDuration(direction: Int) { scope.launch { mutate { adjustDuration(direction); emptyList() } } }

    /** Plays a single recording so settings can be chosen by ear; never touches session state. */
    fun previewGong(choice: GongChoice) { audio.play(choice) }

    fun changeSettings(change: (EnsoSettings) -> EnsoSettings) {
        scope.launch { mutate { updateSettings(change(snapshot.settings)); emptyList() } }
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
