package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.ContributesTo
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.SubAgentBackfill
import io.github.eslamasabry.oc.core.domain.SubAgentsRead
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** The live connection's operations, for the sub-agent backfill. */
@ContributesTo(ConnectionScope::class)
interface SubAgentConnectionAccess {
    val subAgentOperations: ServerOperationsGateway
}

/**
 * The sub-agents of this connection's live conversations, read on attach, after every reconnect and when a
 * conversation starts a run ([SubAgentBackfill]): what the edge dock needs for children that existed before the app
 * connected (no event ever names their parent). [watched] answers the conversations the caller already shows. Empty
 * for a graph without operations; reads nothing while the server lacks `subagentSessions`.
 */
fun ConnectionGraph.subAgentReads(watched: () -> Collection<SessionId> = { emptyList() }): Flow<SubAgentsRead> {
    val operations = (this as? SubAgentConnectionAccess)?.subAgentOperations ?: return emptyFlow()
    return SubAgentBackfill.reads(session.server, operations, watched)
}
