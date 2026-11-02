package io.github.eslamasabry.oc.android.platform

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.github.eslamasabry.oc.engine.entry.EntryOrigin
import io.github.eslamasabry.oc.engine.entry.EntryRouter
import kotlin.time.Clock

/**
 * 07 §7.1, the capture half of `MainActivity` (and of the non-exported `.InternalEntryActivity`): every launcher
 * shortcut, deep link, shared text, tile long-press, alert or scheduled-run tap goes `EntryIntentReader.read` →
 * `strip` → `setIntent` → `EntryRouter.submit`. The activity shells in `:androidApp` call only these two functions:
 *
 * ```kotlin
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     EntryCapture.onCreate(this, savedInstanceState, EntryOrigin.Untrusted, graph.clock, graph.entryRouter)
 *     …
 * }
 * override fun onNewIntent(intent: Intent) {
 *     super.onNewIntent(intent)
 *     EntryCapture.onNewIntent(this, intent, EntryOrigin.Untrusted, graph.clock, graph.entryRouter)
 * }
 * ```
 *
 * Untrusted arrivals only navigate (16 §1.8.1, Astra M2): `MainActivity` is exported, so it always passes
 * [EntryOrigin.Untrusted]; only `.InternalEntryActivity` (app-owned immutable PendingIntents) passes `Internal`. Nothing
 * here touches the network, logs a value or keeps a request: requests submitted before the graph is ready wait inside
 * the router (07 §7.1, rule 9: memory only).
 */
public object EntryCapture {
    /**
     * Captures the launching intent once. A restored activity (`savedInstanceState != null`, process death or Recents)
     * and an activity launched from history get their ORIGINAL intent again and never replay it. Returns how many
     * requests were submitted.
     */
    public fun onCreate(
        activity: Activity,
        savedInstanceState: Bundle?,
        origin: EntryOrigin,
        clock: Clock,
        router: EntryRouter,
    ): Int {
        val intent = activity.intent ?: return 0
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState != null || fromHistory) return 0
        return capture(activity, intent, origin, clock, router)
    }

    /** A live tap while the activity exists (`singleTop`). The new intent replaces the activity's, stripped. */
    public fun onNewIntent(
        activity: Activity,
        intent: Intent,
        origin: EntryOrigin,
        clock: Clock,
        router: EntryRouter,
    ): Int = capture(activity, intent, origin, clock, router)

    private fun capture(activity: Activity, intent: Intent, origin: EntryOrigin, clock: Clock, router: EntryRouter): Int {
        val requests = EntryIntentReader.read(intent, origin, clock)
        // Rejected values are consumed too: a configuration change or a later read must never see them again.
        EntryIntentReader.strip(intent)
        activity.intent = intent
        requests.forEach(router::submit)
        return requests.size
    }
}
