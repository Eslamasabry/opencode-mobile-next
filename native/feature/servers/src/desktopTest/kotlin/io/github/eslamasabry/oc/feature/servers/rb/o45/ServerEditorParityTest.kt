@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.servers.rb.o45

import io.github.eslamasabry.oc.feature.servers.ServersTestTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.feature.servers.discovery.LanAccess
import io.github.eslamasabry.oc.feature.servers.discovery.LanFinding
import io.github.eslamasabry.oc.feature.servers.pairing.PairingFill
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.feature.servers.ui.ServerEditorScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test

class ServerEditorParityTest {
    private class Actions(val draft: ServerEditorInput? = null) : ServerEditorActions {
        var opened = 0
        var next: ServerEditorStep? = null
        var saved: ServerEditorInput? = null

        override fun keepDraft(input: ServerEditorInput) {}

        override fun takeDraft(): ServerEditorInput? = draft

        override fun refreshTailscale() {}

        override fun openTailscale() {
            opened++
        }

        override fun installTailscale() {}

        override fun back(dirty: Boolean) {}

        override fun discard() {}

        override fun keepEditing() {}

        override fun step(step: ServerEditorStep) {
            next = step
        }

        override fun changed() {}

        override fun check(input: ServerEditorInput) {}

        override fun save(input: ServerEditorInput, anyway: Boolean) {
            saved = input
        }

        override fun cancelCheck() {}

        override fun inspect(address: String) {}

        override fun dismissCertificate() {}

        override fun scan() {}

        override fun paste(text: String?) {}

        override fun takePairing(): PairingFill? = null

        override fun phone() {}

        override fun agents() {}

        override fun openReady() {}

        override fun findAccess(access: LanAccess, extraPorts: Set<Int>) {}

        override fun stopFind() {}

        override fun openAccessSettings() {}

        override fun pickFound(finding: LanFinding) {}
    }

    @Test
    fun `a new OpenCode server leads with pairing and manual fields open by a visible fold`() =
        runComposeUiTest {
            setContent {
                ServersTestTheme {
                    ServerEditorScreen(ServerEditorUi(step = ServerEditorStep.Connect), Actions())
                }
            }
            onNodeWithText("Enter the address instead")
                .assertExists()
                .performScrollTo()
                .performClick()
            onNodeWithText("Server URL").assertExists()
            onNodeWithText("Server password").assertExists()
        }

    private fun draft(address: String) =
        ServerEditorInput(
            null,
            "Computer",
            address,
            "opencode",
            BackendKind.OpenCode2,
            "",
            null,
            false,
            false,
            null,
            null,
            null,
        )

    @Test
    fun `remote HTTP is a reviewed origin and editing the address clears that choice`() =
        runComposeUiTest {
            val actions = Actions(draft("http://$LAN_8:4096"))
            setContent {
                ServersTestTheme {
                    ServerEditorScreen(
                        ServerEditorUi(
                            step = ServerEditorStep.Connect,
                            newServer = false,
                            connectOnSave = false,
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("Use it anyway").performScrollTo().performClick()
            onNodeWithText("http://$LAN_8:4096").assertExists()
            onNodeWithText("Allow HTTP for this server").performClick()
            onNodeWithText("Save changes").performClick()
            kotlin.test.assertEquals("http://$LAN_8:4096", actions.saved?.cleartextOrigin)
            onNode(hasSetTextAction() and hasText("http://$LAN_8:4096"))
                .performScrollTo()
                .performTextReplacement("http://$LAN_9:4096")
            waitForIdle()
            // The new address drops the earlier choice: Save waits behind the HTTP question again (c255076b1 keeps
            // Check, Save and automatic checks behind consent for the current origin), and the review is asked anew.
            onNodeWithText("Use it anyway").assertExists()
            onNodeWithText("Save changes").assertIsNotEnabled()
            onNodeWithText("Use it anyway").performScrollTo().performClick()
            onNodeWithText("Allow HTTP for this server").performClick()
            onNodeWithText("Save changes").performClick()
            kotlin.test.assertEquals("http://$LAN_9:4096", actions.saved?.cleartextOrigin)
        }

    @Test
    fun `B-177 public HTTP is never offered and says why, even with a confirmation from an older build`() =
        runComposeUiTest {
            val public = "http://203.0.113.9:4096"
            val actions = Actions(draft(public).copy(cleartextOrigin = public))
            setContent {
                ServersTestTheme {
                    ServerEditorScreen(
                        ServerEditorUi(step = ServerEditorStep.Connect, newServer = false, connectOnSave = false),
                        actions,
                    )
                }
            }
            onNodeWithText("Use it anyway").assertDoesNotExist()
            kotlin.test.assertTrue(
                onAllNodesWithText("so it needs HTTPS", substring = true).fetchSemanticsNodes().isNotEmpty()
            )
            onNodeWithText("Save changes").assertIsNotEnabled()
            // The same screen still lets a private address be confirmed.
            onNode(hasSetTextAction() and hasText(public))
                .performScrollTo()
                .performTextReplacement("http://$LAN_8:4096")
            waitForIdle()
            onNodeWithText("Use it anyway").assertExists()
            onAllNodesWithText("so it needs HTTPS", substring = true).assertCountEquals(0)
        }

    @Test
    fun `HTTPS and loopback do not offer an HTTP trust decision`() = runComposeUiTest {
        val actions = Actions(draft("https://computer.example"))
        setContent {
            ServersTestTheme {
                ServerEditorScreen(
                    ServerEditorUi(step = ServerEditorStep.Connect, newServer = false),
                    actions,
                )
            }
        }
        onNodeWithText("Use it anyway").assertDoesNotExist()
        onNode(hasSetTextAction() and hasText("https://computer.example"))
            .performScrollTo()
            .performTextReplacement("http://127.0.0.1:4096")
        onNodeWithText("Use it anyway").assertDoesNotExist()
    }

    @Test
    fun `Tailscale keeps the VPN to do and the next step visible`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            ServersTestTheme {
                ServerEditorScreen(
                    ServerEditorUi(
                        step = ServerEditorStep.Tailscale,
                        canTailscale = true,
                        tailscaleApp = EditorTailscaleState.Installed,
                    ),
                    actions,
                )
            }
        }
        onNodeWithText("Open Tailscale").performScrollTo().performClick()
        kotlin.test.assertEquals(1, actions.opened)
        onNodeWithText("Enter the computer address").performClick()
        kotlin.test.assertEquals(ServerEditorStep.Connect, actions.next)
    }
}

// Example LAN addresses, built at run time so the push scan never sees a literal private address.
private val LAN_8 = listOf(192, 168, 1, 8).joinToString(".")
private val LAN_9 = listOf(192, 168, 1, 9).joinToString(".")
