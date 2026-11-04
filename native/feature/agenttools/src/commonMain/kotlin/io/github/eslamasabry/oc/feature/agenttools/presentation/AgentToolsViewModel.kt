package io.github.eslamasabry.oc.feature.agenttools.presentation

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
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.AgentFeaturesGateway
import io.github.eslamasabry.oc.core.domain.HostDaemonUpdateGateway
import io.github.eslamasabry.oc.core.domain.ProviderConversationImportGateway
import io.github.eslamasabry.oc.core.domain.ReadOnlySessionsGateway
import io.github.eslamasabry.oc.core.domain.SessionSelectionGateway
import io.github.eslamasabry.oc.core.model.AgentFeature
import io.github.eslamasabry.oc.core.model.HostUpdatePhase
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.ui.routes.AgentToolsRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/**
 * Options are session-owned, imports use listed handles, and an update needs an explicit
 * confirmation.
 */
@AssistedInject
public class AgentToolsViewModel(
    @Assisted private val route: AgentToolsRoute,
    private val host: AgentToolsHost,
    private val nav: AppNavigator,
) : ViewModel(), AgentToolsActions {
    private val mutable = MutableStateFlow(AgentToolsUi())
    public val state: StateFlow<AgentToolsUi> = mutable.asStateFlow()
    private var owner: AgentToolsSession? = null
    private var generation = 0
    private var provider: String? = null

    init {
        refresh()
    }

    override fun back() = nav.back()

    private fun current(run: Int, source: AgentToolsSession): Boolean =
        run == generation && host.isCurrent(route.profile, source.identity)

    private fun stale() {
        mutable.update {
            it.copy(
                connected = false,
                busy = false,
                loading = false,
                failure = Failure.Refused(RefusalReason.StaleScope),
                canOptions = false,
                canImport = false,
                canUpdate = false,
            )
        }
    }

    private fun usable(): AgentToolsSession? {
        val source = owner ?: return null
        if (!host.isCurrent(route.profile, source.identity)) {
            stale()
            return null
        }
        return source
    }

    private fun uncertain(failure: Failure): Boolean =
        failure is Failure.Refused && failure.reason == RefusalReason.DispatchUncertain

    private fun failed(failure: Failure) {
        mutable.update {
            it.copy(
                busy = false,
                loading = false,
                failure = failure,
                uncertain = it.uncertain || uncertain(failure),
                confirmUpdate = false,
            )
        }
    }

    private fun safe(value: String): String = host.display(value)

    private fun safeFeature(feature: AgentFeature): AgentFeature =
        feature.copy(
            label = safe(feature.label),
            description = feature.description?.let(::safe),
            options = feature.options.map { it.copy(label = safe(it.label)) }.toImmutableList(),
        )

    override fun refresh() {
        if (state.value.busy || state.value.loading) return
        val source = host.open(route.profile)
        if (source == null) {
            owner = null
            mutable.value = AgentToolsUi()
            return
        }
        owner = source
        val run = ++generation
        val gateway = source.gateway
        val sid = route.session
        val readOnly =
            sid != null && (gateway as? ReadOnlySessionsGateway)?.isReadOnlySession(sid) == true
        val options =
            sid != null &&
                !readOnly &&
                gateway.capabilities.value.agentFeatures &&
                gateway is AgentFeaturesGateway
        val hostTools = gateway.capabilities.value.hostAgentProviders
        val oldUncertain = state.value.uncertain
        mutable.value =
            AgentToolsUi(
                connected = true,
                name = source.name,
                directory = safe(gateway.location.value.directory.orEmpty()),
                loading = options,
                canOptions = options,
                canImport = hostTools && gateway is ProviderConversationImportGateway,
                canUpdate = hostTools && gateway is HostDaemonUpdateGateway,
                uncertain = oldUncertain,
            )
        if (!options || sid == null) return
        viewModelScope.launch {
            val providers = gateway.providers()
            if (!current(run, source)) {
                stale()
                return@launch
            }
            val session = gateway.session(sid)
            if (!current(run, source)) {
                stale()
                return@launch
            }
            if (providers is Outcome.Failed) {
                failed(providers.failure)
                return@launch
            }
            if (session is Outcome.Failed) {
                failed(session.failure)
                return@launch
            }
            val known = (session as Outcome.Ok).value.selection?.model
            val catalog = (providers as Outcome.Ok).value
            val featuresGateway = gateway as AgentFeaturesGateway
            provider =
                featuresGateway.providerIdForSession(sid)
                    ?: known?.providerId
                    ?: catalog.defaultProviderId
            val models = catalog.providers.firstOrNull { it.id == provider }
            val choices =
                models
                    ?.modelIds
                    .orEmpty()
                    .map { id ->
                        val rawName = models?.modelData?.get(id)?.get("name") as? JsonPrimitive
                        AgentModelChoice(id, safe(rawName?.content ?: id), id == known?.modelId)
                    }
                    .toImmutableList()
            val features = featuresGateway.agentFeatures(sid)
            if (!current(run, source)) {
                stale()
                return@launch
            }
            when (features) {
                is Outcome.Failed -> failed(features.failure)
                is Outcome.Ok ->
                    mutable.update {
                        it.copy(
                            loading = false,
                            models = choices,
                            features = features.value.map(::safeFeature).toImmutableList(),
                            failure = null,
                        )
                    }
            }
        }
    }

    private fun change(id: String, value: JsonPrimitive) {
        val source = usable() ?: return
        val sid = route.session ?: return
        val gateway = source.gateway as? AgentFeaturesGateway ?: return
        if (
            !state.value.canOptions ||
                state.value.busy ||
                state.value.loading ||
                state.value.uncertain
        )
            return
        val run = ++generation
        mutable.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            if (!current(run, source)) {
                stale()
                return@launch
            }
            when (val changed = gateway.setAgentFeature(sid, id, value)) {
                is Outcome.Failed -> if (current(run, source)) failed(changed.failure) else stale()
                is Outcome.Ok ->
                    if (current(run, source))
                        mutable.update {
                            it.copy(
                                busy = false,
                                features = changed.value.map(::safeFeature).toImmutableList(),
                            )
                        }
                    else stale()
            }
        }
    }

    override fun setToggle(id: String, on: Boolean) = change(id, JsonPrimitive(on))

    override fun setChoice(id: String, value: String) = change(id, JsonPrimitive(value))

    override fun setModel(id: String) {
        val source = usable() ?: return
        val sid = route.session ?: return
        val providerId = provider ?: return
        val gateway = source.gateway as? SessionSelectionGateway ?: return
        if (
            !state.value.canOptions ||
                state.value.busy ||
                state.value.loading ||
                state.value.uncertain ||
                state.value.models.none { it.id == id }
        )
            return
        val run = ++generation
        mutable.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            if (!current(run, source)) {
                stale()
                return@launch
            }
            when (val changed = gateway.setSessionModel(sid, ModelRef(providerId, id), "")) {
                is Outcome.Failed -> if (current(run, source)) failed(changed.failure) else stale()
                is Outcome.Ok ->
                    if (current(run, source)) {
                        mutable.update { it.copy(busy = false) }
                        refresh()
                    } else stale()
            }
        }
    }

    override fun readImports() {
        val source = usable() ?: return
        val gateway = source.gateway as? ProviderConversationImportGateway ?: return
        if (!state.value.canImport || state.value.busy || state.value.loading) return
        val run = ++generation
        mutable.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            when (val listed = gateway.importableConversations()) {
                is Outcome.Failed -> if (current(run, source)) failed(listed.failure) else stale()
                is Outcome.Ok ->
                    if (current(run, source))
                        mutable.update {
                            it.copy(
                                busy = false,
                                importsLoaded = true,
                                imports =
                                    listed.value.items
                                        .map { c ->
                                            c.copy(
                                                title = c.title?.let(::safe),
                                                firstPrompt = c.firstPrompt?.let(::safe),
                                            )
                                        }
                                        .toImmutableList(),
                                alreadyImported = listed.value.alreadyImported,
                            )
                        }
                    else stale()
            }
        }
    }

    override fun importConversation(handle: String, agentId: String) {
        val source = usable() ?: return
        val gateway = source.gateway as? ProviderConversationImportGateway ?: return
        val selected =
            state.value.imports.firstOrNull { it.handle == handle && it.agentId == agentId }
                ?: return
        if (state.value.busy || state.value.loading || state.value.uncertain) return
        val run = ++generation
        mutable.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            if (!current(run, source)) {
                stale()
                return@launch
            }
            when (val imported = gateway.importConversation(selected)) {
                is Outcome.Failed -> if (current(run, source)) failed(imported.failure) else stale()
                is Outcome.Ok ->
                    if (current(run, source)) {
                        mutable.update {
                            it.copy(
                                busy = false,
                                imports =
                                    it.imports
                                        .filterNot { c ->
                                            c.handle == handle && c.agentId == agentId
                                        }
                                        .toImmutableList(),
                            )
                        }
                        nav.openChat(ConversationRef(route.profile, imported.value.id))
                    } else stale()
            }
        }
    }

    override fun requestUpdate() {
        if (
            usable() != null &&
                state.value.canUpdate &&
                !state.value.busy &&
                !state.value.loading &&
                !state.value.uncertain
        )
            mutable.update { it.copy(confirmUpdate = true) }
    }

    override fun cancelUpdate() {
        mutable.update { it.copy(confirmUpdate = false) }
    }

    override fun confirmUpdate() {
        val source = usable() ?: return
        val gateway = source.gateway as? HostDaemonUpdateGateway ?: return
        if (!state.value.confirmUpdate || state.value.busy || state.value.uncertain) return
        val run = ++generation
        mutable.update {
            it.copy(
                confirmUpdate = false,
                busy = true,
                failure = null,
                updateResult = null,
                updatePhase = HostUpdatePhase.Starting,
            )
        }
        viewModelScope.launch {
            if (!current(run, source)) {
                stale()
                return@launch
            }
            when (
                val result = gateway.updateHostDaemon { phase ->
                    viewModelScope.launch {
                        if (current(run, source))
                            mutable.update {
                                if (phase.ordinal >= (it.updatePhase?.ordinal ?: -1))
                                    it.copy(updatePhase = phase)
                                else it
                            }
                    }
                }
            ) {
                is Outcome.Failed -> if (current(run, source)) failed(result.failure) else stale()
                is Outcome.Ok ->
                    if (current(run, source))
                        mutable.update {
                            it.copy(
                                busy = false,
                                updatePhase =
                                    if (result.value.success) HostUpdatePhase.Complete
                                    else it.updatePhase,
                                updateResult =
                                    result.value.copy(
                                        previousVersion = result.value.previousVersion?.let(::safe),
                                        newVersion = result.value.newVersion?.let(::safe),
                                    ),
                            )
                        }
                    else stale()
            }
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: AgentToolsRoute): AgentToolsViewModel
    }
}
