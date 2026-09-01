package com.nowpill.app.tracker

import android.os.CountDownTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class StopwatchTracker {
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private var startedAt = 0L
    private var accumulated = 0L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (_running.value) {
                _elapsedMs.value = accumulated + (System.currentTimeMillis() - startedAt)
                handler.postDelayed(this, 200)
            }
        }
    }

    fun start() {
        if (_running.value) return
        startedAt = System.currentTimeMillis()
        _running.value = true
        handler.post(ticker)
    }

    fun pause() {
        if (!_running.value) return
        accumulated += System.currentTimeMillis() - startedAt
        _running.value = false
    }

    fun reset() {
        accumulated = 0
        _elapsedMs.value = 0
        _running.value = false
    }

    companion object {
        fun format(ms: Long): String {
            val totalSec = ms / 1000
            val m = totalSec / 60
            val s = totalSec % 60
            val cs = (ms % 1000) / 10
            return "%02d:%02d.%02d".format(m, s, cs)
        }
    }
}

class TimerTracker {
    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running
    private var timer: CountDownTimer? = null

    fun start(durationMs: Long) {
        timer?.cancel()
        _running.value = true
        timer = object : CountDownTimer(durationMs, 200) {
            override fun onTick(millisUntilFinished: Long) {
                _remainingMs.value = millisUntilFinished
            }
            override fun onFinish() {
                _remainingMs.value = 0
                _running.value = false
            }
        }.start()
    }

    fun cancel() {
        timer?.cancel()
        _running.value = false
        _remainingMs.value = 0
    }

    companion object {
        fun format(ms: Long): String {
            val totalSec = (ms + 999) / 1000
            val m = totalSec / 60
            val s = totalSec % 60
            return "%02d:%02d".format(m, s)
        }
    }
}
