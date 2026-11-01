package io.github.eslamasabry.oc.ui.shell

import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.RouteMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the back stack NavShell attaches (plan 03 §15.3). Calls made while detached (activity recreation) are kept,
 * latest wins, and applied on attach. Main thread only.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
public class ShellNavigator : AppNavigator {
    private var stack: MutableList<NavKey>? = null
    private var pending: ((MutableList<NavKey>) -> Unit)? = null
    private val mutableTop = MutableStateFlow<AppRoute?>(null)
    override val top: StateFlow<AppRoute?> = mutableTop.asStateFlow()

    internal fun attach(backStack: MutableList<NavKey>) {
        stack = backStack
        pending?.invoke(backStack)
        pending = null
        publish()
    }

    internal fun detach(backStack: MutableList<NavKey>) {
        if (stack === backStack) stack = null
    }

    internal fun publish() {
        mutableTop.value = stack?.lastOrNull() as? AppRoute
    }

    private fun mutate(change: (MutableList<NavKey>) -> Unit) {
        val current = stack
        if (current == null) pending = change else change(current)
        publish()
    }

    override fun open(route: AppRoute): Unit = mutate { s ->
        when {
            route.motion == RouteMotion.Root -> s.replaceWith(route)
            s.lastOrNull() != route -> s.add(route)
        }
    }

    override fun openChat(ref: ConversationRef, focusComposer: Boolean): Unit = mutate { s ->
        if ((s.lastOrNull() as? ChatRoute)?.ref != ref) s.add(ChatRoute(ref, focusComposer))
    }

    override fun land(): Unit = mutate { it.replaceWith(ChatsRoute.Home) }

    override fun replaceRoot(root: AppRoute): Unit = mutate { it.replaceWith(root) }

    override fun back(): Unit = mutate { s -> if (s.size > 1) s.removeAt(s.lastIndex) }

    private fun MutableList<NavKey>.replaceWith(route: AppRoute) {
        add(route)
        while (size > 1) removeAt(0)
    }
}
