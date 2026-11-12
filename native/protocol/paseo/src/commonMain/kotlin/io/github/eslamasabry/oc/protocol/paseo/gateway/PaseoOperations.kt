package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.SubAgents
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SubAgent
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.model.TerminalProcess
import io.github.eslamasabry.oc.core.model.VcsDiffMode
import io.github.eslamasabry.oc.core.model.VersionControlFile
import io.github.eslamasabry.oc.core.model.VersionControlHealth
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import io.github.eslamasabry.oc.core.model.CatalogModel
import io.github.eslamasabry.oc.core.model.CatalogProvider
import io.github.eslamasabry.oc.core.model.CatalogSnapshot
import io.github.eslamasabry.oc.core.model.CatalogVariant
import io.github.eslamasabry.oc.core.model.ChatDefaults
import io.github.eslamasabry.oc.core.model.ProvidersResponse
import io.github.eslamasabry.oc.protocol.paseo.boolOrNull
import io.github.eslamasabry.oc.protocol.paseo.numberOrNull
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject

/**
 * [ProvidersResponse] as a catalog (Dart `_catalogModelFromProvider`): one row per runtime that can
 * start, its models with their names, context window and thinking levels.
 */
internal fun paseoCatalog(providers: ProvidersResponse): CatalogSnapshot =
    CatalogSnapshot(
        providers =
            providers.providers
                .map { CatalogProvider(it.id, it.name, enabled = true, integrationId = null) }
                .toImmutableList(),
        models =
            providers.providers
                .flatMap { provider ->
                    provider.modelIds.map { id ->
                        val raw = provider.modelData[id] ?: JsonObject(emptyMap())
                        CatalogModel(
                            id = id,
                            providerId = provider.id,
                            name = raw.str("name") ?: id,
                            family = null,
                            enabled = true,
                            status = "unknown",
                            contextLimit =
                                raw.obj("limit")?.get("context").numberOrNull()?.toInt() ?: 0,
                            outputLimit = 0,
                            reasoning = raw.obj("capabilities")?.get("reasoning").boolOrNull() == true,
                            attachments = false,
                            tools = true,
                            variants =
                                raw.obj("variants")
                                    ?.mapNotNull { (variant, options) ->
                                        options.objectOrNull()?.let {
                                            CatalogVariant(variant, disabled = false, options = it)
                                        }
                                    }
                                    .orEmpty()
                                    .toImmutableList(),
                            cost = null,
                            releasedAtMs = null,
                        )
                    }
                }
                .toImmutableList(),
        agents = persistentListOf(),
    )

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
            gateway.subagentChildrenRead(id.raw)
        }

    /** Read-only conversations (`subagentReplies` is off), each with the status the helper last pushed. */
    override suspend fun subAgents(parent: SessionId): Outcome<ImmutableList<SubAgent>> =
        when (val children = sessionChildren(parent)) {
            is Outcome.Failed -> children
            is Outcome.Ok -> Outcome.Ok(SubAgents.withStates(gateway, parent, children.value))
        }

    /**
     * E04-S05, Dart `PaseoGateway.archiveSession => deleteSession`: the daemon only archives (B-AGENT-164), so the
     * Conversations list's Archive sends the same `archive_agent_request` as the gateway's delete.
     */
    override suspend fun archiveSession(id: SessionId): Outcome<Unit> = gateway.deleteSession(id)

    override suspend fun commands():
        Outcome<ImmutableList<io.github.eslamasabry.oc.core.model.CommandInfo>> = gateway.laneCall {
        gateway.readCommands()
    }

    // ---- CatalogGateway: the model sheet (PZ4)

    /**
     * The runtimes and models the daemon's provider snapshot offers, as Flutter's controller builds
     * its fallback catalog from `providers` (lib/state/connection/catalog.dart `fallbackCatalog`).
     * Without it the chat's "Choose a model" read nothing on a Paseo computer and stayed empty.
     */
    override suspend fun catalog(): Outcome<CatalogSnapshot> =
        when (val read = gateway.providers()) {
            is Outcome.Failed -> read
            is Outcome.Ok -> Outcome.Ok(paseoCatalog(read.value))
        }

    /** The model a new conversation starts on when nobody picks one (the snapshot's default). */
    override suspend fun chatDefaults(): Outcome<ChatDefaults> = gateway.chatDefaults()

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
