package com.nowpill.app

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "nowpill_settings")

/** Which live-activity trackers the user can show in the pill. */
enum class TrackerType(val category: TrackerCategory) {
    MUSIC(TrackerCategory.MEDIA),
    NETWORK_SPEED(TrackerCategory.NETWORK),
    STOPWATCH(TrackerCategory.TIMERS),
    TIMER(TrackerCategory.TIMERS),
    DOWNLOAD(TrackerCategory.NETWORK),
    BATTERY(TrackerCategory.SYSTEM),
}

enum class TrackerCategory { MEDIA, NETWORK, TIMERS, SYSTEM }

/** How a tracker renders inside the merged capsule. */
enum class DisplayStyle { ICON_ONLY, ICON_TEXT, PROGRESS_RING }

object Keys {
    val ANIMATION_SCALE = floatPreferencesKey("animation_scale")   // 0.5x - 2.0x
    val PILL_POSITION_X = intPreferencesKey("pill_pos_x")
    val PILL_POSITION_Y = intPreferencesKey("pill_pos_y")
    val SHOW_ON_LOCKSCREEN = booleanPreferencesKey("show_on_lockscreen")
    val TRACKER_ORDER = stringPreferencesKey("tracker_order")       // comma list, defines order + which are enabled
    val POWER_SAVER = booleanPreferencesKey("power_saver")          // pause polling when screen is off
    fun enabledKey(t: TrackerType) = booleanPreferencesKey("enabled_${t.name}")
    fun styleKey(t: TrackerType) = stringPreferencesKey("style_${t.name}")
}

data class TrackerConfig(
    val type: TrackerType,
    val enabled: Boolean,
    val style: DisplayStyle,
)

data class PillSettingsState(
    val animationScale: Float = 1.0f,
    val trackers: List<TrackerConfig> = TrackerType.entries.map {
        TrackerConfig(it, enabled = true, style = DisplayStyle.ICON_TEXT)
    },
    val showOnLockScreen: Boolean = true,
    val powerSaver: Boolean = true,
    val pillX: Int = 24,
    val pillY: Int = 80,
) {
    val enabledTrackers: List<TrackerType> get() = trackers.filter { it.enabled }.map { it.type }
    fun styleFor(type: TrackerType): DisplayStyle =
        trackers.firstOrNull { it.type == type }?.style ?: DisplayStyle.ICON_TEXT
}

class PillSettingsRepo(private val context: Context) {

    val settingsFlow: Flow<PillSettingsState> = context.dataStore.data.map { p ->
        val orderStr = p[Keys.TRACKER_ORDER]
        val order = orderStr?.split(",")?.mapNotNull { name ->
            runCatching { TrackerType.valueOf(name) }.getOrNull()
        } ?: TrackerType.entries.toList()
        // include any tracker types not yet present in a saved order (e.g. after an app update)
        val fullOrder = order + TrackerType.entries.filter { it !in order }

        val trackers = fullOrder.map { type ->
            TrackerConfig(
                type = type,
                enabled = p[Keys.enabledKey(type)] ?: (type != TrackerType.BATTERY),
                style = p[Keys.styleKey(type)]?.let { runCatching { DisplayStyle.valueOf(it) }.getOrNull() }
                    ?: DisplayStyle.ICON_TEXT
            )
        }

        PillSettingsState(
            animationScale = p[Keys.ANIMATION_SCALE] ?: 1.0f,
            trackers = trackers,
            showOnLockScreen = p[Keys.SHOW_ON_LOCKSCREEN] ?: true,
            powerSaver = p[Keys.POWER_SAVER] ?: true,
            pillX = p[Keys.PILL_POSITION_X] ?: 24,
            pillY = p[Keys.PILL_POSITION_Y] ?: 80,
        )
    }

    suspend fun setAnimationScale(scale: Float) {
        context.dataStore.edit { it[Keys.ANIMATION_SCALE] = scale }
    }

    suspend fun setTrackerEnabled(type: TrackerType, enabled: Boolean) {
        context.dataStore.edit { it[Keys.enabledKey(type)] = enabled }
    }

    suspend fun setTrackerStyle(type: TrackerType, style: DisplayStyle) {
        context.dataStore.edit { it[Keys.styleKey(type)] = style.name }
    }

    suspend fun setShowOnLockScreen(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_ON_LOCKSCREEN] = show }
    }

    suspend fun setPowerSaver(enabled: Boolean) {
        context.dataStore.edit { it[Keys.POWER_SAVER] = enabled }
    }

    suspend fun setPillPosition(x: Int, y: Int) {
        context.dataStore.edit {
            it[Keys.PILL_POSITION_X] = x
            it[Keys.PILL_POSITION_Y] = y
        }
    }

    /** Move a tracker up/down in display order (persists the whole ordering). */
    suspend fun moveTracker(order: List<TrackerType>) {
        context.dataStore.edit { it[Keys.TRACKER_ORDER] = order.joinToString(",") { t -> t.name } }
    }
}
