package io.github.eslamasabry.opencode_mobile

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import org.json.JSONObject

private const val MAX_PREVIEW_CHARS = 960

private data class StatusJob(
    val profile: String,
    val session: String,
    val token: String,
    val attempt: Int,
) {
    companion object {
        fun from(data: Data): StatusJob? {
            val profile = data.getString("profileID")
            val session = data.getString("sessionID")
            val token = data.getString("token")
            val attempt = data.getInt("attempt", -1)
            return if (profile != null && session != null && token != null) {
                StatusJob(profile, session, token, attempt).takeIf {
                    attempt in WidgetStatusPolicy.delaysMs.indices
                }
            } else null
        }
    }
}

/** Ordinary bounded read-only work; suspension keeps storage writes in the native writer lanes. */
class WidgetStatusWorker(context: Context, parameters: WorkerParameters) :
    androidx.work.CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val job = StatusJob.from(inputData) ?: return Result.failure()
        val storage =
            io.github.eslamasabry.oc.android.widgets.WidgetFeed.persistence
                ?: return Result.failure()
        val ticket =
            WidgetStatusTickets.read(applicationContext, job.profile, job.session, job.token)
        if (ticket == null) {
            WidgetStatusTickets.remove(applicationContext, job.profile, job.session, job.token)
            return Result.failure()
        }
        var outcome = "unavailable"
        if (WidgetStatusPolicy.mayRead(runAttemptCount)) {
            val response =
                io.github.eslamasabry.oc.android.widgets.awaitWidgetRun(
                    applicationContext,
                    ticket,
                    JSONObject(),
                    storage,
                    WidgetStatusPolicy.READ_BUDGET_MS,
                    "checkStatus",
                )
            var status = response?.get("status") as? String
            var patched = false
            val current =
                WidgetStatusTickets.read(applicationContext, job.profile, job.session, job.token)
            if (current == null) status = "stale"
            if (status == "idle" && current != null) {
                val preview = (response?.get("preview") as? String)?.take(MAX_PREVIEW_CHARS)
                patched = WidgetActionSnapshot.applyIdleStatus(applicationContext, current, preview)
                if (patched) SessionsWidgetProvider.refreshAll(applicationContext)
            }
            outcome = WidgetStatusPolicy.settledStatus(status, response != null, patched)
            if (current == null) outcome = "stale"
        }
        val continues = WidgetStatusPolicy.continues(outcome, job.attempt)
        if (!continues)
            WidgetStatusTickets.remove(applicationContext, job.profile, job.session, job.token)
        return if (!continues && outcome != "busy" && outcome != "unavailable") Result.failure()
        else Result.success()
    }
}
