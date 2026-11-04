package io.github.eslamasabry.oc.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public interface WelcomeActions {
    /**
     * "Connect to my computer": the server setup, where a saved and answering server finishes first
     * run.
     */
    public fun connect()

    /**
     * Language and appearance before anything else, for a person who cannot read the default
     * language.
     */
    public fun openSettings()

    public fun select(place: WelcomePlace) {}

    public fun begin() {
        connect()
    }

    public fun refresh() {}
}

/** The welcome shares the durable phone job; leaving and returning never creates a second owner. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class WelcomeViewModel(
    private val nav: AppNavigator,
    sources: Set<PhoneWelcomeSource> = emptySet(),
) : ViewModel(), WelcomeActions {
    private val source = sources.singleOrNull()
    private var starting = false
    private val mutable =
        MutableStateFlow(
            WelcomeUi(
                phone = source?.state?.value ?: PhoneWelcomeUi(),
                place =
                    if (source?.state?.value?.available == true) WelcomePlace.Phone
                    else WelcomePlace.Computer,
            )
        )
    public val state: StateFlow<WelcomeUi> = mutable.asStateFlow()

    init {
        source?.let { phone ->
            viewModelScope.launch {
                phone.state.collect { value ->
                    mutable.update {
                        it.copy(phone = value.copy(working = value.working || starting))
                    }
                }
            }
        }
    }

    override fun refresh() {
        source?.let { phone -> viewModelScope.launch { phone.restore() } }
    }

    override fun select(place: WelcomePlace) {
        if (starting || state.value.phone.working) return
        if (place == WelcomePlace.Phone && !state.value.phone.available) return
        mutable.update { it.copy(place = place) }
    }

    override fun begin() {
        if (state.value.place == WelcomePlace.Computer) {
            connect()
            return
        }
        val phone = source ?: return
        if (starting || state.value.phone.working) return
        starting = true
        mutable.update { it.copy(phone = it.phone.copy(working = true, failure = null)) }
        viewModelScope.launch {
            try {
                when (val result = phone.start()) {
                    is Outcome.Ok -> nav.open(PhoneSetupRoute.Setup)
                    is Outcome.Failed ->
                        mutable.update { it.copy(phone = it.phone.copy(failure = result.failure)) }
                }
            } finally {
                starting = false
                mutable.update { it.copy(phone = it.phone.copy(working = false)) }
            }
        }
    }

    override fun connect(): Unit = nav.open(ServersRoute.Home)

    override fun openSettings(): Unit = nav.open(SettingsRoute.Home)
}
