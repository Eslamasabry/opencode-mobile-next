package io.github.eslamasabry.oc.feature.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.feature.servers.discovery.LanAccess
import io.github.eslamasabry.oc.feature.servers.discovery.LanFinding
import io.github.eslamasabry.oc.feature.servers.pairing.PairingFill
import io.github.eslamasabry.oc.feature.servers.pairing.PairingProblem
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorInput
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorRecord
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorStep
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorUi
import io.github.eslamasabry.oc.feature.servers.ui.ServerEditorScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The wide server form is the phone form in a frame (B-SRV-305..308). Each test shows the same state at 360 dp and at
 * 1280 dp and says what a person sees; nothing here types or reads a secret.
 */
@OptIn(ExperimentalTestApi::class, RedactorApi::class)
class ServersEditorWrapTest {
    private var previous: Locale = Locale.getDefault()

    @BeforeTest fun before() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest fun after() = Locale.setDefault(previous)

    private class Recorder : ServerEditorActions {
        val calls = mutableListOf<String>()
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
        override fun check(input: ServerEditorInput) { calls += "check" }
        override fun save(input: ServerEditorInput, anyway: Boolean) { calls += "save anyway=$anyway" }
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

    private val record =
        ServerEditorRecord(
            id = ProfileId("home"),
            name = "Home laptop",
            address = "https://laptop.example.net:4096",
            username = "opencode",
            backend = BackendKind.OpenCode2,
            directory = "/home/dev/projects/oc_app",
            hasSecret = true,
            needsSecret = false,
            tailscale = false,
            cleartextOrigin = null,
            certificatePin = null,
            certificateOrigin = null,
            serverVersion = "2.2.6",
        )

    private fun showAt(widthDp: Int, ui: ServerEditorUi, actions: ServerEditorActions, block: ComposeUiTest.() -> Unit) {
        runDesktopComposeUiTest(widthDp, 800) {
            setContent {
                CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
                    KitTheme(
                        appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                ServerEditorScreen(ui, actions)
                            }
                        }
                    }
                }
            }
            waitForIdle()
            block()
        }
    }

    private fun ComposeUiTest.has(text: String): Boolean =
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.tap(text: String) {
        val node = onNodeWithText(text)
        runCatching { node.performScrollTo() }
        node.performClick()
        waitForIdle()
    }

    /** What a person can find on the page, by words, at one width. */
    private fun seen(width: Int, ui: ServerEditorUi, words: List<String>): Map<String, Boolean> {
        var out = emptyMap<String, Boolean>()
        showAt(width, ui, Recorder()) { out = words.associateWith { has(it) } }
        return out
    }

    private val pairingNotice = "That is not a pairing code"

    @Test
    fun `B-SRV-305 the wide form keeps Scan and Paste for an OpenCode server`() {
        val words = listOf("Scan code", "Paste code", "Enter the address instead", "Server URL")
        val cases =
            listOf(
                ServerEditorUi(step = ServerEditorStep.Connect, newServer = true, canScan = true),
                ServerEditorUi(step = ServerEditorStep.Connect, newServer = false, connectOnSave = false, record = record, canScan = true),
            )
        for (ui in cases) {
            val phone = seen(360, ui, words)
            val wide = seen(1280, ui, words)
            assertEquals(phone, wide, "the wide form shows what the phone form shows (new=${ui.newServer})")
        }
        // and the pairing doors really are there for a new server, on both
        val fresh = seen(1280, cases[0], words)
        assertEquals(true, fresh["Scan code"], "Scan code on the wide form")
        assertEquals(true, fresh["Paste code"], "Paste code on the wide form")
    }

    @Test
    fun `B-SRV-306 the wide form keeps the pairing-problem notice and the fold back to pairing`() {
        val ui =
            ServerEditorUi(step = ServerEditorStep.Connect, newServer = true, canScan = true, pairingProblem = PairingProblem.NotJson)
        for (width in listOf(360, 1280)) {
            showAt(width, ui, Recorder()) {
                assertEquals(true, has(pairingNotice), "the pairing notice at $width dp")
                tap("Enter the address instead")
                assertEquals(true, has("Server URL"), "the manual fields open at $width dp")
                assertEquals(true, has("Enter the address instead"), "the fold back to pairing stays at $width dp")
                tap("Enter the address instead")
                assertEquals(true, has("Paste code"), "folding back shows the pairing doors at $width dp")
            }
        }
    }

    @Test
    fun `B-SRV-307 a failure after a paste is said on the wide form`() {
        val failure = Failure.Network(NetworkWhy.Dns, Redacted("test"))
        val words = listOf("Could not reach this server", "Check connection", "Server URL")
        val ui =
            ServerEditorUi(
                step = ServerEditorStep.Connect,
                newServer = true,
                canScan = true,
                fillGeneration = 1,
                failure = failure,
                failedCheck = true,
            )
        val phone = seen(360, ui, words)
        val wide = seen(1280, ui, words)
        assertEquals(true, phone["Could not reach this server"], "the phone says the failure")
        assertEquals(phone, wide, "the wide form says it too")
    }

    @Test
    fun `B-SRV-308 phone-width form Check and Save call the same actions`() {
        val ui =
            ServerEditorUi(step = ServerEditorStep.Connect, newServer = false, connectOnSave = false, record = record)
        for (width in listOf(360, 1280)) {
            val actions = Recorder()
            showAt(width, ui, actions) {
                tap("Check connection")
                tap("Save changes")
            }
            assertEquals(listOf("check", "save anyway=false"), actions.calls, "calls at $width dp")
        }
    }
}
