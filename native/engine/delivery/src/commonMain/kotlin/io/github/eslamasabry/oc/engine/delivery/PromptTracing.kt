package io.github.eslamasabry.oc.engine.delivery

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.model.StreamStatus

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

    /** As [sent], naming the [connection] it went through: a local id the receiver keeps in memory and never sends. */
    public fun sent(sessionId: String, onPhone: Boolean, agent: String?, hasAttachment: Boolean, connection: String) {
        sent(sessionId, onPhone, agent, hasAttachment)
    }

    public fun accepted(sessionId: String, error: Boolean)

    /** One tool call of the turn, by part id (a call's later updates repeat the id). */
    public fun tool(sessionId: String, partId: String) {}

    /** The person pressed Stop for this session. */
    public fun stopped(sessionId: String) {}

    /** The live stream of a connection dropped or came back; [onPhone] says which kind of server. */
    public fun stream(onPhone: Boolean, connected: Boolean) {}

    /**
     * As [stream], naming the [connection] (a local id, never sent). [dropReason] is a `conn_drop.reason` word when the
     * stream left Connected unasked, null when it came back or the app itself let it rest ([streamDropReason]).
     */
    public fun stream(connection: String, onPhone: Boolean, connected: Boolean, dropReason: String?) {
        stream(onPhone, connected)
    }

    /** The turn ended in error; [kind] is the message error's kind name and [tag] its symbolic tag (never its text). [reason] is the server's own words, for the receiver to classify and drop; it is never stored or sent. */
    public fun error(sessionId: String, kind: String?, tag: String?, reason: String?) {
        error(sessionId)
    }

    public fun message(sessionId: String, messageId: String, assistant: Boolean)

    public fun part(sessionId: String, messageId: String, kind: String)

    public fun delta(sessionId: String)

    /**
     * Reply text the person can see arrived: a non-empty streamed text piece ([streamed]), or a non-empty, non-synthetic
     * text part of [messageId] (the receiver checks it is the assistant's). Reasoning and step parts never call this.
     */
    public fun text(sessionId: String, messageId: String, streamed: Boolean) {}

    public fun busy(sessionId: String)

    public fun idle(sessionId: String)

    public fun error(sessionId: String)
}

/**
 * The contract's `conn_drop.reason` for a stream that is no longer Connected, or null when it is not a drop: Connected,
 * or Suspended (the app is in the background with nothing watching, so it let the stream rest on purpose).
 */
public fun streamDropReason(status: StreamStatus): String? =
    when (status) {
        StreamStatus.Connected, StreamStatus.Suspended -> null
        StreamStatus.WaitingForNetwork -> "network_change"
        is StreamStatus.Stopped -> "server_closed"
        is StreamStatus.Reconnecting, is StreamStatus.Disconnected, StreamStatus.Connecting -> "other"
    }

/**
 * [base], naming [connection] on every send and stream change it reports, so a receiver can tell two servers of the
 * same kind apart (one dropping never drops or recovers the other's turns). The id stays in memory.
 */
public fun PromptTracing.forConnection(connection: String): PromptTracing = ConnectionTracing(this, connection)

private class ConnectionTracing(private val base: PromptTracing, private val connection: String) : PromptTracing {
    override fun sent(sessionId: String) = base.sent(sessionId)

    override fun sent(sessionId: String, onPhone: Boolean) = base.sent(sessionId, onPhone)

    override fun sent(sessionId: String, onPhone: Boolean, agent: String?, hasAttachment: Boolean) =
        base.sent(sessionId, onPhone, agent, hasAttachment, connection)

    override fun sent(sessionId: String, onPhone: Boolean, agent: String?, hasAttachment: Boolean, connection: String) =
        base.sent(sessionId, onPhone, agent, hasAttachment, connection)

    override fun accepted(sessionId: String, error: Boolean) = base.accepted(sessionId, error)

    override fun tool(sessionId: String, partId: String) = base.tool(sessionId, partId)

    override fun stopped(sessionId: String) = base.stopped(sessionId)

    override fun stream(onPhone: Boolean, connected: Boolean) = base.stream(connection, onPhone, connected, null)

    override fun stream(connection: String, onPhone: Boolean, connected: Boolean, dropReason: String?) =
        base.stream(connection, onPhone, connected, dropReason)

    override fun error(sessionId: String, kind: String?, tag: String?, reason: String?) = base.error(sessionId, kind, tag, reason)

    override fun message(sessionId: String, messageId: String, assistant: Boolean) = base.message(sessionId, messageId, assistant)

    override fun part(sessionId: String, messageId: String, kind: String) = base.part(sessionId, messageId, kind)

    override fun delta(sessionId: String) = base.delta(sessionId)

    override fun text(sessionId: String, messageId: String, streamed: Boolean) = base.text(sessionId, messageId, streamed)

    override fun busy(sessionId: String) = base.busy(sessionId)

    override fun idle(sessionId: String) = base.idle(sessionId)

    override fun error(sessionId: String) = base.error(sessionId)
}

@ContributesTo(AppScope::class)
public interface PromptTracingBindings {
    @Multibinds(allowEmpty = true) public val promptTracers: Set<PromptTracing>
}
