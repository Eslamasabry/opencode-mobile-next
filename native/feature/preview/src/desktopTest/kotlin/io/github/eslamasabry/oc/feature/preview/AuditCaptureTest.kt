package io.github.eslamasabry.oc.feature.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.feature.preview.presentation.DevPreviewActions
import io.github.eslamasabry.oc.feature.preview.presentation.DevPreviewTarget
import io.github.eslamasabry.oc.feature.preview.presentation.DevPreviewUi
import io.github.eslamasabry.oc.feature.preview.presentation.PagePhase
import io.github.eslamasabry.oc.feature.preview.presentation.PortRow
import io.github.eslamasabry.oc.feature.preview.presentation.PortsActions
import io.github.eslamasabry.oc.feature.preview.presentation.PortsUi
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewBlock
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewEgress
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewNotice
import io.github.eslamasabry.oc.feature.preview.presentation.WebOrigin
import io.github.eslamasabry.oc.feature.preview.ui.DevPreviewScreen
import io.github.eslamasabry.oc.feature.preview.ui.NoWebPreviewSlot
import io.github.eslamasabry.oc.feature.preview.ui.PortsScreen
import io.github.eslamasabry.oc.feature.preview.ui.WebPreviewEvents
import io.github.eslamasabry.oc.feature.preview.ui.WebPreviewHandle
import io.github.eslamasabry.oc.feature.preview.ui.WebPreviewSlot
import io.github.eslamasabry.oc.ui.routes.PreviewPort
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/** Audit capture only: the preview screens in many states. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private object Actions : DevPreviewActions, PortsActions {
        override fun back() {}
        override fun openInBrowser() {}
        override fun showDetails(open: Boolean) {}
        override fun pagePhase(phase: PagePhase) = Unit
        override fun pageAddress(url: String) = Unit
        override fun pageCanGoBack(can: Boolean) = Unit
        override fun pageLeave(url: String) {}
        override fun confirmLeave() {}
        override fun cancelLeave() {}
        override fun noticeShown() = Unit
        override fun open(port: PreviewPort) {}
        override fun openTyped(text: String) {}
    }

    /** Stands in for the Android web view: draws an empty box. */
    private object FakeWebView : WebPreviewSlot {
        override val supported: Boolean = true

        @Composable
        override fun Page(origin: String, egress: PreviewEgress, handle: WebPreviewHandle, events: WebPreviewEvents, modifier: Modifier) {
            Box(modifier)
        }
    }

    private val phone = DevPreviewTarget.Phone("http://127.0.0.1:5173/")
    private val remote = DevPreviewTarget.Remote("http://box.tail.ts.net:5173/", "box.tail.ts.net")
    private val egress = PreviewEgress(WebOrigin.of("http://127.0.0.1:5173/")!!)

    private fun preview(name: String, ui: DevPreviewUi, web: Boolean = true) =
        Audit.guard("preview__dev-preview__${name}__build") {
            Audit.shoot("preview", "dev-preview", name) {
                DevPreviewScreen(ui, Actions, if (web) FakeWebView else NoWebPreviewSlot(), egress)
            }
        }

    @Test
    fun devPreview() {
        preview("loading", DevPreviewUi(5173, phone, phase = PagePhase.Loading, shown = phone.url))
        preview("ready", DevPreviewUi(5173, phone, phase = PagePhase.Ready, shown = phone.url))
        preview("ready-remote", DevPreviewUi(5173, remote, phase = PagePhase.Ready, shown = remote.url, details = true))
        preview("failed", DevPreviewUi(5173, phone, phase = PagePhase.Failed, shown = phone.url))
        preview("leave-ask", DevPreviewUi(5173, phone, shown = phone.url, leaveHost = "docs.example.com"))
        preview("leave-ask-insecure", DevPreviewUi(5173, phone, shown = phone.url, leaveHost = "docs.example.com", leaveInsecure = true))
        preview("no-server", DevPreviewUi(3000, blocked = PreviewBlock.NoServer))
        preview("no-host", DevPreviewUi(3000, blocked = PreviewBlock.NoHost))
        preview("no-web-view", DevPreviewUi(5173, phone, shown = phone.url), web = false)
        preview("notice-open-failed", DevPreviewUi(5173, phone, phase = PagePhase.Ready, shown = phone.url, notice = PreviewNotice.OpenFailed))
        preview("notice-link-blocked", DevPreviewUi(5173, phone, phase = PagePhase.Ready, shown = phone.url, notice = PreviewNotice.LinkBlocked))
        preview("can-go-back", DevPreviewUi(5173, phone, phase = PagePhase.Ready, shown = phone.url, canGoBack = true, menu = false))
    }

    private fun ports(name: String, ui: PortsUi) =
        Audit.guard("preview__ports__${name}__build") {
            Audit.shoot("preview", "ports", name) { PortsScreen(ui, Actions) }
        }

    @Test
    fun ports() {
        ports("list", PortsUi(persistentListOf(PortRow(5173, false, null), PortRow(8080, false, "box.tail.ts.net"), PortRow(8443, true, "a-very-long-machine-name.example-tailnet.ts.net"))))
        ports("empty", PortsUi())
        ports("invalid-port", PortsUi(invalidPort = true))
        ports("no-server", PortsUi(blocked = PreviewBlock.NoServer))
    }
}
