package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.github.eslamasabry.oc.core.common.AppCoroutineScope

/**
 * Home asks for `AppCoroutineScope?` (ChatsViewModel). Metro keys a nullable type apart from its non-null binding, so
 * without this it was always null on the phone: Home's archive, delete and rename ran on the page's own scope, and
 * leaving Home inside the undo window dropped them (an archived conversation came back; since 3.0.0). Binding the
 * nullable key to the app scope fills it, without touching Home's constructor.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object NullableAppScopeBinding {
    @Provides fun appScope(scope: AppCoroutineScope): AppCoroutineScope? = scope
}
