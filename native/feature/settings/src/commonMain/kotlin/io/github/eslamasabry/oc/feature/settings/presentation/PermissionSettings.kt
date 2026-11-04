package io.github.eslamasabry.oc.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.settings.consent.ConsentChoice
import io.github.eslamasabry.oc.engine.settings.consent.InFlowConsentKind
import io.github.eslamasabry.oc.engine.settings.consent.InFlowConsentRow
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

public data class PermissionSettingsFacts(
    val profile: ProfileId,
    val name: String,
    val ticket: Long,
    val answers: ImmutableList<InFlowConsentRow> = persistentListOf(),
    val writable: Boolean = false,
    val declined: Int? = null,
    val savedGrants: Boolean = false,
)

public interface SettingsAnswersSource {
    public val changes: Flow<Unit>

    public suspend fun read(profile: ProfileId): Outcome<PermissionSettingsFacts>

    public suspend fun allow(
        profile: ProfileId,
        ticket: Long,
        kind: InFlowConsentKind,
        stillCurrent: () -> Boolean,
    ): Outcome<Unit>

    public suspend fun offerAgain(
        profile: ProfileId,
        ticket: Long,
        stillCurrent: () -> Boolean,
    ): Outcome<Unit>

    public fun openHome(kind: InFlowConsentKind)

    public fun openGrants(profile: ProfileId)
}

public data class PermissionSettingsUi(
    val facts: PermissionSettingsFacts? = null,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val confirm: InFlowConsentKind? = null,
    val confirmAgain: Boolean = false,
)

@AssistedInject
public class PermissionSettingsViewModel(
    @Assisted private val route: SettingsRoute.Permissions,
    private val source: SettingsAnswersSource,
    private val nav: AppNavigator,
    private val appScope: AppCoroutineScope,
) : ViewModel() {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: SettingsRoute.Permissions): PermissionSettingsViewModel
    }

    private val mutable = MutableStateFlow(PermissionSettingsUi())
    public val state: StateFlow<PermissionSettingsUi> = mutable.asStateFlow()

    init {
        viewModelScope.launch { source.changes.collect { refresh() } }
    }

    public fun refresh() {
        viewModelScope.launch {
            when (val found = source.read(route.profile)) {
                is Outcome.Failed -> mutable.update { it.copy(facts = null, failed = true) }
                is Outcome.Ok -> mutable.update { it.copy(facts = found.value) }
            }
        }
    }

    public fun back(): Unit = nav.back()

    public fun answer(kind: InFlowConsentKind) {
        val ui = state.value
        val facts = ui.facts ?: return
        val answer = facts.answers.firstOrNull { it.kind == kind } ?: return
        if (ui.busy) return
        if (answer.choice == ConsentChoice.Accepted) source.openHome(kind)
        else if (facts.writable) mutable.update { it.copy(confirm = kind) }
    }

    public fun askAgain() {
        if (state.value.facts?.declined?.let { it > 0 } == true && !state.value.busy)
            mutable.update { it.copy(confirmAgain = true) }
    }

    public fun cancel() {
        mutable.update { it.copy(confirm = null, confirmAgain = false) }
    }

    public fun confirm() {
        val ui = state.value
        val facts = ui.facts ?: return
        if (ui.busy || (ui.confirm == null && !ui.confirmAgain)) return
        mutable.update {
            it.copy(busy = true, confirm = null, confirmAgain = false, failed = false)
        }
        appScope.launch {
            val stillCurrent = { nav.top.value == route }
            val result =
                if (ui.confirmAgain) source.offerAgain(route.profile, facts.ticket, stillCurrent)
                else
                    source.allow(
                        route.profile,
                        facts.ticket,
                        ui.confirm ?: return@launch,
                        stillCurrent,
                    )
            mutable.update { it.copy(busy = false, failed = result is Outcome.Failed) }
            refresh()
        }
    }

    public fun grants() {
        if (state.value.facts?.savedGrants == true) source.openGrants(route.profile)
    }
}
