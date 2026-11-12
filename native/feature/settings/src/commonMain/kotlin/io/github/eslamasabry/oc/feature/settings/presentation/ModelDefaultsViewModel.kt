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

/** One mode (agent) the server lets a conversation start in; [description] is the server's own words, if any. */
@kotlinx.serialization.Serializable
public data class ModelDefaultsMode(val id: String, val description: String = "")

public data class ModelDefaultsData(
    val serverName: String,
    val thisPhone: Boolean,
    val models: List<CatalogModel>,
    val current: ModelRef?,
    val defaults: List<ModelRef>,
    val library: ModelLibrary,
    /** The saved thinking level for [current]; empty = the model's default. */
    val variant: String = "",
    /** Provider id -> the name the server gives it, so the picker's groups read "Anthropic", not "anthropic". */
    val providerNames: Map<String, String> = emptyMap(),
    /** Nothing is saved on this phone: [current] is only the server's own default. */
    val usesDefault: Boolean = false,
    /** The modes this server offers to start in (primary, not hidden); empty = the server offers none to choose. */
    val modes: List<ModelDefaultsMode> = emptyList(),
    /** The mode saved for new conversations (`oc.agent.<profile>`); empty = the server's default. */
    val mode: String = "",
)

public interface ModelDefaultsHost {
    public suspend fun read(
        profile: io.github.eslamasabry.oc.core.common.ids.ProfileId? = null
    ): Outcome<ModelDefaultsData>

    public suspend fun choose(model: ModelRef?): Outcome<Unit>

    /** Saves [model] with its thinking level ([variant] empty = the model's default). */
    public suspend fun chooseWithVariant(model: ModelRef, variant: String): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("modelDefaults")))

    /** Saves the mode new conversations start in ("" = the server's default). */
    public suspend fun chooseMode(mode: String): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("modelDefaults")))

    /** Adds [model] to the favourites, or removes it when it is one (the same shelf the chat's model sheet uses). */
    public suspend fun toggleFavorite(model: ModelRef): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("modelDefaults")))

    /**
     * Cache-first: what the last successful [read] for [profile] (null = the active one) showed, kept per server, so
     * the page opens on its real rows while the live read refreshes them. Null when nothing was kept.
     */
    public suspend fun kept(
        profile: io.github.eslamasabry.oc.core.common.ids.ProfileId? = null
    ): ModelDefaultsData? = null
}

public data class ModelDefaultsRow(
    val model: ModelRef,
    val name: String,
    val reason: ModelReason? = null,
    val free: Boolean = false,
    val inputPrice: Double? = null,
    val outputPrice: Double? = null,
    val selected: Boolean = false,
    /** The thinking levels this model offers (enabled ones only); empty = none. */
    val variants: List<String> = emptyList(),
    /** The provider's name as the server gives it; empty = unknown (the kit falls back to the id). */
    val providerName: String = "",
)

public data class ModelDefaultsUi(
    val serverName: String = "",
    val thisPhone: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val failure: Failure? = null,
    val recommended: List<ModelDefaultsRow> = emptyList(),
    val models: List<ModelDefaultsRow> = emptyList(),
    /** The model new conversations start with: the saved one, else the server's default (see [usesDefault]). */
    val current: ModelRef? = null,
    /** Nothing is saved on this phone, so the server's default applies. */
    val usesDefault: Boolean = false,
    /** The chosen thinking level; empty = the model's default. */
    val variant: String = "",
    val favorites: List<ModelRef> = emptyList(),
    val recent: List<ModelRef> = emptyList(),
    /** The modes to choose from; empty hides the Mode row. */
    val modes: List<ModelDefaultsMode> = emptyList(),
    /** The saved mode; empty = the server's default. */
    val mode: String = "",
)

public interface ModelDefaultsActions {
    public fun back()

    public fun refresh()

    public fun choose(model: ModelRef?)

    /** Keeps the chosen model and sets its thinking level ("" = the model's default). */
    public fun chooseVariant(variant: String)

    public fun toggleFavorite(model: ModelRef)

    /** Sets the mode new conversations start in ("" = the server's default). */
    public fun chooseMode(mode: String)
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
        // Cache-first: the kept rows draw first (no skeleton), the live read replaces them in place.
        viewModelScope.launch {
            host.kept(route.profile)?.let { kept ->
                if (state.value.models.isEmpty()) mutable.value = ui(kept).copy(loading = true)
            }
            if (!state.value.saving) {
                mutable.value = state.value.copy(loading = true, failure = null)
                load()
            }
        }
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
            is Outcome.Ok -> mutable.value = ui(read.value)
        }
    }

    private fun ui(data: ModelDefaultsData): ModelDefaultsUi {
        fun row(model: CatalogModel, reason: ModelReason? = null, free: Boolean = false) =
            ModelDefaultsRow(
                model.ref,
                model.name,
                reason,
                free,
                model.cost?.inputPerMillion,
                model.cost?.outputPerMillion,
                data.current?.sameModel(model.ref) == true,
                model.variants.filterNot { it.disabled }.map { it.id },
                data.providerNames[model.providerId].orEmpty(),
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
        return ModelDefaultsUi(
            data.serverName,
            data.thisPhone,
            loading = false,
            current = data.current,
            usesDefault = data.usesDefault,
            variant = data.variant,
            favorites = data.library.favorites.toList(),
            recent = data.library.recent.toList(),
            modes = data.modes,
            mode = data.mode,
            recommended = recommended,
            models =
                data.models
                    .filter { it.enabled }
                    .map {
                        row(
                            it,
                            free =
                                it.cost?.let { cost ->
                                    cost.inputPerMillion == 0.0 && cost.outputPerMillion == 0.0
                                } == true,
                        )
                    },
        )
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

    override fun chooseVariant(variant: String) {
        val current = state.value.current ?: return
        val row = state.value.models.firstOrNull { it.model.sameModel(current) } ?: return
        if (state.value.saving || state.value.loading || (variant.isNotEmpty() && variant !in row.variants)) return
        mutable.value = state.value.copy(saving = true, failure = null)
        viewModelScope.launch {
            when (val saved = host.chooseWithVariant(row.model, variant)) {
                is Outcome.Failed -> mutable.value = state.value.copy(saving = false, failure = saved.failure)
                is Outcome.Ok -> load()
            }
        }
    }

    override fun chooseMode(mode: String) {
        val shown = state.value
        if (shown.saving || shown.loading || shown.modes.isEmpty()) return
        if (mode.isNotEmpty() && shown.modes.none { it.id == mode }) return
        mutable.value = shown.copy(saving = true, failure = null)
        viewModelScope.launch {
            when (val saved = host.chooseMode(mode)) {
                is Outcome.Failed -> mutable.value = state.value.copy(saving = false, failure = saved.failure)
                is Outcome.Ok -> load()
            }
        }
    }

    override fun toggleFavorite(model: ModelRef) {
        if (state.value.saving || state.value.loading) return
        viewModelScope.launch {
            when (val saved = host.toggleFavorite(model)) {
                is Outcome.Failed -> mutable.value = state.value.copy(failure = saved.failure)
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
