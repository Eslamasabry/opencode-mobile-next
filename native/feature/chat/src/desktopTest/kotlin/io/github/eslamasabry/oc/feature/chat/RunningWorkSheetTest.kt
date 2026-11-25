package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.BackgroundWorkSupport
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundTask
import io.github.eslamasabry.oc.feature.chat.presentation.FactsClock
import io.github.eslamasabry.oc.feature.chat.ui.ChatWorkSheet
import io.github.eslamasabry.oc.feature.chat.ui.runningWorkAction
import io.github.eslamasabry.oc.feature.chat.work.ChatWorkModel
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Owner 2026-11-14 ("Where is the option to see the background activity and the background agent and send to
 * background?"): the conversation's running-work sheet by its visible words, in every state, and the "…" entry that
 * opens it.
 */
@OptIn(ExperimentalTestApi::class)
class RunningWorkSheetTest {
    /** A server without a command list (the agent helper, Codex): the interface defaults answer Unavailable. */
    private class World(private val support: BackgroundWorkSupport) : FakeChatWorld() {
        override suspend fun backgroundWorkSupport(): Outcome<BackgroundWorkSupport> = Outcome.Ok(support)
    }

    @Composable
    private fun Root(content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            KitStandaloneRedaction { KitSheetHost { content() } }
        }
    }

    private fun ComposeUiTest.sheet(
        model: ChatWorkModel,
        tasks: List<BackgroundTask> = emptyList(),
        subagents: Boolean = false,
        onShowSubagents: () -> Unit = {},
        onShowTask: (BackgroundTask) -> Unit = {},
    ) {
        model.refresh()
        setContent {
            Root {
                val work by model.state.collectAsState()
                ChatWorkSheet(
                    work,
                    visible = true,
                    model = model,
                    onDismiss = {},
                    tasks = tasks,
                    clock = FactsClock.at(4 * 60_000L),
                    subagents = subagents,
                    onShowSubagents = onShowSubagents,
                    onShowTask = onShowTask,
                )
            }
        }
    }

    @Test
    fun `B-CHAT-TASKS-009 a server that reports no background work says so, and says it cannot move work there`() =
        kitUiTest {
            sheet(ChatWorkModel(World(BackgroundWorkSupport.Unavailable), Immediate::launch))
            onNodeWithText("This server doesn't report work that runs in the background.").assertIsDisplayed()
            onNodeWithText("This server can't move a running reply's work to the background.").assertIsDisplayed()
            onNodeWithText("Couldn't load this conversation's commands.").assertDoesNotExist()
        }

    @Test
    fun `B-CHAT-TASKS-010 nothing running says so and still leads to the sub-agents`() = kitUiTest {
        var opened = 0
        sheet(ChatWorkModel(World(BackgroundWorkSupport.Unavailable), Immediate::launch), subagents = true, onShowSubagents = { opened++ })
        onNodeWithText("Nothing running").assertIsDisplayed()
        onNodeWithTag("chat-work-subagents").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `B-CHAT-TASKS-011 work the stream reports is listed by name and time and goes to the step that started it`() =
        kitUiTest {
            val task = BackgroundTask(callId = "call-1", messageId = "a1", sinceMs = 60_000L, name = "build_release.sh")
            var shown: BackgroundTask? = null
            sheet(
                ChatWorkModel(World(BackgroundWorkSupport.Unavailable), Immediate::launch),
                tasks = listOf(task),
                subagents = true,
                onShowTask = { shown = it },
            )
            onNodeWithText("build_release.sh").assertIsDisplayed()
            onNodeWithText("3 min").assertIsDisplayed()
            onNodeWithText("This server can't stop these one at a time.").assertIsDisplayed()
            onNodeWithText("Nothing running").assertDoesNotExist()
            onNodeWithTag("chat-work-task-call-1").performClick()
            assertEquals(task, shown)
        }

    @Test
    fun `B-CHAT-TASKS-012 the … entry is always there, with the count while work runs`() = kitUiTest {
        val seen = arrayOfNulls<KitLineAction>(3)
        setContent {
            Root {
                seen[0] = runningWorkAction(offered = true, running = 0, onOpen = {})
                seen[1] = runningWorkAction(offered = true, running = 2, onOpen = {})
                seen[2] = runningWorkAction(offered = false, running = 2, onOpen = {})
            }
        }
        waitForIdle()
        assertEquals("Running work", assertNotNull(seen[0]).label, "nothing runs: the entry stays")
        assertEquals("Tasks · 2 running", assertNotNull(seen[1]).label)
        assertEquals("chat-work-open", seen[1]?.testTag)
        assertNull(seen[2], "only where the conversation has no work sheet (the demo)")
    }
}
