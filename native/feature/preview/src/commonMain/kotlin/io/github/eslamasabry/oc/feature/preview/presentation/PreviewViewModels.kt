package io.github.eslamasabry.oc.feature.preview.presentation

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PreviewLink
import io.github.eslamasabry.oc.ui.routes.PreviewPort
import io.github.eslamasabry.oc.ui.routes.PreviewRoute
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The saved server a preview points at: the conversation's own, else the connected one. */
internal fun serverAddressFor(ref: ConversationRef?, servers: List<SavedServer>): String? =
    (servers.firstOrNull { ref != null && it.id == ref.profileId } ?: servers.firstOrNull { it.connected })?.address

/**
 * The running page of one port (Flutter `DevPreviewScreen`). The target is resolved once, when the page opens, so the
 * page never swaps hosts under the person. Links to anywhere else go through [PreviewBrowser] (the app's link policy and
 * opener, AGENTS invariant 8): the app's own target opens directly, a page's link to elsewhere asks first.
 */
@AssistedInject
public class DevPreviewViewModel(
    @Assisted private val route: PreviewRoute.Page,
    connections: ServerConnections,
    private val nav: AppNavigator,
    private val browser: PreviewBrowser,
) : ViewModel(), DevPreviewActions {
    private val target =
        DevPreviewTarget.resolve(
            PreviewLink(route.port, route.https),
            serverAddressFor(route.ref, connections.servers.value),
        )
    private val egress: PreviewEgress? =
        when (target) {
            is DevPreviewTarget.Phone -> WebOrigin.of(target.url)?.let(::PreviewEgress)
            is DevPreviewTarget.Remote -> WebOrigin.of(target.url)?.let(::PreviewEgress)
            is DevPreviewTarget.Blocked -> null
        }
    private val mutable =
        MutableStateFlow(
            DevPreviewUi(
                port = route.port,
                target = target.takeUnless { it is DevPreviewTarget.Blocked },
                blocked = (target as? DevPreviewTarget.Blocked)?.why,
            ).let { it.copy(shown = it.origin) },
        )
    public val state: StateFlow<DevPreviewUi> = mutable.asStateFlow()

    /** The rules the platform web view applies to every request; null while blocked. */
    public val pageEgress: PreviewEgress? get() = egress

    /** An approved link waiting for "Open in browser"; never in observable state. */
    private var pending: Any? = null

    override fun back(): Unit = nav.back()

    override fun openInBrowser() {
        val url = mutable.value.shown ?: mutable.value.origin ?: return
        // The address the app built (and pages on that same origin) is app-authored; anything else asks first.
        decide(browser.check(url, appAuthored = egress?.mayLoad(url) == true))
    }

    override fun showDetails(open: Boolean): Unit = mutable.update { it.copy(details = open) }

    override fun pagePhase(phase: PagePhase): Unit = mutable.update { it.copy(phase = phase) }

    override fun pageAddress(url: String) {
        // The bar and Details show only addresses the preview may load; a refused one never becomes "current".
        if (egress?.mayLoad(url) == true) mutable.update { it.copy(shown = url) }
    }

    override fun pageCanGoBack(can: Boolean): Unit = mutable.update { it.copy(canGoBack = can) }

    override fun pageLeave(url: String): Unit = decide(browser.check(url, appAuthored = false))

    override fun confirmLeave() {
        val token = pending ?: return
        pending = null
        mutable.update { it.copy(leaveHost = null) }
        open(token)
    }

    override fun cancelLeave() {
        pending = null
        mutable.update { it.copy(leaveHost = null) }
    }

    override fun noticeShown(): Unit = mutable.update { it.copy(notice = null) }

    private fun decide(ticket: BrowserTicket) {
        when (ticket) {
            is BrowserTicket.Open -> open(ticket.token)
            is BrowserTicket.AskFirst -> {
                pending = ticket.token
                mutable.update { it.copy(leaveHost = ticket.host) }
            }
            BrowserTicket.Refused -> mutable.update { it.copy(notice = PreviewNotice.LinkBlocked) }
        }
    }

    private fun open(token: Any) {
        if (!browser.open(token)) mutable.update { it.copy(notice = PreviewNotice.OpenFailed) }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: PreviewRoute.Page): DevPreviewViewModel
    }
}

/** "Preview a running app": the ports the conversation mentioned, and a typed port as the always-visible path. */
@AssistedInject
public class PortsViewModel(
    @Assisted private val route: PreviewRoute.Ports,
    connections: ServerConnections,
    private val nav: AppNavigator,
) : ViewModel(), PortsActions {
    private val mutable =
        MutableStateFlow(
            serverAddressFor(route.ref, connections.servers.value).let { address ->
                val probe = DevPreviewTarget.resolve(PreviewLink(1), address)
                PortsUi(
                    rows =
                        route.mentioned.map { port ->
                            PortRow(port.port, port.https, (probe as? DevPreviewTarget.Remote)?.host)
                        }.toImmutableList(),
                    blocked = (probe as? DevPreviewTarget.Blocked)?.why,
                )
            },
        )
    public val state: StateFlow<PortsUi> = mutable.asStateFlow()

    override fun back(): Unit = nav.back()

    override fun open(port: PreviewPort) {
        mutable.update { it.copy(invalidPort = false) }
        nav.open(PreviewRoute.Page(port.port, port.https, route.ref))
    }

    override fun openTyped(text: String) {
        val trimmed = text.trim()
        val port = trimmed.takeIf { it.length in 1..5 && it.all { c -> c in '0'..'9' } }?.toInt()?.takeIf { it in 1..65535 }
        if (port == null) {
            mutable.update { it.copy(invalidPort = true) }
            return
        }
        open(PreviewPort(port))
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: PreviewRoute.Ports): PortsViewModel
    }
}
