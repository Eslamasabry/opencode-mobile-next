package io.github.eslamasabry.oc.feature.onboarding.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.StateFlow

public enum class WelcomeSetupPhase {
    Idle,
    Running,
    Stopped,
    Done,
}

public enum class WelcomeSetupLaunch {
    Setup,
    Progress,
}

public enum class WelcomePlace {
    Phone,
    Computer,
}

public data class PhoneWelcomeUi(
    val available: Boolean = false,
    val phase: WelcomeSetupPhase = WelcomeSetupPhase.Idle,
    val percent: Int = 0,
    val seconds: Long? = null,
    val bytes: Long? = null,
    val working: Boolean = false,
    val failure: Failure? = null,
)

public data class WelcomeUi(
    val phone: PhoneWelcomeUi = PhoneWelcomeUi(),
    val place: WelcomePlace = WelcomePlace.Computer,
)

/** The same durable phone job the setup screen shows, projected without engine/platform types. */
public interface PhoneWelcomeSource {
    public val state: StateFlow<PhoneWelcomeUi>

    public suspend fun restore()

    public suspend fun start(): Outcome<WelcomeSetupLaunch>
}

/** Platforms contribute a real local runtime when one exists; desktop keeps an empty set. */
@dev.zacsweers.metro.ContributesTo(dev.zacsweers.metro.AppScope::class)
public interface PhoneWelcomeBindings {
    @dev.zacsweers.metro.Multibinds(allowEmpty = true)
    public val phoneWelcomeSources: Set<PhoneWelcomeSource>
}
