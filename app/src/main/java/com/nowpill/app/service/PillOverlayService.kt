package com.nowpill.app.service

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewTreeLifecycleOwner
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nowpill.app.PillSettingsRepo
import com.nowpill.app.R
import com.nowpill.app.overlay.PillContent
import com.nowpill.app.tracker.NetworkSpeedTracker
import com.nowpill.app.tracker.StopwatchTracker
import com.nowpill.app.tracker.TimerTracker
import com.nowpill.app.ui.theme.NowPillTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs as a foreground service so the pill stays alive even when the app is
 * backgrounded, drawing a floating pill anchored to the top-left corner
 * using SYSTEM_ALERT_WINDOW. The pill is draggable; new position is saved.
 */
class PillOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams

    private lateinit var settingsRepo: PillSettingsRepo
    private val networkTracker = NetworkSpeedTracker()
    val stopwatch = StopwatchTracker()
    val timer = TimerTracker()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var expanded = mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        settingsRepo = PillSettingsRepo(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        networkTracker.start()
        startForegroundNotification()
        addPillView()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "nowpill_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "NowPill live tracking", NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("NowPill is tracking live activity")
            .setSmallIcon(R.drawable.ic_pill_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    private fun addPillView() {
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 80
        }

        val view = ComposeView(this).apply {
            ViewTreeLifecycleOwner.set(this, this@PillOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PillOverlayService)
            setContent {
                val settings by settingsRepo.settingsFlow.collectAsState(initial = com.nowpill.app.PillSettingsState())
                val isExpanded by expanded
                NowPillTheme {
                    PillContent(
                        settings = settings,
                        expanded = isExpanded,
                        networkTracker = networkTracker,
                        stopwatch = stopwatch,
                        timer = timer
                    )
                }
            }
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var dragged = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    dragged = false
                    expanded.value = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) dragged = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    expanded.value = false
                    if (dragged) {
                        scope.launch { settingsRepo.setPillPosition(params.x, params.y) }
                    }
                    true
                }
                else -> false
            }
        }

        composeView = view
        windowManager.addView(view, params)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        networkTracker.stop()
        composeView?.let { windowManager.removeView(it) }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
}
