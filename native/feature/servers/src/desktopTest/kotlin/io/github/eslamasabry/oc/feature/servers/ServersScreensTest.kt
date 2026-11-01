package io.github.eslamasabry.oc.feature.servers

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure.Net
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.feature.servers.presentation.EditorMode
import io.github.eslamasabry.oc.feature.servers.presentation.EditorViewModel
import io.github.eslamasabry.oc.feature.servers.presentation.EntryKind
import io.github.eslamasabry.oc.feature.servers.presentation.ProbeAnswer
import io.github.eslamasabry.oc.feature.servers.presentation.SavedServer
import io.github.eslamasabry.oc.feature.servers.presentation.ServerPageViewModel
import io.github.eslamasabry.oc.feature.servers.presentation.ServerStatus
import io.github.eslamasabry.oc.feature.servers.presentation.ServerSwitcherViewModel
import io.github.eslamasabry.oc.feature.servers.presentation.ServersEnvironment
import io.github.eslamasabry.oc.feature.servers.presentation.ServersViewModel
import io.github.eslamasabry.oc.feature.servers.ui.EditorScreen
import io.github.eslamasabry.oc.feature.servers.ui.ServerPageScreen
import io.github.eslamasabry.oc.feature.servers.ui.ServerSwitcherContent
import io.github.eslamasabry.oc.feature.servers.ui.ServersScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Screen tests by visible text (plan 14): the real ViewModels over the fake port, drawn with kit parts. */
@OptIn(ExperimentalTestApi::class)
class ServersScreensTest {
    private val env = ServersEnvironment(supportsTermux = true)
    private val laptop = profile("lp", "Laptop", ServerOrigin("https", "desk.example", 4096))

    private fun editor(port: FakeServersPort, nav: RecordingServersNavigator, mode: EditorMode = EditorMode.Add) =
        EditorViewModel(mode, false, port, RulesAddressPolicy, env, nav, immediateScope())

    @Test
    fun `E2E-01 with nothing saved the welcome says how to pair and offers Add server`() =
        runComposeUiTest {
            val nav = RecordingServersNavigator()
            val vm = ServersViewModel(FakeServersPort(), RulesAddressPolicy, env, nav, immediateScope())
            setContent { ServersHost { val s by vm.state.collectAsState(); ServersScreen(s, vm) } }
            onNodeWithText("On your computer run “opencode2 pair”, then paste or scan the code it prints.").assertIsDisplayed()
            onNodeWithText("Add server").performClick()
            assertEquals(listOf("editor ${EditorMode.Add} focus=false"), nav.calls)
        }

    @OptIn(RedactorApi::class)
    @Test
    fun `B-NAV-007 a rejected password reads as one sentence with Update password, and the raw text only under Technical details`() =
        runComposeUiTest {
            val port = FakeServersPort(listOf(SavedServer(laptop, ServerStatus.NotConnected, false)))
            port.fail(ProfileId("lp"), Net.SignInNeeded(Net.SignInNeeded.Why.PasswordRejected, Redacted("HTTP 401")))
            val nav = RecordingServersNavigator()
            val vm = ServersViewModel(port, RulesAddressPolicy, env, nav, immediateScope())
            setContent { ServersHost { val s by vm.state.collectAsState(); ServersScreen(s, vm) } }
            onNodeWithText("Password rejected").assertIsDisplayed()
            assertEquals(0, onAllNodesWithText("HTTP 401", substring = true).fetchSemanticsNodes().size)
            onNodeWithText("Technical details").performClick()
            onNodeWithText("HTTP 401").assertIsDisplayed()
            onNodeWithText("Update password").performClick()
            assertEquals(listOf("editor ${EditorMode.Credentials(ProfileId("lp"))} focus=true"), nav.calls)
        }

    @Test
    fun `B-SEC-124 a private plain-HTTP address shows the HTTP question and is dialed only after Allow`() =
        runComposeUiTest {
            val port = FakeServersPort()
            val vm = editor(port, RecordingServersNavigator())
            vm.useEntry(EntryKind.Address)
            setContent { ServersHost { val s by vm.state.collectAsState(); EditorScreen(s, vm) } }
            onNodeWithTag("Server address").performTextInput("http://192.168.1.20:4096")
            onNodeWithText("Test connection").performClick()
            waitForIdle()
            onNodeWithText("Use unencrypted HTTP?").assertIsDisplayed()
            onNodeWithText("This private network or Tailscale address uses plain HTTP.", substring = true).assertIsDisplayed()
            assertTrue(port.probes.isEmpty())
            onNodeWithText("Allow HTTP for this server").performClick()
            waitForIdle()
            assertEquals(listOf(ServerOrigin("http", "192.168.1.20", 4096)), port.probes.map { it.origin })
            onNodeWithText("Connection verified. Save and connect to continue.").assertIsDisplayed()
        }

    @Test
    fun `B-SEC-124 a public plain-HTTP address gets the stronger sentence and Cancel sends nothing`() =
        runComposeUiTest {
            val port = FakeServersPort()
            val vm = editor(port, RecordingServersNavigator())
            vm.useEntry(EntryKind.Address)
            setContent { ServersHost { val s by vm.state.collectAsState(); EditorScreen(s, vm) } }
            onNodeWithTag("Server address").performTextInput("http://192.0.2.20:4096")
            onNodeWithText("Test connection").performClick()
            waitForIdle()
            onNodeWithText("At this public HTTP address", substring = true).assertIsDisplayed()
            onNodeWithText("Cancel").performClick()
            waitForIdle()
            assertTrue(port.probes.isEmpty())
        }

    @Test
    fun `B-NAV-008 an untrusted certificate shows its fingerprint before Trust this fingerprint pins it`() =
        runComposeUiTest {
            val port = FakeServersPort()
            port.probeAnswer = { t ->
                if (t.pinnedSha256 == null) Outcome.Failed(Net.TrustRefused(Net.TrustRefused.Why.CertificateUntrusted)) else Outcome.Ok(ProbeAnswer("2.1.1"))
            }
            val vm = editor(port, RecordingServersNavigator())
            vm.useEntry(EntryKind.Address)
            setContent { ServersHost { val s by vm.state.collectAsState(); EditorScreen(s, vm) } }
            onNodeWithTag("Server address").performTextInput("https://desk.example:4097")
            onNodeWithText("Test connection").performClick()
            waitForIdle()
            onNodeWithText("Certificate not trusted").assertIsDisplayed()
            onNodeWithText("Re-check certificate").performClick()
            waitForIdle()
            onNodeWithText("Trust this certificate?").assertIsDisplayed()
            onNodeWithText("AB:12:CD:34", substring = true).assertIsDisplayed()
            onNodeWithText("Trust this fingerprint").performClick()
            waitForIdle()
            assertEquals(FakeServersPort.SHA, port.probes.last().pinnedSha256)
        }

    @Test
    fun `B-SEC-107 a pasted pairing code connects, saves and lands without showing the password`() =
        runComposeUiTest {
            val port = FakeServersPort()
            val nav = RecordingServersNavigator()
            val vm = editor(port, nav)
            setContent { ServersHost { val s by vm.state.collectAsState(); EditorScreen(s, vm) } }
            onNodeWithTag("Paste pairing code")
                .performTextInput("""{"urls":["https://desk.example:4097"],"username":"opencode","password":"fixture-pw"}""")
            onNodeWithText("Test connection").performClick() // the primary button before anything answered
            waitForIdle()
            assertEquals(listOf("land"), nav.calls)
            assertEquals("fixture-pw", port.saved.single().password?.reveal())
        }

    @Test
    fun `B-SEC-108 a pairing code where nothing answers shows each address with its own reason`() =
        runComposeUiTest {
            val port = FakeServersPort()
            port.probeAnswer = { t ->
                if (t.origin.host == "a.example") Outcome.Failed(Net.Unreachable(Net.Unreachable.Why.Timeout))
                else Outcome.Failed(Net.Unreachable(Net.Unreachable.Why.Dns))
            }
            val vm = editor(port, RecordingServersNavigator())
            setContent { ServersHost { val s by vm.state.collectAsState(); EditorScreen(s, vm) } }
            onNodeWithTag("Paste pairing code")
                .performTextInput("""{"urls":["https://a.example:4097","https://b.example:4097"],"password":"x"}""")
            onNodeWithText("Test connection").performClick()
            waitForIdle()
            onNodeWithText("https://a.example:4097").assertIsDisplayed()
            onNodeWithText("The connection timed out. Check the address, and that the server is reachable from this phone.").assertIsDisplayed()
            onNodeWithText("That host name could not be found. Check the address spelling.").assertIsDisplayed()
        }

    @Test
    fun `B-NAV-041 the switcher names same-host servers by port and Add a server opens the editor`() =
        runComposeUiTest {
            val beta = profile("l2", "Laptop beta", ServerOrigin("https", "desk.example", 4097))
            val port = FakeServersPort(listOf(SavedServer(laptop, ServerStatus.Connected, true), SavedServer(beta, ServerStatus.NotConnected, false)))
            val nav = RecordingServersNavigator()
            val vm = ServerSwitcherViewModel(port, nav, immediateScope())
            setContent { ServersHost { val s by vm.state.collectAsState(); ServerSwitcherContent(s, vm) } }
            onNodeWithText("desk.example:4096 · Connected").assertIsDisplayed()
            onNodeWithText("desk.example:4097 · Not connected").assertIsDisplayed()
            onNodeWithText("Laptop details").assertIsDisplayed()
            onNodeWithText("Add a server").performClick()
            assertEquals(listOf("back", "editor ${EditorMode.Add} focus=false"), nav.calls)
        }

    @Test
    fun `E2E-01 the server page names its server in every action and Disconnect resets to Servers`() =
        runComposeUiTest {
            val port = FakeServersPort(listOf(SavedServer(laptop, ServerStatus.Connected, true)))
            val nav = RecordingServersNavigator()
            val vm = ServerPageViewModel(ProfileId("lp"), port, RulesAddressPolicy, env, nav, immediateScope())
            setContent { ServersHost { val s by vm.state.collectAsState(); s?.let { ServerPageScreen(it, vm) } } }
            onNodeWithText("Remove Laptop from this phone").assertIsDisplayed()
            onNodeWithText("Disconnect from Laptop").performClick()
            waitForIdle()
            assertEquals(listOf("resetToServers"), nav.calls)
        }
}
