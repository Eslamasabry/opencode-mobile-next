package io.github.eslamasabry.oc.feature.servers.setup.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.engine.runtime.setupinspection.*
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

public data class SetupContext(
    val inspector: SetupInspector?,
    val registry: SetupRegistry,
    val online: Flow<Boolean>,
    val available: () -> Boolean,
)

public fun interface SetupAccess {
    public suspend fun open(profile: ProfileId): Outcome<SetupContext>
}

public data class SetupUi(
    val loading: Boolean = true,
    val sources: List<SetupConfigSource> = emptyList(),
    val inventory: List<SetupMcpStatus> = emptyList(),
    val inspected: Boolean = false,
    val inspectionAvailable: Boolean = false,
    val migration: SetupHistoryMigration? = null,
    val migrationUnavailable: Boolean = false,
    val failure: Failure? = null,
    val registry: RegistrySnapshot = RegistrySnapshot(),
    val online: Boolean = true,
)

public interface SetupActions {
    public fun back()

    public fun inspect()

    public fun consent(enabled: Boolean)

    public fun refresh(query: String? = null)

    public fun showSaved()
}

@AssistedInject
public class SetupViewModel(
    @Assisted private val profile: ProfileId,
    private val access: SetupAccess,
    private val nav: AppNavigator,
) : ViewModel(), SetupActions {
    private val mutable = MutableStateFlow(SetupUi())
    public val state: StateFlow<SetupUi> = mutable.asStateFlow()
    private var context: SetupContext? = null

    init {
        viewModelScope.launch {
            when (val opened = access.open(profile)) {
                is Outcome.Failed ->
                    mutable.update { it.copy(loading = false, failure = opened.failure) }
                is Outcome.Ok -> {
                    val live = opened.value
                    context = live
                    live.registry.load()
                    mutable.update {
                        it.copy(
                            loading = false,
                            registry = live.registry.state.value,
                            inspectionAvailable = live.inspector != null,
                        )
                    }
                    launch {
                        live.registry.state.collect { next ->
                            mutable.update { it.copy(registry = next) }
                        }
                    }
                    launch {
                        live.online.collect { online ->
                            if (!online) live.registry.offline()
                            mutable.update { it.copy(online = online) }
                        }
                    }
                    inspect()
                }
            }
        }
    }

    override fun back(): Unit = nav.back()

    override fun inspect() {
        val owner = context ?: return
        val inspector = owner.inspector ?: return
        if (state.value.loading || !owner.available()) return
        mutable.update { it.copy(loading = true, failure = null) }
        viewModelScope.launch {
            val migration = inspector.historyMigration()
            if (!owner.available()) {
                stale()
                return@launch
            }
            mutable.update {
                it.copy(
                    migration = (migration as? Outcome.Ok)?.value,
                    migrationUnavailable = migration is Outcome.Failed,
                )
            }
            val config = inspector.readConfig()
            if (!owner.available()) {
                stale()
                return@launch
            }
            if (config is Outcome.Failed) {
                mutable.update { it.copy(loading = false, failure = config.failure) }
                return@launch
            }
            val inventory = inspector.mcpInventory()
            if (!owner.available()) {
                stale()
                return@launch
            }
            when (inventory) {
                is Outcome.Failed ->
                    mutable.update { it.copy(loading = false, failure = inventory.failure) }
                is Outcome.Ok ->
                    mutable.update {
                        it.copy(
                            loading = false,
                            sources = (config as Outcome.Ok).value.sources,
                            inventory = inventory.value,
                            inspected = true,
                        )
                    }
            }
        }
    }

    private fun stale() {
        mutable.update {
            it.copy(loading = false, failure = Failure.Refused(RefusalReason.StaleScope))
        }
    }

    override fun consent(enabled: Boolean) {
        val owner = context ?: return
        viewModelScope.launch { owner.registry.consent(enabled) }
    }

    override fun refresh(query: String?) {
        val owner = context ?: return
        if (!owner.available()) return stale()
        if (!state.value.online) return owner.registry.offline()
        viewModelScope.launch { owner.registry.refresh(query) }
    }

    override fun showSaved() {
        context?.registry?.showSaved()
    }

    override fun onCleared() {
        context?.registry?.close()
        super.onCleared()
    }

    @AssistedFactory
    @ContributesIntoMap(AppScope::class, binding = binding<ManualViewModelAssistedFactory>())
    @ManualViewModelAssistedFactoryKey(SetupViewModel.Factory::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(profile: ProfileId): SetupViewModel
    }
}
