package io.github.eslamasabry.oc.feature.usage.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public enum class UsagePhase {
    Loading,
    Ready,

    /** The server has no usage statistics (explained in place, not an error). */
    Unsupported,

    /** The device time zone cannot be read: usage is never silently dated in UTC. */
    NoTimeZone,

    /** The server refused the password or permissions. */
    Authorization,

    /** The server answered with usage this app cannot trust. */
    Invalid,
    Failed,
    NoServer,
}

/** The project filter: every project, or one by the server's id. */
public sealed interface UsageScope {
    public data object AllProjects : UsageScope

    public data class Project(val id: String) : UsageScope
}

public data class UsageUi(
    val servers: ImmutableList<UsageServer> = persistentListOf(),
    val server: ProfileId? = null,
    val range: UsageRange = UsageRange.ThirtyDays,
    val scope: UsageScope = UsageScope.AllProjects,
    val projects: ImmutableList<UsageProject> = persistentListOf(),
    val projectsFailed: Boolean = false,
    val phase: UsagePhase = UsagePhase.Loading,
    /** Shown while [phase] is Ready, or kept from the last reading of the same filters after a failed refresh. */
    val report: UsageReport? = null,
    /** True when [report] is the previous reading for these filters because the refresh failed. */
    val previous: Boolean = false,
    val refreshing: Boolean = false,
    val timezone: String? = null,
    val failure: Failure? = null,
) {
    val serverName: String?
        get() = servers.firstOrNull { it.id == server }?.name

    val providers: List<UsageProvider>
        get() = report?.let { UsageFigures.providers(it.models) } ?: emptyList()
}

public interface UsageActions {
    public fun back()

    public fun refresh()

    public fun chooseRange(range: UsageRange)

    public fun chooseScope(scope: UsageScope)

    public fun chooseServer(id: ProfileId)

    public fun openServers()
}
