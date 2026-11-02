package io.github.eslamasabry.oc.protocol.paseo.work

import io.github.eslamasabry.oc.protocol.paseo.paseoMillis
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Keeps idle stored agents asleep and their list time honest (B-AGENT-169), a port of
 * `lib/paseo/gateway/idle_guard.dart` (`_PaseoIdleGuard`).
 *
 * Incident 2026-10-27: reading an agent's history makes the daemon load it (a provider process starts) and re-stamp
 * `updatedAt`, so idle chats jumped to "Just now". Two defences:
 * 1. the gateway's `genUiMayLoad(id)` = open live chat ∨ awaiting/active turn ∨ live agent session ∨ [isAwake] ∨
 *    [working] (its snapshot) — background reads touch only agents the daemon already holds;
 * 2. [settle]: a list time that rises without evidence of real work keeps its earlier value.
 *
 * Wiring: the gateway calls [settle] for every agent snapshot it maps into a session (Dart `_guardedSession`), with
 * `saved` = the list time the app showed last run for the daemon id (Dart `savedListTime`), and replaces the session's
 * `time.updatedMs` with the result.
 */
public class PaseoIdleGuard {
    private val awake = mutableSetOf<String>()
    private val prompt = HashMap<String, Long?>()
    private val shown = HashMap<String, Long>()

    /** Seen working (or hydrated, or sent to) since its time last moved: the daemon holds it in memory. */
    public fun isAwake(id: String): Boolean = id in awake

    /** Marks [id] as held in memory (the person opened its chat or sent to it). */
    public fun markAwake(id: String) {
        awake += id
    }

    /**
     * The list time for [agent]: [updatedMs] when the rise is backed by real activity, otherwise the time already
     * shown. [saved] stands in for the time shown on a first sight, so a load stamped while the app was closed does not
     * move the row either; a prompt after it does. An earlier time never counts as a rise.
     */
    public fun settle(id: String, agent: JsonObject, updatedMs: Long?, saved: (() -> Long?)? = null): Long? {
        val stamp = paseoMillis(agent["lastUserMessageAt"])
        var previous = shown[id]
        val before = prompt[id]
        prompt[id] = stamp
        val isWorking = turning(agent)
        if (isWorking) awake += id
        if (updatedMs == null) return null
        val firstSight = previous == null
        if (firstSight) previous = saved?.invoke()
        if (previous == null || updatedMs <= previous + TOLERANCE_MS) {
            shown[id] = updatedMs
            return updatedMs
        }
        // A new prompt, a turn in progress, or a turn this app watched run is real work (a long turn that ends hours
        // after its prompt included). On a first sight against the saved time, only a prompt after that time is new.
        val newPrompt = stamp != null && if (firstSight) stamp > previous + TOLERANCE_MS else before == null || stamp > before
        if (!(newPrompt || isWorking || id in awake)) {
            shown[id] = previous
            return previous
        }
        shown[id] = updatedMs
        // A turn that has ended stops being evidence for the next rise.
        if (!isWorking) awake -= id
        return updatedMs
    }

    public companion object {
        /** A rise below this is the daemon's own bookkeeping, not a wake-up. */
        public const val TOLERANCE_MS: Long = 1_000

        /** Starting, running, or with a turn: the daemon holds it in memory. */
        public fun working(agent: JsonObject): Boolean =
            agent.str("status") == "running" || agent.str("status") == "initializing" || hasTurn(agent)

        /**
         * A turn runs. An agent the daemon is only loading ("initializing" with no turn: the person opened its chat) is
         * in memory but has done nothing, so it earns no new list time (jitter J10, 2026-10-28).
         */
        public fun turning(agent: JsonObject): Boolean = agent.str("status") == "running" || hasTurn(agent)

        private fun hasTurn(agent: JsonObject): Boolean = agent["activeTurn"].let { it != null && it !is JsonNull }
    }
}
