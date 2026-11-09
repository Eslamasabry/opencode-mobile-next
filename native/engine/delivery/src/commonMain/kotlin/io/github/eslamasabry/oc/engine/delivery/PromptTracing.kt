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

    /** B-190: as [sent], saying whether the server runs on this phone (`send_first_reply.server_kind`). */
    public fun sent(sessionId: String, onPhone: Boolean) {
        sent(sessionId)
    }

    /** As [sent], with what the analytics journey counts: the chat's agent mode and whether files ride along. */
    public fun sent(sessionId: String, onPhone: Boolean, agent: String?, hasAttachment: Boolean) {
        sent(sessionId, onPhone)
    }

    public fun accepted(sessionId: String, error: Boolean)

    /** One tool call of the turn, by part id (a call's later updates repeat the id). */
    public fun tool(sessionId: String, partId: String) {}

    /** The person pressed Stop for this session. */
    public fun stopped(sessionId: String) {}

    /** The live stream of a connection dropped or came back; [onPhone] says which kind of server. */
    public fun stream(onPhone: Boolean, connected: Boolean) {}

    /** The turn ended in error; [kind] is the message error's kind name and [tag] its symbolic tag (never its text). */
    public fun error(sessionId: String, kind: String?, tag: String?) {
        error(sessionId)
    }

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
