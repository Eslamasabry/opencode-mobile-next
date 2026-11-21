package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitPaneColumn
import io.github.eslamasabry.oc.ui.kit.foundation.KitPaneColumnPage
import io.github.eslamasabry.oc.ui.kit.frame.KitPaneWindow
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.util.Locale
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real conversation in the pane beside the list (big screens), at every pane width the windows from 840 to 1700 dp
 * give it (the shell's list takes 320 to 420 dp of them): the header capsule, the transcript and the message box share
 * one pair of edges, that column fills the pane up to its cap, and only past it two equal margins appear.
 */
@OptIn(ExperimentalTestApi::class)
class PaneColumnSweepTest {
    private var locale: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        locale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(locale)

    private fun ComposeUiTest.show(pane: androidx.compose.runtime.MutableIntState, column: Boolean) {
        val platform = DesktopKitPlatform()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = platform, localeTag = "en") {
                    CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                        Box(Modifier.width(pane.intValue.dp).fillMaxHeight()) {
                            // As the shell draws the pane beside its list; one page without it.
                            if (column) KitPaneColumnPage { ListPaneChatRenders().Conversation() }
                            else KitPaneWindow { ListPaneChatRenders().Conversation() }
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.bounds(tag: String): DpRect = onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    @Test
    fun `B-NAV-230 one column fills the pane at every width, two equal margins only past its cap`() =
        kitUiTest(WIDEST + 40, 900) {
            val pane = mutableIntStateOf(NARROWEST)
            show(pane, column = true)
            var offset: Float? = null
            for (p in NARROWEST..WIDEST step STEP) {
                runOnIdle { pane.intValue = p }
                waitForIdle()
                val head = bounds(CAPSULE)
                val box = bounds(BOX)
                val words = bounds(WORDS_TAG)
                val at = "pane $p dp"
                assertTrue(
                    abs(head.left.value - box.left.value) <= 1f,
                    "$at: header and box start together ($head, $box)",
                )
                assertTrue(abs(head.right.value - box.right.value) <= 1f, "$at: header and box end together")
                // The transcript moves with them: a reply's actions (at the column's end) keep one distance from the
                // box's end at every width.
                val shift = box.right.value - words.right.value
                if (offset == null) offset = shift
                assertTrue(
                    abs(shift - offset!!) <= 1f,
                    "$at: the transcript keeps the column's edge ($shift vs $offset)",
                )
                val left = box.left.value
                val right = p - box.right.value
                if (p <= KitPaneColumn.PAGE_MAX_DP) {
                    assertTrue(
                        left <= SIDE_MAX && right <= SIDE_MAX,
                        "$at: no band, only the side padding ($left, $right)",
                    )
                } else {
                    assertTrue(abs(left - right) <= 1f, "$at: past the cap two equal margins ($left, $right)")
                }
            }
        }

    @Test
    fun `B-NAV-229 beside the list the header draws no Back, on one page it does`() {
        kitUiTest(1000, 900) {
            show(mutableIntStateOf(920), column = true)
            val back = hasContentDescription("Back") and hasTestTag("kit-top-bar-exit")
            assertEquals(
                0,
                onAllNodes(back, useUnmergedTree = true).fetchSemanticsNodes().size,
                "the list is the way back",
            )
        }
        kitUiTest(1000, 900) {
            show(mutableIntStateOf(412), column = false)
            val back = hasContentDescription("Back") and hasTestTag("kit-top-bar-exit")
            assertEquals(
                1,
                onAllNodes(back, useUnmergedTree = true).fetchSemanticsNodes().size,
                "one page keeps its Back",
            )
        }
    }

    private companion object {
        /** 840 dp less the widest list; 1700 dp less the widest list. */
        const val NARROWEST = 420
        const val WIDEST = 1380
        const val STEP = 40
        const val CAPSULE = "kit-header-capsule"
        const val BOX = "kit-composer-box"
        const val WORDS_TAG = "kit-reply-actions"

        /** The page's side padding at most (gutter and the capsule's own margin). */
        const val SIDE_MAX = 24f
    }
}
