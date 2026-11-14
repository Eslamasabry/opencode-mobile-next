package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.Tokens
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.CostWords
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyFooterPart
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyFooterRules
import io.github.eslamasabry.oc.feature.chat.presentation.StepEntry
import io.github.eslamasabry.oc.feature.chat.presentation.StepListRules
import io.github.eslamasabry.oc.feature.chat.presentation.TokenAmount
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnMetadataUi
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * D1 "steps only" (owner 2026-11-14): a turn's work is its steps, one row each, the newest five in view and the older
 * ones behind one row that names the declined and failed inside; sub-agents started together are one group; under each
 * finished reply a quiet line says when it ended, how long it took, and tokens and cost only when the server counted
 * them.
 */
@OptIn(ExperimentalTestApi::class)
class StepsTreeTest {
    private val sid = Ref.sessionId.raw

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private fun info(id: String, role: Role, at: Long, done: Long? = null, model: String = "m", tokens: Tokens = Tokens(), cost: Double = 0.0) =
        MessageInfo(id, sid, role, providerId = "anthropic", modelId = model, tokens = tokens, cost = cost, time = MsgTime(at, done), finish = if (done != null) "stop" else null)

    private fun text(id: String, mid: String, words: String) = Part(id, mid, sid, PartType.Text, "text", text = words)

    private fun tool(
        id: String,
        name: String,
        status: ToolStatus,
        input: Map<String, String>,
        start: Long?,
        end: Long?,
        output: String? = null,
        child: String? = null,
        mid: String = "m2",
    ) =
        Part(
            id, mid, sid, PartType.Tool, "tool",
            callId = "call-$id",
            toolName = name,
            tool =
                ToolState(
                    status,
                    input = JsonObject(input.mapValues { JsonPrimitive(it.value) }),
                    output = output,
                    metadata = child?.let { JsonObject(mapOf("sessionId" to JsonPrimitive(it))) },
                    startedAtMs = start,
                    completedAtMs = end,
                ),
        )

    private fun msg(info: MessageInfo, vararg parts: Part) = MessageWithParts(info, parts.toList().toImmutableList())

    private val prompt = msg(info("m1", Role.User, 1_000), text("p1", "m1", "merge the rivers files"))

    /**
     * Nine steps: a read, a refused delete, a failed edit, a search, an edit, a new file, the tests, then two sub-agents
     * that ran at the same time; the reply ended 73 s after the prompt with 19 340 tokens and six cents.
     */
    private fun turn(cost: Double = 0.06, tokens: Tokens = Tokens(input = 19_000, output = 340)): List<MessageWithParts> =
        listOf(
            prompt,
            msg(
                info("m2", Role.Assistant, 2_000, done = 74_000, tokens = tokens, cost = cost),
                tool("t1", "read", ToolStatus.Completed, mapOf("filePath" to "/p/rivers.txt"), 2_000, 2_300, output = "x"),
                tool("t2", "bash", ToolStatus.Error, mapOf("command" to "rm rivers-300.txt"), 2_400, 2_500, output = "The user rejected permission to use this specific tool call."),
                tool("t3", "edit", ToolStatus.Error, mapOf("filePath" to "/p/app.js"), 2_600, 2_700, output = "could not apply the change"),
                tool("t4", "grep", ToolStatus.Completed, mapOf("pattern" to "rivers"), 2_800, 2_900, output = "3 matches"),
                tool("t5", "edit", ToolStatus.Completed, mapOf("filePath" to "/p/app.js"), 3_000, 3_200, output = "ok"),
                tool("t6", "write", ToolStatus.Completed, mapOf("filePath" to "/p/rivers.test.js"), 3_300, 3_400, output = "ok"),
                tool("t7", "bash", ToolStatus.Completed, mapOf("command" to "npm test"), 4_000, 7_800, output = "1 failing"),
                tool("t8", "task", ToolStatus.Completed, mapOf("description" to "Find every file that reads rivers", "subagent_type" to "explore"), 8_000, 20_000, output = "found", child = "c1"),
                tool("t9", "task", ToolStatus.Completed, mapOf("description" to "Compare the old copies", "subagent_type" to "explore"), 8_100, 30_000, output = "same", child = "c2"),
                text("x", "m2", "Done."),
            ),
        )

    private fun facts(page: List<MessageWithParts> = turn()): TurnFactsUi {
        val k = TurnFeedKeeper(sid)
        k.history(page, newest = true)
        k.idle()
        return TurnFactsPresenter(FixedClock).present(k.feed.value, 80_000)
    }

    @Test
    fun `D1-STEPS-001 a turn's rows keep their order, one per step, sub-agents that ran together as one group`() {
        val f = facts()
        val work = assertNotNull(f.work["m1"])
        val rows = StepListRules.entries(work, f.reading.workEntries["m1"].orEmpty())
        assertEquals(8, rows.size, "seven steps and one group: $rows")
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5", "t6", "t7"), rows.take(7).map { (it as StepEntry.Step).step.partId })
        val group = assertIs<StepEntry.Group>(rows.last())
        assertEquals(listOf("t8", "t9"), group.steps.map { it.partId })
        assertEquals(listOf(StepState.Declined, StepState.Failed), rows.subList(1, 3).map { (it as StepEntry.Step).step.state })
    }

    @Test
    fun `D1-STEPS-002 sub-agents one after another, or with no times, are rows of their own`() {
        val f =
            facts(
                listOf(
                    prompt,
                    msg(
                        info("m2", Role.Assistant, 2_000, done = 9_000),
                        tool("a", "task", ToolStatus.Completed, mapOf("description" to "first"), 2_000, 3_000, output = "x"),
                        tool("b", "task", ToolStatus.Completed, mapOf("description" to "second"), 3_500, 4_000, output = "x"),
                        tool("c", "task", ToolStatus.Completed, mapOf("description" to "third"), null, null, output = "x"),
                        tool("d", "task", ToolStatus.Completed, mapOf("description" to "fourth"), null, null, output = "x"),
                        text("x", "m2", "ok"),
                    ),
                )
            )
        val rows = StepListRules.entries(assertNotNull(f.work["m1"]), f.reading.workEntries["m1"].orEmpty())
        assertTrue(rows.all { it is StepEntry.Step }, "no time proves they ran together: $rows")
        assertEquals(4, rows.size)
    }

    @Test
    fun `D1-STEPS-003 the newest five stay in view, the rest fold behind one row naming the declined and the failed`() {
        val f = facts()
        val rows = StepListRules.entries(assertNotNull(f.work["m1"]), f.reading.workEntries["m1"].orEmpty())
        val cut = StepListRules.cut(rows)
        assertEquals(5, cut.shown.size)
        assertEquals(3, cut.earlier.size)
        val fold = assertNotNull(cut.fold)
        assertEquals(3, fold.hidden)
        assertEquals(1, fold.declined)
        assertEquals(1, fold.failed)
        // Six rows fold nothing: one row behind a row saves nothing.
        assertNull(StepListRules.cut(rows.take(6)).fold)
        assertEquals(6, StepListRules.cut(rows.take(6)).shown.size)
        assertEquals(2, assertNotNull(StepListRules.cut(rows.take(7)).fold).hidden)
        // A group's children count inside the fold.
        val group = rows.last() as StepEntry.Group
        val failedGroup = StepEntry.Group(group.steps.map { it.copy(state = StepState.Failed) })
        val withGroup = listOf<StepEntry>(failedGroup) + rows.take(6)
        assertEquals(2, assertNotNull(StepListRules.cut(withGroup).fold).failed)
        val counts = StepListRules.counts(group)
        assertEquals(2, counts.done)
        assertEquals(0, counts.working)
    }

    @Test
    fun `D1-FOOT-001 the footer says each part only when the data has it, never a dollar zero`() {
        val full = TurnMetadataUi(listOf("anthropic/m"), 19_340, 0.06, true, endedAtMs = 74_000, tookMs = 73_000)
        val parts = ReplyFooterRules.parts(full)
        assertEquals(4, parts.size)
        assertIs<ReplyFooterPart.Clock>(parts[0])
        assertEquals(73_000L, assertIs<ReplyFooterPart.Took>(parts[1]).ms)
        assertEquals(TokenAmount(19_340, 19.3, 1, TokenAmount.Scale.K), assertIs<ReplyFooterPart.Tokens>(parts[2]).amount)
        assertEquals(CostWords.Dollars(0.06), assertIs<ReplyFooterPart.Cost>(parts[3]).words)
        // Paseo, Codex, the phone's agents: no tokens or cost on a message: the short form, nothing in their place.
        val short = ReplyFooterRules.parts(full.copy(tokens = 0, cost = 0.0))
        assertEquals(listOf(ReplyFooterPart.Clock::class, ReplyFooterPart.Took::class), short.map { it::class })
        // A free model reports no cost: nothing is said. A cost under half a cent never reads $0.00.
        assertTrue(ReplyFooterRules.parts(full.copy(cost = 0.0)).none { it is ReplyFooterPart.Cost })
        assertEquals(CostWords.UnderACent, ReplyFooterRules.cost(0.003))
        // The model only when it is not the one in use now.
        assertTrue(ReplyFooterRules.parts(full).none { it is ReplyFooterPart.Model })
        assertEquals("GPT 5", assertIs<ReplyFooterPart.Model>(ReplyFooterRules.parts(full.copy(otherModel = "GPT 5"))[2]).name)
        // Nothing known, nothing said.
        assertTrue(ReplyFooterRules.parts(TurnMetadataUi(emptyList(), 0, 0.0, false)).isEmpty())
    }

    @Test
    fun `D1-FOOT-002 tokens are said short`() {
        assertEquals(TokenAmount(950, 950.0, 0, TokenAmount.Scale.None), ReplyFooterRules.tokens(950))
        assertEquals(TokenAmount(2_000, 2.0, 0, TokenAmount.Scale.K), ReplyFooterRules.tokens(2_000))
        assertEquals(TokenAmount(193_400, 193.0, 0, TokenAmount.Scale.K), ReplyFooterRules.tokens(193_400))
        assertEquals(TokenAmount(1_260_000, 1.3, 1, TokenAmount.Scale.M), ReplyFooterRules.tokens(1_260_000))
    }

    @Test
    fun `D1-FOOT-003 the turn's own times, and its model named only when another one answers now`() {
        val f = facts()
        val meta = assertNotNull(f.reading.metadata["m1"])
        assertEquals(74_000L, meta.endedAtMs)
        assertEquals(73_000L, meta.tookMs, "from the prompt to the reply's end")
        assertNull(meta.otherModel, "the only model is the one in use")
        val two =
            facts(
                turn() +
                    listOf(
                        msg(info("m3", Role.User, 90_000), text("p3", "m3", "again")),
                        msg(info("m4", Role.Assistant, 91_000, done = 95_000, model = "other-model"), text("y", "m4", "ok")),
                    )
            )
        assertNotNull(assertNotNull(two.reading.metadata["m1"]).otherModel, "the first turn ran on another model than now")
        assertNull(assertNotNull(two.reading.metadata["m3"]).otherModel, "the newest turn's model is the one in use")
    }

    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    @Composable
    private fun Root(content: @Composable () -> Unit) {
        KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "en", stillFrame = true) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor(), content = content)
        }
    }

    private fun screen(): ChatUi =
        ChatUi.loading(Ref).copy(
            title = "Native chat",
            phase = ChatPhase.Live,
            rows =
                persistentListOf(
                    FooterRow("footer:m1", "m1", failed = false),
                    WorkLineRow("work:m1", "m1", calls = 9, failed = 1, state = WorkState.Done),
                ),
        )

    @Test
    fun `D1-SCREEN-001 the steps show without a summary line, the fold names the problems and opens, the footer says time and cost`() =
        runComposeUiTest {
            val opened = mutableListOf<String>()
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Root { ChatScreen(screen(), actions, composer, facts = facts(), onOpenSession = { opened += it }) } }
            // No summary line: the newest steps are in view at once.
            onAllNodesWithText("Read 1 file", substring = true).assertCountEquals(0)
            onNodeWithText("npm test", substring = true).assertIsDisplayed()
            onNodeWithText("Show 3 earlier steps").assertIsDisplayed()
            onNodeWithText("1 declined · 1 failed").assertIsDisplayed()
            onAllNodesWithText("You declined this").assertCountEquals(0)
            // The two sub-agents are one row, folded; opened, each is named and opens its own conversation.
            onNodeWithText("2 sub-agents").assertIsDisplayed()
            onNodeWithText("2 done").assertIsDisplayed()
            onNodeWithText("2 sub-agents").performClick()
            waitForIdle()
            onNodeWithText("Compare the old copies").assertIsDisplayed().performClick()
            assertEquals(listOf("c2"), opened)
            // The fold opens to every step, the refused one in words.
            onNodeWithText("Show 3 earlier steps").performClick()
            waitForIdle()
            onNodeWithText("Hide earlier steps").assertIsDisplayed()
            onNodeWithText("You declined this").assertExists()
            // The footer: how long, tokens and cost, said once.
            onNodeWithText("1m 13s", substring = true).assertExists()
            onNodeWithText("19.3k tokens", substring = true).assertExists()
            onNodeWithText("$0.06", substring = true).assertExists()
            onNodeWithTag("chat-work-line").assertExists()
        }

    @Test
    fun `D1-SCREEN-002 a reply with no counted tokens or cost ends on its short footer`() =
        runComposeUiTest {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Root { ChatScreen(screen(), actions, composer, facts = facts(turn(cost = 0.0, tokens = Tokens()))) } }
            onNodeWithText("1m 13s", substring = true).assertExists()
            onAllNodesWithText("tokens", substring = true).assertCountEquals(0)
            onAllNodesWithText("$", substring = true).assertCountEquals(0)
        }
}
