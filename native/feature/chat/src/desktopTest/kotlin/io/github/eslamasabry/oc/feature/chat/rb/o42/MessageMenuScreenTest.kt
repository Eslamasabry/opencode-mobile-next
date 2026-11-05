package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chat.FakeChatWorld
import io.github.eslamasabry.oc.feature.chat.Immediate
import io.github.eslamasabry.oc.feature.chat.RecordingNavigator
import io.github.eslamasabry.oc.feature.chat.presentation.*
import io.github.eslamasabry.oc.feature.chat.ui.MessageMenu
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class MessageMenuScreenTest {
    @Test
    fun `C04 prompt copy remains available without share and keeps its reviewed target`() =
        runComposeUiTest {
            val target = MessageTarget("turn", "prompt", TargetKind.Prompt, "message")
            val calls = mutableListOf<Pair<MessageTarget, MessageAction>>()
            var dismissed = 0
            val presenter = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            val actions =
                object : ChatActions by presenter {
                    override fun closeMessageMenu() {
                        dismissed++
                    }

                    override fun messageAction(target: MessageTarget, action: MessageAction) {
                        calls += target to action
                    }
                }
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    appearance = KitAppearance(motion = MotionLevel.Off),
                ) {
                    MessageMenu(
                        MessageMenuUi(
                            target,
                            persistentListOf(MessageAction.Copy, MessageAction.Fork),
                        ),
                        actions,
                        Modifier,
                    )
                }
            }
            onNodeWithText("Copy message text").performClick()
            runOnIdle {
                assertEquals(listOf(target to MessageAction.Copy), calls)
                assertEquals(1, dismissed)
            }
        }

    @Test
    fun `C04 reply More omits adjacent copy and unavailable read aloud`() = runComposeUiTest {
        val presenter = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                appearance = KitAppearance(motion = MotionLevel.Off),
            ) {
                MessageMenu(
                    MessageMenuUi(
                        MessageTarget("turn", "footer", TargetKind.Reply, "reply"),
                        persistentListOf(
                            MessageAction.Copy,
                            MessageAction.ReadAloud,
                            MessageAction.Delete,
                        ),
                    ),
                    presenter,
                    Modifier,
                )
            }
        }
        onNodeWithText("Copy message text").assertDoesNotExist()
        onNodeWithText("Read aloud").assertDoesNotExist()
        onNodeWithText("Delete message").assertExists()
    }
}
