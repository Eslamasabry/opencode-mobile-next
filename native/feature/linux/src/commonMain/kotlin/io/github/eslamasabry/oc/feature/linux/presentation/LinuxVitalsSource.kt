package io.github.eslamasabry.oc.feature.linux.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.vitals.LinuxVitals

public fun interface LinuxVitalsSource {
    public suspend fun read(): Outcome<LinuxVitals>
}

@ContributesTo(AppScope::class)
public interface LinuxVitalsBindings {
    @Multibinds(allowEmpty = true)
    public val linuxVitalsSources: Set<LinuxVitalsSource>
}

/** Route coverage follows RESUMED: another page or sheet suspends sampling. */
public interface LinuxVitalsActions {
    public fun vitalsShown(visible: Boolean)
}
