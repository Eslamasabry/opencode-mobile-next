package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.FakeChatWorld
import io.github.eslamasabry.oc.feature.chat.Immediate
import io.github.eslamasabry.oc.feature.chat.RecordingNavigator
import io.github.eslamasabry.oc.feature.chat.presentation.ChatActions
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TimelineUi
import io.github.eslamasabry.oc.feature.chat.presentation.TimelineEntryUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatTimelineSheet
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TimelineScreenTest {
    @Test fun `C03 timeline fork is a separate glyph target and never also jumps`() = runComposeUiTest {
        val selections = mutableListOf<Pair<String, Boolean>>()
        val presenter = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        val actions = object : ChatActions by presenter {
            override fun timelineSelect(messageId: String, fork: Boolean) { selections += messageId to fork }
        }
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off)) {
                KitStandaloneRedaction {
                    KitSheetHost {
                        ChatTimelineSheet(TimelineUi("", persistentListOf(TimelineEntryUi("prompt", "row", true, "Fix checkout", null, true)), false, false, null), actions)
                    }
                }
            }
        }
        // KitRow reads as one node; the fork glyph is its own target under it (clicked where it is drawn).
        onNodeWithTag("timeline-fork-prompt", useUnmergedTree = true).performClick()
        assertEquals(listOf("prompt" to true), selections)
        // The glyph forks at once: no menu or second step opens.
        onNodeWithTag("kit-menu").assertDoesNotExist()
        onNodeWithTag("timeline-row-prompt").performClick()
        assertEquals(listOf("prompt" to true, "prompt" to false), selections)
    }

    @Test fun `C03 kit row disables its glyph action with the row`() = runComposeUiTest {
        var calls = 0
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                KitRow(
                    "Fix checkout", enabled = false, disabledReason = "Conversation changed",
                    trailing = KitRowTrailing.IconAction(KitLineAction("Fork from this prompt", { calls++ }, icon = KitIcons.branch)),
                )
            }
        }
        onNodeWithContentDescription("Fork from this prompt").assertIsNotEnabled().performClick()
        assertEquals(0, calls)
    }

    @Test fun `C03 timeline filters before the search announcement settles`() = runComposeUiTest {
        val queries = mutableListOf<String>()
        val presenter = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        val actions = object : ChatActions by presenter {
            override fun timelineQuery(query: String) { queries += query }
        }
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off)) {
                KitStandaloneRedaction {
                    KitSheetHost {
                        ChatTimelineSheet(TimelineUi("", persistentListOf(), false, false, null), actions)
                    }
                }
            }
        }
        mainClock.autoAdvance = false
        onNodeWithTag("kit-search-field").performTextInput("checkout")
        waitForIdle()
        assertEquals(listOf("checkout"), queries)
    }

    @Test fun `C03 kit row permits the timeline's two line preview at normal text size`() = runComposeUiTest {
        val title = "A long message preview that needs more than one line in this narrow timeline"
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                Column(Modifier.width(240.dp)) {
                    // A short title is one line; a long one wraps to two (every row may, since audit 2: a row title
                    // never ends in "…" while a second line fits), and the timeline asks for two by name.
                    KitRow("Short", Modifier.testTag("one-line"))
                    KitRow(title, Modifier.testTag("two-lines"), titleMaxLines = 2)
                }
            }
        }
        fun titleHeight(tag: String) = onNode(hasTestTag("kit-row-title") and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(titleHeight("two-lines") > titleHeight("one-line"))
    }
}
