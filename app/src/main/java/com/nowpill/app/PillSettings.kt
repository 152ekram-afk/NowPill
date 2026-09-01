package com.nowpill.app

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "nowpill_settings")

/** Which live-activity trackers the user wants visible in the pill. */
enum class TrackerType { MUSIC, NETWORK_SPEED, STOPWATCH, TIMER, DOWNLOAD }

object Keys {
    val ANIMATION_SCALE = floatPreferencesKey("animation_scale")   // 0.5x - 2.0x
    val PILL_POSITION_X = intPreferencesKey("pill_pos_x")
    val PILL_POSITION_Y = intPreferencesKey("pill_pos_y")
    val ENABLED_MUSIC = booleanPreferencesKey("enabled_music")
    val ENABLED_NETWORK = booleanPreferencesKey("enabled_network")
    val ENABLED_STOPWATCH = booleanPreferencesKey("enabled_stopwatch")
    val ENABLED_TIMER = booleanPreferencesKey("enabled_timer")
    val ENABLED_DOWNLOAD = booleanPreferencesKey("enabled_download")
    val SHOW_ON_LOCKSCREEN = booleanPreferencesKey("show_on_lockscreen")
    val PILL_CORNER_TOP_LEFT = booleanPreferencesKey("pill_corner_top_left") // false = user-dragged custom pos
}

data class PillSettingsState(
    val animationScale: Float = 1.0f,
    val enabledTrackers: Set<TrackerType> = setOf(
        TrackerType.MUSIC, TrackerType.NETWORK_SPEED, TrackerType.STOPWATCH,
        TrackerType.TIMER, TrackerType.DOWNLOAD
    ),
    val showOnLockScreen: Boolean = true,
    val pillX: Int = 24,
    val pillY: Int = 80,
)

class PillSettingsRepo(private val context: Context) {

    val settingsFlow: Flow<PillSettingsState> = context.dataStore.data.map { p ->
        val enabled = buildSet {
            if (p[Keys.ENABLED_MUSIC] != false) add(TrackerType.MUSIC)
            if (p[Keys.ENABLED_NETWORK] != false) add(TrackerType.NETWORK_SPEED)
            if (p[Keys.ENABLED_STOPWATCH] != false) add(TrackerType.STOPWATCH)
            if (p[Keys.ENABLED_TIMER] != false) add(TrackerType.TIMER)
            if (p[Keys.ENABLED_DOWNLOAD] != false) add(TrackerType.DOWNLOAD)
        }
        PillSettingsState(
            animationScale = p[Keys.ANIMATION_SCALE] ?: 1.0f,
            enabledTrackers = enabled,
            showOnLockScreen = p[Keys.SHOW_ON_LOCKSCREEN] ?: true,
            pillX = p[Keys.PILL_POSITION_X] ?: 24,
            pillY = p[Keys.PILL_POSITION_Y] ?: 80,
        )
    }

    suspend fun setAnimationScale(scale: Float) {
        context.dataStore.edit { it[Keys.ANIMATION_SCALE] = scale }
    }

    suspend fun setTrackerEnabled(type: TrackerType, enabled: Boolean) {
        val key = when (type) {
            TrackerType.MUSIC -> Keys.ENABLED_MUSIC
            TrackerType.NETWORK_SPEED -> Keys.ENABLED_NETWORK
            TrackerType.STOPWATCH -> Keys.ENABLED_STOPWATCH
            TrackerType.TIMER -> Keys.ENABLED_TIMER
            TrackerType.DOWNLOAD -> Keys.ENABLED_DOWNLOAD
        }
        context.dataStore.edit { it[key] = enabled }
    }

    suspend fun setShowOnLockScreen(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_ON_LOCKSCREEN] = show }
    }

    suspend fun setPillPosition(x: Int, y: Int) {
        context.dataStore.edit {
            it[Keys.PILL_POSITION_X] = x
            it[Keys.PILL_POSITION_Y] = y
        }
    }
}
