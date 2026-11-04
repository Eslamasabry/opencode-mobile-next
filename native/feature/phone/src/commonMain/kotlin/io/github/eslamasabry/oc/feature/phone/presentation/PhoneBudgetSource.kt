package io.github.eslamasabry.oc.feature.phone.presentation

import io.github.eslamasabry.oc.core.common.Outcome

/** The device-wide choice for easing local agent builds, adopted from Flutter's oc.fullSpeed. */
public interface PhoneBudgetSource {
    public suspend fun fullSpeed(): Boolean
    public suspend fun setFullSpeed(enabled: Boolean): Outcome<Unit>
}

/** Optional on desktop, where there is no on-phone agent environment to change. */
@dev.zacsweers.metro.ContributesTo(dev.zacsweers.metro.AppScope::class)
public interface PhoneBudgetBindings {
    @dev.zacsweers.metro.Multibinds(allowEmpty = true)
    public val phoneBudgetSources: Set<PhoneBudgetSource>
}

/** Added independently so existing screen actions remain source-compatible. */
public interface PhoneBudgetActions {
    public fun setFullSpeed(enabled: Boolean)
}
