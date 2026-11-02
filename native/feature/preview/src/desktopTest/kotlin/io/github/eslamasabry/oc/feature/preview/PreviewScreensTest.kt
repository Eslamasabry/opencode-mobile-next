package io.github.eslamasabry.oc.feature.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.preview.presentation.DevPreviewActions
import io.github.eslamasabry.oc.feature.preview.presentation.DevPreviewTarget
import io.github.eslamasabry.oc.feature.preview.presentation.DevPreviewUi
import io.github.eslamasabry.oc.feature.preview.presentation.PagePhase
import io.github.eslamasabry.oc.feature.preview.presentation.PortRow
import io.github.eslamasabry.oc.feature.preview.presentation.PortsActions
import io.github.eslamasabry.oc.feature.preview.presentation.PortsUi
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewBlock
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewEgress
import io.github.eslamasabry.oc.feature.preview.presentation.WebOrigin
import io.github.eslamasabry.oc.feature.preview.ui.DevPreviewScreen
import io.github.eslamasabry.oc.feature.preview.ui.NoWebPreviewSlot
import io.github.eslamasabry.oc.feature.preview.ui.PortsScreen
import io.github.eslamasabry.oc.feature.preview.ui.WebPreviewEvents
import io.github.eslamasabry.oc.feature.preview.ui.WebPreviewHandle
import io.github.eslamasabry.oc.feature.preview.ui.WebPreviewSlot
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.routes.PreviewPort
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** The real preview screens by visible words (unit PREVIEW-QR). */
@OptIn(ExperimentalTestApi::class)
class PreviewScreensTest {
    private var previous: Locale = Locale.getDefault()

    @BeforeTest fun before() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest fun after() = Locale.setDefault(previous)

    private class Actions : DevPreviewActions, PortsActions {
        val calls = mutableListOf<String>()

        override fun back() { calls += "back" }

        override fun openInBrowser() { calls += "browser" }

        override fun showDetails(open: Boolean) { calls += "details:$open" }

        override fun pagePhase(phase: PagePhase) = Unit

        override fun pageAddress(url: String) = Unit

        override fun pageCanGoBack(can: Boolean) = Unit

        override fun pageLeave(url: String) { calls += "leave" }

        override fun confirmLeave() { calls += "confirmLeave" }

        override fun cancelLeave() { calls += "cancelLeave" }

        override fun noticeShown() = Unit

        override fun open(port: PreviewPort) { calls += "open:${port.port}" }

        override fun openTyped(text: String) { calls += "typed:$text" }
    }

    /** Stands in for the Android web view: draws a marked box. */
    private object FakeWebView : WebPreviewSlot {
        override val supported: Boolean = true

        @Composable
        override fun Page(origin: String, egress: PreviewEgress, handle: WebPreviewHandle, events: WebPreviewEvents, modifier: Modifier) {
            Box(modifier.testTag("fake-web-view"))
        }
    }

    private val phone = DevPreviewTarget.Phone("http://127.0.0.1:5173/")
    private val egress = PreviewEgress(WebOrigin.of("http://127.0.0.1:5173/")!!)

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "en", stillFrame = true) {
            Box(Modifier.width(412.dp)) { content() }
        }
    }

    @Test
    fun `B-SEC-058 the running page fills the screen under Preview with its port`() = runComposeUiTest {
        val actions = Actions()
        setContent { Themed { DevPreviewScreen(DevPreviewUi(5173, phone, phase = PagePhase.Ready, shown = phone.url), actions, FakeWebView, egress) } }
        onNodeWithText("Preview").assertExists()
        onNodeWithText("Port 5173").assertExists()
        onNodeWithTag("fake-web-view").assertExists()
        onNodeWithText("Open in browser").performClick()
        assertEquals(listOf("browser"), actions.calls)
    }

    @Test
    fun `B-SEC-058 where there is no web view the screen says so and offers the browser`() = runComposeUiTest {
        setContent { Themed { DevPreviewScreen(DevPreviewUi(5173, phone, shown = phone.url), Actions(), NoWebPreviewSlot(), egress) } }
        onNodeWithText("Preview isn't available here").assertExists()
        onNodeWithTag("fake-web-view").assertDoesNotExist()
    }

    @Test
    fun `B-CHAT-027 with no server the preview is blocked in plain words`() = runComposeUiTest {
        setContent { Themed { DevPreviewScreen(DevPreviewUi(3000, blocked = PreviewBlock.NoServer), Actions(), FakeWebView, null) } }
        onNodeWithText("Connect a server first").assertExists()
        onNodeWithTag("fake-web-view").assertDoesNotExist()
    }

    @Test
    fun `B-CHAT-027 a port that does not answer says so and offers Try again`() = runComposeUiTest {
        setContent { Themed { DevPreviewScreen(DevPreviewUi(5173, phone, phase = PagePhase.Failed, shown = phone.url), Actions(), FakeWebView, egress) } }
        onNodeWithText("Nothing is answering yet").assertExists()
        onNodeWithText("Port 5173 is not responding. The app may still be starting.").assertExists()
        onNodeWithText("Try again").assertExists()
    }

    @Test
    fun `B-SEC-058 a link to elsewhere asks before the browser opens it`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            Themed { DevPreviewScreen(DevPreviewUi(5173, phone, shown = phone.url, leaveHost = "docs.example.com"), actions, FakeWebView, egress) }
        }
        onNodeWithText("Open this link in your browser?").assertExists()
        onNodeWithText("Opens docs.example.com outside this app.").assertExists()
        onNodeWithText("Don't open").performClick()
        assertEquals(listOf("cancelLeave"), actions.calls)
    }

    @Test
    fun `B-CHAT-026 the ports page lists mentioned ports with where they open`() = runComposeUiTest {
        val actions = Actions()
        val rows = persistentListOf(PortRow(5173, false, null), PortRow(8080, false, "box.tail.ts.net"))
        setContent { Themed { PortsScreen(PortsUi(rows), actions) } }
        onNodeWithText("Preview a running app").assertExists()
        onNodeWithText("On this phone").assertExists()
        onNodeWithText("On box.tail.ts.net").assertExists()
        onNodeWithText("Port 8080").performClick()
        assertEquals(listOf("open:8080"), actions.calls)
    }

    @Test
    fun `B-CHAT-025 with nothing mentioned a typed port is the visible way`() = runComposeUiTest {
        setContent { Themed { PortsScreen(PortsUi(invalidPort = true), Actions()) } }
        onNodeWithText("No running apps found").assertExists()
        onNodeWithText("Port number").assertExists()
        onNodeWithText("Enter a port from 1 to 65535.").assertExists()
        onNodeWithText("Open port").assertIsDisplayed()
    }
}
