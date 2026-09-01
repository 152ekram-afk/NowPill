package com.nowpill.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NowPlayingInfo(val title: String?, val artist: String?, val packageName: String)
data class DownloadInfo(val title: String?, val progress: Int, val max: Int, val packageName: String)

/**
 * Reads currently-posted notifications to detect media playback (MediaStyle
 * notifications) and active downloads (progress notifications), without
 * needing per-app integrations. Requires the user to grant Notification
 * Access once in system settings.
 */
class MediaListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        refreshState()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        refreshState()
    }

    override fun onListenerConnected() {
        refreshState()
    }

    private fun refreshState() {
        val active = try { activeNotifications } catch (e: SecurityException) { emptyArray() }

        var media: NowPlayingInfo? = null
        var download: DownloadInfo? = null

        for (sbn in active) {
            val n = sbn.notification
            val extras = n.extras
            val isMediaStyle = n.category == Notification.CATEGORY_TRANSPORT ||
                extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
            if (isMediaStyle && media == null) {
                media = NowPlayingInfo(
                    title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                    artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                    packageName = sbn.packageName
                )
            }
            if (n.category == Notification.CATEGORY_PROGRESS && download == null) {
                val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
                val prog = extras.getInt(Notification.EXTRA_PROGRESS, 0)
                if (max > 0) {
                    download = DownloadInfo(
                        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                        progress = prog, max = max, packageName = sbn.packageName
                    )
                }
            }
        }

        _nowPlaying.value = media
        _activeDownload.value = download
    }

    companion object {
        private val _nowPlaying = MutableStateFlow<NowPlayingInfo?>(null)
        val nowPlaying: StateFlow<NowPlayingInfo?> = _nowPlaying

        private val _activeDownload = MutableStateFlow<DownloadInfo?>(null)
        val activeDownload: StateFlow<DownloadInfo?> = _activeDownload
    }
}
