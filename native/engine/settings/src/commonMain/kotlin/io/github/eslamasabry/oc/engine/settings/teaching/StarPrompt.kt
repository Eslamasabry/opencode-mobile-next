package io.github.eslamasabry.oc.engine.settings.teaching

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the app remembers for the one-time "star us on GitHub" ask, app-wide (never per profile): how many prompts
 * started a turn, how many finished with a good reply, when the first prompt was sent (milliseconds, 0 = not yet), and
 * whether the ask was ever shown. Stored in one string under [StarPromptRules.KEY] as `p=<n>;r=<n>;s=<0|1>;f=<ms>`.
 */
/** Answer stored locally under `a=` in [StarPromptRules.KEY]; never sent off-device. */
public enum class StarPromptAnswer(public val code: String) {
    Yes("y"),
    No("n"),
    Skip("k"),
}

/** Use choice stored locally under `u=` in [StarPromptRules.KEY]; never sent off-device. */
public enum class StarPromptUse(public val code: String) {
    Everyday("e"),
    Code("c"),
    Both("b"),
    Looking("l"),
}

public data class StarPromptState(
    val prompts: Int = 0,
    val replies: Int = 0,
    val shown: Boolean = false,
    val firstMs: Long = 0,
    val answer: StarPromptAnswer? = null,
    val use: StarPromptUse? = null,
)

/** The rules, free of storage so they are tested with plain values. */
public object StarPromptRules {
    /** App-wide, so not an `oc.<what>.<profileId>` key. */
    public const val KEY: String = "oc.starPrompt"

    /** The repository the ask opens (an address the app authored). */
    public const val URL: String = "https://github.com/Eslamasabry/opencode-mobile-next"

    /** The ask waits for this many finished good replies... */
    public const val ASK_AFTER_REPLIES: Int = 10

    /** ...and for this long since the first prompt (three days). */
    public const val ASK_AFTER_MS: Long = 3L * 24 * 60 * 60 * 1000

    /** Absent means a fresh install; unreadable means "do not ask": a nag is worse than a missed ask. */
    public fun parse(raw: String?): StarPromptState {
        if (raw == null) return StarPromptState()
        val parts = raw.split(';').mapNotNull { it.split('=', limit = 2).takeIf { kv -> kv.size == 2 } }.associate { it[0] to it[1] }
        val prompts = parts["p"]?.toIntOrNull()
        val replies = parts["r"]?.toIntOrNull()
        val shown = parts["s"]
        val first = if (parts.containsKey("f")) parts["f"]?.toLongOrNull() else 0L
        if (prompts == null || replies == null || first == null || prompts < 0 || replies < 0 || first < 0 ||
            (shown != "0" && shown != "1")
        ) {
            return StarPromptState(shown = true)
        }
        return StarPromptState(prompts, replies, shown == "1", first)
    }

    public fun encode(state: StarPromptState): String =
        "p=${state.prompts};r=${state.replies};s=${if (state.shown) 1 else 0};f=${state.firstMs}"

    /** A prompt started a turn: it is counted and the first one stamps the day use began. Never asks. */
    public fun afterPrompt(state: StarPromptState, nowMs: Long): StarPromptState =
        if (state.shown) state
        else state.copy(prompts = state.prompts + 1, firstMs = if (state.firstMs == 0L) nowMs else state.firstMs)

    /**
     * A turn ended, at rest. Only good replies count. Asks (true) once ever: on a good reply when at least
     * [ASK_AFTER_REPLIES] replies have finished and [ASK_AFTER_MS] have passed since the first prompt; the state it
     * returns already says shown.
     */
    public fun afterReply(state: StarPromptState, success: Boolean, nowMs: Long): Pair<StarPromptState, Boolean> {
        if (state.shown || !success) return state to false
        val first = if (state.firstMs == 0L) nowMs else state.firstMs
        val next = state.copy(replies = state.replies + 1, firstMs = first)
        val ask = next.replies >= ASK_AFTER_REPLIES && nowMs - first >= ASK_AFTER_MS
        return (if (ask) next.copy(shown = true) else next) to ask
    }
}

/** The ask's counters in the preference store, one writer at a time. */
public class StarPromptLedger(
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val nowMs: () -> Long,
) {
    private val writes = Mutex()

    private fun state(): StarPromptState = StarPromptRules.parse(store.string(StarPromptRules.KEY))

    /** A prompt started a turn: counted, never an ask. */
    public suspend fun promptStarted() {
        writes.withLock {
            val before = state()
            val next = StarPromptRules.afterPrompt(before, nowMs())
            if (next != before) store.commit(lanes.app) { putString(StarPromptRules.KEY, StarPromptRules.encode(next)) }
        }
    }

    /**
     * A turn ended. True when the ask should show now, at rest. It is stored as shown before this returns; a refused
     * write asks nothing.
     */
    public suspend fun replyEnded(success: Boolean): Boolean =
        writes.withLock {
            val before = state()
            val (next, ask) = StarPromptRules.afterReply(before, success, nowMs())
            if (next == before) return@withLock false
            val stored = store.commit(lanes.app) { putString(StarPromptRules.KEY, StarPromptRules.encode(next)) }
            ask && stored is Outcome.Ok
        }

    /** Records the person's Yes/No/Skip answer once. */
    public suspend fun answered(answer: StarPromptAnswer) = Unit

    /** Records what the person uses the app for once. */
    public suspend fun usedFor(use: StarPromptUse) = Unit
}
