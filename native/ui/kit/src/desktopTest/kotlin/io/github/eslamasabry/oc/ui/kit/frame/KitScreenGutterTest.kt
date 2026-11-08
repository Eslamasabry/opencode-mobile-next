package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Audit C02 (2026-11-09): 14 screens drew buttons, labels, fields and terminal output against the window edge because
 * [KitScreen] padded only its bottom block and each body had to remember its own gutter. The frame now keeps the page
 * gutter for every body unless the screen opts into [KitScreenEdges.Bleed]; these tests hold that.
 */
@OptIn(ExperimentalTestApi::class)
class KitScreenGutterTest {
    private val width = 412
    private val gutter = 16.dp

    private fun close(expected: Dp, actual: Dp, what: String) =
        assertTrue(kotlin.math.abs((expected - actual).value) < 0.5f, "$what: expected $expected, was $actual")

    @Test
    fun `C02 a body keeps the page gutter on both sides by default, in both directions`() {
        for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            runDesktopComposeUiTest(width, 600) {
                setContent {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        val tag = if (direction == LayoutDirection.Rtl) "ar" else "en"
                        KitTheme(platform = FakeKitPlatform(), localeTag = tag) {
                            KitScreen(topBar = { KitTopBar("Terminal") }) {
                                Box(Modifier.fillMaxWidth().height(40.dp).testTag("body-part"))
                            }
                        }
                    }
                }
                waitForIdle()
                val screen = onNodeWithTag("kit-screen").getBoundsInRoot()
                val part = onNodeWithTag("body-part").getBoundsInRoot()
                close(screen.left + gutter, part.left, "$direction start edge")
                close(screen.right - gutter, part.right, "$direction end edge")
            }
        }
    }

    @Test
    fun `C02 a button placed straight in the body never touches the window edge`() =
        runDesktopComposeUiTest(width, 600) {
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    KitScreen(topBar = { KitTopBar("Project health") }) {
                        Column { KitButton("Refresh", {}, Modifier.testTag("refresh")) }
                    }
                }
            }
            waitForIdle()
            val button = onNodeWithTag("refresh").getBoundsInRoot()
            assertTrue(button.left >= gutter - 0.5.dp, "Refresh starts at ${button.left}")
        }

    @Test
    fun `C02 Bleed is an explicit opt-out that runs the body edge to edge`() =
        runDesktopComposeUiTest(width, 600) {
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    KitScreen(topBar = { KitTopBar("Chat") }, edges = KitScreenEdges.Bleed) {
                        Box(Modifier.fillMaxWidth().height(40.dp).testTag("body-part"))
                    }
                }
            }
            waitForIdle()
            val screen = onNodeWithTag("kit-screen").getBoundsInRoot()
            val part = onNodeWithTag("body-part").getBoundsInRoot()
            close(screen.left, part.left, "start edge")
            close(screen.right, part.right, "end edge")
        }

    @Test
    fun `C02 the gutter applies once - a nested screen and kit rows add none of their own`() =
        runDesktopComposeUiTest(width, 600) {
            var inner = Dp.Unspecified
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    KitScreen(topBar = { KitTopBar("Saved prompts") }) {
                        Column {
                            KitScreen {
                                inner = kitPageGutter()
                                Box(Modifier.fillMaxWidth().height(40.dp).testTag("nested-part"))
                            }
                            KitRow("Tasks", Modifier.testTag("row"))
                        }
                    }
                }
            }
            waitForIdle()
            val screen = onAllNodesWithTag("kit-screen", useUnmergedTree = true)[0].getBoundsInRoot()
            val nested = onNodeWithTag("nested-part").getBoundsInRoot()
            close(screen.left + gutter, nested.left, "nested body start")
            assertEquals(0.dp, inner, "a part inside the gutter keeps no second page gutter")
            val row = onNodeWithTag("row").getBoundsInRoot()
            close(screen.left + gutter, row.left, "the row sits on the rail")
        }
}
