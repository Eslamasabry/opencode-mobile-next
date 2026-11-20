package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.github.eslamasabry.oc.ui.routes.AppNavigator

/**
 * The composer asks for `AppNavigator?` (BasicComposerViewModel). Metro keys a nullable type apart from its non-null
 * binding, so without this it was always null on the phone: in a sub-agent's read-only conversation, Open parent and
 * Continue in new conversation did nothing (since 3.0.0). Binding the nullable key to the one navigator fills it,
 * without touching the composer's constructor.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object NullableNavigatorBinding {
    @Provides fun navigator(navigator: AppNavigator): AppNavigator? = navigator
}
