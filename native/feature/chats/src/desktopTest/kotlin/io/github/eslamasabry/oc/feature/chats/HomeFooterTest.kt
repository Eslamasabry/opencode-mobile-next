package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The home's foot: "All conversations" and the round + are one bar under the list, margin to margin. The list ends
 * above it (its last row is whole at the end, and rows never pass under the button), and without the + (the server is
 * still opening) the link takes the whole line (review 2026-11-13 #11, set-C C-003 / C-026 / C-080).
 */
@OptIn(ExperimentalTestApi::class)
class HomeFooterTest {
    private val savedLocale: Locale = Locale.getDefault()

    @AfterTest fun restoreLocale() = Locale.setDefault(savedLocale)

    private fun ComposeUiTest.home(state: ChatsUi) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                ChatEnvironment {
                    KitTheme(
                        appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off, style = Style.Solid),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { ChatsScreen(state, RecordingChatsActions()) }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun has(text: String) =
        SemanticsMatcher("content description contains $text") { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { text in it } == true }

    private val list = SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)

    private fun ComposeUiTest.toTheEnd() {
        repeat(4) { onNode(list).performTouchInput { swipeUp() } }
        waitForIdle()
    }

    @Test
    fun `the list ends above the foot bar and its last row is whole at the end`() =
        runDesktopComposeUiTest(360, 640) {
            home(mixed(extra = 12))
            toTheEnd()
            val foot = onNodeWithTag("kit-screen-bottom").getBoundsInRoot()
            val viewport = onNode(list).getBoundsInRoot()
            assertTrue(viewport.bottom <= foot.top + 0.5.dp, "the list $viewport runs under the foot bar $foot")
            val rows = onAllRowsBottoms()
            assertTrue(rows.isNotEmpty() && rows.max() <= foot.top.value + 0.5f, "a row ends at ${rows.maxOrNull()}, the foot bar starts at ${foot.top}")
        }

    private fun ComposeUiTest.onAllRowsBottoms(): List<Float> =
        onAllNodes(has("Older conversation")).fetchSemanticsNodes().map { it.boundsInRoot.bottom }

    @Test
    fun `All conversations and the plus are one bar from margin to margin`() =
        runDesktopComposeUiTest(360, 640) {
            home(mixed(extra = 12))
            toTheEnd()
            val foot = onNodeWithTag("kit-screen-bottom").getBoundsInRoot()
            val link = onNodeWithTag("kit-panel-link-row", useUnmergedTree = true).getBoundsInRoot()
            val plus = onNode(has("New conversation")).getBoundsInRoot()
            assertTrue(link.top >= foot.top && link.bottom <= foot.bottom, "the link $link lies in the foot bar $foot")
            assertTrue(plus.top >= foot.top && plus.bottom <= foot.bottom, "the plus $plus lies in the foot bar $foot")
            assertTrue(link.left.value in 15f..17f, "the bar starts on the page margin: ${link.left}")
            assertTrue(plus.right.value in 343f..345f, "the plus ends on the page margin: ${plus.right}")
            assertTrue(plus.left - link.right in 8.dp..16.dp, "the link ends ${link.right}, the plus starts ${plus.left}")
        }

    @Test
    fun `while the server opens the plus waits and the link takes the whole line`() =
        runDesktopComposeUiTest(360, 640) {
            val ui = mixed(extra = 12)
            home(ui.copy(home = ui.home.copy(opening = ChatsOpeningUi(ChatsOpeningPhase.Connecting, "This phone"))))
            toTheEnd()
            val link = onNodeWithTag("kit-panel-link-row", useUnmergedTree = true).getBoundsInRoot()
            assertTrue(link.left.value in 15f..17f && link.right.value in 343f..345f, "the link spans margin to margin: $link")
        }
}
