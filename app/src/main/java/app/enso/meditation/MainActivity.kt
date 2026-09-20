package app.enso.meditation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.enso.meditation.ui.EnsoScreen
import app.enso.meditation.ui.theme.EnsoTheme
import app.enso.meditation.ui.theme.Paper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val controller get() = (application as EnsoApplication).controller
    private var notificationRequested = false
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationRequested = savedInstanceState?.getBoolean("notification_requested") ?: false
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, Paper.toArgb()),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, Paper.toArgb()),
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.awaitReady()
                if (controller.state.value.phase != TimerPhase.Idle) {
                    MeditationService.send(this@MainActivity, SessionAction.Restore)
                }
                controller.state.collect { state ->
                    if (state.phase == TimerPhase.Running && state.settings.keepScreenAwake)
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        setContent {
            val state by controller.state.collectAsState()
            EnsoTheme {
                EnsoScreen(state, controller::adjustDuration, controller::changeSettings) { action ->
                    MeditationService.send(this, action)
                    if (action == SessionAction.Start) requestNotifications()
                }
            }
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationRequested &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationRequested = true
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStop() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("notification_requested", notificationRequested)
        super.onSaveInstanceState(outState)
    }
}
