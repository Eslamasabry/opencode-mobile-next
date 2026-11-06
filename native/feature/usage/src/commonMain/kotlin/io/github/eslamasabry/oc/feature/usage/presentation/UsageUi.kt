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
    /**
     * Shown while [phase] is Ready, or kept from the last reading of the same filters after a
     * failed refresh.
     */
    val report: UsageReport? = null,
    /** True when [report] is the previous reading for these filters because the refresh failed. */
    val previous: Boolean = false,
    val refreshing: Boolean = false,
    val timezone: String? = null,
    val failure: Failure? = null,
    val budgetsAvailable: Boolean = false,
    val hasSavedBudgets: Boolean = false,
    val usdBudget: Double? = null,
    val tokenBudget: Double? = null,
    val budgetUnit: io.github.eslamasabry.oc.engine.integrations.usage.UsageBudgetUnit? = null,
    val budgetInput: String = "",
    val budgetSaving: Boolean = false,
    val budgetFailed: Boolean = false,
    val budgetInvalid: Boolean = false,
    /** Inspection filter over the returned model records (Flutter `UsageOverview.modelSearch`). */
    val modelSearch: String = "",
    val providerFilter: String? = null,
) {
    val hasInspectionFilters: Boolean
        get() = modelSearch.isNotBlank() || providerFilter != null

    /** The model records that match the search words and the provider filter. */
    val matchingModels: List<UsageModel>
        get() {
            val all = report?.models ?: return emptyList()
            val words = modelSearch.trim().lowercase().split(' ').filter { it.isNotEmpty() }
            return all.filter { m ->
                (providerFilter == null || m.providerId == providerFilter) &&
                    words.all { w ->
                        m.providerId.lowercase().contains(w) ||
                            m.modelId.lowercase().contains(w) ||
                            (m.variant?.lowercase()?.contains(w) == true)
                    }
            }
        }

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

    public fun searchModels(query: String)

    public fun filterProvider(providerId: String?)

    public fun clearInspectionFilters()

    public fun editBudget(unit: io.github.eslamasabry.oc.engine.integrations.usage.UsageBudgetUnit)

    public fun budgetChanged(value: String)

    public fun saveBudget()

    public fun removeBudget()

    public fun closeBudget()

    public fun clearBudgets()

    public fun openServers()
}
