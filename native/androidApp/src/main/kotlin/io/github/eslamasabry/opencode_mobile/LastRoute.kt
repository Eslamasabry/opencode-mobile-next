package io.github.eslamasabry.opencode_mobile

import android.os.Bundle
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.RouteMotion
import io.github.eslamasabry.oc.ui.routes.RouteSaving
import kotlinx.serialization.PolymorphicSerializer

/**
 * P-110 / gap 2: the pages the person had open when Android killed the app in the background (the system camera in
 * front, say), bottom first. The shell's back stack is saveable, but it is first composed behind the update gate's
 * "Opening…" screen, so after a process death it can come back on home with every page under the top one gone. The
 * activity keeps the whole stack in its own saved state and puts it back once the shell is up.
 */
internal object LastRoute {
    const val KEY: String = "oc.shell.lastRoute"
    private const val STACK: String = "oc.shell.stack"
    private const val COUNT: String = "n"

    private val serializer = PolymorphicSerializer(NavKey::class)

    /** [stack] bottom first, whole: the root page it grew from and every page above it. */
    fun save(stack: List<AppRoute>, out: Bundle) {
        val routes = stack.map(::restorable)
        if (routes.size < 2) return // home alone is what a fresh start shows anyway
        val bundle = Bundle()
        bundle.putInt(COUNT, routes.size)
        routes.forEachIndexed { index, route ->
            bundle.putBundle(index.toString(), encodeToSavedState(serializer, route, RouteSaving.configuration))
        }
        out.putBundle(STACK, bundle)
    }

    fun read(saved: Bundle?): List<AppRoute> {
        val bundle = saved?.getBundle(STACK) ?: return emptyList()
        return (0 until bundle.getInt(COUNT)).mapNotNull { index ->
            val one = bundle.getBundle(index.toString()) ?: return@mapNotNull null
            try {
                decodeFromSavedState(serializer, one, RouteSaving.configuration) as? AppRoute
            } catch (_: IllegalArgumentException) {
                null // a route this build no longer knows (SerializationException is one): skip that page
            }
        }
    }

    /**
     * The stack to put back, or empty. Only when the shell came back on home or another root page (or has not
     * published yet): a stack that restored itself, or a link that already opened a page, is left as it is. Home is
     * the bottom when the saved stack did not start on a root page.
     */
    fun reopen(saved: List<AppRoute>, current: AppRoute?, home: AppRoute): List<AppRoute> {
        if (saved.size < 2) return emptyList()
        if (current != null && current.motion != RouteMotion.Root) return emptyList()
        val routes = saved.map(::restorable)
        return if (routes.first().motion == RouteMotion.Root) routes else listOf(home) + routes
    }

    /** A chat's one-shot request and menu act never run twice. */
    fun restorable(route: AppRoute): AppRoute =
        if (route is ChatRoute) route.copy(requestId = null, thenAction = null) else route
}
