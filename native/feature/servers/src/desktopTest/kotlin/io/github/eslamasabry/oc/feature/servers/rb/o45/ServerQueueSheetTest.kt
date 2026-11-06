@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.servers.rb.o45

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.feature.servers.ui.ServerQueueScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.*

class ServerQueueSheetTest {
    private class Actions : ServerQueueActions {
        var moved = false
        var toggled: String? = null
        var chosen: SessionId? = SessionId("before")

        override fun back() {}

        override fun refresh() {}

        override fun toggle(id: String) {
            toggled = id
        }

        override fun choose(conversation: SessionId?) {
            chosen = conversation
        }

        override fun move() {
            moved = true
        }

        override fun undo() {}

        override fun openConversation() {}
    }

    private val plan =
        ServerQueuePlan(
            "ticket",
            "Old computer",
            "Laptop",
            persistentListOf(
                QueueMoveItem("one", "Fix the tests", null),
                QueueMoveItem("blocked", "Already sending", QueueMoveBlock.Sending),
            ),
            persistentListOf(QueueMoveConversation(SessionId("recent"), "Recent work")),
        )

    @Test
    fun `the sheet names only movable selected prompts and choices dispatch the target`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    // The screen is a sheet over the window's modal layer, which the app root holds (App.kt).
                    KitSheetHost {
                        ServerQueueScreen(
                            ServerQueueUi(
                                loading = false,
                                plan = plan,
                                selected = persistentSetOf("one", "blocked"),
                            ),
                            actions,
                        )
                    }
                }
            }
            onNodeWithText("From Old computer").assertExists()
            onNodeWithText("Already sending").assertIsNotEnabled()
            onNodeWithText("Recent work").performClick()
            assertEquals(SessionId("recent"), actions.chosen)
            onNodeWithText("New conversation").performClick()
            assertEquals(null, actions.chosen)
            onNodeWithText("Move 1 prompt to Laptop").performClick()
            assertEquals(true, actions.moved)
            assertEquals(null, actions.toggled)
        }

    @Test
    fun `an empty source keeps the destination visible and disables moving`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                KitSheetHost {
                    ServerQueueScreen(
                        ServerQueueUi(loading = false, plan = plan.copy(items = persistentListOf())),
                        Actions(),
                    )
                }
            }
        }
        onNodeWithText("Nothing waits for Old computer any more").assertExists()
        onNodeWithText("Move 0 prompts to Laptop").assertIsNotEnabled()
        onNodeWithText("New conversation").assertExists()
    }
}
