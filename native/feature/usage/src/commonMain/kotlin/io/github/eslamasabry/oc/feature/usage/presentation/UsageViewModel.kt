package io.github.eslamasabry.oc.feature.usage.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.UsageRoute
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Usage and cost one server reports (Flutter `UsageScreen` + `UsageOverview`, the Spent section): a calendar range and
 * a project scope shape one statistics read. Every change of server, range or scope starts a new read and cancels the
 * older one, so a late answer can never label the wrong totals; a failed refresh keeps the previous reading only when
 * the filters are unchanged (B-NAV-078). Reads only.
 */
@AssistedInject
public class UsageViewModel(
    @Assisted private val route: UsageRoute.Hub,
    private val source: UsageSource,
    private val zone: UsageTimeZone,
    private val clock: Clock,
    private val nav: AppNavigator,
) : ViewModel(), UsageActions {
    private val local = MutableStateFlow(UsageUi(server = route.profile))
    public val state: StateFlow<UsageUi> = local.asStateFlow()

    private var cancelRead: () -> Unit = {}
    private var cancelProjects: () -> Unit = {}
    private var generation = 0

    /** The last good reading and the filters it answers. */
    private var lastGood: Pair<Filters, UsageReport>? = null

    /** What a reading answers; the time zone sets its day boundaries, so a reading never stands in for another zone. */
    private data class Filters(val server: ProfileId, val range: UsageRange, val scope: UsageScope, val timezone: String)

    init {
        viewModelScope.launch {
            source.servers.collect { servers ->
                val before = local.value.server
                val chosen =
                    before?.takeIf { id -> servers.any { it.id == id } }
                        ?: source.active.value?.takeIf { id -> servers.any { it.id == id } }
                        ?: servers.firstOrNull()?.id
                local.update { it.copy(servers = servers, server = chosen) }
                val noServerShown = local.value.phase == UsagePhase.NoServer
                if (chosen != before || (chosen == null) != noServerShown || generation == 0) {
                    if (chosen != before) local.update { it.copy(scope = UsageScope.AllProjects, projects = persistentListOf()) }
                    loadProjects()
                    read()
                }
            }
        }
    }

    override fun back(): Unit = nav.back()

    override fun openServers(): Unit = nav.open(ServersRoute.Home)

    override fun refresh() {
        if (local.value.refreshing) return
        read()
    }

    override fun chooseRange(range: UsageRange) {
        if (local.value.range == range) return
        local.update { it.copy(range = range) }
        read()
    }

    override fun chooseScope(scope: UsageScope) {
        if (local.value.scope == scope) return
        local.update { it.copy(scope = scope) }
        read()
    }

    override fun chooseServer(id: ProfileId) {
        if (local.value.server == id || local.value.servers.none { it.id == id }) return
        local.update { it.copy(server = id, scope = UsageScope.AllProjects, projects = persistentListOf()) }
        loadProjects()
        read()
    }

    private fun loadProjects() {
        cancelProjects()
        val server = local.value.server ?: return
        viewModelScope.launch {
            val listed = source.projects(server)
            if (local.value.server != server) return@launch
            when (listed) {
                is Outcome.Ok -> local.update { it.copy(projects = listed.value, projectsFailed = false) }
                is Outcome.Failed -> local.update { it.copy(projectsFailed = true) }
            }
        }.also { job -> cancelProjects = { job.cancel() } }
    }

    private fun read() {
        cancelRead()
        val mine = ++generation
        val s = local.value
        val server = s.server
        if (server == null) {
            local.update { it.copy(phase = UsagePhase.NoServer, report = null, previous = false, refreshing = false, failure = null) }
            return
        }
        val timezone = zone.id()
        val project = (s.scope as? UsageScope.Project)?.id
        val query = UsageQuery.forRange(s.range, clock.now(), timezone, project)
        if (query == null) {
            local.update {
                it.copy(phase = UsagePhase.NoTimeZone, report = null, previous = false, refreshing = false, timezone = null)
            }
            return
        }
        val filters = Filters(server, s.range, s.scope, query.timezone)
        // A reading for other filters (another zone included) is dropped, never relabelled.
        if (lastGood?.first != filters) lastGood = null
        val kept = lastGood?.takeIf { it.first == filters }?.second
        local.update {
            it.copy(
                phase = if (kept != null) UsagePhase.Ready else UsagePhase.Loading,
                report = kept,
                previous = false,
                refreshing = true,
                timezone = query.timezone,
                failure = null,
            )
        }
        viewModelScope.launch {
            val result = source.statistics(server, query)
            if (mine != generation) return@launch
            when (result) {
                is Outcome.Ok -> {
                    val value = result.value
                    val covers =
                        value.toMs == query.toMs &&
                            (value.fromMs == query.fromMs || (s.range == UsageRange.AllTime && query.fromMs == null))
                    val answer = value.copy(coversRequest = covers)
                    lastGood = filters to answer
                    local.update {
                        it.copy(phase = UsagePhase.Ready, report = answer, previous = false, refreshing = false)
                    }
                }
                is Outcome.Failed -> {
                    val failure = result.failure
                    val phase = phaseOf(failure)
                    // Only an unchanged-filter reading may stand in, and it is labelled as the previous result.
                    val previous = lastGood?.takeIf { it.first == filters && phase != UsagePhase.Unsupported }?.second
                    local.update {
                        it.copy(
                            phase = phase,
                            report = previous,
                            previous = previous != null,
                            refreshing = false,
                            failure = failure,
                        )
                    }
                }
            }
        }.also { job -> cancelRead = { job.cancel() } }
    }

    private fun phaseOf(failure: Failure): UsagePhase =
        when (failure) {
            is Failure.Unavailable -> UsagePhase.Unsupported
            is Failure.SignIn -> UsagePhase.Authorization
            is Failure.Rejected -> if (failure.status == 401 || failure.status == 403) UsagePhase.Authorization else UsagePhase.Failed
            is Failure.UnreadableReply -> UsagePhase.Invalid
            else -> UsagePhase.Failed
        }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: UsageRoute.Hub): UsageViewModel
    }
}
