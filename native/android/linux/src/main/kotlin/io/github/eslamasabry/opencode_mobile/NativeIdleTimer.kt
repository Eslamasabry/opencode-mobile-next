package io.github.eslamasabry.opencode_mobile

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val RECEIVER_TIMEOUT_MS = 8_000L
private const val WORKER_KEEP_ALIVE_SECONDS = 30L
private const val SCHEDULER_KEEP_ALIVE_SECONDS = 10L

/** One elapsed-time check; every dispatch rechecks native authority rather than authorizing a stop. */
internal class NativeIdleTimer(
    private val now: () -> Long,
    private val enqueue: (Long, () -> Unit) -> (() -> Unit),
    private val alarms: Alarms,
    private val check: () -> Unit,
) {
    interface Alarms { fun schedule(deadlineElapsedMs: Long); fun cancel() }
    constructor(context: Context, check: () -> Unit) : this(
        SystemClock::elapsedRealtime, queue(), AndroidAlarms(context.applicationContext), check)

    private val guard = Any()
    private var generation = 0L
    private var exhausted = false
    private var deadline: Long? = null
    private var highWater: Long? = null
    private var cancelLocal: (() -> Unit)? = null

    fun schedule(deadlineElapsedMs: Long?) = synchronized(guard) {
        if (deadlineElapsedMs == null || deadlineElapsedMs < 0) { invalidate(); return@synchronized }
        val current = clock() ?: run { invalidate(); return@synchronized }
        if (exhausted || deadline == deadlineElapsedMs) return@synchronized
        invalidate()
        if (exhausted) return@synchronized
        deadline = deadlineElapsedMs
        try { alarms.schedule(deadlineElapsedMs) } catch (_: Throwable) { /* Local fallback remains available. */ }
        armLocal(deadlineElapsedMs, current, generation)
    }

    fun cancel() = synchronized(guard) { invalidate() }

    private fun invalidate() {
        if (generation == Long.MAX_VALUE) exhausted = true else generation++
        deadline = null
        try { cancelLocal?.invoke() } catch (_: Throwable) { }
        cancelLocal = null
        try { alarms.cancel() } catch (_: Throwable) { }
    }

    private fun clock(): Long? {
        val current = try { now() } catch (_: Throwable) { null }
        return current?.takeIf { it >= 0 && highWater?.let { high -> it < high } != true }
            ?.also { highWater = it }
    }

    private fun armLocal(expectedDeadline: Long, current: Long, revision: Long) {
        val delay = if (expectedDeadline <= current) 0 else expectedDeadline - current
        try {
            cancelLocal = enqueue(delay, task@{
                val due = synchronized(guard) {
                    if (exhausted || revision != generation || deadline != expectedDeadline) return@task
                    cancelLocal = null
                    val at = clock() ?: run { invalidate(); return@task }
                    if (at < expectedDeadline) {
                        armLocal(expectedDeadline, at, revision)
                        false
                    } else {
                        invalidate()
                        true
                    }
                }
                // Linux calls schedule while holding its monitor. Never acquire Linux under guard.
                if (due) try { check() } catch (_: Throwable) { }
            })
        } catch (_: Throwable) { /* A scheduled inexact alarm can still deliver its independent check. */ }
    }

    private class AndroidAlarms(context: Context) : Alarms {
        private val manager by lazy { context.getSystemService(AlarmManager::class.java) }
        private val intent by lazy { PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE,
            Intent(context, BuiltinIdleReceiver::class.java).setAction(BuiltinIdleReceiver.ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        override fun schedule(deadlineElapsedMs: Long) {
            manager?.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadlineElapsedMs, intent)
        }
        override fun cancel() { manager?.cancel(intent) }
    }

    private companion object {
        const val ALARM_REQUEST_CODE = 4095

        fun queue(): (Long, () -> Unit) -> (() -> Unit) {
            val executor = idleScheduler("phone-idle-timer")
            return { delay, work ->
                val future = executor.schedule({ work() }, delay, TimeUnit.MILLISECONDS)
                val cancel: () -> Unit = { future.cancel(false); Unit }
                cancel
            }
        }
    }
}

/** Pure dispatch boundary: one finish, including worker rejection, failure and timeout. */
internal class NativeIdleReceiverDispatch(
    private val run: (() -> Unit) -> (() -> Unit),
    private val later: (Long, () -> Unit) -> (() -> Unit),
) {
    fun dispatch(check: () -> Unit, finish: () -> Unit) {
        val finished = AtomicBoolean(false)
        val cancelWork = AtomicReference<(() -> Unit)?>(null)
        val cancelWatch = AtomicReference<(() -> Unit)?>(null)
        fun quietly(work: (() -> Unit)?) { try { work?.invoke() } catch (_: Throwable) { } }
        fun complete(interrupt: Boolean) {
            if (!finished.compareAndSet(false, true)) return
            if (interrupt) quietly(cancelWork.get())
            quietly(cancelWatch.get())
            quietly(finish)
        }
        try {
            val watch = later(RECEIVER_TIMEOUT_MS) { complete(true) }
            cancelWatch.set(watch)
            if (finished.get()) { quietly(watch); return }
            val work = run(task@{
                if (finished.get()) return@task
                try { check() } catch (_: Throwable) { }
                finally { complete(false) }
            })
            cancelWork.set(work)
            if (finished.get()) quietly(work)
        } catch (_: Throwable) { complete(true) }
    }
}

/** Nonexported manifest receiver. This path only checks whether an owned runtime must stop. */
internal class BuiltinIdleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = try { goAsync() } catch (_: Throwable) { null } ?: return
        try {
            dispatch.dispatch({ BuiltinLinux.get(context.applicationContext).checkIdleStop() }, pending::finish)
        } catch (_: Throwable) {
            // Includes failure to allocate the bounded dispatch executors.
            try { pending.finish() } catch (_: Throwable) { }
        }
    }

    companion object {
        const val ACTION = "io.github.eslamasabry.opencode_mobile.IDLE_CHECK"
        private val dispatch by lazy {
            // No queue of broadcasts waiting behind an unresponsive native check.
            val worker = ThreadPoolExecutor(0, 1, WORKER_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                SynchronousQueue<Runnable>(),
                { task -> Thread(task, "phone-idle-receiver").apply { isDaemon = true } })
            val watch = idleScheduler("phone-idle-receiver-watch")
            NativeIdleReceiverDispatch({ task ->
                val future = worker.submit { task() }
                val cancel: () -> Unit = { future.cancel(true); Unit }
                cancel
            }, { delay, task ->
                val future = watch.schedule({ task() }, delay, TimeUnit.MILLISECONDS)
                val cancel: () -> Unit = { future.cancel(false); Unit }
                cancel
            })
        }
    }
}

private fun idleScheduler(name: String) = ScheduledThreadPoolExecutor(1,
    { task -> Thread(task, name).apply { isDaemon = true } }).apply {
    removeOnCancelPolicy = true
    setKeepAliveTime(SCHEDULER_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS)
    allowCoreThreadTimeOut(true)
}
