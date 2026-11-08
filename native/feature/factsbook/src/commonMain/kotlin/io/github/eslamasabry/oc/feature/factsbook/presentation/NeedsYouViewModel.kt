package io.github.eslamasabry.oc.feature.factsbook.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.engine.factsbook.CouldNotSaveState
import io.github.eslamasabry.oc.engine.factsbook.ReviewItem
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.FactsBookRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public data class NeedsYouUi(
    val review: ImmutableList<ReviewItem> = persistentListOf(),
    val notSaved: Int = 0,
    /**
     * A decision is signed by this phone's owner key. The native app has no owner signer yet, so this stays false and
     * the answers say why they are off, even where Flutter had set up approval (report: owner signer).
     */
    val canApprove: Boolean = false,
)

public interface NeedsYouActions {
    public fun back()

    public fun openNotSaved()

    public fun setUpApproval()
}

/** Needs you: one page for changes to approve and notes not saved yet (F: openFactsBookNeedsYou). */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class NeedsYouViewModel(private val access: FactsBookAccess, private val nav: AppNavigator) : ViewModel(), NeedsYouActions {
    public val state: StateFlow<NeedsYouUi> =
        access.engine.state
            .map { s ->
                NeedsYouUi(
                    review = s.review.toImmutableList(),
                    notSaved = s.pending.count { it.state != CouldNotSaveState.Discarded },
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NeedsYouUi())

    init {
        viewModelScope.launch {
            access.engine.open(access.activeProfileId.value)
            access.engine.refresh()
        }
    }

    override fun back(): Unit = nav.back()

    override fun openNotSaved(): Unit = nav.open(FactsBookRoute.NotSaved)

    override fun setUpApproval(): Unit = nav.open(FactsBookRoute.Connect)
}
