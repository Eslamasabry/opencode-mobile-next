package io.github.eslamasabry.oc.engine.delivery.receipts

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import kotlin.time.Clock

/**
 * The server side of an idempotent session create (Dart `CommandReceiptTransport.createSession` /
 * `lookupSession`). Only a backend that accepts a client-chosen session id can implement it; every
 * other backend has no transport, and then there is no receipt owner at all (B-AGENT-226).
 */
public interface SessionCreationTransport {
    /**
     * The connection's own server root, compared with the profile's stored address before anything
     * is written.
     */
    public val endpoint: String

    /** The location the connection is pinned to: part of the receipt scope. */
    public val directory: String?
    public val workspace: String?

    /** True once the connection was closed: a closed connection neither creates nor confirms. */
    public val closed: Boolean

    /** Creates the session with [id]; answers the id the server admitted. */
    public suspend fun createSession(id: SessionId): Outcome<SessionId>

    /** Reads only: true when the server holds [id]. Not found is `Ok(false)` (stays uncertain). */
    public suspend fun lookupSession(id: SessionId): Outcome<Boolean>
}

/** The profile's stored address (Dart `profile.baseUrl`); null when the profile is unknown. */
public fun interface ProfileEndpoints {
    public fun baseUrl(profile: ProfileId): String?
}

/**
 * Port of `lib/state/session_creation_receipts.dart` (row B-AGENT-226): creating a session is
 * idempotent. The command is claimed in the profile's [PendingCommandJournal] (key
 * `create:<commandId>`) before any request, a lost response becomes `uncertain`, and [check] only
 * looks the session up — it never creates a replacement. The receipt is bound to the profile, its
 * stored address and the connection's location at [open] time; any change refuses.
 */
public class SessionCreationReceipts
private constructor(
    private val profile: ProfileId,
    private val endpoints: ProfileEndpoints,
    private val transport: SessionCreationTransport,
    private val journals: PendingCommandJournals,
    private val clock: Clock,
    private val newSessionId: () -> String,
    private val scope: String,
) {
    private fun currentScope(): String? = scopeOf(profile, endpoints, transport)

    private val bindingMatches: Boolean
        get() = !transport.closed && currentScope() == scope

    /**
     * Sends the create for [commandId] at most once. A repeat with the same command answers the
     * stored receipt (and never creates again); another tab cannot borrow the command.
     */
    public suspend fun send(commandId: String, tabId: String): Outcome<CommandReceipt> {
        if (!bindingMatches) return refused()
        val journal =
            when (val j = journals.forProfile(profile)) {
                is Outcome.Ok -> j.value
                is Outcome.Failed -> return j
            }
        val key = keyOf(commandId)
        val previous =
            when (val r = journal.read()) {
                is Outcome.Ok -> r.value.firstOrNull { it.commandId == key }
                is Outcome.Failed -> return r
            }
        val id = previous?.receiptId ?: newSessionId()
        val receipt =
            CommandReceipt(
                key,
                id,
                id,
                tabId,
                scope,
                clock.now().toEpochMilliseconds(),
                CommandReceiptState.Sent,
            )
        return CommandReceiptController(journal).send(receipt) { wireId ->
            if (!bindingMatches || journal.read() is Outcome.Failed)
                return@send DispatchProof.Uncertain
            when (val created = transport.createSession(SessionId(wireId))) {
                is Outcome.Ok ->
                    if (created.value.raw == wireId) DispatchProof.Confirmed
                    else DispatchProof.Uncertain
                is Outcome.Failed -> DispatchProof.Uncertain
            }
        }
    }

    /**
     * The confirmed session for the original tab to open, or null. Never creates; unknown, removed
     * or rebound (endpoint or location changed) receipts answer null without any request.
     */
    public suspend fun check(commandId: String, tabId: String? = null): SessionId? {
        if (!bindingMatches) return null
        val journal = (journals.forProfile(profile) as? Outcome.Ok)?.value ?: return null
        val key = keyOf(commandId)
        val receipt =
            (journal.read() as? Outcome.Ok)?.value?.firstOrNull { it.commandId == key }
                ?: return null
        if (receipt.scope != scope || (tabId != null && receipt.tabId != tabId)) return null
        val checked =
            CommandReceiptController(journal).retry(key) { r ->
                val found = transport.lookupSession(SessionId(r.sessionId))
                bindingMatches && found is Outcome.Ok && found.value
            }
        val value = (checked as? Outcome.Ok)?.value ?: return null
        if (!bindingMatches || value.state != CommandReceiptState.Confirmed) return null
        return SessionId(value.sessionId)
    }

    public companion object {
        private fun keyOf(commandId: String) = "create:$commandId"

        private fun refused(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()

        /**
         * Dart `Api2Transport.normalizeServerRoot`, for the comparison only: no trailing slash,
         * lower-case scheme/host.
         */
        public fun normalizeRoot(url: String): String {
            val trimmed = url.trim().trimEnd('/')
            val schemeEnd = trimmed.indexOf("://")
            if (schemeEnd < 0) return trimmed
            val hostEnd =
                trimmed.indexOf('/', schemeEnd + 3).let { if (it < 0) trimmed.length else it }
            return trimmed.substring(0, hostEnd).lowercase() + trimmed.substring(hostEnd)
        }

        private fun scopeOf(
            profile: ProfileId,
            endpoints: ProfileEndpoints,
            transport: SessionCreationTransport,
        ): String? {
            val baseUrl = endpoints.baseUrl(profile) ?: return null
            if (normalizeRoot(baseUrl) != normalizeRoot(transport.endpoint)) return null
            return DispatchScope(profile, baseUrl, transport.directory, transport.workspace)
                .receiptScope
        }

        /**
         * Builds the owner, or refuses before anything is written: no [transport] (the backend
         * cannot take a client session id), an unknown profile, or a profile address that is not
         * the connection's.
         */
        public fun open(
            profile: ProfileId,
            endpoints: ProfileEndpoints,
            transport: SessionCreationTransport?,
            journals: PendingCommandJournals,
            clock: Clock,
            newSessionId: () -> String,
        ): Outcome<SessionCreationReceipts> {
            if (transport == null) return Failure.Refused(RefusalReason.DispatchUncertain).failed()
            val scope = scopeOf(profile, endpoints, transport) ?: return refused()
            return SessionCreationReceipts(
                    profile,
                    endpoints,
                    transport,
                    journals,
                    clock,
                    newSessionId,
                    scope,
                )
                .ok()
        }
    }
}
