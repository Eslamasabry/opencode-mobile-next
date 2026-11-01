package io.github.eslamasabry.opencode_mobile

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Schedules the finite follow-up checks after an accepted widget action. */
internal object WidgetStatusScheduler {
    private fun tag(profile: String) = "widget-status-profile-$profile"
    private fun name(profile: String, session: String) = "widget-status-$profile-$session"

    fun afterAction(context: Context, payload: Map<String, Any?>) {
        val profile = payload["profileID"] as? String
        val session = payload["sessionID"] as? String
        // One lock with the ticket store keeps scheduling and worker reads ordered.
        if (profile != null && session != null) {
            synchronized(WidgetStatusTickets) { schedule(context, payload, profile, session) }
        }
    }

    private fun schedule(context: Context, payload: Map<String, Any?>, profile: String, session: String) {
        val identity = WidgetStatusIdentity.current(context, profile, session)
        if (identity == null || payload["scopeToken"] != identity["scopeToken"]) return
        if (!WidgetStatusPolicy.matches(identity, identity)) {
            cancel(context, profile, session)
            return
        }
        val token = UUID.randomUUID().toString()
        val ticket = JSONObject(identity + mapOf("token" to token))
        if (WidgetStatusTickets.save(context, profile, session, ticket)) enqueue(context, profile, session, token)
    }

    private fun enqueue(context: Context, profile: String, session: String, token: String) {
        val requests = WidgetStatusPolicy.delaysMs.mapIndexed { attempt, delay ->
            OneTimeWorkRequest.Builder(WidgetStatusWorker::class.java)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString("profileID", profile)
                    .putString("sessionID", session).putString("token", token)
                    .putInt("attempt", attempt).build())
                .addTag(tag(profile)).build()
        }
        // A chain bounds total reads and prevents overlapping cold engines.
        // Delays are minimums: Android may defer ordinary background work.
        var chain = WorkManager.getInstance(context).beginUniqueWork(
            name(profile, session), ExistingWorkPolicy.REPLACE, requests.first(),
        )
        requests.drop(1).forEach { chain = chain.then(it) }
        chain.enqueue()
    }

    private fun cancel(context: Context, profile: String, session: String) {
        WidgetStatusTickets.discard(context, profile, session)
        WorkManager.getInstance(context).cancelUniqueWork(name(profile, session))
    }

    fun clearProfile(context: Context, profile: String) {
        synchronized(WidgetStatusTickets) {
            WidgetStatusTickets.clear(context, profile)
            WorkManager.getInstance(context).cancelAllWorkByTag(tag(profile))
        }
    }
}
