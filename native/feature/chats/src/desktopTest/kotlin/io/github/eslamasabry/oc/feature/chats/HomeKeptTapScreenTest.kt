package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/**
 * B-333: a row whose tap is kept shows the working mark alone in the time's place and says to a screen reader that its
 * agent is getting ready (home-getting-ready: never the plain word "Connecting"); the tap is taken.
 */
@OptIn(ExperimentalTestApi::class)
class HomeKeptTapScreenTest {
    private fun ui(waiting: Boolean): ChatsUi {
        val row = item("s1", at = 9_000).copy(canReopen = false)
        val built = ChatsState.build(ChatFeedSnapshot(persistentListOf(row)), ReturnBrief.None, ChatsFilters(), 10_000, true)
        return if (waiting) built.copy(waitingOpen = row.ref) else built
    }

    private fun spoken(waiting: Boolean, actions: RecordingChatsActions = RecordingChatsActions()): List<String> {
        var words: List<String> = emptyList()
        kitUiTest(360, 900) {
            Locale.setDefault(Locale.ENGLISH)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ChatEnvironment {
                        KitTheme(
                            appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off, style = Style.Solid),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { ChatsScreen(ui(waiting), actions) }
                        }
                    }
                }
            }
            waitForIdle()
            val rows = onAllNodesWithTag("kit-recent-row", useUnmergedTree = true)
            val description = androidx.compose.ui.semantics.SemanticsProperties.ContentDescription
            words =
                rows.fetchSemanticsNodes().mapNotNull { node ->
                    if (description in node.config) node.config[description].joinToString(" ") else null
                }
            if (waiting) assertEquals(1, words.count { "Getting ready…" in it }, "rows: $words")
            assertTrue(words.none { "Connecting" in it }, "rows: $words")
        }
        return words
    }

    @Test
    fun `B-333 a row whose tap is kept says its agent is getting ready, and a row with none does not`() {
        assertTrue(spoken(waiting = true).any { "Getting ready…" in it })
        assertTrue(spoken(waiting = false).none { "Getting ready" in it })
    }
}
