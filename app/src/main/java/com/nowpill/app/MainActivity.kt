package com.nowpill.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nowpill.app.service.PillOverlayService
import com.nowpill.app.ui.theme.NowPillTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepo: PillSettingsRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepo = PillSettingsRepo(applicationContext)
        setContent {
            NowPillTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        settingsRepo = settingsRepo,
                        onGrantOverlay = ::requestOverlayPermission,
                        onGrantNotificationAccess = ::requestNotificationAccess,
                        onStartPill = { startForegroundService(Intent(this, PillOverlayService::class.java)) }
                    )
                }
            }
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun requestNotificationAccess() {
        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }
}

@Composable
fun SettingsScreen(
    settingsRepo: PillSettingsRepo,
    onGrantOverlay: () -> Unit,
    onGrantNotificationAccess: () -> Unit,
    onStartPill: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings by settingsRepo.settingsFlow.collectAsState(initial = PillSettingsState())

    Column(Modifier.padding(20.dp)) {
        Text("NowPill", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Material 3 Expressive live-activity pill",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))

        Text("1. Setup", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onGrantOverlay) { Text("Allow \"display over other apps\"") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onGrantNotificationAccess) { Text("Allow notification access (music/downloads)") }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onStartPill) { Text("Start the pill") }

        Spacer(Modifier.height(24.dp))
        Text("2. Trackers to show", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.heightIn(max = 260.dp)) {
            items(TrackerType.values().toList()) { tracker ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(tracker.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() })
                    Switch(
                        checked = tracker in settings.enabledTrackers,
                        onCheckedChange = { checked ->
                            scope.launch { settingsRepo.setTrackerEnabled(tracker, checked) }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("3. Animation speed: ${"%.1f".format(settings.animationScale)}x", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = settings.animationScale,
            valueRange = 0.5f..2.0f,
            onValueChange = { scope.launch { settingsRepo.setAnimationScale(it) } }
        )

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Show on lock screen (tap-to-open)", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = settings.showOnLockScreen,
                onCheckedChange = { scope.launch { settingsRepo.setShowOnLockScreen(it) } }
            )
        }
    }
}
