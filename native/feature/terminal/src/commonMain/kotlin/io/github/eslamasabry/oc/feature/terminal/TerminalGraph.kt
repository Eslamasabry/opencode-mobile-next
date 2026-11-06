package io.github.eslamasabry.oc.feature.terminal

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.runtime.terminal.RemoteTerminalSource
import io.github.eslamasabry.oc.engine.runtime.terminal.RemoteTerminalTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@ContributesTo(ConnectionScope::class)
public interface TerminalGraphAccessors {
    public val terminalGateway: ServerGateway
    public val terminalOperations: ServerOperationsGateway
}

/** A project is never silently replaced by the currently selected project's terminal. */
@Inject
@ContributesBinding(AppScope::class)
public class GraphRemoteTerminalSource(private val registry: ConnectionRegistry) :
    RemoteTerminalSource {
    override suspend fun activate(ref: ConversationRef): Outcome<RemoteTerminalTarget> =
        resolveAt(ref, activate = true)

    override suspend fun resolve(ref: ConversationRef): Outcome<RemoteTerminalTarget> =
        resolveAt(ref, activate = false)

    private suspend fun resolveAt(
        ref: ConversationRef,
        activate: Boolean,
    ): Outcome<RemoteTerminalTarget> {
        val graph =
            registry.graphFor(ref)
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        val services =
            graph as? TerminalGraphAccessors
                ?: return Outcome.Failed(Failure.Unavailable(FeatureId("terminal")))
        val gateway = services.terminalGateway
        if (!gateway.capabilities.value.terminal)
            return Outcome.Failed(Failure.Unavailable(FeatureId("terminal")))
        val before = gateway.location.value
        val read = gateway.session(ref.sessionId)
        if (read is Outcome.Failed) return read
        val session = (read as Outcome.Ok).value
        // A project change during the session read cannot authorize switching it back.
        if (registry.graphFor(ref) !== graph || gateway.location.value != before)
            return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        val at = before
        val expected =
            ServerLocation(
                session.directory ?: at.directory,
                session.workspaceId?.raw ?: at.workspace,
            )
        if (activate && expected != at) {
            if (!gateway.capabilities.value.terminal)
                return Outcome.Failed(Failure.Unavailable(FeatureId("terminal")))
            // Like the explicit project-terminal route, enter the conversation's project once.
            // A later refresh or command only validates; it never changes the active project.
            gateway.setLocation(expected)
            services.terminalOperations.setLocation(expected)
            val arrived =
                withTimeoutOrNull(10_000) {
                    gateway.location.first { it != at }
                }
            if (arrived == null) return Outcome.Failed(Failure.TimedOut())
            if (arrived != expected)
                return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        }
        fun current(): Boolean =
            registry.graphFor(ref) === graph &&
                gateway.location.value == expected &&
                gateway.capabilities.value.terminal
        if (!current()) return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        return Outcome.Ok(RemoteTerminalTarget(services.terminalOperations, ::current))
    }
}
