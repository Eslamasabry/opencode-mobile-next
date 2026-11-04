package io.github.eslamasabry.oc.feature.servers.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.setupassist.*
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Config inspection, local guided proposals and an explicitly opted-in public registry. */
@AssistedInject
public class ServerSetupViewModel(
    @Assisted private val route: ServersRoute.Setup,
    private val source: ServersSetupSource,
    private val nav: AppNavigator,
    private val appScope: AppCoroutineScope,
) : ViewModel(), ServerSetupActions {
    private val shown = MutableStateFlow(ServerSetupUi())
    public val state: StateFlow<ServerSetupUi> = shown.asStateFlow()
    private var session: ServersSetupSession? = null
    private var guided: String? = null

    init {
        refresh()
    }

    override fun back(): Unit = nav.back()

    override fun refresh() = command {
        val owner =
            session
                ?: when (val opened = source.open(route.profileId)) {
                    is Outcome.Failed -> {
                        shown.update { it.copy(failed = true, available = false) }
                        return@command
                    }
                    is Outcome.Ok -> opened.value.also { session = it }
                }
        if (!owner.current()) {
            shown.update { it.copy(failed = true, available = false, config = "", proposal = "") }
            return@command
        }
        owner.controller.setOnline(owner.online())
        val result = owner.controller.refresh()
        if (!owner.current()) return@command
        shown.update {
            it.copy(
                available = true,
                failed = result is SetupOutcome.Failed,
                config = owner.controller.snapshot.config.toString(),
            )
        }
        registry(owner)
    }

    override fun choose(intent: SetupIntent) {
        if (shown.value.busy) return
        guided?.let { session?.planner?.closeSession(it) }
        guided = null
        shown.update { it.copy(intent = intent, guidance = null, proposal = "", invalid = false) }
    }

    override fun propose(values: Map<String, String>) = command {
        val owner = session ?: return@command
        if (!owner.current()) {
            shown.update { it.copy(failed = true, available = false) }
            return@command
        }
        val input = linkedMapOf<String, JsonElement>()
        for ((key, value) in values) {
            if (value.isBlank()) continue
            val element =
                if (key == "command") {
                    try {
                        Json.parseToJsonElement(value)
                    } catch (_: IllegalArgumentException) {
                        shown.update { it.copy(invalid = true, guidance = null, proposal = "") }
                        return@command
                    }
                } else JsonPrimitive(value.trim())
            input[key] = element
        }
        val reply =
            guided?.let { owner.planner.continueSession(it, inputs = input) }
                ?: owner.planner.start(shown.value.intent, inputs = input)
        guided = reply.sessionId.takeIf { it.isNotEmpty() }
        val invalid =
            reply.edits.isNotEmpty() && owner.controller.validate(reply.edits).isNotEmpty()
        // Guided proposals are local review only. Production has no atomic source restore endpoint,
        // so there is no
        // Apply button and no attempt to emulate one with GET/PATCH.
        val safe =
            if (!invalid)
                reply.edits.joinToString("\n") { edit ->
                    edit.path.joinToString(".") + " = " + (edit.value?.toString() ?: "∅")
                }
            else ""
        shown.update { it.copy(guidance = reply.state, invalid = invalid, proposal = safe) }
    }

    override fun optIn(enabled: Boolean) = command {
        val owner = session ?: return@command
        if (!owner.current()) return@command
        val result = owner.registry.setOptIn(enabled)
        shown.update { it.copy(failed = result is SetupOutcome.Failed) }
        registry(owner)
    }

    override fun refreshRegistry(query: String) = command {
        val owner = session ?: return@command
        if (!owner.current()) return@command
        owner.registry.refresh(online = owner.online(), query = query)
        if (owner.current()) registry(owner)
    }

    private fun registry(owner: ServersSetupSession) {
        val data = owner.registry.snapshot
        shown.update { state ->
            state.copy(
                optedIn = data.optedIn,
                registryStatus = data.status,
                listings =
                    data.entries.map { entry ->
                        SetupListingUi(
                            entry.title.ifBlank { entry.name },
                            entry.description,
                            when {
                                entry.connectableRemote != null -> SetupListingDetail.Remote
                                entry.remotes.isNotEmpty() -> SetupListingDetail.NeedsDetails
                                entry.packages.isNotEmpty() -> SetupListingDetail.ReviewedCommand
                                else -> SetupListingDetail.NoEndpoint
                            },
                        )
                    },
            )
        }
    }

    private fun command(action: suspend () -> Unit) {
        if (shown.value.busy) return
        shown.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch {
            try {
                action()
            } finally {
                shown.update { it.copy(busy = false) }
            }
        }
    }

    override fun onCleared() {
        session?.let { owner ->
            owner.stop()
            CoroutineScope(appScope.coroutineContext).launch { owner.close() }
        }
        super.onCleared()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ServersRoute.Setup): ServerSetupViewModel
    }
}
