package io.github.eslamasabry.oc.android.platform

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window

// Field recorder, part 2 (plan 13 §3.6, WP-13.4; unit S2-FIELD): smoothness in the last hour, counted per minute in
// memory (60 one-minute buckets, plan 13 §7.1) and never stored. The frame listener is attached only while an activity
// is resumed, so nothing runs while the app is hidden. Per-frame work on the UI thread is the platform's FrameMetrics
// copy only; the counting runs on a background handler and allocates nothing (budget ≤ 0.1 ms per frame).
//
// JankStats 1.0.0 (`androidx.metrics:metrics-performance`) is the plan's API. This module cannot add it (S2-FIELD
// writes new files only), so this binds the same platform source JankStats uses on API 24+
// (Window.addOnFrameMetricsAvailableListener) and the same jank rule (a frame is janky when it took longer than twice
// its deadline, JankStats' default `jankHeuristicMultiplier`). Swapping to JankStats is a catalog request, not a
// contract change: [FrameCounter] and [FrameSummary] stay.

/** Frames in the last hour; [streamingFrames] is the share counted while a reply was streaming. */
public data class FrameSummary(
    val frames: Int,
    val onTime: Int,
    val streamingFrames: Int,
    val streamingOnTime: Int,
    val minutes: Int,
) {
    /** "99 of 100 frames on time": a whole number 0..100, or null when no frame was counted. */
    val onTimePerHundred: Int? get() = if (frames == 0) null else (onTime.toLong() * 100 / frames).toInt()

    public companion object {
        public val Empty: FrameSummary = FrameSummary(0, 0, 0, 0, 0)
    }
}

/**
 * The per-minute ring (pure, thread-safe). One bucket per minute of the monotonic frame clock; a frame older than the
 * window is dropped; reads sum the buckets inside the hour that ends at `now`. No allocation per frame.
 */
public class FrameCounter(private val capacityMinutes: Int = WINDOW_MINUTES) {
    private val minute = LongArray(capacityMinutes) { Long.MIN_VALUE }
    private val frames = IntArray(capacityMinutes)
    private val onTime = IntArray(capacityMinutes)
    private val streaming = IntArray(capacityMinutes)
    private val streamingOnTime = IntArray(capacityMinutes)
    private val lock = Any()

    /**
     * Counts one frame that started at [startNs] (monotonic nanoseconds) and took [durationNs] against its
     * [deadlineNs]. A non-positive deadline counts the frame as on time (the platform did not say).
     */
    public fun record(startNs: Long, durationNs: Long, deadlineNs: Long, isStreaming: Boolean) {
        if (startNs < 0 || durationNs < 0) return
        val m = startNs / NANOS_PER_MINUTE
        val slot = (m % capacityMinutes).toInt()
        val janky = deadlineNs > 0 && durationNs > deadlineNs * JANK_MULTIPLIER
        synchronized(lock) {
            if (minute[slot] != m) {
                if (minute[slot] > m) return // an older minute that was already overwritten
                minute[slot] = m
                frames[slot] = 0
                onTime[slot] = 0
                streaming[slot] = 0
                streamingOnTime[slot] = 0
            }
            frames[slot]++
            if (!janky) onTime[slot]++
            if (isStreaming) {
                streaming[slot]++
                if (!janky) streamingOnTime[slot]++
            }
        }
    }

    /** The hour that ends at [nowNs] (same clock as [record]). */
    public fun lastHour(nowNs: Long): FrameSummary {
        val now = nowNs / NANOS_PER_MINUTE
        var f = 0
        var o = 0
        var s = 0
        var so = 0
        var minutes = 0
        synchronized(lock) {
            for (i in 0 until capacityMinutes) {
                val m = minute[i]
                if (m == Long.MIN_VALUE || m > now || m <= now - capacityMinutes) continue
                if (frames[i] == 0) continue
                f += frames[i]
                o += onTime[i]
                s += streaming[i]
                so += streamingOnTime[i]
                minutes++
            }
        }
        return FrameSummary(f, o, s, so, minutes)
    }

    public fun clear() {
        synchronized(lock) {
            minute.fill(Long.MIN_VALUE)
            frames.fill(0)
            onTime.fill(0)
            streaming.fill(0)
            streamingOnTime.fill(0)
        }
    }

    public companion object {
        public const val WINDOW_MINUTES: Int = 60
        public const val JANK_MULTIPLIER: Long = 2
        private const val NANOS_PER_MINUTE = 60_000_000_000L
    }
}

/**
 * Attaches the frame listener to each resumed activity's window and removes it on pause, so the count runs only while
 * the app is on screen. [start] and [close] repeat safely; activity callbacks arrive on the main thread.
 * [setStreaming] is the `streaming` state tag (plan 13 §3.6); it is read on the counting thread.
 */
public class FieldFrameStats(
    private val application: Application,
    private val counter: FrameCounter = FrameCounter(),
    private val monotonicNs: () -> Long = System::nanoTime,
) : AutoCloseable {
    @Volatile private var streaming = false
    @Volatile private var started = false
    private val attached = HashMap<Window, Window.OnFrameMetricsAvailableListener>() // main thread only
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    /** Windows that currently carry the listener. Zero while the app is hidden. */
    public val attachedWindows: Int get() = attached.size

    public fun setStreaming(isStreaming: Boolean) {
        streaming = isStreaming
    }

    /** Frames in the hour that ends now. */
    public fun summary(): FrameSummary = counter.lastHour(monotonicNs())

    /** Main thread. Starts listening from the next resumed activity. */
    public fun start() {
        if (started) return
        started = true
        application.registerActivityLifecycleCallbacks(callbacks)
    }

    /** Main thread. Detaches every listener and stops the counting thread. */
    override fun close() {
        if (!started) return
        started = false
        application.unregisterActivityLifecycleCallbacks(callbacks)
        attached.keys.toList().forEach(::detach)
        thread?.quitSafely()
        thread = null
        handler = null
    }

    /** Main thread. Call from an activity that is already resumed when [start] runs (callbacks miss it). */
    public fun attach(activity: Activity) {
        if (!started) return
        val window = activity.window ?: return
        if (window in attached) return
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ -> onFrame(metrics, window) }
        try {
            window.addOnFrameMetricsAvailableListener(listener, countingHandler())
            attached[window] = listener
        } catch (_: Exception) {
            // Hardware acceleration off or the window is gone: no numbers for this window.
        }
    }

    public fun detach(activity: Activity) {
        activity.window?.let(::detach)
    }

    private fun detach(window: Window) {
        val listener = attached.remove(window) ?: return
        try {
            window.removeOnFrameMetricsAvailableListener(listener)
        } catch (_: Exception) {
            // The window already dropped it.
        }
    }

    private fun countingHandler(): Handler = handler ?: HandlerThread("oc-frame-stats").let { t ->
        t.start()
        thread = t
        Handler(t.looper).also { handler = it }
    }

    private fun onFrame(metrics: FrameMetrics, window: Window) {
        if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return
        val start = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
        val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        counter.record(start, duration, deadlineOf(metrics, window), streaming)
    }

    private fun deadlineOf(metrics: FrameMetrics, window: Window): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val deadline = metrics.getMetric(FrameMetrics.DEADLINE)
            if (deadline > 0) return deadline
        }
        return refreshIntervalNs(window)
    }

    @Suppress("DEPRECATION") // windowManager.defaultDisplay is the API 24–29 path; context.display needs 30.
    private fun refreshIntervalNs(window: Window): Long {
        val rate = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.context.display.refreshRate
            else window.windowManager.defaultDisplay.refreshRate
        } catch (_: Exception) {
            null
        }
        return if (rate == null || rate < 1f) 0L else (1_000_000_000.0 / rate).toLong()
    }

    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) = attach(activity)

        override fun onActivityPaused(activity: Activity) = detach(activity)

        override fun onActivityDestroyed(activity: Activity) = detach(activity)

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

        override fun onActivityStarted(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {}

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    }
}
