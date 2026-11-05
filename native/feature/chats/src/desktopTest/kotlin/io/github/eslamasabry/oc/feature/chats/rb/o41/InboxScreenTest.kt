package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.agents.feed.FeedPermission
import io.github.eslamasabry.oc.feature.chats.P1
import io.github.eslamasabry.oc.feature.chats.RecordingChatsActions
import io.github.eslamasabry.oc.feature.chats.Themed
import io.github.eslamasabry.oc.feature.chats.presentation.*
import io.github.eslamasabry.oc.feature.chats.ui.ChatsInboxScreen
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kitchat.request.KitInboxRequestRow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class InboxScreenTest {
    @Test fun `B-KIT-011 inbox permission answers are independent from opening the conversation`() = runComposeUiTest {
        var opened = 0
        var allowed = 0
        var rejected = 0
        setContent {
            Themed {
                Column {
                    KitInboxRequestRow("Run a shell command", "laptop · shopfront", "Open checkout", "Open", { opened++ },
                        command = "flutter test", allowLabel = "Run once", rejectLabel = "Don't run",
                        onAllow = { allowed++ }, onReject = { rejected++ }, primary = true)
                    KitInboxRequestRow("Waiting", "laptop · api", "Open waiting", "Open", {},
                        modifier = Modifier.testTag("disabled-row"), command = "npm test",
                        allowLabel = "Allow once", rejectLabel = "Reject", onAllow = {}, onReject = {},
                        enabled = KitEnabled.Disabled("Sending"))
                }
            }
        }
        onNodeWithText("Run once").performClick()
        assertEquals(1, allowed)
        assertEquals(0, opened)
        assertEquals(0, rejected)
        onNodeWithText("Allow once").assertIsNotEnabled()
    }

    @Test fun `B-KIT-011 Inbox dispatches the displayed permission and excludes an in-flight answer from its count`() = runComposeUiTest {
        val recorded = RecordingChatsActions()
        val answers = mutableListOf<Pair<String, Boolean>>()
        val actions = object : ChatsActions by recorded {
            override fun answerPermission(row: ConversationRowUi, allow: Boolean) { answers += row.key to allow }
        }
        val first = ConversationRowUi("one", ConversationRef(P1, SessionId("one")), RowTitle.Text("Checkout"), "shopfront", RowStatus.NeedsYou,
            permission = FeedPermission("ask1", "flutter test"))
        val second = first.copy(key = "two", ref = ConversationRef(P1, SessionId("two")), title = RowTitle.Text("API"), permission = FeedPermission("ask2", "npm test"))
        setContent {
            Themed {
                ChatsInboxScreen(ChatsUi(inbox = persistentListOf(first, second), answering = persistentSetOf("ask2")), actions)
            }
        }
        onNodeWithText("1 waiting").assertExists()
        onNode(hasText("Allow once") and hasAnyAncestor(hasTestTag("inbox-request-one"))).performClick()
        assertEquals(listOf("one" to true), answers)
        onNode(hasText("Allow once") and hasAnyAncestor(hasTestTag("inbox-request-two"))).assertIsNotEnabled()
        onNodeWithContentDescription("Open API").assertIsEnabled()
        onNodeWithContentDescription("Open Checkout").performClick()
        assertEquals(listOf("open one"), recorded.calls)
    }
}
