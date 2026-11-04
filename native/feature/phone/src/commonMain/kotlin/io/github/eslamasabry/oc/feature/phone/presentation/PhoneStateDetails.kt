package io.github.eslamasabry.oc.feature.phone.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.lifecycle.AppStorageFacts
import io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecycleReport
import kotlinx.coroutines.flow.StateFlow

/** Read-only foreground recovery and storage observations for This phone. */
public interface PhoneStateDetails {
    public val recovery: StateFlow<LifecycleReport>

    public fun invalidate()

    public fun dismissRecovery()

    public suspend fun refresh()

    public suspend fun measureStorage(): Outcome<AppStorageFacts>
}

@ContributesTo(AppScope::class)
public interface PhoneStateDetailsBindings {
    @Multibinds(allowEmpty = true) public val details: Set<PhoneStateDetails>
}
