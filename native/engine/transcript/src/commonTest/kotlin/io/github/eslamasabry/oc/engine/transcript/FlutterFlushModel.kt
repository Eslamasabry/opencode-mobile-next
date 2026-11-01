package io.github.eslamasabry.oc.engine.transcript

/**
 * A line-for-line model of the Flutter app's stream flush (lib/ui/screens/chat/chat_stream.dart, `_scheduleStreamFlush`
 * and `_flushStreamDeltas`), in virtual milliseconds. It is the reference the native publish counts are compared with.
 *
 * - A task is one synchronous run of the Dart event loop (one SSE frame, or the test's burst loop).
 * - Each delta: timer running → dirty; already scheduled → nothing; else schedule a microtask flush.
 * - The microtask runs at the end of the task: flush (count++), clear dirty, start a 50 ms timer.
 * - The timer fires at its deadline and flushes again only if dirty (which restarts it).
 * Timers due at or before a task's time fire before that task (Dart runs expired timers first).
 */
class FlutterFlushModel {
    var flushes: Int = 0
        private set
    private var timerAt: Long? = null
    private var dirty = false

    fun task(atMs: Long, deltas: Int) {
        fireTimersUpTo(atMs)
        var scheduled = false
        repeat(deltas) {
            when {
                timerAt != null -> dirty = true
                scheduled -> Unit
                else -> scheduled = true
            }
        }
        if (scheduled) flush(atMs)
    }

    fun finish() = fireTimersUpTo(Long.MAX_VALUE)

    private fun fireTimersUpTo(t: Long) {
        while (true) {
            val due = timerAt ?: return
            if (due > t) return
            timerAt = null
            if (dirty) flush(due)
        }
    }

    private fun flush(at: Long) {
        flushes++
        dirty = false
        timerAt = at + 50
    }
}
