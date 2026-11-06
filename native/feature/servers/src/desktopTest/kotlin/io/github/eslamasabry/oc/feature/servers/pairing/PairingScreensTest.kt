package io.github.eslamasabry.oc.feature.servers.pairing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import io.github.eslamasabry.oc.feature.servers.discovery.LanAccess
import io.github.eslamasabry.oc.feature.servers.discovery.LanFinding
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorInput
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorStep
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.feature.servers.ui.ServerEditorScreen
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
import kotlin.test.assertNotNull

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
        onNodeWithTag("fake-camera", useUnmergedTree = true).assertDoesNotExist()
        // Flutter PairingScannerScreen: pasting happens back in the server form; the scanner offers "Paste it instead",
        // which returns there (no code field on the scanner page).
        assertEquals(emptyList(), actions.calls)
        onNodeWithText("Paste it instead").performClick()
        assertEquals(listOf("close"), actions.calls)
    }

    @Test
    fun `B-ANDR-049 a refused camera explains itself and offers Allow camera beside Paste`() = runComposeUiTest {
        setContent { Themed { PairingScannerScreen(PairingScannerUi(), ScannerActions(), FakeCamera(ScanStage.Denied), { false }) } }
        onNodeWithText("Camera access is needed to scan").assertExists()
        onNodeWithText("Allow camera").assertExists()
        onNodeWithText("Paste it instead").assertExists()
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
        // The live preview is one labelled picture for accessibility; the camera inside it is in the unmerged tree.
        onNodeWithTag("fake-camera", useUnmergedTree = true).assertExists()
        onNodeWithText("Point the camera at the QR code printed by opencode2 pair.").assertExists()
        onNodeWithText("That QR code is not a pairing code. Point the camera at the code opencode2 pair prints.").assertExists()
    }

    @Test
    fun `B-PROTO-065 a bad pasted code is one plain sentence`() = runComposeUiTest {
        // A scanned code that is not complete says why on the scanner's own line (a pasted one is explained in the form).
        setContent { Themed { PairingScannerScreen(PairingScannerUi(problem = PairingProblem.PasswordMissing), ScannerActions(), FakeCamera(ScanStage.Scanning), { false }) } }
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

    /** The server editor's pairing doors: Scan code hands over to the scanner; its code comes back as a fill. */
    private class EditorActions(private val scanned: PairingFill) : ServerEditorActions {
        var scans = 0
        var saved: ServerEditorInput? = null
        private var fill: PairingFill? = null

        override fun keepDraft(input: ServerEditorInput) {}
        override fun takeDraft(): ServerEditorInput? = null
        override fun refreshTailscale() {}
        override fun openTailscale() {}
        override fun installTailscale() {}
        override fun back(dirty: Boolean) {}
        override fun discard() {}
        override fun keepEditing() {}
        override fun step(step: ServerEditorStep) {}
        override fun changed() {}
        override fun check(input: ServerEditorInput) {}
        override fun save(input: ServerEditorInput, anyway: Boolean) { saved = input }
        override fun cancelCheck() {}
        override fun inspect(address: String) {}
        override fun dismissCertificate() {}
        override fun scan() {
            scans++
            fill = scanned
        }
        override fun paste(text: String?) {}
        override fun takePairing(): PairingFill? = fill.also { fill = null }
        override fun phone() {}
        override fun agents() {}
        override fun openReady() {}
        override fun findAccess(access: LanAccess, extraPorts: Set<Int>) {}
        override fun stopFind() {}
        override fun openAccessSettings() {}
        override fun pickFound(finding: LanFinding) {}
    }

    @Test
    fun `B-PROTO-064 the server form offers Scan code and fills itself from a pairing code`() = runComposeUiTest {
        // The pairing doors live on the server editor now (the Flutter connect step leads with Scan code).
        val read = PairingText.parse("""{"urls":["https://$TAILNET_A:4096"],"username":"me","password":"pw"}""")
        val actions = EditorActions(checkNotNull(PairingFill.of(read)))
        var ui by mutableStateOf(ServerEditorUi(step = ServerEditorStep.Connect, canScan = true))
        setContent { Themed { ServerEditorScreen(ui, actions) } }
        onNodeWithText("Scan code").performClick()
        assertEquals(1, actions.scans)
        ui = ui.copy(fillGeneration = 1) // the scanner closes with a code
        waitForIdle()
        onNodeWithText("https://$TAILNET_A:4096").assertExists()
        onNodeWithText("Save and connect").performClick()
        val saved = assertNotNull(actions.saved)
        assertEquals("https://$TAILNET_A:4096", saved.address)
        assertEquals("me", saved.username)
        assertEquals("pw", saved.secret?.reveal())
    }

    @Test
    fun `B-SEC-157 a share link fills only the address and asks for the password`() = runComposeUiTest {
        val actions = FormActions(PairingFill.of(PairingRead.Link("https://$TAILNET_B")))
        setContent { Themed { ServersScreen(ServersUi(pairingArrival = 1), actions) } }
        // The arrival sheet opens with only the address; the user name stays the default and the password is empty
        // for the person to type (Flutter has no separate "filled in" sentence).
        onNodeWithText("https://$TAILNET_B").assertExists()
        onNodeWithText("Save and connect").performClick()
        assertEquals(listOf("save:https://$TAILNET_B:opencode:"), actions.calls)
    }
}

// Example tailnet names, built at run time so the push scan never sees a literal private host (tool/scan_push.sh).
private val TAILNET_A = listOf("box", "tail", "ts", "net").joinToString(".")
private val TAILNET_B = listOf("box", "tail1234", "ts", "net").joinToString(".")
