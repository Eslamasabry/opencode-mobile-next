package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatConfirm
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** The app root supplies a theme, never an external modal host around a feature entry. */
@OptIn(ExperimentalTestApi::class)
class ChatScreenHostTest {
    private val platform =
        object : KitPlatform by DesktopKitPlatform() {
            override val isDebug: Boolean = true
        }
    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(
                target: ComposerTarget,
                chrome: ComposerChrome,
                modifier: Modifier,
            ) {
                KitText("Composer slot", modifier)
            }
        }

    @Composable
    private fun Root(content: @Composable () -> Unit) {
        KitTheme(
            platform = platform,
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
            content = content,
        )
    }

    @Test
    fun `B-NAV-005 a real chat entry renders with kit assertions and no enclosing modal host`() =
        runComposeUiTest {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            val state = ChatUi.loading(Ref).copy(title = "Native chat", phase = ChatPhase.Live)
            setContent { Root { ChatScreen(state, actions, composer) } }
            onNodeWithText("Native chat").assertIsDisplayed()
            onNodeWithText("Composer slot").assertIsDisplayed()
        }

    @Test
    fun `B-NAV-005 the chat owns its confirmation host and leaving dispatches the rendered action`() =
        runComposeUiTest {
            val world = FakeChatWorld()
            val nav = RecordingNavigator()
            val actions = ChatPresenter(world, nav, Immediate)
            world.draftChanged("unsaved text")
            actions.leaveRequested()
            val state = actions.current()
            assertEquals(ChatConfirm.LeaveUnsaved, state.confirm)
            setContent { Root { ChatScreen(state, actions, composer) } }
            onNodeWithText("Leave without saving").assertIsDisplayed().performClick()
            assertEquals(listOf("back"), nav.calls)
        }
}
