package io.github.eslamasabry.opencode_mobile

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs [NativeOrphanReaper] for [BuiltinLinux]: once soon after a proot
 * starts or ends ([soon]), and every [periodMs] while any proot runs
 * ([running]). Its one thread ends when nothing runs and starts again on
 * the next [soon]. [liveRoots] are the pids of the proots the app started.
 */
internal class NativeOrphanReaperHost(
    private val uid: Int,
    private val appPid: Int,
    private val running: () -> Boolean,
    private val liveRoots: () -> Set<Int>,
    private val periodMs: Long = PERIOD_MS,
) {
    private val wake = Semaphore(0)
    private var worker: Thread? = null
    private val total = AtomicInteger()
    @Volatile private var lastAtMs: Long? = null

    /** How many orphans were stopped since the app started, and when last. */
    fun diagnostics(): Map<String, Any?> =
        mapOf("orphansReaped" to total.get(), "orphansLastReapedAtMs" to lastAtMs)

    fun soon() {
        synchronized(this) {
            if (worker?.isAlive != true) {
                worker = Thread(::loop, "phone-orphan-reaper").apply { isDaemon = true; start() }
            }
        }
        wake.release()
    }

    private fun loop() {
        try {
            while (true) {
                val woken = wake.tryAcquire(periodMs, TimeUnit.MILLISECONDS)
                wake.drainPermits()
                val busy = try { running() } catch (_: Throwable) { true }
                if (!woken && !busy && retire()) return
                reapOnce()
            }
        } catch (_: InterruptedException) {
            synchronized(this) { worker = null }
        }
    }

    /** True when the thread may end: no wake arrived since the last look. */
    private fun retire(): Boolean = synchronized(this) {
        (wake.availablePermits() == 0).also { if (it) worker = null }
    }

    /** One pass; the number of orphans that ended. Never throws. */
    fun reapOnce(): Int = try {
        val chosen = NativeOrphanReaper.orphans(NativeProcScan.scan(uid), uid, appPid, liveRoots())
        if (chosen.isEmpty()) 0 else {
            val ended = NativeProcScan.terminate(chosen, GRACE_MS).size
            if (ended > 0) {
                total.addAndGet(ended)
                lastAtMs = System.currentTimeMillis()
            }
            ended
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        0
    } catch (_: Throwable) { 0 /* Supervision must not crash the app; the next pass tries again. */ }

    private companion object {
        const val PERIOD_MS = 60_000L
        const val GRACE_MS = 1_500L
    }
}
