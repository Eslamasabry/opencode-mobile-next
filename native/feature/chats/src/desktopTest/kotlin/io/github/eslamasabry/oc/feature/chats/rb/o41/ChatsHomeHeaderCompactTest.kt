package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chats.ChatEnvironment
import io.github.eslamasabry.oc.feature.chats.RecordingChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertTrue

/** Real Home: shell bar, server pill and two saved connections, rather than a chips-only fixture. */
@OptIn(ExperimentalTestApi::class)
class ChatsHomeHeaderCompactTest {
    private fun source(id: String, name: String, main: Boolean, phone: Boolean) =
        ChatsListSourceUi(ProfileId(id), name, shown = true, main = main, onThisPhone = phone, unreachable = false)

    @Test
    fun `COMPACT-030 Home uses whole chips and the more cue at 360 dp text 1_0`() = checkHeader(360, 1f)

    @Test
    fun `COMPACT-030 Home uses whole chips and the more cue at 360 dp text 1_3`() = checkHeader(360, 1.3f)

    @Test
    fun `COMPACT-030 Home uses whole chips and the more cue at 320 dp text 1_0`() = checkHeader(320, 1f)

    private fun checkHeader(width: Int, scale: Float) = runDesktopComposeUiTest(width, 780) {
        val state = ChatsUi(
            phase = ChatsPhase.NoMatch,
            nowMs = 1_791_028_800_000L,
            home = ChatsHomeExtraState(
                sources = persistentListOf(source("p1", "This phone", true, true), source("p2", "Laptop", false, false)),
            ),
        )
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                ChatEnvironment {
                    KitTheme(KitAppearance(motion = MotionLevel.Off), DesktopKitPlatform(), "en", stillFrame = true) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            KitSheetHost { ChatsScreen(state, RecordingChatsActions(), shellBar = true) }
                        }
                    }
                }
            }
        }
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        val pill = onNodeWithTag("kit-status-pill", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val line = onNodeWithTag("kit-filter-chips").getUnclippedBoundsInRoot()
        val cues = onAllNodes(hasText("+", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
        val label = onNodeWithText("This phone", useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val name = layouts.single()
        val numbers = "$width/text$scale pill=${pill.width.value} line=${line.width.value} cues=${cues.size} name=${name.size.width} ellipsis=${name.isLineEllipsized(0)}"
        println("COMPACT-030 $numbers")
        assertTrue(cues.isNotEmpty(), "$numbers: +N must be visible, never the plain-scroll fallback")
        assertTrue(!name.hasVisualOverflow && !name.isLineEllipsized(0), "$numbers: the pill name stays whole")
        fun checkVisible() {
            val clip = onNodeWithTag("kit-filter-chips").fetchSemanticsNode().boundsInRoot
            for (text in listOf("All projects", "All connections", "Running")) {
                val nodes = onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes()
                for (node in nodes.filter { it.boundsInRoot.width > .5f }) {
                    val full = onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
                    assertTrue(full.left.value >= clip.left - .5f && full.right.value <= clip.right + .5f,
                        "$numbers: $text whole, bounds=$full clip=$clip")
                    assertTrue(kotlin.math.abs(full.center.y.value - pill.center.y.value) <= 1f,
                        "$numbers: $text stays on the pill's row")
                }
            }
        }
        checkVisible()
        onAllNodes(hasText("+", substring = true), useUnmergedTree = true).onLast().performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        val connections = onNodeWithText("All connections", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(connections.width > .5f, "$numbers: +N reaches All connections")
        checkVisible()
    }
}
