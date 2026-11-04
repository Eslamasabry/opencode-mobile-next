package io.github.eslamasabry.oc.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.StateFlow

public enum class ManagedMoveProblem {
    SeparateHistory,
    NotReady,
    NotConfirmed,
    Stopped,
}

public data class ManagedMoveUi(
    val running: Boolean = true,
    val problem: ManagedMoveProblem? = null,
)

public interface ManagedMoveActions {
    public fun retry()

    public fun changeServer()
}

/**
 * The exact saved phone owner's actual migration, never a status inferred from an OC2 health reply.
 */
public interface ManagedMoveWorld : ManagedMoveActions {
    public val state: StateFlow<ManagedMoveUi>
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class ManagedMoveViewModel(private val world: ManagedMoveWorld) :
    ViewModel(), ManagedMoveActions {
    public val state: StateFlow<ManagedMoveUi> = world.state

    override fun retry(): Unit = world.retry()

    override fun changeServer(): Unit = world.changeServer()
}
