package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.github.eslamasabry.oc.feature.chats.presentation.HomeStop

/**
 * Home asks for `HomeStop?` (ChatsViewModel). Metro keys a nullable type apart from its non-null binding
 * (LampBindings.homeStop), so without this it was always null on the phone and Home's row menu never offered Stop for
 * a running conversation (since 3.0.0). Binding the nullable key to the same stop fills it, without touching Home's
 * constructor.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object NullableHomeStopBinding {
    @Provides fun homeStop(stop: HomeStop): HomeStop? = stop
}
