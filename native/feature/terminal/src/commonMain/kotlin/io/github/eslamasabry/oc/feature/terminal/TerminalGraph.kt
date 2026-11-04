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
    override suspend fun resolve(ref: ConversationRef): Outcome<RemoteTerminalTarget> {
        val graph =
            registry.graphFor(ref)
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        val services =
            graph as? TerminalGraphAccessors
                ?: return Outcome.Failed(Failure.Unavailable(FeatureId("terminal")))
        val gateway = services.terminalGateway
        if (!gateway.capabilities.value.terminal)
            return Outcome.Failed(Failure.Unavailable(FeatureId("terminal")))
        val read = gateway.session(ref.sessionId)
        if (read is Outcome.Failed) return read
        val session = (read as Outcome.Ok).value
        val at = gateway.location.value
        val expected =
            ServerLocation(
                session.directory ?: at.directory,
                session.workspaceId?.raw ?: at.workspace,
            )
        fun current(): Boolean =
            registry.graphFor(ref) === graph &&
                gateway.location.value == expected &&
                gateway.capabilities.value.terminal
        if (!current()) return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        return Outcome.Ok(RemoteTerminalTarget(services.terminalOperations, ::current))
    }
}
