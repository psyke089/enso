package app.enso.meditation

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** One serialized worker. A running session holds a CPU lock, never a screen lock. */
class MeditationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Channel<SessionAction>(Channel.UNLIMITED)
    private val controller get() = (application as EnsoApplication).controller
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var notifications: NotificationManager
    private var lastNotification = ""
    private var latestStartId = 0
    private var pendingCommands = 0

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Meditation",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quiet controls for an active meditation"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            })
        }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Enso:meditation")
            .apply { setReferenceCounted(false) }
        scope.launch {
            controller.awaitReady()
            while (isActive) {
                val action = if (controller.state.value.phase == TimerPhase.Running)
                    withTimeoutOrNull(250) { commands.receive() } else commands.receive()
                if (action != null) {
                    pendingCommands--
                    controller.command(action)
                } else controller.tick()
                val state = controller.state.value
                updateWakeLock(state.phase == TimerPhase.Running)
                if (state.phase == TimerPhase.Idle && pendingCommands == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(latestStartId)
                } else {
                    val key = "${state.phase}:${formatTime(state.remainingMs)}:${state.continuous}"
                    if (key != lastNotification) {
                        notifications.notify(NOTIFICATION, notification(state))
                        lastNotification = key
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        startForeground(NOTIFICATION, notification(controller.state.value))
        val action = SessionAction.entries.firstOrNull { it.name == intent?.action } ?: SessionAction.Restore
        pendingCommands++
        commands.trySend(action)
        return START_STICKY
    }

    @Suppress("WakelockTimeout") // Lasts for a user-controlled continuous session; released on pause/stop/destroy.
    private fun updateWakeLock(running: Boolean) {
        if (running && !wakeLock.isHeld) wakeLock.acquire()
        if (!running && wakeLock.isHeld) wakeLock.release()
    }

    private fun notification(state: TimerUiState): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Enso")
            .setContentText(if (!state.ready) "Meditation" else
                "${formatTime(state.remainingMs)}${if (state.phase == TimerPhase.Paused) " · Paused" else ""}${if (state.continuous) " · Continuous Gong" else ""}")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setSilent(true).setShowWhen(false).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (state.phase != TimerPhase.Idle) {
            val action = if (state.phase == TimerPhase.Paused) SessionAction.Resume else SessionAction.Pause
            builder.addAction(0, action.name, pendingAction(action))
            builder.addAction(0, "Stop", pendingAction(SessionAction.Stop))
        }
        return builder.build()
    }

    private fun pendingAction(action: SessionAction) = PendingIntent.getService(this, action.ordinal,
        Intent(this, MeditationService::class.java).setAction(action.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    override fun onDestroy() {
        scope.cancel()
        updateWakeLock(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "meditation"
        private const val NOTIFICATION = 1
        fun send(context: Context, action: SessionAction) {
            ContextCompat.startForegroundService(context,
                Intent(context, MeditationService::class.java).setAction(action.name))
        }
    }
}
