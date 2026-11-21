package io.github.eslamasabry.oc.feature.chats

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationProject
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Layout B (owner, device): where it runs sits under the title, who works on it is the box's foot, and with the keyboard
 * up (360 x 780 with about 303 dp of keyboard leaves 477 dp for the page) the box is never clipped: it grows with the
 * words up to the room between the where-line and Start, then its words scroll inside it; Start is whole above the keyboard.
 */
@OptIn(ExperimentalTestApi::class)
class NewConversationLayoutBTest {
    private class Recording : NewConversationActions {
        val sheets = mutableListOf<NewConversationSheet?>()
        override fun back() {}
        override fun addServer() {}
        override fun chooseServer(profile: ProfileId) {}
        override fun draftChanged(text: String) {}
        override fun chooseDirectory(directory: String) {}
        override fun chooseModel(model: ModelRef?) {}
        override fun chooseAgent(agent: String?) {}
        override fun separateCopy(on: Boolean) {}
        override fun sheet(sheet: NewConversationSheet?) { sheets += sheet }
        override fun start() {}
        override fun import(conversation: ImportableConversation) {}
        override fun retryLoad() {}
    }

    private val p1 = ProfileId("p1")
    private val dir = "/root/projects/docsdemo"

    private fun ui(lines: Int) =
        NewConversationUi(
            servers = listOf(NewConversationServer(p1, "This phone", connected = true)),
            profile = p1,
            setup = NewConversationSetup(dir, listOf(NewConversationProject(dir, "docsdemo", false)), emptyList(), emptyList()),
            directory = dir,
            text = (1..lines).joinToString("\n") { "Create a file named agnote py printing line $it" },
        )

    private fun check(width: Int, visibleHeight: Int, lines: Int, fontScale: Float = 1f, actions: Recording = Recording()) =
        kitUiTest(width, visibleHeight) {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                        NewConversationScreen(ui(lines), actions)
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            val where = onNodeWithTag("kit-where-line").getBoundsInRoot()
            val box = onNodeWithTag("chats-new-field").getBoundsInRoot()
            val chip = onNodeWithTag("kit-agent-model-chip").getBoundsInRoot()
            val start = onNodeWithTag("kit-button-hero").getBoundsInRoot()
            val bottom = visibleHeight.dp
            assertTrue(box.top >= where.bottom, "the box starts under the where-line: ${box.top} vs ${where.bottom}")
            assertTrue(box.bottom <= start.top, "the box ends above Start: ${box.bottom} vs ${start.top}")
            assertTrue(start.bottom <= bottom, "Start is whole above the keyboard: ${start.bottom} vs $bottom")
            assertTrue(start.top >= 0.dp && box.left >= 0.dp && box.right <= width.dp, "nothing is cut at the sides")
            assertTrue(chip.top >= box.top && chip.bottom <= box.bottom, "the foot chip is inside the box, whole")
        }

    @Test fun `I147-FIRST-RUN layout B a four-line prompt with the keyboard up is whole`() = check(360, 477, 4)

    @Test fun `I147-FIRST-RUN layout B a twelve-line prompt scrolls inside the box, Start stays whole`() = check(360, 477, 12)

    @Test fun `I147-FIRST-RUN layout B at 320 dp wide`() = check(320, 477, 12)

    @Test fun `I147-FIRST-RUN layout B at text size 1_3`() = check(360, 477, 12, fontScale = 1.3f)

    @Test
    fun `I147-FIRST-RUN layout B the where-line opens the project sheet and the box chip the agent sheet`() {
        val actions = Recording()
        kitUiTest(360, 780) {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(ui(1), actions)
                }
            }
            waitForIdle()
            onNodeWithTag("kit-where-line").performClick()
            onNodeWithTag("kit-agent-model-chip").performClick()
            assertEquals(listOf<NewConversationSheet?>(NewConversationSheet.Projects, NewConversationSheet.Agents), actions.sheets)
        }
    }
}
