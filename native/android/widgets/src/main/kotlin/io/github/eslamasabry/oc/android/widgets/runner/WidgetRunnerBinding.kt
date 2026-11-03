package io.github.eslamasabry.oc.android.widgets.runner

import android.content.Context
import io.github.eslamasabry.oc.android.widgets.WidgetBackgroundRunner
import io.github.eslamasabry.oc.android.widgets.WidgetLiveDispatcher
import io.github.eslamasabry.oc.android.widgets.WidgetPersistence
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Builds the private cold connection of one saved profile, pinned to the target's directory and workspace. */
public fun interface WidgetRunOpener {
    public suspend fun open(profileId: String, directory: String?, workspace: String?): WidgetOpen
}

/**
 * Both runner worlds over the ONE acknowledged widget store (keys re-read on every guard) and the app's saved
 * profiles. [profile] answers the fingerprinted metadata of a saved profile, never a secret.
 */
public class PersistedWidgetWorld(
    persistence: WidgetPersistence,
    private val profile: suspend (String) -> WidgetRunProfile?,
    private val opener: WidgetRunOpener,
    /** The snapshot the tap was built from; a durable replacement of its run blocks an old stop. */
    private val expectedSnapshot: Map<*, *>? = null,
) : WidgetActionWorld, WidgetStatusWorld {
    private val guards = WidgetRunGuards(persistence::string)

    override suspend fun loadProfile(profileId: String): WidgetRunProfile? = profile(profileId)

    override suspend fun loadTarget(tap: WidgetTap, fingerprint: String): Map<String, Any?>? =
        guards.target(tap, fingerprint)

    override suspend fun isCurrent(tap: WidgetTap, fingerprint: String): Boolean =
        guards.actionIsCurrent(tap, fingerprint, expectedSnapshot)

    override suspend fun isCurrent(ticket: Map<String, Any?>): Boolean = guards.statusIsCurrent(ticket)

    override suspend fun open(profile: WidgetRunProfile, target: Map<String, Any?>): WidgetOpen =
        opener.open(profile.id, target["directory"] as? String, target["workspace"] as? String)
}

/**
 * The [WidgetBackgroundRunner] the app binds (replaces the headless Dart engine): `execute` runs a
 * [WidgetActionRunner], `checkStatus` a [WidgetStatusRunner], each on its own instance in [scope]. The handle's
 * cancel stops the run and closes its transport; a malformed request completes at once with the Dart refusal.
 */
public class WidgetRunnerHost(
    private val scope: CoroutineScope,
    private val persistence: WidgetPersistence,
    private val profile: suspend (String) -> WidgetRunProfile?,
    private val opener: WidgetRunOpener,
    private val preview: (List<MessageWithParts>) -> String,
    private val now: () -> Long,
) : WidgetBackgroundRunner {
    override fun start(
        context: Context,
        payload: Map<String, Any?>,
        snapshotJson: String,
        deadlineEpochMs: Long,
        operation: String,
        complete: (Map<*, *>?) -> Unit,
    ): WidgetBackgroundRunner.Handle {
        if (operation == "checkStatus") {
            val runner = WidgetStatusRunner(PersistedWidgetWorld(persistence, profile, opener), now, preview)
            return launch(complete, runner::cancel) { runner.check(payload, deadlineEpochMs) }
        }
        val tap = WidgetTap.from(payload)
        val snapshot = jsonMap(snapshotJson)
        if (operation != "execute" || tap == null || snapshot == null) {
            complete(mapOf("status" to "rejected", "error" to "invalidRequest"))
            return WidgetBackgroundRunner.Handle {}
        }
        val runner = WidgetActionRunner(PersistedWidgetWorld(persistence, profile, opener, snapshot), now)
        return launch(complete, runner::cancel) { runner.execute(tap, snapshot, deadlineEpochMs).toMap() }
    }

    private fun launch(
        complete: (Map<*, *>?) -> Unit,
        cancel: suspend () -> Unit,
        block: suspend () -> Map<String, Any?>,
    ): WidgetBackgroundRunner.Handle {
        val job = scope.launch(start = CoroutineStart.LAZY) { complete(block()) }
        job.invokeOnCompletion { cause -> if (cause != null) complete(null) }
        job.start()
        return WidgetBackgroundRunner.Handle {
            scope.launch { withContext(NonCancellable) { cancel() } }
            job.cancel()
        }
    }
}

/** The [WidgetLiveDispatcher] the app binds: one live handler, its answer posted back as the Dart-shaped map. */
public class WidgetLiveHost(private val scope: CoroutineScope, private val actions: WidgetLiveActions) :
    WidgetLiveDispatcher {
    override fun dispatch(payload: Map<String, Any?>, complete: (Map<String, Any?>) -> Unit) {
        scope.launch { complete(actions.handle(payload)) }
    }
}

/** org.json keeps small integers as Int, as the copied snapshot patcher expects (`version == 2`). */
internal fun jsonMap(raw: String): Map<String, Any?>? =
    try {
        toKotlin(JSONObject(raw)) as? Map<String, Any?>
    } catch (_: org.json.JSONException) {
        null
    }

private fun toKotlin(value: Any?): Any? =
    when (value) {
        is JSONObject -> LinkedHashMap<String, Any?>().apply { for (key in value.keys()) put(key, toKotlin(value.opt(key))) }
        is JSONArray -> List(value.length()) { toKotlin(value.opt(it)) }
        JSONObject.NULL -> null
        else -> value
    }
