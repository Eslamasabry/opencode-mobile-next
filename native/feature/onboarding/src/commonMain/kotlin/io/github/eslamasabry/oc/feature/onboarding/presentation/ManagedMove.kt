package io.github.eslamasabry.oc.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.OnboardingRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Reasons have fixed native copy; technical text stays in the redacted failure. */
public enum class ManagedMoveReason {
    PhoneNotReady,
    SeparateHistory,
    MoveStopped,
    NotConfirmed,
}

public sealed interface ManagedMoveResult {
    public data object Done : ManagedMoveResult

    public data class Failed(val reason: ManagedMoveReason, val failure: Failure? = null) :
        ManagedMoveResult
}

/**
 * The host owns migration admission, setup and durable verification, independently of the screen.
 */
public interface ManagedMoveSource {
    public suspend fun needed(): Boolean

    public suspend fun move(): ManagedMoveResult
}

public data class ManagedMoveUi(
    val failure: ManagedMoveResult.Failed? = null,
    val retry: Int = 0,
    val retries: Int = 3,
)

public interface ManagedMoveActions {
    public fun retry()
}

/**
 * A bounded retry sequence; a separate history is always terminal and both databases remain intact.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class ManagedMoveViewModel(
    private val source: ManagedMoveSource,
    private val nav: AppNavigator,
) : ViewModel(), ManagedMoveActions {
    private val mutable = MutableStateFlow(ManagedMoveUi())
    public val state: StateFlow<ManagedMoveUi> = mutable.asStateFlow()
    private var job: Job? = null

    init {
        retry()
    }

    override fun retry() {
        if (
            job?.isActive == true ||
                mutable.value.failure?.reason == ManagedMoveReason.SeparateHistory
        )
            return
        job = viewModelScope.launch {
            mutable.value = ManagedMoveUi()
            val backoff = listOf(8_000L, 20_000L, 45_000L)
            for (attempt in 0..backoff.size) {
                when (val result = source.move()) {
                    ManagedMoveResult.Done -> {
                        if (nav.top.value == OnboardingRoute.ManagedMove) nav.land()
                        return@launch
                    }
                    is ManagedMoveResult.Failed -> {
                        if (
                            result.reason == ManagedMoveReason.SeparateHistory ||
                                attempt == backoff.size
                        ) {
                            mutable.value = ManagedMoveUi(failure = result)
                            return@launch
                        }
                        mutable.value = ManagedMoveUi(retry = attempt + 1)
                        delay(backoff[attempt])
                    }
                }
            }
        }
    }
}
