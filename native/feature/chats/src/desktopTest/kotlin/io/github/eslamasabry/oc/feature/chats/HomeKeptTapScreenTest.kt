package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
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
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** B-333: a row whose tap is kept says, to the eye and to a screen reader, that it is connecting; the tap is taken. */
@OptIn(ExperimentalTestApi::class)
class HomeKeptTapScreenTest {
    private fun ui(waiting: Boolean): ChatsUi {
        val row = item("s1", at = 9_000).copy(canReopen = false)
        val built = ChatsState.build(ChatFeedSnapshot(persistentListOf(row)), ReturnBrief.None, ChatsFilters(), 10_000, true)
        return if (waiting) built.copy(waitingOpen = row.ref) else built
    }

    private fun spoken(waiting: Boolean, actions: RecordingChatsActions = RecordingChatsActions()): List<String> {
        var words: List<String> = emptyList()
        runDesktopComposeUiTest(360, 900) {
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
            words = rows.fetchSemanticsNodes().mapNotNull {
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.joinToString(" ")
            }
            if (waiting) rows.filter(hasContentDescription("Connecting", substring = true)).fetchSemanticsNodes().let {
                assertEquals(1, it.size, "rows: $words")
            }
        }
        return words
    }

    @Test
    fun `B-333 a row whose tap is kept says Connecting, and a row with none does not`() {
        assertTrue(spoken(waiting = true).any { "Connecting" in it })
        assertTrue(spoken(waiting = false).none { "Connecting" in it })
    }
}
