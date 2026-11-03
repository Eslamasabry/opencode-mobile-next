package io.github.eslamasabry.oc.engine.agents.phoneagents

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.auth.AgentAuth
import io.github.eslamasabry.oc.engine.agents.diagnostics.AgentHelperStatus
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeed
import io.github.eslamasabry.oc.engine.agents.host.AgentHost
import io.github.eslamasabry.oc.engine.agents.ownership.AgentOwnership
import io.github.eslamasabry.oc.engine.agents.readiness.AgentRow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why the phone agents of an owner close. */
public enum class PhoneAgentsCloseReason {
    /** The server profile is being deleted: close before the profile storage sweep. */
    ProfileDeletion,

    /** "Clear saved sign-ins": clients close and the app asks for a restart before agents run again. */
    SignInReset,
}

public data class PhoneAgentsSessionState(
    val available: Boolean = true,
    val needsRestart: Boolean = false,
    val rows: ImmutableList<AgentRow> = persistentListOf(),
)

/**
 * One owner's live phone-agent resources (composition of the Dart `ConnectionController` phone-agent close paths,
 * lib/state/connection/phone_agents.dart: `_paCloseOwner`, `closePhoneAgentsForSignInReset`, `phoneAgentHelperStatus`).
 * The owner, not the protocol profile, keys it: a protocol switch between profiles that alias one owner keeps the same
 * session, its sign-in answers and its host (BA5).
 *
 * [close] runs, in order: cancel a running install, stop the host, dispose the host binding; then closes auth answers
 * and every feed, and empties the rows. The profile storage sweep comes after (the deletion transaction's job:
 * `:engine:profiles` `ProfileDeletion` drains its closers before the sweep). Failures of a step never skip the next.
 */
public class PhoneAgentsSession(
    public val owner: OwnerId,
    private val host: AgentHost,
    public val auth: AgentAuth,
    /** `AgentInstaller.cancel` for this owner. */
    private val cancelInstall: suspend (OwnerId) -> Outcome<Unit>,
    /** Releases the host binding (Dart `host.dispose`). */
    private val disposeHost: suspend () -> Unit = {},
) {
    private val mutable = MutableStateFlow(PhoneAgentsSessionState())
    public val state: StateFlow<PhoneAgentsSessionState> = mutable.asStateFlow()
    private val feeds = mutableListOf<ChatFeed>()
    private var generation = 0
    private var closed = false

    public fun attachFeed(feed: ChatFeed) {
        if (closed) feed.close() else feeds += feed
    }

    public fun publishRows(rows: List<AgentRow>) {
        if (!closed) mutable.value = mutable.value.copy(rows = rows.toImmutableList())
    }

    /** A new chat needs live agents: refused once closed (Dart: `ProductException`). */
    public fun admitChat(): Outcome<Unit> =
        if (closed || !mutable.value.available) Failure.Refused(RefusalReason.ProfileClosing).failed() else Unit.ok()

    /**
     * Read-only helper evidence (BD13): [read] never starts or restarts the helper. A result that arrives after
     * [close] (or after another read began a new generation) is dropped as null.
     */
    public suspend fun helperStatus(read: suspend () -> AgentHelperStatus?): AgentHelperStatus? {
        if (closed) return null
        val mine = ++generation
        val status = (attempt({ Failure.Unexpected() }) { read() } as? Outcome.Ok)?.value
        return if (!closed && mine == generation) status else null
    }

    public suspend fun close(reason: PhoneAgentsCloseReason) {
        if (closed) return
        closed = true
        ++generation
        mutable.value = PhoneAgentsSessionState(
            available = false,
            needsRestart = reason == PhoneAgentsCloseReason.SignInReset,
            rows = persistentListOf(),
        )
        attempt({ Failure.Unexpected() }) { cancelInstall(owner) }
        attempt({ Failure.Unexpected() }) { host.stop(owner, guardedForUpdate = false) }
        attempt({ Failure.Unexpected() }) { disposeHost() }
        auth.close()
        feeds.forEach { it.close() }
        feeds.clear()
    }
}

/**
 * The live sessions by owner. [sessionFor] maps a protocol profile to its owner through [ownership] and reuses that
 * owner's session, so switching OpenCode 1 ⇄ OpenCode 2 on the same phone server creates no second host (BA5).
 */
public class PhoneAgentSessions(
    private val ownership: AgentOwnership,
    private val open: (OwnerId) -> PhoneAgentsSession,
) {
    private val live = mutableMapOf<OwnerId, PhoneAgentsSession>()

    public fun sessionFor(profile: ProfileId): PhoneAgentsSession {
        val owner = ownership.ownerOf(profile)
        return live.getOrPut(owner) { open(owner) }
    }

    public suspend fun close(profile: ProfileId, reason: PhoneAgentsCloseReason) {
        live.remove(ownership.ownerOf(profile))?.close(reason)
    }
}
