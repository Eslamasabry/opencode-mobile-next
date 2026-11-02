package io.github.eslamasabry.oc.feature.servers.pairing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.feature.servers.ui.ServersScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The pairing scanner page and the server form's pairing doors, by visible words (unit PREVIEW-QR). */
@OptIn(ExperimentalTestApi::class)
class PairingScreensTest {
    private var previous: Locale = Locale.getDefault()

    @BeforeTest fun before() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest fun after() = Locale.setDefault(previous)

    private class ScannerActions : PairingScannerActions {
        val calls = mutableListOf<String>()

        override fun close() { calls += "close" }

        override fun scanned(text: String) { calls += "scanned" }

        override fun rejected() { calls += "rejected" }

        override fun pasted(text: String?) { calls += "pasted" }
    }

    /** A camera that reports [stage] and draws a marked box; it never decodes anything. */
    private class FakeCamera(private val stage: ScanStage) : QrScannerSlot {
        override val supported: Boolean = true

        @Composable
        override fun Viewfinder(handle: QrScannerHandle, accept: (String) -> Boolean, onCode: (String) -> Unit, onRejected: () -> Unit, modifier: Modifier) {
            SideEffect { handle.stage = stage }
            Box(modifier.testTag("fake-camera"))
        }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "en", stillFrame = true) {
                Box(Modifier.width(412.dp)) { content() }
            }
        }
    }

    @Test
    fun `B-ANDR-049 with nothing to scan with the page says so and pasting is the way`() = runComposeUiTest {
        val actions = ScannerActions()
        setContent { Themed { PairingScannerScreen(PairingScannerUi(), actions, NoQrScannerSlot(), { false }) } }
        onNodeWithText("This device has no camera").assertExists()
        onNodeWithTag("fake-camera").assertDoesNotExist()
        onNodeWithText("Pairing code").assertExists()
        onNodeWithText("Paste pairing code").assertExists()
        assertEquals(emptyList(), actions.calls)
    }

    @Test
    fun `B-ANDR-049 a refused camera explains itself and offers Allow camera beside Paste`() = runComposeUiTest {
        setContent { Themed { PairingScannerScreen(PairingScannerUi(), ScannerActions(), FakeCamera(ScanStage.Denied), { false }) } }
        onNodeWithText("Camera access is needed to scan").assertExists()
        onNodeWithText("Allow camera").assertExists()
        onNodeWithText("Pairing code").assertExists()
    }

    @Test
    fun `B-ANDR-049 a camera blocked for good points to app settings with no retry`() = runComposeUiTest {
        setContent { Themed { PairingScannerScreen(PairingScannerUi(), ScannerActions(), FakeCamera(ScanStage.Blocked), { false }) } }
        onNodeWithText("Camera access is turned off").assertExists()
        onNodeWithText("Open app settings").assertExists()
        onNodeWithText("Allow camera").assertDoesNotExist()
    }

    @Test
    fun `B-PROTO-069 scanning shows the instruction and a QR that is not a pairing code is refused plainly`() = runComposeUiTest {
        setContent { Themed { PairingScannerScreen(PairingScannerUi(rejected = true), ScannerActions(), FakeCamera(ScanStage.Scanning), { false }) } }
        onNodeWithTag("fake-camera").assertExists()
        onNodeWithText("Point the camera at the QR code printed by opencode2 pair.").assertExists()
        onNodeWithText("That QR code is not a pairing code. Point the camera at the code opencode2 pair prints.").assertExists()
    }

    @Test
    fun `B-PROTO-065 a bad pasted code is one plain sentence`() = runComposeUiTest {
        setContent { Themed { PairingScannerScreen(PairingScannerUi(problem = PairingProblem.PasswordMissing), ScannerActions(), NoQrScannerSlot(), { false }) } }
        onNodeWithText("That pairing code has no “password” field. It may have been truncated — scan or copy the whole code.").assertExists()
    }

    private class FormActions(private var fill: PairingFill?) : ServersActions {
        val calls = mutableListOf<String>()

        override fun back() = Unit

        override fun allowCleartext(allowed: Boolean, address: String) = Unit

        override fun save(name: String, address: String, username: String, password: String) { calls += "save:$address:$username:$password" }

        override fun connect(id: ProfileId) = Unit

        override fun create(id: ProfileId) = Unit

        override fun remove(id: ProfileId) = Unit

        override fun scanPairing() { calls += "scan" }

        override fun pastePairing(text: String?) { calls += "paste" }

        override fun takePairing(): PairingFill? = fill.also { fill = null }
    }

    @Test
    fun `B-PROTO-064 the server form offers Scan code and fills itself from a pairing code`() = runComposeUiTest {
        val read = PairingText.parse("""{"urls":["https://box.tail.ts.net:4096"],"username":"me","password":"pw"}""")
        val actions = FormActions(PairingFill.of(read))
        setContent { Themed { ServersScreen(ServersUi(pairingArrival = 1), actions) } }
        onNodeWithText("Filled in from the pairing code. Check it, then save.").assertExists()
        onNodeWithText("https://box.tail.ts.net:4096").assertExists()
        onNodeWithText("Pairing code").assertExists()
        onNodeWithText("Scan code").performClick()
        onNodeWithText("Save and connect").performScrollTo().performClick()
        assertEquals(listOf("scan", "save:https://box.tail.ts.net:4096:me:pw"), actions.calls)
    }

    @Test
    fun `B-SEC-157 a share link fills only the address and asks for the password`() = runComposeUiTest {
        val actions = FormActions(PairingFill.of(PairingRead.Link("https://box.tail1234.ts.net")))
        setContent { Themed { ServersScreen(ServersUi(pairingArrival = 1), actions) } }
        onNodeWithText("Filled in the address from the link. Add the password, then save.").assertExists()
        onNodeWithText("https://box.tail1234.ts.net").assertExists()
    }
}
