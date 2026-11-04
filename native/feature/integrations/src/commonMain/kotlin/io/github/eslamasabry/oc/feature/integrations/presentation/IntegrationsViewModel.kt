package io.github.eslamasabry.oc.feature.integrations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.integrations.catalog.SkillConversationStatus
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class IntegrationsViewModel(
    private val source: IntegrationsSource,
    private val nav: AppNavigator,
) : ViewModel(), IntegrationsActions {
    private val local = MutableStateFlow(IntegrationsUi())
    public val state: StateFlow<IntegrationsUi> = local.asStateFlow()
    private var session: IntegrationSession? = null
    private var generation = 0
    private var connectorJob: Job? = null
    private var pendingLink: String? = null

    init {
        viewModelScope.launch {
            source.servers.collect { servers ->
                local.update { it.copy(servers = servers) }
                if (servers.none { it.id == local.value.server }) {
                    val id =
                        source.active.value?.takeIf { active -> servers.any { it.id == active } }
                            ?: servers.firstOrNull()?.id
                    select(id)
                }
            }
        }
    }

    override fun back() = nav.back()

    override fun chooseServer(id: ProfileId) {
        if (local.value.servers.any { it.id == id } && id != local.value.server) select(id)
    }

    private fun select(id: ProfileId?) {
        session?.close()
        connectorJob?.cancel()
        pendingLink = null
        ++generation
        session = id?.let(source::open)
        local.update { IntegrationsUi(servers = it.servers, server = id) }
        session?.let { pinned ->
            connectorJob = viewModelScope.launch {
                pinned.connector.collect { value -> local.update { it.copy(connector = value) } }
            }
        }
        refresh()
    }

    override fun refresh() {
        val pinned = session ?: return
        if (local.value.busy) return
        val mine = generation
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = pinned.read()
            if (mine != generation) return@launch
            local.update {
                when (result) {
                    is Outcome.Ok -> it.copy(data = result.value, busy = false)
                    is Outcome.Failed -> it.copy(busy = false, notice = notice(result.failure))
                }
            }
        }
    }

    override fun chooseProvider(id: String) {
        if (local.value.busy || local.value.data.providers.none { it.id == id }) return
        local.update { it.copy(selected = id, authorization = null, notice = null) }
    }

    private fun command(work: suspend (IntegrationSession) -> Outcome<Unit>) {
        val pinned = session ?: return
        if (local.value.busy) return
        val mine = generation
        local.update { it.copy(busy = true, notice = null) }
        viewModelScope.launch {
            val result = work(pinned)
            if (mine != generation) return@launch
            local.update {
                it.copy(
                    busy = false,
                    notice =
                        if (result is Outcome.Ok) IntegrationsNotice.Saved
                        else notice((result as Outcome.Failed).failure),
                )
            }
            if (result is Outcome.Ok) refresh()
        }
    }

    override fun saveKey(key: String) {
        val id = local.value.selected ?: return
        command { it.saveKey(id, key) }
    }

    override fun startOAuth(method: String) {
        val pinned = session ?: return
        val provider = local.value.selected ?: return
        if (local.value.busy) return
        val mine = generation
        local.update { it.copy(busy = true, notice = null, authorization = null) }
        viewModelScope.launch {
            val result = pinned.startOAuth(provider, method)
            if (mine != generation) return@launch
            local.update {
                when (result) {
                    is Outcome.Ok -> it.copy(busy = false, authorization = result.value)
                    is Outcome.Failed -> it.copy(busy = false, notice = notice(result.failure))
                }
            }
        }
    }

    override fun completeOAuth(code: String) {
        val authorization = local.value.authorization ?: return
        command { pinned ->
            pinned.completeOAuth(authorization.attempt, code).also {
                if (it is Outcome.Ok) local.update { s -> s.copy(authorization = null) }
            }
        }
    }

    private fun requestLink(url: String) {
        when (val result = source.openLink(url)) {
            is Outcome.Failed -> local.update { it.copy(notice = notice(result.failure)) }
            is Outcome.Ok -> {
                pendingLink = url.takeIf { result.value != null }
                local.update { it.copy(linkPrompt = result.value) }
            }
        }
    }

    override fun openAuthorization() {
        local.value.authorization?.url?.let(::requestLink)
    }

    override fun openKeyPage() {
        local.value.selected?.let(source::keyPage)?.let(::requestLink)
    }

    override fun confirmLink() {
        val url = pendingLink ?: return
        pendingLink = null
        val result = source.openLink(url, confirmed = true)
        local.update {
            it.copy(
                linkPrompt = null,
                notice = if (result is Outcome.Failed) notice(result.failure) else it.notice,
            )
        }
    }

    override fun dismissLink() {
        pendingLink = null
        local.update { it.copy(linkPrompt = null) }
    }

    override fun saveConnector(name: String, url: String, headerName: String, headerValue: String) =
        command {
            it.saveConnector(name, url, headerName, headerValue)
        }

    override fun writeSkill(requirements: String) {
        val pinned = session ?: return
        if (local.value.busy || local.value.draft != null) return
        val mine = generation
        local.update { it.copy(busy = true, notice = null) }
        viewModelScope.launch {
            val result = pinned.writeSkill(requirements)
            if (mine != generation) return@launch
            local.update {
                it.copy(
                    busy = false,
                    draft = result.ref,
                    notice =
                        when (result.result.status) {
                            SkillConversationStatus.Ready -> IntegrationsNotice.DraftSent
                            SkillConversationStatus.Unsupported -> IntegrationsNotice.Unsupported
                            SkillConversationStatus.InvalidInput -> IntegrationsNotice.Invalid
                            SkillConversationStatus.Changed -> IntegrationsNotice.Changed
                            else -> IntegrationsNotice.DraftUncertain
                        },
                )
            }
        }
    }

    override fun openDraft() {
        local.value.draft?.let { nav.open(ChatRoute(it)) }
    }

    private fun notice(failure: Failure): IntegrationsNotice =
        when (failure) {
            is Failure.Unavailable -> IntegrationsNotice.Unsupported
            is Failure.Refused -> IntegrationsNotice.Changed
            else -> IntegrationsNotice.Failed
        }

    override fun onCleared() {
        session?.close()
        super.onCleared()
    }
}
