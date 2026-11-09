package io.github.eslamasabry.oc.engine.settings.teaching

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the app remembers for the one-time "star us on GitHub" ask, app-wide (never per profile): how many prompts
 * started a turn, how many of those got a good reply, and whether the ask was ever shown. Stored in one string under
 * [StarPromptRules.KEY] as `p=<n>;r=<n>;s=<0|1>`.
 */
public data class StarPromptState(val prompts: Int = 0, val replies: Int = 0, val shown: Boolean = false)

/** The rules, free of storage so they are tested with plain values. */
public object StarPromptRules {
    /** App-wide, so not an `oc.<what>.<profileId>` key. */
    public const val KEY: String = "oc.starPrompt"

    /** The repository the ask opens (an address the app authored). */
    public const val URL: String = "https://github.com/Eslamasabry/opencode-mobile-next"

    /** The ask comes with the second prompt ever. */
    public const val ASK_ON_PROMPT: Int = 2

    /** Absent means a fresh install; unreadable means "do not ask": a nag is worse than a missed ask. */
    public fun parse(raw: String?): StarPromptState {
        if (raw == null) return StarPromptState()
        val parts = raw.split(';').mapNotNull { it.split('=', limit = 2).takeIf { kv -> kv.size == 2 } }.associate { it[0] to it[1] }
        val prompts = parts["p"]?.toIntOrNull()
        val replies = parts["r"]?.toIntOrNull()
        val shown = parts["s"]
        if (prompts == null || replies == null || prompts < 0 || replies < 0 || (shown != "0" && shown != "1")) {
            return StarPromptState(shown = true)
        }
        return StarPromptState(prompts, replies, shown == "1")
    }

    public fun encode(state: StarPromptState): String = "p=${state.prompts};r=${state.replies};s=${if (state.shown) 1 else 0}"

    /**
     * A prompt started a turn. Asks (true) only on the second prompt ever, when the first got a good reply, and only
     * once ever: the state it returns already says shown.
     */
    public fun afterPrompt(state: StarPromptState): Pair<StarPromptState, Boolean> {
        if (state.shown || state.prompts >= ASK_ON_PROMPT) return state to false
        val next = state.copy(prompts = state.prompts + 1)
        val ask = next.prompts == ASK_ON_PROMPT && next.replies >= 1
        return (if (ask) next.copy(shown = true) else next) to ask
    }

    /** A turn ended. Only good replies count, and only before the second prompt: later ones decide nothing. */
    public fun afterReply(state: StarPromptState, success: Boolean): StarPromptState =
        if (state.shown || !success || state.prompts >= ASK_ON_PROMPT) state else state.copy(replies = state.replies + 1)
}

/** The ask's counters in the preference store, one writer at a time. */
public class StarPromptLedger(private val store: KeyValueStore, private val lanes: WriterLanes) {
    private val writes = Mutex()

    private fun state(): StarPromptState = StarPromptRules.parse(store.string(StarPromptRules.KEY))

    /** True when the ask should show now. It is stored as shown before this returns; a refused write asks nothing. */
    public suspend fun promptStarted(): Boolean =
        writes.withLock {
            val (next, ask) = StarPromptRules.afterPrompt(state())
            if (next == state()) return@withLock false
            val stored = store.commit(lanes.app) { putString(StarPromptRules.KEY, StarPromptRules.encode(next)) }
            ask && stored is Outcome.Ok
        }

    public suspend fun replyEnded(success: Boolean) {
        writes.withLock {
            val before = state()
            val next = StarPromptRules.afterReply(before, success)
            if (next != before) store.commit(lanes.app) { putString(StarPromptRules.KEY, StarPromptRules.encode(next)) }
        }
    }
}
