package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** S24-CHAT story 7: the "Stopped" note under a stopped reply starts on the reply's own text edge. */
@OptIn(ExperimentalTestApi::class)
class S24StoppedFooterTest {
    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    private fun androidx.compose.ui.test.ComposeUiTest.host(dir: LayoutDirection) {
        val rows =
            persistentListOf(
                FooterRow("footer:m1", "m1", failed = false, stopped = true),
                block("m1", "m1", "p1", 0, "Here is the start of the answer"),
            )
        val state = ChatUi.loading(Ref).copy(title = "Native chat", phase = ChatPhase.Live, rows = rows)
        val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = if (dir == LayoutDirection.Rtl) "ar" else "en",
                stillFrame = true,
            ) {
                CompositionLocalProvider(
                    LocalKitRedactor provides Redactor(),
                    LocalLayoutDirection provides dir,
                ) {
                    ChatScreen(state, actions, composer)
                }
            }
        }
    }

    // The real list item wraps a reply block in a clickable box (message actions) that starts at the list edge; the
    // text sits one page gutter inside it. Measure the TEXT (unmerged node), not the merged clickable box.
    @Test
    fun `B-CHAT-S24-70 the Stopped note lines up with the reply text, not 40 dp in`() = runComposeUiTest {
        host(LayoutDirection.Ltr)
        val reply = onNodeWithText("Here is the start of the answer", substring = true, useUnmergedTree = true).getBoundsInRoot().left
        val note = onNodeWithTag("chat-turn-footer-note", useUnmergedTree = true).getBoundsInRoot().left
        assertTrue(abs((note - reply).value) <= 2f, "note at $note, reply text at $reply")
        assertTrue(reply > 8.dp, "the reply text keeps the page gutter, at $reply")
        assertTrue(note > 8.dp, "the note keeps the page gutter, at $note")
    }

    @Test
    fun `B-CHAT-S24-71 in a right-to-left chat the note and the reply text share the start edge`() = runComposeUiTest {
        host(LayoutDirection.Rtl)
        val width = onNodeWithTag("chat-turn-footer", useUnmergedTree = true).getBoundsInRoot()
        val reply = onNodeWithText("Here is the start of the answer", substring = true, useUnmergedTree = true).getBoundsInRoot().right
        val note = onNodeWithTag("chat-turn-footer-note", useUnmergedTree = true).getBoundsInRoot().right
        assertTrue(abs((note - reply).value) <= 2f, "note right edge $note, reply text right edge $reply")
        assertTrue(note < width.right - 8.dp, "the note keeps the page gutter from the right edge, at $note")
    }
}
