package io.github.eslamasabry.oc.engine.delivery

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

/**
 * The prompt timeline's feed (Dart `PromptTrace`, B-BUILD-027): the send path reports sent / accepted, the live stream
 * reports the signals that end a turn. Implemented by the diagnostics owner; delivery never reads what it records and
 * never lets it break a send. Ids only, never prompt text.
 */
public interface PromptTracing {
    public fun sent(sessionId: String)

    public fun accepted(sessionId: String, error: Boolean)

    public fun message(sessionId: String, messageId: String, assistant: Boolean)

    public fun part(sessionId: String, messageId: String, kind: String)

    public fun delta(sessionId: String)

    public fun busy(sessionId: String)

    public fun idle(sessionId: String)

    public fun error(sessionId: String)
}

@ContributesTo(AppScope::class)
public interface PromptTracingBindings {
    @Multibinds(allowEmpty = true) public val promptTracers: Set<PromptTracing>
}
