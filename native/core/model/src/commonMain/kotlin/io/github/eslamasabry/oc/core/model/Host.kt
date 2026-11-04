package io.github.eslamasabry.oc.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

// Projects, worktrees, workspaces, organizations, terminals and managed shells
// (value_types_core.dart,
// managed_shell.dart).

public data class WorkspaceProject(
    val id: String,
    val name: String,
    val directory: String,
    val worktrees: ImmutableList<String>,
    val updatedAtMs: Long,
    val isGit: Boolean?,
)

public data class WorkspaceInfo(
    val id: String,
    val projectId: String,
    val name: String,
    val type: String,
    val branch: String?,
    val directory: String?,
    val status: String?,
)

public data class WorkspaceAdapterInfo(val type: String, val name: String, val description: String)

public data class WorktreeInfo(
    val name: String,
    val directory: String,
    val branch: String?,
    val ready: Boolean,
    val projectId: String? = null,
)

public data class ProjectDirectoryInfo(val directory: String, val strategy: String?)

/** [accountEmail] is personal data: never logged. */
public data class ConsoleOrganization(
    val accountId: String,
    val accountEmail: String,
    val accountUrl: String,
    val orgId: String,
    val orgName: String,
    val active: Boolean,
)

// ---- Terminals (PTY)

public data class TerminalProcess(
    val id: String,
    val title: String,
    val command: String,
    val arguments: ImmutableList<String>,
    val directory: String,
    val running: Boolean,
    val pid: Int,
    val exitCode: Int?,
)

public data class TerminalShellOption(val path: String, val name: String, val acceptable: Boolean)

public data class TerminalShellSettings(
    val selected: String,
    val options: ImmutableList<TerminalShellOption>,
)

/** One interactive PTY connection. Cold until [output] is collected; [close] ends it. */
public interface TerminalChannel {
    public val output: Flow<String>
    public val cursor: Long?

    public suspend fun write(value: String)

    public suspend fun close()
}

// ---- Managed shells

public enum class ManagedShellStatus {
    Running,
    Exited,
    Timeout,
    Killed,
    Unknown,
}

public data class ManagedShell(
    val id: String,
    val command: String,
    val status: ManagedShellStatus,
    val startedAtMs: Long,
    val completedAtMs: Long?,
    val exitCode: Int?,
    val sessionId: String?,
    val directory: String?,
    /** App-authored ownership tag; never shown. */
    val ownerToken: String?,
)

public data class ManagedShellList(val supported: Boolean, val shells: ImmutableList<ManagedShell>)

public data class ManagedShellOutput(
    val text: String,
    val cursor: Long,
    val size: Long,
    val truncated: Boolean,
)
