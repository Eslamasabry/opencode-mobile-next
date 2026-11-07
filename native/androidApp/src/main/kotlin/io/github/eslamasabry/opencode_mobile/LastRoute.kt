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
 * P-110: the page the person was on when Android killed the app in the background (the system
 * camera in front, say). The shell's back stack is saveable, but it is first composed behind the
 * update gate's "Opening…" screen, so after a process death it can come back on home. The activity
 * keeps the top route in its own saved state and reopens it once the shell is up.
 */
internal object LastRoute {
    const val KEY: String = "oc.shell.lastRoute"

    private val serializer = PolymorphicSerializer(NavKey::class)

    fun save(top: AppRoute?, out: Bundle) {
        val route = top?.let(::restorable) ?: return
        out.putBundle(KEY, encodeToSavedState(serializer, route, RouteSaving.configuration))
    }

    fun read(saved: Bundle?): AppRoute? {
        val bundle = saved?.getBundle(KEY) ?: return null
        return try {
            decodeFromSavedState(serializer, bundle, RouteSaving.configuration) as? AppRoute
        } catch (_: IllegalArgumentException) {
            null // a route this build no longer knows (SerializationException is one): stay on home
        }
    }

    /**
     * The route to reopen, or null. Only when the shell came back on home (null or a root page): a
     * stack that restored itself, or a link that already opened a page, is left as it is.
     */
    fun reopen(saved: AppRoute?, current: AppRoute?): AppRoute? {
        val route = saved?.let(::restorable) ?: return null
        if (current != null && current.motion != RouteMotion.Root) return null
        return route.takeUnless { it == current }
    }

    /** Root pages are home itself; a chat's one-shot request and menu act never run twice. */
    private fun restorable(route: AppRoute): AppRoute? =
        when {
            route.motion == RouteMotion.Root -> null
            route is ChatRoute -> route.copy(requestId = null, thenAction = null)
            else -> route
        }
}
