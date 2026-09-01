package com.nowpill.app.overlay

import androidx.compose.animation.core.*
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nowpill.app.PillSettingsState
import com.nowpill.app.TrackerType
import com.nowpill.app.service.MediaListenerService
import com.nowpill.app.tracker.NetworkSpeedTracker
import com.nowpill.app.tracker.StopwatchTracker
import com.nowpill.app.tracker.TimerTracker

/** One live "card" of info that gets time-sliced into the pill, expressive Material 3 style. */
private data class LiveItem(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)

/**
 * The pill itself. Cycles through whichever trackers are active with a fluid
 * crossfade + scale "morph" transition, speed controlled by [settings.animationScale].
 */
@Composable
fun PillContent(
    settings: PillSettingsState,
    expanded: Boolean,
    networkTracker: NetworkSpeedTracker,
    stopwatch: StopwatchTracker,
    timer: TimerTracker,
) {
    val speedBps by networkTracker.speedBps.collectAsState()
    val swElapsed by stopwatch.elapsedMs.collectAsState()
    val swRunning by stopwatch.running.collectAsState()
    val timerRemaining by timer.remainingMs.collectAsState()
    val timerRunning by timer.running.collectAsState()
    val nowPlaying by MediaListenerService.nowPlaying.collectAsState()
    val download by MediaListenerService.activeDownload.collectAsState()

    val items = buildList {
        if (TrackerType.MUSIC in settings.enabledTrackers && nowPlaying != null) {
            add(LiveItem(Icons.Filled.MusicNote, nowPlaying!!.title ?: "Playing"))
        }
        if (TrackerType.DOWNLOAD in settings.enabledTrackers && download != null) {
            val pct = if (download!!.max > 0) (download!!.progress * 100 / download!!.max) else 0
            add(LiveItem(Icons.Filled.Download, "$pct%"))
        }
        if (TrackerType.TIMER in settings.enabledTrackers && timerRunning) {
            add(LiveItem(Icons.Filled.Timer, TimerTracker.format(timerRemaining)))
        }
        if (TrackerType.STOPWATCH in settings.enabledTrackers && swRunning) {
            add(LiveItem(Icons.Filled.Timer, StopwatchTracker.format(swElapsed)))
        }
        if (TrackerType.NETWORK_SPEED in settings.enabledTrackers) {
            add(LiveItem(Icons.Filled.NetworkCheck, NetworkSpeedTracker.formatSpeed(speedBps)))
        }
        if (isEmpty()) add(LiveItem(Icons.Filled.Circle, "Now"))
    }

    // Cycle the visible item every 3s, scaled by the animation-speed setting.
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(items.size, settings.animationScale) {
        while (true) {
            kotlinx.coroutines.delay((3000 / settings.animationScale).toLong())
            index = (index + 1) % items.size.coerceAtLeast(1)
        }
    }
    val current = items[index % items.size]

    val transition = updateTransition(targetState = expanded, label = "pillExpand")
    val cornerRadius by transition.animateDp(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 300f * settings.animationScale) },
        label = "corner"
    ) { if (it) 28.dp else 24.dp }
    val horizontalPad by transition.animateDp(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 300f * settings.animationScale) },
        label = "pad"
    ) { if (it) 20.dp else 14.dp }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(horizontal = horizontalPad, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedIcon(icon = current.icon, scale = settings.animationScale)
        androidx.compose.animation.AnimatedContent(
            targetState = current.label,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(animationSpec = tween((250 / settings.animationScale).toInt())) +
                    androidx.compose.animation.slideInVertically { it / 3 })
                    .togetherWith(androidx.compose.animation.fadeOut(animationSpec = tween((150 / settings.animationScale).toInt())))
            },
            label = "label"
        ) { label ->
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
        if (items.size > 1) {
            Text(
                text = "${index % items.size + 1}/${items.size}",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun AnimatedIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, scale: Float) {
    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween((900 / scale).toInt(), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseVal"
    )
    Box(
        modifier = Modifier
            .size((18 * pulse).dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
