package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.core.model.VcsDiffMode
import io.github.eslamasabry.oc.core.model.VersionControlFile
import io.github.eslamasabry.oc.core.model.VersionControlHealth
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import kotlinx.collections.immutable.ImmutableList

/**
 * The Paseo half of the gateway pair's operations (Dart `PaseoGateway` as a ProductRepository
 * source): Changes ([io.github.eslamasabry.oc.core.domain.VcsGateway]), Worktrees (HostGateway's
 * worktree calls) and Terminal ([io.github.eslamasabry.oc.core.domain.TerminalGateway]), all on
 * [gateway]'s folder and lane. Everything else keeps the interface defaults (Failure.Unavailable
 * naming its capability). Files live on the live gateway (FileGateway).
 */
public class PaseoOperations(private val gateway: PaseoGateway) : ServerOperationsGateway {
    // ---- SessionOperationsGateway: a conversation's sub-agents (B-AGENT-165)

    override suspend fun sessionChildren(id: SessionId): Outcome<ImmutableList<Session>> =
        gateway.laneCall {
            gateway.subagentChildren(id.raw)
        }

    override suspend fun commands():
        Outcome<ImmutableList<io.github.eslamasabry.oc.core.model.CommandInfo>> = gateway.laneCall {
        gateway.readCommands()
    }

    // ---- VcsGateway

    override suspend fun versionControlHealth(): Outcome<VersionControlHealth> = gateway.laneCall {
        gateway.readVersionControlHealth()
    }

    override suspend fun listFileStatuses(): Outcome<ImmutableList<VersionControlFile>> =
        gateway.laneCall {
            gateway.readFileStatuses()
        }

    override suspend fun listVcsDiffs(mode: VcsDiffMode): Outcome<ImmutableList<FileDiff>> =
        gateway.laneCall {
            gateway.readVcsDiffs(mode)
        }

    // ---- HostGateway: worktrees (no reset: the daemon has no call for it)

    override suspend fun listWorktrees(
        projectDirectory: String,
        projectId: String?,
    ): Outcome<ImmutableList<WorktreeInfo>> = gateway.laneCall {
        gateway.readWorktrees(projectDirectory)
    }

    override suspend fun createWorktree(
        projectDirectory: String,
        name: String?,
    ): Outcome<WorktreeInfo> = gateway.laneCall { gateway.makeWorktree(projectDirectory, name) }

    override suspend fun listWorktreeFileStatuses(
        directory: String
    ): Outcome<ImmutableList<VersionControlFile>> = gateway.laneCall {
        gateway.readFileStatuses(directory)
    }

    override suspend fun removeWorktree(
        projectDirectory: String,
        directory: String,
    ): Outcome<Unit> = gateway.laneCall { gateway.archiveWorktree(projectDirectory, directory) }

    // ---- TerminalGateway

    override suspend fun listTerminals(): Outcome<ImmutableList<TerminalProcess>> =
        gateway.laneCall {
            gateway.readTerminals()
        }

    override suspend fun createTerminal(title: String?): Outcome<TerminalProcess> =
        gateway.laneCall {
            gateway.openTerminal(title)
        }

    override suspend fun renameTerminal(id: String, title: String): Outcome<Unit> =
        gateway.laneCall {
            gateway.retitleTerminal(id, title)
        }

    override suspend fun resizeTerminal(id: String, rows: Int, cols: Int): Outcome<Unit> =
        gateway.laneCall {
            gateway.sizeTerminal(id, rows, cols)
        }

    override suspend fun removeTerminal(id: String): Outcome<Unit> = gateway.laneCall {
        gateway.killTerminal(id)
    }

    override suspend fun connectTerminal(id: String, cursor: Long?): Outcome<TerminalChannel> =
        gateway.laneCall {
            gateway.subscribeTerminal(id)
        }
}
