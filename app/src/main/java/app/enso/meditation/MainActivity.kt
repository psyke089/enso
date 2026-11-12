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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
    private var splashIcon by mutableStateOf<ImageBitmap?>(null)
    private var splashBounds by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    private var splashReleased by mutableStateOf(Build.VERSION.SDK_INT < 31)
    private var notificationRequested = false
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Enso)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 31) {
            splashScreen.setOnExitAnimationListener { splash ->
                val icon = splash.iconView
                if (icon != null && icon.width > 0) {
                    val iconPosition = IntArray(2)
                    val rootPosition = IntArray(2)
                    icon.getLocationOnScreen(iconPosition)
                    window.decorView.getLocationOnScreen(rootPosition)
                    val left = (iconPosition[0] - rootPosition[0]).toFloat()
                    val top = (iconPosition[1] - rootPosition[1]).toFloat()
                    splashBounds = androidx.compose.ui.geometry.Rect(left, top,
                        left + icon.width, top + icon.height)
                    // Android normalizes the drawable inside this view. Reusing its rendered
                    // pixels preserves that scale; drawing the padded PNG again shrinks the ink.
                    val bitmap = android.graphics.Bitmap.createBitmap(icon.width, icon.height,
                        android.graphics.Bitmap.Config.ARGB_8888)
                    icon.draw(android.graphics.Canvas(bitmap))
                    bitmap.prepareToDraw()
                    splashIcon = bitmap.asImageBitmap()
                }
                // Remove on the next frame, after Compose receives the native icon bounds.
                splashReleased = true
                window.decorView.postOnAnimation { splash.remove() }
            }
        }
        notificationRequested = savedInstanceState?.getBoolean("notification_requested") ?: false
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, Paper.toArgb()),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, Paper.toArgb()),
        )
        lifecycleScope.launch {
            if (savedInstanceState == null) controller.onLaunch()
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
            val state by controller.layoutState.collectAsState()
            EnsoTheme {
                EnsoScreen(state, controller.tickState, controller::adjustDuration, controller::adjustRepetitions,
                    controller::changeSettings,
                    controller::previewGong, animateLaunch = savedInstanceState == null,
                    splashReleased = splashReleased, splashBounds = splashBounds, splashIcon = splashIcon) { action ->
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

    override fun onStart() {
        super.onStart()
        controller.prepareAdjustmentFeedback()
    }

    override fun onStop() {
        controller.releaseAdjustmentFeedback()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("notification_requested", notificationRequested)
        super.onSaveInstanceState(outState)
    }
}
