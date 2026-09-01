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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
                    StudioScreen(
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
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    private fun requestNotificationAccess() {
        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }
}

private fun iconFor(type: TrackerType): ImageVector = when (type) {
    TrackerType.MUSIC -> Icons.Filled.MusicNote
    TrackerType.NETWORK_SPEED -> Icons.Filled.NetworkCheck
    TrackerType.STOPWATCH -> Icons.Filled.AvTimer
    TrackerType.TIMER -> Icons.Filled.Timer
    TrackerType.DOWNLOAD -> Icons.Filled.Download
    TrackerType.BATTERY -> Icons.Filled.BatteryAlert
}

private fun labelFor(type: TrackerType): String = when (type) {
    TrackerType.MUSIC -> "Music player"
    TrackerType.NETWORK_SPEED -> "Internet speed"
    TrackerType.STOPWATCH -> "Stopwatch"
    TrackerType.TIMER -> "Timer"
    TrackerType.DOWNLOAD -> "Download progress"
    TrackerType.BATTERY -> "Low battery alert"
}

@Composable
fun StudioScreen(
    settingsRepo: PillSettingsRepo,
    onGrantOverlay: () -> Unit,
    onGrantNotificationAccess: () -> Unit,
    onStartPill: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings by settingsRepo.settingsFlow.collectAsState(initial = PillSettingsState())
    var categoryFilter by remember { mutableStateOf<TrackerCategory?>(null) }
    var editingTracker by remember { mutableStateOf<TrackerType?>(null) }

    val visible = settings.trackers.filter { categoryFilter == null || it.type.category == categoryFilter }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp, 20.dp, 20.dp, 0.dp)) {
            Text("NowPill", style = MaterialTheme.typography.headlineMedium)
            Text(
                "A bold live-activity bar for music, speed, downloads, and timers — fully yours to customize.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGrantOverlay, modifier = Modifier.weight(1f)) { Text("Overlay access") }
                OutlinedButton(onClick = onGrantNotificationAccess, modifier = Modifier.weight(1f)) { Text("Notification access") }
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onStartPill, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Start the live bar")
            }

            Spacer(Modifier.height(20.dp))
            Text("${settings.enabledTrackers.size} trackers active", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryChip("All", categoryFilter == null) { categoryFilter = null }
                CategoryChip("Media", categoryFilter == TrackerCategory.MEDIA) { categoryFilter = TrackerCategory.MEDIA }
                CategoryChip("Network", categoryFilter == TrackerCategory.NETWORK) { categoryFilter = TrackerCategory.NETWORK }
                CategoryChip("Timers", categoryFilter == TrackerCategory.TIMERS) { categoryFilter = TrackerCategory.TIMERS }
            }
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            items(visible, key = { it.type.name }) { config ->
                val index = settings.trackers.indexOf(config)
                TrackerRow(
                    config = config,
                    canMoveUp = index > 0,
                    canMoveDown = index < settings.trackers.lastIndex,
                    onToggle = { checked -> scope.launch { settingsRepo.setTrackerEnabled(config.type, checked) } },
                    onMoveUp = {
                        scope.launch {
                            val newOrder = settings.trackers.map { it.type }.toMutableList()
                            newOrder.removeAt(index); newOrder.add(index - 1, config.type)
                            settingsRepo.moveTracker(newOrder)
                        }
                    },
                    onMoveDown = {
                        scope.launch {
                            val newOrder = settings.trackers.map { it.type }.toMutableList()
                            newOrder.removeAt(index); newOrder.add(index + 1, config.type)
                            settingsRepo.moveTracker(newOrder)
                        }
                    },
                    onEditStyle = { editingTracker = config.type }
                )
                Spacer(Modifier.height(8.dp))
            }
            item {
                Spacer(Modifier.height(24.dp))
                Text("Animation speed: ${"%.1f".format(settings.animationScale)}x", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = settings.animationScale,
                    valueRange = 0.5f..2.0f,
                    onValueChange = { scope.launch { settingsRepo.setAnimationScale(it) } }
                )
                Spacer(Modifier.height(8.dp))
                SettingsSwitchRow(
                    label = "Show on lock screen (tap-to-open)",
                    checked = settings.showOnLockScreen,
                    onCheckedChange = { scope.launch { settingsRepo.setShowOnLockScreen(it) } }
                )
                SettingsSwitchRow(
                    label = "Power saver (pause tracking, screen off)",
                    checked = settings.powerSaver,
                    onCheckedChange = { scope.launch { settingsRepo.setPowerSaver(it) } }
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    editingTracker?.let { type ->
        val current = settings.trackers.first { it.type == type }
        StylePickerSheet(
            type = type,
            selected = current.style,
            onSelect = { style ->
                scope.launch { settingsRepo.setTrackerStyle(type, style) }
                editingTracker = null
            },
            onDismiss = { editingTracker = null }
        )
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun TrackerRow(
    config: TrackerConfig,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEditStyle: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(4.dp))
            Icon(iconFor(config.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(labelFor(config.type), style = MaterialTheme.typography.bodyLarge)
                Text(
                    config.style.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onEditStyle) { Text("Style") }
            Switch(checked = config.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StylePickerSheet(
    type: TrackerType,
    selected: DisplayStyle,
    onSelect: (DisplayStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp)) {
            Text("Display style — ${labelFor(type)}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            DisplayStyle.entries.forEach { style ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = style == selected, onClick = { onSelect(style) })
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (style) {
                            DisplayStyle.ICON_ONLY -> "Icon only"
                            DisplayStyle.ICON_TEXT -> "Icon + text"
                            DisplayStyle.PROGRESS_RING -> "Mini progress ring"
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
