package io.github.eslamasabry.oc.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.models.CatalogModel
import io.github.eslamasabry.oc.engine.agents.models.ModelLibrary
import io.github.eslamasabry.oc.engine.agents.models.ModelReason
import io.github.eslamasabry.oc.engine.agents.models.ModelRef
import io.github.eslamasabry.oc.engine.agents.models.recommendModels
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

public data class ModelDefaultsData(
    val serverName: String,
    val thisPhone: Boolean,
    val models: List<CatalogModel>,
    val current: ModelRef?,
    val defaults: List<ModelRef>,
    val library: ModelLibrary,
)

public interface ModelDefaultsHost {
    public suspend fun read(
        profile: io.github.eslamasabry.oc.core.common.ids.ProfileId? = null
    ): Outcome<ModelDefaultsData>

    public suspend fun choose(model: ModelRef?): Outcome<Unit>
}

public data class ModelDefaultsRow(
    val model: ModelRef,
    val name: String,
    val reason: ModelReason? = null,
    val free: Boolean = false,
    val inputPrice: Double? = null,
    val outputPrice: Double? = null,
    val selected: Boolean = false,
)

public data class ModelDefaultsUi(
    val serverName: String = "",
    val thisPhone: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val failure: Failure? = null,
    val recommended: List<ModelDefaultsRow> = emptyList(),
    val models: List<ModelDefaultsRow> = emptyList(),
)

public interface ModelDefaultsActions {
    public fun back()

    public fun refresh()

    public fun choose(model: ModelRef?)
}

@dev.zacsweers.metro.AssistedInject
public class ModelDefaultsViewModel(
    @dev.zacsweers.metro.Assisted
    private val route: io.github.eslamasabry.oc.ui.routes.SettingsRoute.ModelDefaults,
    private val host: ModelDefaultsHost,
    private val nav: AppNavigator,
) : ViewModel(), ModelDefaultsActions {
    private val mutable = MutableStateFlow(ModelDefaultsUi())
    public val state: StateFlow<ModelDefaultsUi> = mutable.asStateFlow()

    init {
        refresh()
    }

    override fun back() = nav.back()

    override fun refresh() {
        if (state.value.saving) return
        mutable.value = state.value.copy(loading = true, failure = null)
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        when (val read = host.read(route.profile)) {
            is Outcome.Failed ->
                mutable.value =
                    state.value.copy(loading = false, saving = false, failure = read.failure)
            is Outcome.Ok -> {
                val data = read.value
                fun row(model: CatalogModel, reason: ModelReason? = null, free: Boolean = false) =
                    ModelDefaultsRow(
                        model.ref,
                        model.name,
                        reason,
                        free,
                        model.cost?.inputPerMillion,
                        model.cost?.outputPerMillion,
                        data.current?.sameModel(model.ref) == true,
                    )
                val recommended =
                    recommendModels(
                            data.models,
                            data.current,
                            data.defaults,
                            data.library.recent,
                            data.library.favorites,
                        )
                        .map { row(it.model, it.reason, it.free) }
                mutable.value =
                    ModelDefaultsUi(
                        data.serverName,
                        data.thisPhone,
                        loading = false,
                        recommended = recommended,
                        models =
                            data.models
                                .filter { it.enabled }
                                .map {
                                    row(
                                        it,
                                        free =
                                            it.cost?.let { cost ->
                                                cost.inputPerMillion == 0.0 &&
                                                    cost.outputPerMillion == 0.0
                                            } == true,
                                    )
                                },
                    )
            }
        }
    }

    override fun choose(model: ModelRef?) {
        if (
            state.value.saving ||
                state.value.loading ||
                (model != null && state.value.models.none { it.model.sameModel(model) })
        )
            return
        mutable.value = state.value.copy(saving = true, failure = null)
        viewModelScope.launch {
            when (val saved = host.choose(model)) {
                is Outcome.Failed ->
                    mutable.value = state.value.copy(saving = false, failure = saved.failure)
                is Outcome.Ok -> load()
            }
        }
    }

    @dev.zacsweers.metro.AssistedFactory
    @dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
    @dev.zacsweers.metro.ContributesIntoMap(AppScope::class)
    public fun interface Factory : dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory {
        public fun create(
            @dev.zacsweers.metro.Assisted
            route: io.github.eslamasabry.oc.ui.routes.SettingsRoute.ModelDefaults
        ): ModelDefaultsViewModel
    }
}

@dev.zacsweers.metro.ContributesBinding(AppScope::class)
@Inject
public class NoModelDefaultsHost : ModelDefaultsHost {
    override suspend fun read(
        profile: io.github.eslamasabry.oc.core.common.ids.ProfileId?
    ): Outcome<ModelDefaultsData> =
        Outcome.Failed(
            Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("modelDefaults"))
        )

    override suspend fun choose(model: ModelRef?): Outcome<Unit> =
        Outcome.Failed(
            Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("modelDefaults"))
        )
}
