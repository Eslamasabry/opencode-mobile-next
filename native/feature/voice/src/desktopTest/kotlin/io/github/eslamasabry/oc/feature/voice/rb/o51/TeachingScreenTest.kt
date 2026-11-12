package io.github.eslamasabry.oc.feature.voice.rb.o51

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.settings.teaching.*
import io.github.eslamasabry.oc.feature.voice.presentation.*
import io.github.eslamasabry.oc.feature.voice.ui.TeachingScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TeachingScreenTest {
    @Test
    fun `B-NAV-068 tips name each entry and replay only the selected tip`() = runComposeUiTest {
        val calls = mutableListOf<TipId>()
        val actions =
            object : TeachingActions {
                override fun back() {}

                override fun replay(id: TipId) {
                    calls += id
                }

                override fun next() {}

                override fun dismiss() {}

                override fun reset() {}

                override fun retry() {}
            }
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                appearance = KitAppearance(motion = MotionLevel.Off),
                stillFrame = true,
            ) {
                Box(Modifier.size(412.dp, 915.dp)) {
                    TeachingScreen(TeachingUi(ready = true), actions)
                }
            }
        }
        val first = TeachingRegistry.defaults.tips.first { it.kind == TeachingKind.Tip }
        onNodeWithTag("tips-show-me-${first.id.raw}", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(listOf(first.id), calls)
        // Tip prose stays behind Show me rather than turning the list into a wall of messages.
        onNodeWithText("Each tip shows at its moment, at most twice. Replay any of them here.")
            .assertExists()
    }

    /** A stand-in for the page's ViewModel: one replay at a time, like the controller's single active slot. */
    private class Fake : TeachingActions {
        var ui by mutableStateOf(TeachingUi(ready = true))
        val opened = mutableListOf<TipId>()

        override fun back() {}

        override fun replay(id: TipId) {
            val entry = TeachingRegistry.defaults.entry(id) ?: return
            if (entry.kind == TeachingKind.Tip) ui = ui.copy(replay = entry) else opened += id
        }

        override fun next() {}

        override fun dismiss() {
            ui = ui.copy(replay = null)
        }

        override fun reset() {}

        override fun retry() {}
    }

    private fun ComposeUiTest.show(fake: Fake) =
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                appearance = KitAppearance(motion = MotionLevel.Off),
                stillFrame = true,
            ) {
                Box(Modifier.size(412.dp, 915.dp)) { TeachingScreen(fake.ui, fake) }
            }
        }

    private fun ComposeUiTest.showMe(id: TipId) {
        onNodeWithTag("tips-show-me-${id.raw}", useUnmergedTree = true).performScrollTo().performClick()
        waitForIdle()
    }

    private val tips get() = TeachingRegistry.defaults.tips.filter { it.kind == TeachingKind.Tip }

    @Test
    fun `a tip opens in its own row, in place, with no floating card`() = runComposeUiTest {
        val fake = Fake()
        show(fake)
        val tip = tips.first()
        val words = "Hold a message"
        // Closed: only the name, and the button says Show me.
        onAllNodesWithText(words, substring = true, useUnmergedTree = true).assertCountEquals(0)
        showMe(tip.id)
        // Open: the tip's words are the row's own supporting lines, under its title.
        onAllNodesWithText(words, substring = true, useUnmergedTree = true).assertCountEquals(1)
        // The button now reads the opposite; "Show me" is gone from this row only.
        onNode(hasText("Hide tip") and hasAnyAncestor(hasTestTag("tips-hide-${tip.id.raw}")), useUnmergedTree = true)
            .assertExists()
        onNodeWithTag("tips-show-me-${tip.id.raw}", useUnmergedTree = true).assertDoesNotExist()
        // Every other row still shows its title and its own Show me: nothing is covered.
        tips.drop(1).forEach { other ->
            onNodeWithTag("tips-show-me-${other.id.raw}", useUnmergedTree = true).assertExists()
        }
        // Hiding closes it.
        onNodeWithTag("tips-hide-${tip.id.raw}", useUnmergedTree = true).performClick()
        waitForIdle()
        onAllNodesWithText(words, substring = true, useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("tips-show-me-${tip.id.raw}", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `opening a second tip closes the first`() = runComposeUiTest {
        val fake = Fake()
        show(fake)
        val (a, b) = tips
        showMe(a.id)
        onNodeWithTag("tips-hide-${a.id.raw}", useUnmergedTree = true).assertExists()
        showMe(b.id)
        onNodeWithTag("tips-hide-${b.id.raw}", useUnmergedTree = true).assertExists()
        onNodeWithTag("tips-hide-${a.id.raw}", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("tips-show-me-${a.id.raw}", useUnmergedTree = true).assertExists()
        assertEquals(1, onAllNodesWithText("Hide tip", useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
