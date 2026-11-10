package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** WorkManager provides a bounded wake lock; this does not start a dataSync service. */
class ScheduledPromptWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    /** What this attempt still owns, so cleanup releases exactly that. */
    private class Attempt(val entry: ScheduledPromptStore.Entry) {
        var acquired = false
        var claimed = false
    }

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var runtime: ScheduledPromptRuntime? = null

    override fun doWork(): Result {
        val entry = ScheduledPromptStore.Entry.orNull(
            inputData.getString("profileId").orEmpty(),
            inputData.getString("scheduleId").orEmpty(),
            inputData.getLong("revision", -1),
            inputData.getLong("scheduledAtMs", -1),
        ) ?: return Result.success()
        val started = SystemClock.elapsedRealtime()
        val attempt = Attempt(entry)
        val outcome = try {
            execute(attempt, started)
        } catch (_: InterruptedException) {
            settleInterrupted(attempt)
            Thread.currentThread().interrupt()
            Result.success()
        } catch (_: Exception) {
            settleInterrupted(attempt)
            Result.success()
        } finally {
            cleanup(attempt)
        }
        // Never retry: transport failure can follow accepted delivery.
        return outcome
    }

    private fun execute(attempt: Attempt, started: Long): Result {
        attempt.acquired = gate.tryAcquire(ScheduledPromptPolicy.WORKER_BUDGET_MS, TimeUnit.MILLISECONDS)
        val remaining = ScheduledPromptPolicy.remaining(started, SystemClock.elapsedRealtime())
        return early(attempt.entry, attempt.acquired, remaining) ?: claimAndRun(attempt, remaining)
    }

    /** A result for work that must end before any claim or transport exists; null to continue. */
    private fun early(entry: ScheduledPromptStore.Entry, acquired: Boolean, remaining: Long): Result? = when {
        !acquired -> Result.retry()
        isStopped -> Result.success()
        !ScheduledPromptPolicy.isDue(entry.due, System.currentTimeMillis()) -> {
            // Clock rollback: replace the pending fallback, including this
            // still-unclaimed worker, so KEEP cannot swallow the new date.
            ScheduledPromptScheduler(applicationContext).arm(entry, replacePending = true)
            Result.success()
        }
        remaining <= MIN_DART_WINDOW_MS -> Result.retry() // Still before claim.
        else -> null
    }

    private fun claimAndRun(attempt: Attempt, remaining: Long): Result {
        val entry = attempt.entry
        active.add(entry.workName)
        val store = ScheduledPromptStore(applicationContext)
        val claim = store.claim(entry)
        if (claim !in CLAIMABLE) return Result.success()
        attempt.claimed = true
        val response = runDart(entry, claim, remaining)
        val responseTime = System.currentTimeMillis()
        val notDue = response.field("status") == "skipped" && response.field("error") == "not_due" &&
            entry.due > responseTime
        if (notDue) {
            // The wall clock may have moved backward after the native guard.
            // Do not turn an explicitly unsent future run into failed history.
            attempt.claimed = false
            val scheduler = ScheduledPromptScheduler(applicationContext)
            if (store.deferEarlyOccurrence(entry, responseTime, claim == "recover")) {
                scheduler.arm(entry, replacePending = true)
            } else {
                scheduler.replace(entry.profileId)
            }
        } else {
            settleAndNotify(entry, store, response)
        }
        return Result.success()
    }

    private fun runDart(entry: ScheduledPromptStore.Entry, claim: String, remaining: Long): Map<*, *>? {
        val done = CountDownLatch(1)
        var response: Map<*, *>? = null
        val dartWindow = minOf(remaining - MIN_DART_WINDOW_MS, ScheduledPromptPolicy.DART_BUDGET_MS)
        val args = mapOf(
            "profileId" to entry.profileId, "scheduleId" to entry.id, "revision" to entry.revision,
            "scheduledAtMs" to entry.due, "deadlineEpochMs" to System.currentTimeMillis() + dartWindow,
            "recoveryOnly" to (claim == "recover"),
        )
        handler.post {
            if (!isStopped) {
                runtime = ScheduledPromptRuntime(applicationContext, args) { value ->
                    response = value
                    done.countDown()
                }
                runtime?.start()
            } else {
                done.countDown()
            }
        }
        done.await(remaining, TimeUnit.MILLISECONDS)
        return response
    }

    private fun settleAndNotify(
        entry: ScheduledPromptStore.Entry,
        store: ScheduledPromptStore,
        response: Map<*, *>?,
    ) {
        val valid = response.field("status") in VALID_STATUSES
        val incomplete = !valid || (entry.id != "__digest" && store.occurrenceCurrent(entry))
        val reportedError = (response.field("error") as? String)?.takeIf { it.matches(ERROR_CODE) }
        store.settle(entry, if (incomplete) reportedError ?: "interrupted" else null)
        if (response != null && valid && store.profileExists(entry.profileId)) {
            ScheduledPromptNotifications.show(applicationContext, entry, response)
        }
    }

    private fun settleInterrupted(attempt: Attempt) {
        if (!attempt.claimed) return
        runCatching { ScheduledPromptStore(applicationContext).settle(attempt.entry, "interrupted") }
    }

    private fun cleanup(attempt: Attempt) {
        val stopped = CountDownLatch(1)
        handler.post {
            try {
                runtime?.destroy()
                runtime = null
            } finally {
                stopped.countDown()
            }
        }
        runCatching { stopped.await(RUNTIME_STOP_WAIT_SECONDS, TimeUnit.SECONDS) }
        if (attempt.claimed) {
            runCatching { ScheduledPromptScheduler(applicationContext).replace(attempt.entry.profileId) }
        }
        active.remove(attempt.entry.workName)
        if (attempt.acquired) gate.release()
    }

    override fun onStopped() {
        handler.post {
            runtime?.destroy()
            runtime = null
        }
    }

    companion object {
        private const val MIN_DART_WINDOW_MS = 15_000L
        private const val RUNTIME_STOP_WAIT_SECONDS = 5L
        private val CLAIMABLE = setOf("new", "recover")
        private val VALID_STATUSES = setOf("ran", "failed", "skipped", "digest")
        private val ERROR_CODE = Regex("[a-z][a-z0-9_]{0,79}")
        private val gate = Semaphore(1)
        private val active = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        fun isActive(name: String) = name in active
    }
}

private fun Map<*, *>?.field(key: String): Any? = this?.get(key)
