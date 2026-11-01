package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import io.github.eslamasabry.oc.android.widgets.WidgetActionRuntime

private const val MAX_PREVIEW_CHARS = 960
private const val LATCH_SLACK_MS = 1_000L

private data class StatusJob(val profile: String, val session: String, val token: String, val attempt: Int) {
    companion object {
        fun from(data: Data): StatusJob? {
            val profile = data.getString("profileID")
            val session = data.getString("sessionID")
            val token = data.getString("token")
            val attempt = data.getInt("attempt", -1)
            return if (profile != null && session != null && token != null) {
                StatusJob(profile, session, token, attempt).takeIf { attempt in WidgetStatusPolicy.delaysMs.indices }
            } else null
        }
    }
}

/** Ordinary JobScheduler work, never a foreground service or mutation replay. */
class WidgetStatusWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    private val handler = Handler(Looper.getMainLooper())
    private var runtime: WidgetActionRuntime? = null

    override fun doWork(): Result {
        val job = StatusJob.from(inputData) ?: return Result.failure()
        val ticket = readTicket(job)
        return when {
            ticket == null -> {
                removeTicket(job)
                Result.failure()
            }
            // WorkManager may restart a checkpoint after an OS stop. Do not repeat
            // its read, but let the remaining finite checkpoints retain their turn.
            !WidgetStatusPolicy.mayRead(runAttemptCount) -> {
                if (!WidgetStatusPolicy.continues("unavailable", job.attempt)) removeTicket(job)
                Result.success()
            }
            else -> runCheck(job, ticket)
        }
    }

    private fun readTicket(job: StatusJob) =
        WidgetStatusTickets.read(applicationContext, job.profile, job.session, job.token)

    private fun removeTicket(job: StatusJob) =
        WidgetStatusTickets.remove(applicationContext, job.profile, job.session, job.token)

    private fun runCheck(job: StatusJob, ticket: Map<String, Any?>): Result {
        val check = Check(job, ticket)
        check.start()
        val completed = check.await()
        if (isStopped) return Result.failure()
        // A delayed main callback or failed commit is an unavailable check,
        // never evidence that idle reached the widget. Keep later checkpoints.
        var outcome = if (completed) {
            WidgetStatusPolicy.settledStatus(check.status, true, check.idlePatched)
        } else {
            WidgetStatusPolicy.settledStatus(null, false, false)
        }
        if (completed && outcome != "idle" && readTicket(job) == null) outcome = "stale"
        return conclude(job, outcome)
    }

    private fun conclude(job: StatusJob, outcome: String): Result {
        if (!WidgetStatusPolicy.continues(outcome, job.attempt)) removeTicket(job)
        // Failed dependencies stop the remaining one-offs immediately.
        val stopped = !WidgetStatusPolicy.continues(outcome, job.attempt) &&
            outcome != "busy" && outcome != "unavailable"
        return if (stopped) Result.failure() else Result.success()
    }

    /** One bounded status read on the main thread, awaited by the Worker thread. */
    private inner class Check(private val job: StatusJob, private val ticket: Map<String, Any?>) {
        private val latch = CountDownLatch(1)
        private val finished = AtomicBoolean(false)
        private val timeout = Runnable { finish(null) }
        var status: String? = "unavailable"
            private set
        var idlePatched = false
            private set

        fun start() {
            handler.post {
                if (isStopped || finished.get()) finish(null) else begin()
            }
        }

        private fun begin() {
            handler.postDelayed(timeout, WidgetStatusPolicy.READ_BUDGET_MS)
            runtime = WidgetActionRuntime(applicationContext, ticket, JSONObject(),
                System.currentTimeMillis() + WidgetStatusPolicy.READ_BUDGET_MS,
                operation = "checkStatus", complete = ::finish)
            try { runtime?.start() } catch (_: RuntimeException) { finish(null) }
        }

        fun await(): Boolean {
            val completed = try {
                latch.await(WidgetStatusPolicy.READ_BUDGET_MS + LATCH_SLACK_MS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
            handler.post { handler.removeCallbacks(timeout); finish(null) }
            return completed
        }

        private fun finish(response: Map<*, *>?) {
            if (!finished.compareAndSet(false, true)) return
            status = response?.get("status") as? String ?: "unavailable"
            try {
                settleIdle(response)
            } catch (_: RuntimeException) {
                status = "unavailable"
            } finally {
                runCatching { runtime?.destroy() }
                runtime = null
                latch.countDown()
            }
        }

        /** Complete the guarded patch on this same main-thread callback before publishing idle
         * to the waiting Worker. */
        private fun settleIdle(response: Map<*, *>?) {
            if (status != "idle" || isStopped) return
            val current = readTicket(job)
            if (current == null) {
                status = "stale"
                return
            }
            val preview = (response?.get("preview") as? String)?.take(MAX_PREVIEW_CHARS)
            idlePatched = WidgetActionSnapshot.applyIdleStatus(applicationContext, current, preview)
            if (idlePatched) runCatching { SessionsWidgetProvider.refreshAll(applicationContext) }
        }
    }

    override fun onStopped() {
        handler.post { runtime?.destroy(); runtime = null }
    }
}
