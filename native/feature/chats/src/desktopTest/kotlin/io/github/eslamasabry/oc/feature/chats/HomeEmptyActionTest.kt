package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * The empty home's "New conversation" is on screen even with a notice above it. The empty page used to take a whole
 * window's height under whatever stood above, so the notice a new person gets right after setting up This phone
 * ("Notify you when the agent needs you?") pushed the only way to start a conversation below the fold (emulator walk
 * of the release candidate, 2026-11-10).
 */
@OptIn(ExperimentalTestApi::class)
class HomeEmptyActionTest {
    @Test
    fun `the empty home keeps New conversation on screen under a tall notice`() = runComposeUiTest {
        setContent {
            ChatEnvironment {
                KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = DesktopKitPlatform(), localeTag = "en") {
                    Box(Modifier.size(412.dp, 800.dp)) {
                        KitSheetHost {
                            ChatsScreen(
                                ChatsUi(phase = ChatsPhase.Empty),
                                RecordingChatsActions(),
                                replyOffer = { Box(Modifier.fillMaxWidth().height(260.dp)) },
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        val button = onNodeWithText("New conversation").getBoundsInRoot()
        assertTrue(button.bottom <= 800.dp, "New conversation ends at ${button.bottom}, below the 800 dp window")
        assertTrue(button.top > 260.dp, "it sits under the notice (top ${button.top})")
    }

    @Test
    fun `B-243 a list that was not read says so and offers Try again, never the first-run words`() = runComposeUiTest {
        val actions = object : RecordingChatsActions() { override fun retryList() { calls += "retry list" } }
        setContent {
            ChatEnvironment {
                KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = DesktopKitPlatform(), localeTag = "en") {
                    Box(Modifier.size(412.dp, 800.dp)) {
                        KitSheetHost { ChatsScreen(ChatsUi(phase = ChatsPhase.NotRead), actions) }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("No conversations yet").assertDoesNotExist()
        onNodeWithText("Try again").performClick()
        assertTrue("retry list" in actions.calls, "Try again reaches the list read: ${actions.calls}")
    }
}
