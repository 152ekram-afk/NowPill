package com.nowpill.app.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nowpill.app.PillSettingsRepo
import com.nowpill.app.PillSettingsState
import com.nowpill.app.R
import com.nowpill.app.overlay.PillContent
import com.nowpill.app.tracker.BatteryTracker
import com.nowpill.app.tracker.NetworkSpeedTracker
import com.nowpill.app.tracker.StopwatchTracker
import com.nowpill.app.tracker.TimerTracker
import com.nowpill.app.ui.theme.NowPillTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Runs as a foreground service so the pill stays alive even when the app is
 * backgrounded, drawing a floating pill anchored to the top-left corner
 * using SYSTEM_ALERT_WINDOW. The pill is draggable; new position is saved.
 *
 * Power efficiency: when "power saver" is on (default), live polling
 * (network speed, stopwatch/timer ticks) pauses while the screen is off,
 * since nothing is visible to update anyway.
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
    private lateinit var battery: BatteryTracker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var expanded = mutableStateOf(false)
    private var powerSaverEnabled = true

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!powerSaverEnabled) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> networkTracker.stop()
                Intent.ACTION_SCREEN_ON -> networkTracker.start()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        settingsRepo = PillSettingsRepo(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        battery = BatteryTracker(applicationContext)
        battery.start()
        networkTracker.start()
        registerReceiver(screenStateReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        scope.launch { powerSaverEnabled = settingsRepo.settingsFlow.first().powerSaver }
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
            setViewTreeLifecycleOwner(this@PillOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PillOverlayService)
            setContent {
                val settings by settingsRepo.settingsFlow.collectAsState(initial = PillSettingsState())
                val isExpanded by expanded
                NowPillTheme {
                    PillContent(
                        settings = settings,
                        expanded = isExpanded,
                        networkTracker = networkTracker,
                        stopwatch = stopwatch,
                        timer = timer,
                        battery = battery
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
        runCatching { unregisterReceiver(screenStateReceiver) }
        battery.stop()
        networkTracker.stop()
        composeView?.let { windowManager.removeView(it) }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
}
