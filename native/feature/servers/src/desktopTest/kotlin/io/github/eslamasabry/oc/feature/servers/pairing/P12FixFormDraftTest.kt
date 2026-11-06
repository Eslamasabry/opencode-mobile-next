package io.github.eslamasabry.oc.feature.servers.pairing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.servers.discovery.LanAccess
import io.github.eslamasabry.oc.feature.servers.discovery.LanFinding
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorInput
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorStep
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorUi
import io.github.eslamasabry.oc.feature.servers.ui.ServerEditorScreen
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

/**
 * P12-FIX-PREVIEWQR (Codex review previewqr #3): opening Scan code puts a page on top of the server form, which leaves
 * composition; closing the scanner must find the unsaved draft as it was. The form is the server editor now (its pairing
 * doors moved there with the Flutter port); it hands its draft to the ViewModel (keepDraft) and takes it back when it
 * returns (takeDraft), the password in memory only.
 */
@OptIn(ExperimentalTestApi::class)
class P12FixFormDraftTest {
    private var previous: Locale = Locale.getDefault()

    @BeforeTest fun before() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest fun after() = Locale.setDefault(previous)

    /** Holds the draft like the ViewModel does: memory only. */
    private class DraftActions : ServerEditorActions {
        var kept: ServerEditorInput? = null
        val saves = mutableListOf<String>()

        override fun keepDraft(input: ServerEditorInput) {
            kept = input
        }

        override fun takeDraft(): ServerEditorInput? = kept

        override fun refreshTailscale() {}

        override fun openTailscale() {}

        override fun installTailscale() {}

        override fun back(dirty: Boolean) {}

        override fun discard() {}

        override fun keepEditing() {}

        override fun step(step: ServerEditorStep) {}

        override fun changed() {}

        override fun check(input: ServerEditorInput) {}

        override fun save(input: ServerEditorInput, anyway: Boolean) {
            saves += "${input.name}|${input.address}|${input.username}|${input.secret?.reveal()}"
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

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "en", stillFrame = true) {
                Box(Modifier.width(412.dp)) { content() }
            }
        }
    }

    @Test
    fun `B-PROTO-069 closing the scanner finds the unsaved server draft as it was`() = runComposeUiTest {
        val actions = DraftActions()
        var formShown by mutableStateOf(true)
        val ui = ServerEditorUi(step = ServerEditorStep.Connect, newServer = false, connectOnSave = false)
        setContent {
            Themed {
                val pages = rememberSaveableStateHolder()
                if (formShown) pages.SaveableStateProvider("server-editor") { ServerEditorScreen(ui, actions) }
            }
        }
        fun field(label: String) = onNode(hasSetTextAction() and hasContentDescription(label))
        field("Display name (optional)").performScrollTo().performTextInput("laptop")
        field("Server URL").performScrollTo().performTextInput("https://box.example:4096")
        field("Server password").performScrollTo().performTextInput("pw")
        waitForIdle()

        formShown = false // Scan code: the scanner page is on top, the form leaves composition
        waitForIdle()
        formShown = true // the scanner closes
        waitForIdle()

        onNodeWithText("laptop").assertExists()
        onNodeWithText("https://box.example:4096").assertExists()
        onNodeWithText("Save changes").performClick()
        assertEquals(listOf("laptop|https://box.example:4096|opencode|pw"), actions.saves)
    }
}
