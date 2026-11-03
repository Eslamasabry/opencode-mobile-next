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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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

/**
 * P12-FIX-PREVIEWQR (Codex review previewqr #3): opening Scan code puts a page on top of the server form, which leaves
 * composition; closing the scanner must find the unsaved draft as it was. Navigation keeps a page's saveable state
 * (`rememberSaveableStateHolderNavEntryDecorator`), which this test stands in for.
 */
@OptIn(ExperimentalTestApi::class)
class P12FixFormDraftTest {
    private var previous: Locale = Locale.getDefault()

    @BeforeTest fun before() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest fun after() = Locale.setDefault(previous)

    /** Holds the password like the ViewModel does: memory only. */
    private class DraftActions : ServersActions {
        val saves = mutableListOf<String>()
        private var held = ""

        override fun back() = Unit

        override fun allowCleartext(allowed: Boolean, address: String) = Unit

        override fun save(name: String, address: String, username: String, password: String) {
            saves += "$name|$address|$username|$password"
        }

        override fun connect(id: ProfileId) = Unit

        override fun create(id: ProfileId) = Unit

        override fun remove(id: ProfileId) = Unit

        override fun holdPassword(text: String) {
            held = text
        }

        override fun heldPassword(): String = held
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
        setContent {
            Themed {
                val pages = rememberSaveableStateHolder()
                if (formShown) pages.SaveableStateProvider("servers") { ServersScreen(ServersUi(), actions) }
            }
        }
        val fields = onAllNodes(hasSetTextAction())
        // Pairing code, Name, Address, Username, Password: the order on screen.
        fields[1].performTextInput("laptop")
        fields[2].performTextInput("https://box.example:4096")
        fields[4].performTextInput("pw")
        waitForIdle()

        formShown = false // Scan code: the scanner page is on top, the form leaves composition
        waitForIdle()
        formShown = true // the scanner closes
        waitForIdle()

        onNodeWithText("laptop").assertExists()
        onNodeWithText("https://box.example:4096").assertExists()
        onAllNodesWithText("Save and connect")[0].performScrollTo().performClick()
        assertEquals(listOf("laptop|https://box.example:4096|opencode|pw"), actions.saves)
    }
}
