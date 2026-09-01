package com.nowpill.app.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nowpill.app.DisplayStyle
import com.nowpill.app.PillSettingsState
import com.nowpill.app.TrackerType
import com.nowpill.app.service.MediaListenerService
import com.nowpill.app.tracker.BatteryTracker
import com.nowpill.app.tracker.NetworkSpeedTracker
import com.nowpill.app.tracker.StopwatchTracker
import com.nowpill.app.tracker.TimerTracker

/** A single live value ready to render, resolved from whichever tracker backs it. */
private data class LiveSlot(
    val type: TrackerType,
    val icon: ImageVector,
    val label: String,
    val progress: Float?,   // 0f..1f for PROGRESS_RING style, null if not applicable
    val tint: androidx.compose.ui.graphics.Color,
)

/**
 * Renders every *active* tracker as one merged capsule — small dividers between
 * items, like Samsung's Now Bar / MT Capsule combine multiple live activities
 * into a single bar instead of cycling one at a time.
 * Tap anywhere to expand (bigger text, looser padding, spring "morph").
 */
@Composable
fun PillContent(
    settings: PillSettingsState,
    expanded: Boolean,
    networkTracker: NetworkSpeedTracker,
    stopwatch: StopwatchTracker,
    timer: TimerTracker,
    battery: BatteryTracker? = null,
) {
    val speedBps by networkTracker.speedBps.collectAsState()
    val swElapsed by stopwatch.elapsedMs.collectAsState()
    val swRunning by stopwatch.running.collectAsState()
    val timerRemaining by timer.remainingMs.collectAsState()
    val timerRunning by timer.running.collectAsState()
    val nowPlaying by MediaListenerService.nowPlaying.collectAsState()
    val download by MediaListenerService.activeDownload.collectAsState()
    val batteryPct by (battery?.percent ?: remember { kotlinx.coroutines.flow.MutableStateFlow(100) }).collectAsState()

    val enabledSet = settings.enabledTrackers.toSet()

    val slots = buildList {
        if (TrackerType.MUSIC in enabledSet && nowPlaying != null) {
            add(LiveSlot(TrackerType.MUSIC, Icons.Filled.MusicNote, nowPlaying!!.title ?: "Playing", null, MaterialTheme.colorScheme.tertiary))
        }
        if (TrackerType.DOWNLOAD in enabledSet && download != null) {
            val pct = if (download!!.max > 0) download!!.progress.toFloat() / download!!.max else 0f
            add(LiveSlot(TrackerType.DOWNLOAD, Icons.Filled.Download, "${(pct * 100).toInt()}%", pct, MaterialTheme.colorScheme.secondary))
        }
        if (TrackerType.TIMER in enabledSet && timerRunning) {
            add(LiveSlot(TrackerType.TIMER, Icons.Filled.Timer, TimerTracker.format(timerRemaining), null, MaterialTheme.colorScheme.primary))
        }
        if (TrackerType.STOPWATCH in enabledSet && swRunning) {
            add(LiveSlot(TrackerType.STOPWATCH, Icons.Filled.AvTimer, StopwatchTracker.format(swElapsed), null, MaterialTheme.colorScheme.primary))
        }
        if (TrackerType.NETWORK_SPEED in enabledSet) {
            add(LiveSlot(TrackerType.NETWORK_SPEED, Icons.Filled.NetworkCheck, NetworkSpeedTracker.formatSpeed(speedBps), null, MaterialTheme.colorScheme.tertiary))
        }
        if (TrackerType.BATTERY in enabledSet && batteryPct <= 20) {
            add(LiveSlot(TrackerType.BATTERY, Icons.Filled.BatteryAlert, "$batteryPct%", batteryPct / 100f, MaterialTheme.colorScheme.error))
        }
        if (isEmpty()) add(LiveSlot(TrackerType.NETWORK_SPEED, Icons.Filled.Circle, "Now", null, MaterialTheme.colorScheme.primary))
    }.sortedBy { slot -> settings.trackers.indexOfFirst { it.type == slot.type } }

    val transition = updateTransition(targetState = expanded, label = "pillExpand")
    val spec = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 320f * settings.animationScale
    )
    val cornerRadius by transition.animateDp(transitionSpec = { spec }, label = "corner") { if (it) 30.dp else 26.dp }
    val vPad by transition.animateDp(transitionSpec = { spec }, label = "vpad") { if (it) 12.dp else 9.dp }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.horizontalGradient(
                    listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
                )
            )
            .padding(horizontal = 14.dp, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.forEachIndexed { i, slot ->
            SlotView(
                slot = slot,
                style = settings.styleFor(slot.type),
                expanded = expanded,
                animationScale = settings.animationScale
            )
            if (i != slots.lastIndex) {
                Box(
                    Modifier
                        .padding(horizontal = 10.dp)
                        .size(width = 1.dp, height = 16.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                )
            }
        }
    }
}

@Composable
private fun SlotView(slot: LiveSlot, style: DisplayStyle, expanded: Boolean, animationScale: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (style) {
            DisplayStyle.PROGRESS_RING -> RingIcon(slot, animationScale)
            else -> PulseIcon(slot, animationScale)
        }
        if (style == DisplayStyle.ICON_TEXT || expanded) {
            AnimatedContent(
                targetState = slot.label,
                transitionSpec = {
                    (fadeIn(tween((220 / animationScale).toInt())) + slideInVertically { it / 3 })
                        .togetherWith(fadeOut(tween((120 / animationScale).toInt())))
                },
                label = "label-${slot.type}"
            ) { label ->
                Text(text = label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PulseIcon(slot: LiveSlot, scale: Float) {
    val infinite = rememberInfiniteTransition(label = "pulse-${slot.type}")
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween((950 / scale).toInt(), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseVal-${slot.type}"
    )
    Box(modifier = Modifier.size((17 * pulse).dp)) {
        Icon(imageVector = slot.icon, contentDescription = null, tint = slot.tint)
    }
}

@Composable
private fun RingIcon(slot: LiveSlot, scale: Float) {
    val target = slot.progress ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween((600 / scale).toInt()),
        label = "ring-${slot.type}"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = slot.tint,
            trackColor = slot.tint.copy(alpha = 0.2f),
        )
        Icon(
            imageVector = slot.icon,
            contentDescription = null,
            tint = slot.tint,
            modifier = Modifier.size(11.dp)
        )
    }
}
