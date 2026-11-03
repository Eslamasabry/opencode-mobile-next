package io.github.eslamasabry.oc.feature.about.managespace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Clear storage guard page, found by the words a person reads. */
@OptIn(ExperimentalTestApi::class)
class ManageSpaceScreenTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(previousLocale)

    private class Recording : ManageSpaceActions {
        val calls = mutableListOf<String>()

        override fun onClose() { calls += "close" }

        override fun onClearCache() { calls += "cache" }

        override fun onDeleteAll() { calls += "deleteAll" }

        override fun onConfirmDelete() { calls += "confirm" }

        override fun onCancelDelete() { calls += "cancel" }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "en", stillFrame = true) {
            KitSheetHost {
                Box(Modifier.size(412.dp, 1400.dp).background(LocalKitColors.current.ground)) { content() }
            }
        }
    }

    private fun ComposeUiTest.page(state: ManageSpaceUi, actions: Recording = Recording()): Recording {
        setContent { Themed { ManageSpaceScreen(state, actions) } }
        waitForIdle()
        return actions
    }

    private fun ComposeUiTest.shows(text: String): Boolean = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `B-ANDR-035 the guard page says what clearing deletes and what stays`() = runComposeUiTest {
        val actions = page(ManageSpaceUi(facts = ManageSpaceFacts(cacheBytes = 3L * 1024 * 1024, savedServers = 2)))
        onNodeWithText("Clear this app's storage").assertExists()
        onNodeWithText("Clearing deletes everything OpenCode Mobile keeps on this phone, and it cannot be undone.").assertExists()
        onNodeWithText("Frees 3.0 MB. Projects, servers and settings stay.").assertExists()
        onNodeWithText("2 saved servers").performScrollTo().assertExists()
        onNodeWithText("Anything you pushed to git").performScrollTo().assertExists()
        assertTrue(!shows("Ubuntu, OpenCode, its sign-ins and its conversations"), "no built-in Linux in this build")
        onNodeWithText("Clear the app's cache only").performScrollTo().performClick()
        onNodeWithText("Delete everything").performScrollTo().performClick()
        assertEquals(listOf("cache", "deleteAll"), actions.calls)
    }

    @Test
    fun `B-ANDR-035 delete everything asks first and names the loss`() = runComposeUiTest {
        val actions =
            page(ManageSpaceUi(facts = ManageSpaceFacts(cacheBytes = 0, savedServers = 1), confirmingDelete = true))
        onNodeWithText("Delete everything?").assertExists()
        onNodeWithText("OpenCode Mobile then starts again as if it were new. This cannot be undone.").assertExists()
        onNodeWithText("1 saved server and all settings").assertExists()
        onNodeWithText("Termux, your computers and anything pushed to git stay").assertExists()
        onAllNodesWithText("Delete everything").fetchSemanticsNodes().let { assertTrue(it.size >= 2) }
        onNodeWithText("Cancel").performClick()
        assertEquals(listOf("cancel"), actions.calls)
    }

    @Test
    fun `B-ANDR-035 while measuring the page says so`() = runComposeUiTest {
        page(ManageSpaceUi())
        onNodeWithContentDescription("Measuring what is stored…").assertExists()
    }
}
