package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.WorkLineRules
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Review #2 (set-C/C-058): the work line said "Edited 1 file" right above the reply "The write to e2.txt was declined,
 * so no file was created". The person had refused the write with a note; OpenCode 2 ends that call as an error whose
 * text is the person's note alone (no refusal sentence), so the step reads Failed, and a failed step was counted as an
 * edit. The work line counts only work that happened: a step that failed, was declined or was stopped never makes
 * "Edited", "Read", "Ran", "Searched"…; when nothing in the stretch happened it says how much was asked ("1 step").
 */
@OptIn(ExperimentalTestApi::class)
class R2DeclinedNotEditedTest {
    private val sid = Ref.sessionId.raw

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private fun tool(
        id: String,
        name: String,
        status: ToolStatus,
        input: Map<String, String>,
        output: String?,
        metadata: JsonObject? = null,
    ) =
        Part(
            id, "m2", sid, PartType.Tool, "tool", callId = "call-$id", toolName = name,
            tool =
                ToolState(
                    status,
                    input = JsonObject(input.mapValues { JsonPrimitive(it.value) }),
                    output = output,
                    metadata = metadata,
                    startedAtMs = 2_000,
                    completedAtMs = 3_000,
                ),
        )

    private fun page(vararg parts: Part): List<MessageWithParts> =
        listOf(
            MessageWithParts(
                MessageInfo("m1", sid, Role.User, time = MsgTime(1_000, null)),
                listOf(Part("u1", "m1", sid, PartType.Text, "text", text = "create a file e2.txt with one line", ended = true)).toImmutableList(),
            ),
            MessageWithParts(
                MessageInfo("m2", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle", time = MsgTime(1_100, 4_000), finish = "stop"),
                parts.toList().toImmutableList(),
            ),
        )

    private fun facts(vararg parts: Part) = run {
        val keeper = TurnFeedKeeper(sid)
        keeper.history(page(*parts), newest = true)
        keeper.idle()
        TurnFactsPresenter(FixedClock).present(keeper.feed.value, 20_000)
    }

    /** The person's note, as OpenCode 2 sends it back for a write refused with a note (`Permission.CorrectedError`). */
    private val note = "first l second li third l"

    private val reply = "The write to /root/projects/e2.txt was declined, so no file was created."

    @Test
    fun `R2 a write refused with a note is not counted as an edit`() {
        val work =
            assertNotNull(
                facts(
                    tool("t1", "write", ToolStatus.Error, mapOf("filePath" to "/root/projects/e2.txt", "content" to "x"), note),
                    Part("x2", "m2", sid, PartType.Text, "text", text = reply),
                ).work["m1"],
            )
        val done = WorkLineRules.split(work).done
        assertEquals(0, done.edited, "a write that did not happen is not an edit")
        assertEquals(1, done.steps, "the line still says how much was asked: 1 step")
    }

    @Test
    fun `R2 no verb counts a step that failed, was declined or was stopped`() {
        val failedNames = listOf("read", "glob", "grep", "list", "edit", "write", "patch", "multiedit", "webfetch", "websearch", "task")
        for (name in failedNames) {
            val work = assertNotNull(facts(tool("t-$name", name, ToolStatus.Error, mapOf("filePath" to "/p/a.txt", "pattern" to "x", "url" to "https://e.x"), "ENOENT: no such file")).work["m1"], name)
            assertEquals(StepState.Failed, work.steps.single().state, name)
            val d = WorkLineRules.split(work).done
            assertEquals(0, d.read + d.searched + d.listed + d.edited + d.ran + d.fetched + d.delegated + d.other, "$name failed: $d")
        }
        for (refusal in listOf("The user declined this tool call", "The user rejected permission to use this specific tool call.")) {
            for (name in listOf("read", "grep", "write", "bash")) {
                val work = assertNotNull(facts(tool("t-$name", name, ToolStatus.Error, mapOf("filePath" to "/p/a.txt", "command" to "ls"), refusal)).work["m1"])
                val d = WorkLineRules.split(work).done
                assertEquals(0, d.read + d.searched + d.edited + d.ran, "$name declined: $d")
            }
        }
        val stopped = assertNotNull(facts(tool("t-s", "edit", ToolStatus.Cancelled, mapOf("filePath" to "/p/a.txt"), null)).work["m1"])
        assertEquals(0, WorkLineRules.split(stopped).done.edited)
    }

    @Test
    fun `R2 a command refused with a note did not run, a command that exited with an error did`() {
        val refused = assertNotNull(facts(tool("t-b", "bash", ToolStatus.Error, mapOf("command" to "rm -rf build"), note)).work["m1"])
        assertEquals(0, WorkLineRules.split(refused).done.ran, "no exit code: the command never ran")
        val exited =
            assertNotNull(
                facts(tool("t-b", "bash", ToolStatus.Error, mapOf("command" to "make"), "make: *** No rule", JsonObject(mapOf("exit" to JsonPrimitive(2))))).work["m1"],
            )
        assertEquals(1, WorkLineRules.split(exited).done.ran, "it ran and exited 2")
    }

    @Test
    fun `R2 a completed read beside the refused write says only what happened`() {
        val work =
            assertNotNull(
                facts(
                    tool("t0", "read", ToolStatus.Completed, mapOf("filePath" to "/p/a.txt"), "hello"),
                    tool("t1", "write", ToolStatus.Error, mapOf("filePath" to "/p/e2.txt"), note),
                ).work["m1"],
            )
        val done = WorkLineRules.split(work).done
        assertEquals(1, done.read)
        assertEquals(0, done.edited)
    }

    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    @Test
    fun `R2 gate - on screen the refused write's line reads 1 step, never Edited 1 file`() = runComposeUiTest {
        val facts =
            facts(
                tool("t1", "write", ToolStatus.Error, mapOf("filePath" to "/root/projects/e2.txt", "content" to "x"), note),
                Part("x2", "m2", sid, PartType.Text, "text", text = reply),
            )
        val rows =
            persistentListOf(
                footer("m1"),
                block("m1", "m2", "x2", 0, reply),
                WorkLineRow("work:m1", "m1", calls = 1, failed = 1, state = WorkState.Done),
                prompt("m1", "create a file e2.txt with one line"),
            )
        val state = ChatUi.loading(Ref).copy(title = "Native chat", phase = ChatPhase.Live, rows = rows)
        val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) { ChatScreen(state, actions, composer, facts = facts) }
            }
        }
        onAllNodesWithText("Edited 1 file", substring = true, useUnmergedTree = true).assertCountEquals(0)
        // D1: the turn's work is its own step row, in view (was: a summary line "1 step").
        onAllNodesWithTag("chat-step-t1", useUnmergedTree = true).assertCountEquals(1)
    }
}
