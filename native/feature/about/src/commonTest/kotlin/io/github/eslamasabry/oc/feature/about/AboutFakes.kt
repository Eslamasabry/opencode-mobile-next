package io.github.eslamasabry.oc.feature.about

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlinx.coroutines.flow.MutableStateFlow

/** Records Back; nothing else is opened from these pages. */
internal class FakeNavigator : AppNavigator {
    var backs = 0
    override val top = MutableStateFlow<AppRoute?>(null)

    override fun open(route: AppRoute) {
        top.value = route
    }

    override fun openChat(ref: ConversationRef, focusComposer: Boolean) = Unit

    override fun land() = Unit

    override fun replaceRoot(root: AppRoute) {
        top.value = root
    }

    override fun back() {
        backs++
    }
}
