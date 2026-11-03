package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.CatalogModel
import io.github.eslamasabry.oc.core.model.CatalogSnapshot
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.Tokens
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ContextLevel
import io.github.eslamasabry.oc.engine.transcript.turnfacts.RunOutcomeKind
import io.github.eslamasabry.oc.engine.transcript.turnfacts.TurnStallNotice
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveNoticeUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** CHAT-TRANSCRIPT: the turn facts from real gateway events to the chat screen's visible words. */
@OptIn(ExperimentalTestApi::class)
class TurnFactsTest {
    private val sid = Ref.sessionId.raw

    private fun info(id: String, role: Role, at: Long, done: Long? = null, tokens: Tokens = Tokens(), finish: String? = null) =
        MessageInfo(id, sid, role, providerId = "anthropic", modelId = "m", tokens = tokens, time = MsgTime(at, done), finish = finish)

    private fun text(id: String, mid: String, words: String) = Part(id, mid, sid, PartType.Text, "text", text = words)

    private fun tool(
        id: String,
        mid: String,
        name: String,
        status: ToolStatus,
        input: Map<String, String> = emptyMap(),
        start: Long? = null,
        end: Long? = null,
        output: String? = null,
        metadata: JsonObject? = null,
    ) =
        Part(
            id,
            mid,
            sid,
            PartType.Tool,
            "tool",
            callId = "call-$id",
            toolName = name,
            tool =
                ToolState(
                    status,
                    input = JsonObject(input.mapValues { JsonPrimitive(it.value) }),
                    output = output,
                    metadata = metadata,
                    startedAtMs = start,
                    completedAtMs = end,
                ),
        )

    private fun msg(info: MessageInfo, vararg parts: Part) = MessageWithParts(info, parts.toList().toImmutableList())

    private val prompt = msg(info("m1", Role.User, 1_000), text("p1", "m1", "fix the build"))

    private fun finishedTurn(): List<MessageWithParts> =
        listOf(
            prompt,
            msg(
                info("m2", Role.Assistant, 2_000, done = 9_000, tokens = Tokens(input = 30_000, output = 2_000, cacheRead = 10_000), finish = "stop"),
                tool("t1", "m2", "read", ToolStatus.Completed, mapOf("filePath" to "/p/src/a.kt"), 2_000, 2_300),
                tool("t2", "m2", "edit", ToolStatus.Completed, mapOf("filePath" to "/p/src/a.kt"), 3_000, 3_400),
                tool("t3", "m2", "bash", ToolStatus.Completed, mapOf("command" to "npm test"), 4_000, 8_200, output = "  BUILD OK  "),
                text("x", "m2", "Fixed."),
            ),
        )

    private fun keeper(): TurnFeedKeeper = TurnFeedKeeper(sid)

    @Test
    fun `B-CHAT-100 B-CHAT-101 B-CHAT-068 a finished turn reports its changed files and commands from the tools`() {
        val k = keeper()
        k.history(finishedTurn())
        val facts = TurnFactsPresenter(FixedClock).present(k.feed.value, 10_000)
        val result = assertNotNull(facts.results["m1"])
        assertEquals(RunOutcomeKind.Completed, result.outcome)
        assertEquals(listOf("/p/src/a.kt"), result.files.map { it.path })
        assertEquals(listOf("npm test"), result.commands.map { it.command })
        assertTrue(result.commands.single().isTest)
    }

    @Test
    fun `B-CHAT-063 B-CHAT-109 B-CHAT-112 the work line holds every step with its duration and its output kept as given`() {
        val k = keeper()
        k.history(finishedTurn())
        val work = assertNotNull(TurnFactsPresenter(FixedClock).present(k.feed.value, 10_000).work["m1"])
        assertEquals(WorkLineState.Done, work.state)
        assertEquals(listOf(300L, 400L, 4_200L), work.steps.map { it.durationMs })
        assertEquals("  BUILD OK  ", work.steps[2].output.text)
    }

    @Test
    fun `B-CHAT-016 a retry is a rate limit only when it is one and counts down to the next try`() {
        val k = keeper()
        k.history(listOf(prompt))
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 1_000)
        k.event(GatewayEvent.RunRetrying(SessionId(sid), 2, 31_000, WireTag("rate limit exceeded"), null), 1_000)
        val live = TurnFactsPresenter(FixedClock).present(k.feed.value, 1_000).live
        val retry = assertIs<LiveNoticeUi.Retry>(live).facts
        assertTrue(retry.rateLimited)
        assertEquals(2, retry.attempt)
        assertEquals(30, retry.secondsLeft)
        k.event(GatewayEvent.RunRetrying(SessionId(sid), 3, null, WireTag("connection reset by peer"), null), 2_000)
        val other = assertIs<LiveNoticeUi.Retry>(TurnFactsPresenter(FixedClock).present(k.feed.value, 2_000).live).facts
        assertEquals(false, other.rateLimited)
        assertNull(other.secondsLeft)
    }

    @Test
    fun `B-CHAT-118 a silent turn reads model slow while connected and connection lost when not, and progress resets it`() {
        val k = keeper()
        k.history(listOf(prompt))
        k.link(ChatLink.Live)
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 0)
        val p = TurnFactsPresenter(FixedClock)
        assertNull(p.present(k.feed.value, 10_000).live)
        assertEquals(LiveNoticeUi.Stall(TurnStallNotice.ModelSlow), p.present(k.feed.value, 60_000).live)
        // Progress resets the silence.
        k.event(GatewayEvent.MessageUpserted(info("m2", Role.Assistant, 61_000)), 61_000)
        assertNull(p.present(k.feed.value, 62_000).live)
        k.link(ChatLink.Offline)
        assertEquals(LiveNoticeUi.Stall(TurnStallNotice.ConnectionLost), p.present(k.feed.value, 120_000).live)
    }

    @Test
    fun `B-CHAT-118 waiting for the person is never a stall`() {
        val k = keeper()
        k.link(ChatLink.Live)
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 0)
        assertNull(TurnFactsPresenter(FixedClock).present(k.feed.value, 120_000, waitingForYou = true).live)
    }

    @Test
    fun `B-CHAT-019 B-CHAT-057 B-CHAT-102 the ring reads the newest reply against its own model and the sheet counts cache`() {
        val k = keeper()
        k.history(finishedTurn())
        assertNull(TurnFactsPresenter(FixedClock).present(k.feed.value, 10_000).context, "no catalog, no ring")
        runBlocking {
            k.loadWindows {
                Outcome.Ok(CatalogSnapshot(persistentListOf(), persistentListOf(model("anthropic", "m", 50_000)), persistentListOf()))
            }
        }
        val context = assertNotNull(TurnFactsPresenter(FixedClock).present(k.feed.value, 10_000).context)
        assertEquals(84, context.percent) // 42,000 of 50,000
        assertEquals(ContextLevel.Warning, context.level)
        assertEquals(42_000, context.sheet.contextTokens)
        assertEquals(10_000, context.sheet.tokens.cacheRead)
    }

    @Test
    fun `B-CHAT-092 a reply is timed from prompt to first output to its end and joins its turn`() {
        val k = keeper()
        k.event(GatewayEvent.MessageUpserted(prompt.info), 1_000)
        k.event(GatewayEvent.PartUpserted(prompt.parts.single()), 1_000)
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 1_000)
        val reply = info("m2", Role.Assistant, 1_500)
        k.event(GatewayEvent.MessageUpserted(reply), 1_500)
        k.event(GatewayEvent.PartUpserted(text("x", "m2", "Hi")), 3_100)
        k.event(GatewayEvent.RunEnded(SessionId(sid), RunOutcome.Succeeded), 6_000)
        val timing = assertNotNull(k.feed.value.timings["m1"])
        assertEquals(2_100, timing.firstTokenMs)
        assertEquals(5_000, timing.totalMs)
        assertEquals(timing, TurnFactsPresenter(FixedClock).present(k.feed.value, 7_000).results["m1"]?.timing)
    }

    @Test
    fun `B-CHAT-063 a stopped run's work reads stopped`() {
        val k = keeper()
        k.history(
            listOf(prompt, msg(info("m2", Role.Assistant, 2_000), tool("t1", "m2", "bash", ToolStatus.Running, start = 2_000)))
        )
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 2_000)
        k.event(GatewayEvent.RunEnded(SessionId(sid), RunOutcome.Interrupted(InterruptReason.User)), 3_000)
        assertEquals(WorkLineState.Stopped, TurnFactsPresenter(FixedClock).present(k.feed.value, 3_000).work["m1"]?.state)
    }

    @Test
    fun `B-CHAT-007 B-CHAT-008 a background command shows by its program until its notice ends it`() {
        val k = keeper()
        val marker = JsonObject(mapOf("backgroundTask" to JsonObject(emptyMap())))
        val started =
            tool("t1", "m2", "bash", ToolStatus.Completed, mapOf("command" to "cd app && ./build_tv.sh --release"), 2_000, 2_100, metadata = marker)
        k.history(listOf(prompt, msg(info("m2", Role.Assistant, 2_000, done = 3_000), started)))
        val running = TurnFactsPresenter(FixedClock).present(k.feed.value, 60_000).background
        assertEquals(1, running.size)
        assertTrue(running.single().name.startsWith("build_tv.sh"), running.single().name)
        val notice =
            tool("n1", "m3", "task_notification", ToolStatus.Completed, mapOf("toolUseId" to "call-t1", "status" to "completed"), 9_000, 9_000)
        k.history(listOf(msg(info("m3", Role.Assistant, 9_000, done = 9_100), notice)))
        assertTrue(TurnFactsPresenter(FixedClock).present(k.feed.value, 60_000).background.isEmpty())
    }

    @Test
    fun `B-CHAT-016 an OpenCode 2 retry reads its reason from the details, so an overload is not a rate limit`() {
        val k = keeper()
        k.history(listOf(prompt))
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 1_000)
        val details = io.github.eslamasabry.oc.core.security.Redactor().details("Overloaded")
        k.event(GatewayEvent.RunRetrying(SessionId(sid), 2, 31_000, null, details), 1_000)
        assertEquals(false, assertIs<LiveNoticeUi.Retry>(TurnFactsPresenter(FixedClock).present(k.feed.value, 1_000).live).facts.rateLimited)
        val reset = io.github.eslamasabry.oc.core.security.Redactor().details("ECONNRESET")
        k.event(GatewayEvent.RunRetrying(SessionId(sid), 3, 31_000, null, reset), 2_000)
        assertEquals(false, assertIs<LiveNoticeUi.Retry>(TurnFactsPresenter(FixedClock).present(k.feed.value, 2_000).live).facts.rateLimited)
        val limit = io.github.eslamasabry.oc.core.security.Redactor().details("429 Too Many Requests")
        k.event(GatewayEvent.RunRetrying(SessionId(sid), 4, 31_000, null, limit), 3_000)
        assertTrue(assertIs<LiveNoticeUi.Retry>(TurnFactsPresenter(FixedClock).present(k.feed.value, 3_000).live).facts.rateLimited)
    }

    @Test
    fun `B-CHAT-118 a model-slow diagnosis does not survive losing the connection`() {
        val k = keeper()
        k.history(listOf(prompt))
        k.link(ChatLink.Live)
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 0)
        val p = TurnFactsPresenter(FixedClock)
        assertEquals(LiveNoticeUi.Stall(TurnStallNotice.ModelSlow), p.present(k.feed.value, 60_000).live)
        // The connection drops with no further progress.
        k.link(ChatLink.Offline)
        assertEquals(LiveNoticeUi.Stall(TurnStallNotice.ConnectionLost), p.present(k.feed.value, 61_000).live)
    }

    @Test
    fun `B-CHAT-007 background work keeps the clock ticking after its turn ends`() {
        val k = keeper()
        val marker = JsonObject(mapOf("backgroundTask" to JsonObject(emptyMap())))
        val started = tool("t1", "m2", "bash", ToolStatus.Completed, mapOf("command" to "./serve.sh"), 0, 100, metadata = marker)
        k.history(listOf(prompt, msg(info("m2", Role.Assistant, 0, done = 200), started)))
        assertEquals(false, k.feed.value.busy)
        assertTrue(TurnFactsPresenter(FixedClock).ticking(k.feed.value), "a quiet chat with background work still ticks")
        assertEquals(false, TurnFactsPresenter(FixedClock).ticking(keeper().also { it.history(finishedTurn()) }.feed.value))
    }

    // ---- The real chat screen, by visible text ----

    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    @Composable
    private fun Root(content: @Composable () -> Unit) {
        KitTheme(
            platform = object : KitPlatform by DesktopKitPlatform() {
                override val isDebug: Boolean = true
            },
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor(), content = content)
        }
    }

    private fun screenFacts(): TurnFactsUi {
        val k = keeper()
        k.history(finishedTurn())
        runBlocking {
            k.loadWindows {
                Outcome.Ok(CatalogSnapshot(persistentListOf(), persistentListOf(model("anthropic", "m", 100_000)), persistentListOf()))
            }
        }
        return TurnFactsPresenter(FixedClock).present(k.feed.value, 10_000)
    }

    private fun screenState(live: LiveTurnUi? = null): ChatUi {
        val rows =
            persistentListOf(
                FooterRow("footer:m1", "m1", failed = false),
                WorkLineRow("work:m1", "m1", calls = 3, failed = 0, state = WorkState.Done),
            )
        return ChatUi.loading(Ref).copy(title = "Native chat", phase = ChatPhase.Live, rows = rows, live = live)
    }

    @Test
    fun `B-CHAT-063 B-CHAT-111 B-CHAT-109 the work line folds the steps and opens to plain-word steps with durations`() =
        runComposeUiTest {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Root { ChatScreen(screenState(), actions, composer, facts = screenFacts()) } }
            onNodeWithText("Read 1 file · edited 1 file · ran 1 command").assertIsDisplayed().performClick()
            onNodeWithText("Shell", substring = true).assertIsDisplayed()
            onNodeWithText("4.2s", substring = true).assertIsDisplayed()
        }

    @Test
    fun `B-CHAT-068 B-CHAT-101 the turn result names the changed file and the command`() =
        runComposeUiTest {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Root { ChatScreen(screenState(), actions, composer, facts = screenFacts()) } }
            onNodeWithText("Edited 1 file · ran 1 command").assertIsDisplayed().performClick()
            onNodeWithText("/p/src/a.kt", substring = true).assertIsDisplayed()
            onNodeWithText("$ npm test", substring = true).assertIsDisplayed()
        }

    @Test
    fun `B-CHAT-019 B-CHAT-102 the context reading opens the context sheet with cache activity`() =
        runComposeUiTest {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Root { ChatScreen(screenState(), actions, composer, facts = screenFacts()) } }
            onNodeWithTag("chat-context-open").assertIsDisplayed().performClick()
            onNodeWithText("Conversation context").assertIsDisplayed()
            onNodeWithText("Cache read").assertIsDisplayed()
        }

    @Test
    fun `B-CHAT-016 the retry folds into the live line in plain words`() =
        runComposeUiTest {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            val facts =
                TurnFactsUi(live = LiveNoticeUi.Retry(io.github.eslamasabry.oc.engine.transcript.turnfacts.RetryBannerFacts(true, 2, 42)))
            setContent { Root { ChatScreen(screenState(LiveTurnUi(stopping = false)), actions, composer, facts = facts) } }
            onNodeWithText("Rate limited. Retrying 2 in 0:42").assertIsDisplayed()
        }

    @Test
    fun `B-CHAT-110 a task step opens the conversation its sub-agent ran in`() =
        runComposeUiTest {
            val k = keeper()
            val task =
                tool(
                    "t9", "m2", "task", ToolStatus.Completed, mapOf("subagent_type" to "explore", "description" to "look"),
                    2_000, 3_000, output = "found it",
                    metadata = JsonObject(mapOf("sessionId" to JsonPrimitive("child-1"))),
                )
            k.history(listOf(prompt, msg(info("m2", Role.Assistant, 2_000, done = 4_000, finish = "stop"), task, text("x", "m2", "ok"))))
            val facts = TurnFactsPresenter(FixedClock).present(k.feed.value, 5_000)
            val opened = mutableListOf<String>()
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Root { ChatScreen(screenState(), actions, composer, facts = facts, onOpenSession = { opened += it }) } }
            onNodeWithText("Delegated 1 task").assertIsDisplayed().performClick()
            onNodeWithTag("chat-step-t9").performClick()
            onNodeWithText("Open its conversation").assertIsDisplayed().performClick()
            assertEquals(listOf("child-1"), opened)
        }

    private val bearerTail = "FIX3Pk6BearerSecret" + "3H4I5J6K7L"

    @Test
    fun `B-SEC-094 a finished turn's command and a running step's label never show a bearer token`() =
        runComposeUiTest {
            val command = "curl -H 'Authorization: Bearer $bearerTail' https://example.test"
            val k = keeper()
            k.history(
                listOf(
                    prompt,
                    msg(
                        info("m2", Role.Assistant, 2_000, done = 4_000, finish = "stop"),
                        tool("t1", "m2", "bash", ToolStatus.Completed, mapOf("command" to command), 2_000, 3_000, output = "ok"),
                        text("x", "m2", "done"),
                    ),
                )
            )
            val facts = TurnFactsPresenter(FixedClock).present(k.feed.value, 5_000)
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Root { ChatScreen(screenState(), actions, composer, facts = facts) } }
            onNodeWithTag("chat-turn-result").onChildren().filterToOne(hasClickAction()).performClick()
            onNodeWithText("$ curl -H 'Authorization:", substring = true).assertIsDisplayed()
            onAllNodesWithText(bearerTail, substring = true).assertCountEquals(0)
        }

    @Test
    fun `B-SEC-094 the folded running work line names the command redacted`() =
        runComposeUiTest {
            val command = "curl -H 'Authorization: Bearer $bearerTail' https://example.test"
            val k = keeper()
            k.history(listOf(prompt, msg(info("m2", Role.Assistant, 2_000), tool("t1", "m2", "bash", ToolStatus.Running, mapOf("command" to command), 2_000))))
            k.event(GatewayEvent.RunStarted(SessionId(sid)), 2_000)
            val facts = TurnFactsPresenter(FixedClock).present(k.feed.value, 3_000)
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Root { ChatScreen(screenState(), actions, composer, facts = facts) } }
            onNodeWithText("curl -H 'Authorization:", substring = true).assertIsDisplayed()
            onAllNodesWithText(bearerTail, substring = true).assertCountEquals(0)
        }

    @Test
    fun `B-CHAT-069 a compaction reaches the transcript as a notice, never a reply`() {
        val compaction = Part("c1", "m5", sid, PartType.Compaction, "compaction")
        val bridged = io.github.eslamasabry.oc.feature.chat.world.P3Bridge(sid).message(msg(info("m5", Role.User, 5_000), compaction))
        val part = bridged.parts.single()
        assertEquals(io.github.eslamasabry.oc.engine.transcript.state.PartType.Compaction, part.type)
        assertEquals(io.github.eslamasabry.oc.engine.transcript.state.NoticeKind.Compaction.name, part.text)
    }

    private fun model(provider: String, id: String, window: Int) =
        CatalogModel(
            id = id,
            providerId = provider,
            name = id,
            family = null,
            enabled = true,
            status = "active",
            contextLimit = window,
            outputLimit = 0,
            reasoning = false,
            attachments = false,
            tools = true,
            variants = persistentListOf(),
            cost = null,
            releasedAtMs = null,
        )

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }
}
