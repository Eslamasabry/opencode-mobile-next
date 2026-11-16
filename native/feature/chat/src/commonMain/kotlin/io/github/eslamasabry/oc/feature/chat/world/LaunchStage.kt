package io.github.eslamasabry.oc.feature.chat.world

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The real current stage of getting a phone agent's (or This phone's own) conversation ready, in the order a cold start
 * goes through them. One reading per source: the chat page's waiting line names it, and the home's getting-ready row can
 * read the same flow ([ChatPhoneServer.stage]).
 *
 * A stage exists only while a real signal says it is current; a source with no signal for the moment has no stage
 * (null), and the readers fall back to their generic words. Nothing here is a timer or a guessed sequence.
 */
public enum class LaunchStage {
    /** The phone's Linux is being started (its start is wanted and its process is not running yet). */
    Linux,

    /** This phone's OpenCode is starting inside a Linux that already runs (not answering yet). */
    OpenCode,

    /** The agents' helper is being started, or has started and does not answer yet. */
    Helper,

    /** The helper answers; the agent's own source is connecting (its agents are warming up). */
    Agent,

    /** The server is up and the conversation itself is being read. */
    Conversation,
}

/** A source with no stage signal: [ChatPhoneServer.stage]'s default. */
internal val NoStage: StateFlow<LaunchStage?> = kotlinx.coroutines.flow.MutableStateFlow(null)

/** Each shown stage stays at least this long before the next shows. */
public const val STAGE_HOLD_MS: Long = 500L

/**
 * A stage shown stays for at least [holdMs] before another non-null value replaces it (the line never flickers through
 * quick stages); the newest value waits and shows when the hold ends. A null (the page is ready) always passes at once and
 * ends the hold. Values that are not a stage ([isStage] false) are not held and never hold.
 */
public fun <T : Any> Flow<T?>.holdStages(holdMs: Long = STAGE_HOLD_MS, isStage: (T) -> Boolean): Flow<T?> =
    this
