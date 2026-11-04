package io.github.eslamasabry.oc.feature.preview.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.launch

/** The saved server a preview points at: the conversation's own, else the connected one. */
internal fun serverAddressFor(ref: ConversationRef?, servers: List<SavedServer>): String? =
    (servers.firstOrNull { ref != null && it.id == ref.profileId }
            ?: servers.firstOrNull { it.connected })
        ?.address

/**
 * The running page of one port (Flutter `DevPreviewScreen`). The target is resolved once, when the
 * page opens, so the page never swaps hosts under the person. Links to anywhere else go through
 * [PreviewBrowser] (the app's link policy and opener, AGENTS invariant 8): the app's own target
 * opens directly, a page's link to elsewhere asks first.
 */
@AssistedInject
public class DevPreviewViewModel(
    @Assisted private val route: PreviewRoute.Page,
    connections: ServerConnections,
    private val nav: AppNavigator,
    private val browser: PreviewBrowser,
    private val tools: PreviewTools = NoPreviewTools(),
) : ViewModel(), DevPreviewActions {
    private val target =
        DevPreviewTarget.resolve(
            PreviewLink(
                route.port,
                route.https,
                route.path.substringBefore('?').takeIf { it.startsWith("/") } ?: "/",
                route.path.substringAfter('?', "").ifEmpty { null },
            ),
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
                    canAsk = route.ref?.let(tools::canAsk) == true,
                    logsAvailable = tools.canReadLogs(route.ref),
                    target = target.takeUnless { it is DevPreviewTarget.Blocked },
                    blocked = (target as? DevPreviewTarget.Blocked)?.why,
                )
                .let { it.copy(shown = it.origin) }
        )
    public val state: StateFlow<DevPreviewUi> = mutable.asStateFlow()

    /** The rules the platform web view applies to every request; null while blocked. */
    public val pageEgress: PreviewEgress?
        get() = egress

    /** An approved link waiting for "Open in browser"; never in observable state. */
    private var pending: Any? = null

    override fun back(): Unit = nav.back()

    override fun openInBrowser() {
        val url = mutable.value.shown ?: mutable.value.origin ?: return
        // The address the app built (and pages on that same origin) is app-authored; anything else
        // asks first.
        decide(browser.check(url, appAuthored = egress?.mayLoad(url) == true))
    }

    override fun menu(open: Boolean): Unit = mutable.update { it.copy(menu = open) }

    override fun showDetails(open: Boolean): Unit = mutable.update {
        it.copy(details = open, menu = false)
    }

    override fun pagePhase(phase: PagePhase): Unit = mutable.update { it.copy(phase = phase) }

    override fun pageAddress(url: String) {
        // The bar and Details show only addresses the preview may load; a refused one never becomes
        // "current".
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

    override fun ask(text: String) {
        val ref = route.ref ?: return
        if (!mutable.value.canAsk || mutable.value.asking || text.isBlank()) return
        val delivery = tools.delivery(ref)
        if (
            delivery == PreviewSent.RestartsTurn &&
                mutable.value.delivery != PreviewSent.RestartsTurn
        ) {
            mutable.update { it.copy(delivery = delivery) }
            return
        }
        val allowRestart = mutable.value.delivery == PreviewSent.RestartsTurn
        mutable.update { it.copy(asking = true, notice = null, delivery = delivery) }
        viewModelScope.launch {
            when (val result = tools.send(ref, text.trim(), allowRestart)) {
                is io.github.eslamasabry.oc.core.common.Outcome.Failed ->
                    mutable.update { it.copy(asking = false, notice = PreviewNotice.AskFailed) }
                is io.github.eslamasabry.oc.core.common.Outcome.Ok ->
                    mutable.update {
                        it.copy(
                            asking = false,
                            askClear = it.askClear + 1,
                            // A later restart needs its own visible "Stop and send".
                            delivery = PreviewSent.Now,
                            notice =
                                when (result.value) {
                                    PreviewSent.Now -> PreviewNotice.Sent
                                    PreviewSent.JoinsTurn -> PreviewNotice.Joined
                                    PreviewSent.AfterReply -> PreviewNotice.AfterReply
                                    PreviewSent.RestartsTurn -> PreviewNotice.Restarted
                                },
                        )
                    }
            }
        }
    }

    private var copySerial = 0L

    override fun copyAddress() {
        val url = mutable.value.shown ?: return
        mutable.update { it.copy(copied = ++copySerial, copyText = url) }
    }

    override fun copied(id: Long) {
        if (mutable.value.copied == id) mutable.update { it.copy(copied = null, copyText = null) }
    }

    override fun showLogs(open: Boolean) {
        mutable.update { it.copy(details = false, logsOpen = open) }
        if (open) refreshLogs()
    }

    private var readingLogs = false

    override fun refreshLogs() {
        if (readingLogs || !mutable.value.logsOpen) return
        readingLogs = true
        viewModelScope.launch {
            try {
                when (val read = tools.readLogs(route.ref, route.port)) {
                    is io.github.eslamasabry.oc.core.common.Outcome.Ok ->
                        if (mutable.value.logsOpen)
                            mutable.update { it.copy(logs = read.value, logsFailed = false) }
                    is io.github.eslamasabry.oc.core.common.Outcome.Failed ->
                        if (mutable.value.logsOpen) mutable.update { it.copy(logsFailed = true) }
                }
            } finally {
                readingLogs = false
            }
        }
    }

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

/**
 * "Preview a running app": the ports the conversation mentioned, and a typed port as the
 * always-visible path.
 */
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
                        route.mentioned
                            .map { port ->
                                PortRow(
                                    port.port,
                                    port.https,
                                    (probe as? DevPreviewTarget.Remote)?.host,
                                    port.path,
                                )
                            }
                            .toImmutableList(),
                    blocked = (probe as? DevPreviewTarget.Blocked)?.why,
                )
            }
        )
    public val state: StateFlow<PortsUi> = mutable.asStateFlow()

    override fun back(): Unit = nav.back()

    override fun open(port: PreviewPort) {
        mutable.update { it.copy(invalidPort = false) }
        nav.open(PreviewRoute.Page(port.port, port.https, route.ref, port.path))
    }

    override fun openTyped(text: String) {
        val trimmed = text.trim()
        val port =
            trimmed
                .takeIf { it.length in 1..5 && it.all { c -> c in '0'..'9' } }
                ?.toInt()
                ?.takeIf { it in 1..65535 }
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
