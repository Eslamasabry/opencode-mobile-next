package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.FeedPermission
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.ChatEnvironment
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationRowUi
import io.github.eslamasabry.oc.feature.chats.RecordingChatsActions
import io.github.eslamasabry.oc.feature.chats.item
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** F: lib/ui/screens/chats/chats_host.dart:240-270: a list row answers Allow once, Reject and Always allow. */
@OptIn(ExperimentalTestApi::class)
class HomeAlwaysAllowTest {
    private fun asking(always: Boolean): ChatsUi {
        val base =
            ChatsState.build(
                ChatFeedSnapshot(
                    persistentListOf(
                        item("ask", at = 12_000, title = "Theme packs", status = ChatStatus.NeedsYou)
                            .copy(permission = FeedPermission("req1", "npm test"))
                    )
                ),
                ReturnBrief.None,
                ChatsFilters(),
                10_000,
                true,
            )
        val row = (base.rightNow + base.recent).single()
        return base.copy(alwaysAllowable = if (always) persistentSetOf(row.key) else persistentSetOf())
    }

    private class Actions(private val inner: RecordingChatsActions = RecordingChatsActions()) : ChatsActions by inner {
        val calls get() = inner.calls

        override fun answerPermission(row: ConversationRowUi, allow: Boolean) {
            calls += if (allow) "once" else "reject"
        }

        override fun askAlwaysAllow(row: ConversationRowUi) {
            calls += "askAlways"
        }

        override fun confirmAlwaysAllow() {
            calls += "always"
        }

        override fun cancelAlwaysAllow() {
            calls += "cancelAlways"
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: ChatsUi, actions: ChatsActions) = setContent {
        ChatEnvironment {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                KitSheetHost { ChatsScreen(state, actions) }
            }
        }
    }

    @Test fun `PARITY-B-224 the row offers Allow once, Reject and Always allow, each its own answer`() = runComposeUiTest {
        val actions = Actions()
        show(asking(always = true), actions)
        onNodeWithText("Allow once").performClick()
        onNodeWithText("Deny").performClick()
        onNodeWithText("Always allow", substring = true).performClick()
        assertEquals(listOf("once", "reject", "askAlways"), actions.calls)
    }

    @Test fun `PARITY-B-224 Always allow asks first, naming the command and the project`() = runComposeUiTest {
        val actions = Actions()
        val state = asking(always = true)
        show(state.copy(confirmAlways = (state.rightNow + state.recent).single()), actions)
        onNodeWithText("in this project?", substring = true).assertExists()
        onNode(hasText("Always allow") and hasClickAction()).performClick()
        assertEquals(listOf("always"), actions.calls)
    }

    @Test fun `PARITY-B-224 where the server keeps no grants the row offers no Always allow`() = runComposeUiTest {
        show(asking(always = false), Actions())
        onNodeWithText("Always allow", substring = true).assertDoesNotExist()
        onNodeWithText("Allow once").assertExists()
    }
}
