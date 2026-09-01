package com.nowpill.app

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.nowpill.app.overlay.PillContent
import com.nowpill.app.tracker.NetworkSpeedTracker
import com.nowpill.app.tracker.StopwatchTracker
import com.nowpill.app.tracker.TimerTracker
import com.nowpill.app.ui.theme.NowPillTheme

/**
 * A real, transparent Activity shown *over* the lock screen using the
 * official setShowWhenLocked/setTurnScreenOn APIs. This is the only
 * lock-screen approach Google allows for third-party apps (Android 10+
 * blocks SYSTEM_ALERT_WINDOW overlays from drawing above the keyguard).
 * Launch it from a notification tap, tile, or PendingIntent.
 */
class LockPillActivity : ComponentActivity() {

    private val networkTracker = NetworkSpeedTracker()
    private val stopwatch = StopwatchTracker()
    private val timer = TimerTracker()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val km = getSystemService(KeyguardManager::class.java)
        km?.requestDismissKeyguard(this, null)

        networkTracker.start()
        val settingsRepo = PillSettingsRepo(applicationContext)

        setContent {
            NowPillTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    val settings by settingsRepo.settingsFlow.collectAsState(initial = PillSettingsState())
                    Box(
                        modifier = Modifier.fillMaxSize().padding(top = 48.dp, start = 16.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        PillContent(
                            settings = settings,
                            expanded = true,
                            networkTracker = networkTracker,
                            stopwatch = stopwatch,
                            timer = timer
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        networkTracker.stop()
        super.onDestroy()
    }
}
