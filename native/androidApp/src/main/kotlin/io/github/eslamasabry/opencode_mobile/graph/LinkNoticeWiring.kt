package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.feature.servers.links.LinkNoticeHost
import io.github.eslamasabry.oc.feature.servers.links.LinkNotices
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.shell.ShellOverlay

/**
 * s28-app (stories 5, 6): the app's one link-notice slot. A deep link that cannot complete leaves a notice here
 * instead of silently landing on Servers (F: lib/main_parts/link_routes.dart).
 */
@SingleIn(AppScope::class)
@Inject
class AppLinkNotices {
    val notices: LinkNotices = LinkNotices()
}

/** Draws the notice above whatever screen is open; Add server opens the add flow itself (F: link_routes.dart:315). */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ShellOverlay>())
class LinkNoticeOverlay(private val links: AppLinkNotices, private val navigator: AppNavigator) : ShellOverlay {
    @Composable
    override fun Content() {
        val notice by links.notices.current.collectAsStateWithLifecycle()
        LinkNoticeHost(
            notice = notice,
            onAddServer = { navigator.open(ServersRoute.Edit()) },
            onDismiss = links.notices::dismiss,
        )
    }
}
