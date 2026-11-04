package io.github.eslamasabry.oc.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.StateFlow

/**
 * The page mirrors the root-owned move; the world owns retries, admission and landing, so leaving
 * and reopening the page never starts a second move.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class ManagedMoveViewModel(private val world: ManagedMoveWorld) :
    ViewModel(), ManagedMoveActions {
    public val state: StateFlow<ManagedMoveUi> = world.state

    override fun retry() {
        if (state.value.running || state.value.problem == ManagedMoveProblem.SeparateHistory) return
        world.retry()
    }

    override fun changeServer(): Unit = world.changeServer()
}
