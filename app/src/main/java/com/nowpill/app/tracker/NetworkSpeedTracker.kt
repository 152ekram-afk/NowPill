package com.nowpill.app.tracker

import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Polls TrafficStats every second and reports total (down+up) throughput.
 * Works without extra permissions beyond ACCESS_NETWORK_STATE.
 */
class NetworkSpeedTracker {
    private val handler = Handler(Looper.getMainLooper())
    private var lastBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
    private var lastTime = System.currentTimeMillis()

    private val _speedBps = MutableStateFlow(0L)
    val speedBps: StateFlow<Long> = _speedBps

    private val ticker = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val bytesNow = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
            val deltaBytes = (bytesNow - lastBytes).coerceAtLeast(0)
            val deltaSeconds = ((now - lastTime).coerceAtLeast(1)) / 1000.0
            _speedBps.value = (deltaBytes / deltaSeconds).toLong()
            lastBytes = bytesNow
            lastTime = now
            handler.postDelayed(this, 1000)
        }
    }

    fun start() {
        handler.post(ticker)
    }

    fun stop() {
        handler.removeCallbacks(ticker)
    }

    companion object {
        fun formatSpeed(bps: Long): String {
            val kbps = bps / 1024.0
            return if (kbps < 1024) "%.0f KB/s".format(kbps)
            else "%.1f MB/s".format(kbps / 1024.0)
        }
    }
}
