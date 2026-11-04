package io.github.eslamasabry.oc.feature.phone.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The app's heat protection setting; absent on platforms without a phone runtime. */
public interface ThermalSettings {
    public val supported: Boolean
    public val enabled: StateFlow<Boolean>

    public suspend fun setEnabled(value: Boolean): Outcome<Unit>
}

@ContributesBinding(AppScope::class)
@Inject
public class NoThermalSettings : ThermalSettings {
    override val supported: Boolean = false
    override val enabled: StateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun setEnabled(value: Boolean): Outcome<Unit> =
        Outcome.Failed(Failure.Refused(RefusalReason.NotOnThisPhone))
}

/** Heat controls are present only when the actions own a real thermal settings port. */
public interface ThermalKeepRunningActions : KeepRunningActions {
    public fun setThermalEnabled(value: Boolean)
}
