package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.chip.KitPhraseChips
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.tool.STEP_FOLD_ROW_TAG
import io.github.eslamasabry.oc.ui.kitchat.transcript.LocalTranscriptAnchor
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptAnchor
import kotlin.math.abs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalTestApi::class)
internal object ScrollAnchorWalks {
    private val sid = Ref.sessionId.raw
    private const val TURN_A = "a-m1"
    private const val MSG_A = "a-m2"
    private const val TURN_B = "b-m1"
    private const val MSG_B = "b-m2"
    private const val ASK_B = "Check the player logs and run the test suite"
    private const val EXTRA_REPLY_BLOCKS = 30
    private const val WIDE_MIN_EXTRA_PX = 200f
    private const val TOTAL_STEPS = 10
    private const val FRAMES_PER_STEP = 12
    private const val END_FRAMES = 40
    private const val SETTLE_MS = 900L
    private const val TOLERANCE_PX = 0.5f
    private const val PHONE_W = 360
    private const val PHONE_H = 800
    private const val WIDE_W = 840

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                Column(modifier) {
                    KitPhraseChips(
                        phrases = listOf("Continue", "Run the tests", "Commit this"),
                        label = "Quick phrases",
                        insertLabel = { it },
                        menuLabel = { it },
                        hideLabel = "Hide",
                        onChoose = {},
                        onHide = {},
                        away = chrome.busy(),
                    )
                    KitText("Composer slot")
                }
            }
        }

    @Composable
    private fun Root(
        anchor: TranscriptAnchor,
        direction: LayoutDirection = LayoutDirection.Ltr,
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(
            LocalTranscriptAnchor provides anchor,
            LocalLayoutDirection provides direction,
        ) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Full),
                localeTag = "en",
                stillFrame = false,
            ) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor(), content = content)
            }
        }
    }

    private fun info(id: String, role: Role, at: Long, done: Long?): MessageInfo =
        MessageInfo(
            id = id,
            sessionId = sid,
            role = role,
            providerId = "anthropic",
            modelId = "claude-demo",
            time = MsgTime(at, done),
            finish = if (done != null && role == Role.Assistant) "stop" else null,
        )

    private fun doneTurnA(): List<MessageWithParts> {
        val base = StepsCraftScene.done()
        val userA =
            MessageWithParts(
                info = base[0].info.copy(id = TURN_A),
                parts =
                    base[0]
                        .parts
                        .map { p -> p.copy(id = "a-${p.id}", messageId = TURN_A) }
                        .toImmutableList(),
            )
        val baseAssistantParts =
            base[1].parts.map { p ->
                p.copy(
                    id = "a-${p.id}",
                    messageId = MSG_A,
                    callId = p.callId?.let { "a-$it" },
                )
            }
        val extraReplies =
            (1..EXTRA_REPLY_BLOCKS).map { i ->
                Part("a-rb$i", MSG_A, sid, PartType.Text, "text", text = StepsCraftScene.REPLY)
            }
        val assistantA =
            MessageWithParts(
                info = base[1].info.copy(id = MSG_A),
                parts = (baseAssistantParts + extraReplies).toImmutableList(),
            )
        return listOf(userA, assistantA)
    }

    private fun bashPart(n: Int, status: ToolStatus): Part {
        val start = 102_000L + n * 5_000L
        return Part(
            id = "b-c$n",
            messageId = MSG_B,
            sessionId = sid,
            type = PartType.Tool,
            rawType = "tool",
            callId = "call-b-c$n",
            toolName = "bash",
            tool =
                ToolState(
                    status = status,
                    input = JsonObject(mapOf("command" to JsonPrimitive(StepsCraftScene.commands[n - 1]))),
                    output = "ok",
                    startedAtMs = start,
                    completedAtMs = if (status == ToolStatus.Completed) start + 2_400L else null,
                ),
        )
    }

    private fun turnB(steps: Int, ended: Boolean): List<MessageWithParts> {
        val userB =
            MessageWithParts(
                info = info(TURN_B, Role.User, 100_000L, 100_000L),
                parts = persistentListOf(Part("b-p1", TURN_B, sid, PartType.Text, "text", text = ASK_B)),
            )
        val parts =
            (1..steps).map { n ->
                val status = if (!ended && n == steps) ToolStatus.Running else ToolStatus.Completed
                bashPart(n, status)
            }
        val assistantB =
            MessageWithParts(
                info = info(MSG_B, Role.Assistant, 101_000L, if (ended) 160_000L else null),
                parts = parts.toImmutableList(),
            )
        return listOf(userB, assistantB)
    }

    private fun sceneFacts(steps: Int, ended: Boolean): TurnFactsUi {
        val keeper = TurnFeedKeeper(sid)
        keeper.history(doneTurnA() + turnB(steps, ended), newest = ended)
        if (ended) keeper.idle() else keeper.event(GatewayEvent.RunStarted(SessionId(sid)), 100_000L)
        return TurnFactsPresenter(FixedClock).present(keeper.feed.value, 160_000L)
    }

    private fun sceneState(steps: Int, ended: Boolean): ChatUi {
        val rows =
            buildList {
                add(
                    WorkLineRow(
                        key = "work:$TURN_B",
                        turnId = TURN_B,
                        calls = steps,
                        failed = 0,
                        state = if (ended) WorkState.Done else WorkState.Running,
                    )
                )
                add(prompt(TURN_B, ASK_B))
                add(FooterRow("footer:$TURN_A", TURN_A, failed = false))
                for (i in EXTRA_REPLY_BLOCKS downTo 1) {
                    add(block(TURN_A, MSG_A, "a-rb$i", 0, StepsCraftScene.REPLY))
                }
                add(block(TURN_A, MSG_A, "a-r", 0, StepsCraftScene.REPLY))
                add(WorkLineRow("work:$TURN_A", TURN_A, calls = 13, failed = 0, state = WorkState.Done))
                add(prompt(TURN_A, StepsCraftScene.ASK))
            }
        return ChatUi.loading(Ref).copy(
            title = StepsCraftScene.ASK,
            phase = ChatPhase.Live,
            rows = rows.toPersistentList(),
            live = if (ended) null else LiveTurnUi(stopping = false),
        )
    }

    private data class TrackedNode(val id: Int, val label: String, val top: Float)

    private data class Drift(val maxPx: Float, val frame: Int, val node: String)

    private fun nodeLabel(node: SemanticsNode): String? {
        val tag = if (SemanticsProperties.TestTag in node.config) node.config[SemanticsProperties.TestTag] else null
        if (!tag.isNullOrBlank()) return tag
        val text =
            if (SemanticsProperties.Text in node.config) {
                node.config[SemanticsProperties.Text].joinToString(" ") { it.text }.trim()
            } else {
                null
            }
        return text?.takeIf { it.isNotBlank() }?.take(48)
    }

    private fun collectDescendants(root: SemanticsNode, out: MutableList<SemanticsNode>) {
        for (child in root.children) {
            out += child
            collectDescendants(child, out)
        }
    }

    private fun ComposeUiTest.headerBottom(): Float =
        onAllNodesWithTag("kit-screen-header", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot
            ?.bottom ?: 0f

    private fun ComposeUiTest.composerTop(): Float =
        onAllNodesWithTag("chat-composer-pane", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot
            ?.top ?: Float.MAX_VALUE

    private fun ComposeUiTest.visibleNodes(): Map<Int, TrackedNode> {
        val listNode = onNodeWithTag("transcript-list", useUnmergedTree = true).fetchSemanticsNode()
        val topBound = headerBottom()
        val bottomBound = composerTop()
        val all = ArrayList<SemanticsNode>()
        collectDescendants(listNode, all)
        val out = LinkedHashMap<Int, TrackedNode>()
        for (n in all) {
            val label = nodeLabel(n) ?: continue
            val b = n.boundsInRoot
            if (b.height > 0f && b.top >= topBound && b.bottom <= bottomBound) {
                out[n.id] = TrackedNode(n.id, label, b.top)
            }
        }
        return out
    }

    private fun ComposeUiTest.currentTops(): Map<Int, Float> {
        val listNode = onNodeWithTag("transcript-list", useUnmergedTree = true).fetchSemanticsNode()
        val all = ArrayList<SemanticsNode>()
        collectDescendants(listNode, all)
        val out = HashMap<Int, Float>(all.size * 2)
        for (n in all) out[n.id] = n.boundsInRoot.top
        return out
    }

    private fun ComposeUiTest.checkFrame(
        baseline: Map<Int, TrackedNode>,
        frame: Int,
        worst: Drift,
    ): Drift {
        val now = currentTops()
        var best = worst
        for ((id, tracked) in baseline) {
            val y = now[id] ?: continue
            val delta = abs(y - tracked.top)
            if (delta > best.maxPx) best = Drift(delta, frame, tracked.label)
        }
        return best
    }

    private fun ComposeUiTest.jumpControlShown(): Boolean =
        onAllNodesWithTag("jump-to-latest", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .any { it.boundsInRoot.height > 0f && it.boundsInRoot.width > 0f }

    private fun assertNoDrift(drift: Drift, context: String) {
        assertTrue(
            drift.maxPx <= TOLERANCE_PX,
            "$context: max=${"%.1f".format(drift.maxPx)} frame=${drift.frame} node=${drift.node}",
        )
    }

    private fun assertPageSpan(anchor: TranscriptAnchor, width: Int, required: Float) {
        kitUiTest(width, 4000) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            mainClock.autoAdvance = false
            setContent {
                Root(anchor) {
                    ChatScreen(sceneState(steps = 1, ended = false), actions, composer, facts = sceneFacts(1, false))
                }
            }
            mainClock.advanceTimeBy(SETTLE_MS)
            val listNode = onNodeWithTag("transcript-list", useUnmergedTree = true).fetchSemanticsNode()
            val all = ArrayList<SemanticsNode>()
            collectDescendants(listNode, all)
            val measured = all.filter { it.boundsInRoot.height > 0f }
            val span =
                if (measured.isEmpty()) 0f
                else measured.maxOf { it.boundsInRoot.bottom } - measured.minOf { it.boundsInRoot.top }
            println("ScrollAnchorWalks span ${width}x$PHONE_H=${"%.1f".format(span)} px (required >= $required px)")
            assertTrue(
                span >= required,
                "scene height at ${width}x$PHONE_H must be >= $required px, measured=$span px",
            )
        }
    }

    private fun assertPageTwoScreensTall(anchor: TranscriptAnchor) {
        assertPageSpan(anchor, PHONE_W, PHONE_H * 2f)
    }

    private fun ComposeUiTest.dragUp(dpAmount: Float) {
        onNodeWithTag("transcript-list", useUnmergedTree = true).performTouchInput {
            val slop = viewConfiguration.touchSlop
            var remaining = dpAmount.dp.toPx()
            while (remaining > 0f) {
                val chunk = minOf(remaining, height * 0.6f)
                val start = Offset(centerX, top + height * 0.2f)
                down(start)
                moveBy(Offset(0f, slop))
                moveBy(Offset(0f, chunk))
                advanceEventTime(200)
                up()
                remaining -= chunk
            }
        }
        mainClock.advanceTimeByFrame()
    }

    private fun ComposeUiTest.dragDownToEnd() {
        repeat(3) {
            onNodeWithTag("transcript-list", useUnmergedTree = true).performTouchInput {
                val slop = viewConfiguration.touchSlop
                val start = Offset(centerX, bottom - height * 0.2f)
                down(start)
                moveBy(Offset(0f, -slop))
                moveBy(Offset(0f, -(height * 0.6f)))
                advanceEventTime(200)
                up()
            }
            repeat(5) { mainClock.advanceTimeByFrame() }
        }
    }

    private fun ComposeUiTest.findGroupTag(): String {
        val summary = onAllNodesWithTag("chat-work-summary", useUnmergedTree = true).fetchSemanticsNodes()
        if (summary.isNotEmpty()) return "chat-work-summary"
        val fold = onAllNodesWithTag(STEP_FOLD_ROW_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        if (fold.isNotEmpty()) return STEP_FOLD_ROW_TAG
        throw AssertionError("neither chat-work-summary nor $STEP_FOLD_ROW_TAG is on screen")
    }

    fun walk001Following(anchor: TranscriptAnchor) {
        assertPageTwoScreensTall(anchor)
        kitUiTest(PHONE_W, PHONE_H) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var step by mutableIntStateOf(1)
            mainClock.autoAdvance = false
            setContent {
                Root(anchor) {
                    ChatScreen(sceneState(step, ended = false), actions, composer, facts = sceneFacts(step, false))
                }
            }
            mainClock.advanceTimeBy(SETTLE_MS)
            val baseBottom = boundsOfTag("chat-live-line").bottom
            var worst = Drift(0f, 0, "chat-live-line")
            var frame = 0
            for (k in 2..TOTAL_STEPS) {
                step = k
                repeat(FRAMES_PER_STEP) {
                    frame++
                    mainClock.advanceTimeByFrame()
                    val now = boundsOfTag("chat-live-line").bottom
                    val delta = abs(now - baseBottom)
                    if (delta > worst.maxPx) worst = Drift(delta, frame, "chat-live-line")
                }
            }
            assertNoDrift(worst, "001 following")
        }
    }

    fun walk002ScrolledUp40(
        anchor: TranscriptAnchor,
        width: Int = PHONE_W,
        height: Int = PHONE_H,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        if (width == PHONE_W && direction == LayoutDirection.Ltr) assertPageTwoScreensTall(anchor)
        kitUiTest(width, height) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var step by mutableIntStateOf(1)
            var ended by mutableStateOf(false)
            mainClock.autoAdvance = false
            setContent {
                Root(anchor, direction) {
                    ChatScreen(sceneState(step, ended), actions, composer, facts = sceneFacts(step, ended))
                }
            }
            mainClock.advanceTimeBy(SETTLE_MS)
            dragUp(40f)
            val baseline = visibleNodes()
            assertTrue(baseline.isNotEmpty(), "visible set must not be empty after 40 dp drag")
            var worst = Drift(0f, 0, "none")
            var frame = 0
            var missingControlFrame = -1
            for (k in 2..TOTAL_STEPS) {
                step = k
                repeat(FRAMES_PER_STEP) {
                    frame++
                    mainClock.advanceTimeByFrame()
                    worst = checkFrame(baseline, frame, worst)
                    if (!jumpControlShown() && missingControlFrame < 0) missingControlFrame = frame
                }
            }
            ended = true
            repeat(END_FRAMES) {
                frame++
                mainClock.advanceTimeByFrame()
                worst = checkFrame(baseline, frame, worst)
                if (!jumpControlShown() && missingControlFrame < 0) missingControlFrame = frame
            }
            assertNoDrift(worst, "002 scrolled up 40dp (${width}x$height $direction)")
            assertTrue(
                missingControlFrame < 0,
                "002 jump-to-latest hidden: max=0.0 frame=$missingControlFrame node=jump-to-latest",
            )
        }
    }

    fun walk003ScrolledUpScreen(anchor: TranscriptAnchor) {
        assertPageTwoScreensTall(anchor)
        kitUiTest(PHONE_W, PHONE_H) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var step by mutableIntStateOf(1)
            var ended by mutableStateOf(false)
            mainClock.autoAdvance = false
            setContent {
                Root(anchor) {
                    ChatScreen(sceneState(step, ended), actions, composer, facts = sceneFacts(step, ended))
                }
            }
            mainClock.advanceTimeBy(SETTLE_MS)
            dragUp(PHONE_H.toFloat())
            val baseline = visibleNodes()
            assertTrue(baseline.isNotEmpty(), "visible set must not be empty after full-screen drag")
            var worst = Drift(0f, 0, "none")
            var frame = 0
            for (k in 2..TOTAL_STEPS) {
                step = k
                repeat(FRAMES_PER_STEP) {
                    frame++
                    mainClock.advanceTimeByFrame()
                    worst = checkFrame(baseline, frame, worst)
                }
            }
            ended = true
            repeat(END_FRAMES) {
                frame++
                mainClock.advanceTimeByFrame()
                worst = checkFrame(baseline, frame, worst)
            }
            assertNoDrift(worst, "003 scrolled up screen")
        }
    }

    fun walk004TapGroup(
        anchor: TranscriptAnchor,
        width: Int = PHONE_W,
        height: Int = PHONE_H,
    ) {
        if (width == PHONE_W) assertPageTwoScreensTall(anchor)
        kitUiTest(width, height) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            mainClock.autoAdvance = false
            setContent {
                Root(anchor) {
                    ChatScreen(
                        sceneState(TOTAL_STEPS, ended = true),
                        actions,
                        composer,
                        facts = sceneFacts(TOTAL_STEPS, ended = true),
                    )
                }
            }
            mainClock.advanceTimeBy(SETTLE_MS)
            val tag = findGroupTag()
            val baseline = visibleNodes()
            assertTrue(baseline.isNotEmpty(), "visible set must not be empty before tap")
            var worst = Drift(0f, 0, "none")
            var frame = 0
            var missingControlFrame = -1
            fun tapGroup() {
                onNodeWithTag(tag, useUnmergedTree = true).performTouchInput {
                    down(center)
                    up()
                }
            }
            tapGroup()
            repeat(END_FRAMES) {
                frame++
                mainClock.advanceTimeByFrame()
                worst = checkFrame(baseline, frame, worst)
                if (!jumpControlShown() && missingControlFrame < 0) missingControlFrame = frame
            }
            tapGroup()
            repeat(END_FRAMES) {
                frame++
                mainClock.advanceTimeByFrame()
                worst = checkFrame(baseline, frame, worst)
                if (!jumpControlShown() && missingControlFrame < 0) missingControlFrame = frame
            }
            assertNoDrift(worst, "004 tap group (${width}x$height)")
            assertTrue(
                missingControlFrame < 0,
                "004 jump-to-latest hidden: max=0.0 frame=$missingControlFrame node=jump-to-latest",
            )
        }
    }

    fun walk005WideAndRtl(anchor: TranscriptAnchor) {
        assertPageSpan(anchor, WIDE_W, PHONE_H + WIDE_MIN_EXTRA_PX)
        walk002ScrolledUp40(anchor, width = WIDE_W, height = PHONE_H)
        walk004TapGroup(anchor, width = WIDE_W, height = PHONE_H)
        walk002ScrolledUp40(anchor, width = PHONE_W, height = PHONE_H, direction = LayoutDirection.Rtl)
    }

    fun walk006OnlyPersonResumesFollow(anchor: TranscriptAnchor) {
        assertPageTwoScreensTall(anchor)
        kitUiTest(PHONE_W, PHONE_H) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            mainClock.autoAdvance = false
            setContent {
                Root(anchor) {
                    ChatScreen(
                        sceneState(TOTAL_STEPS, ended = true),
                        actions,
                        composer,
                        facts = sceneFacts(TOTAL_STEPS, ended = true),
                    )
                }
            }
            mainClock.advanceTimeBy(SETTLE_MS)
            val tag = findGroupTag()
            onNodeWithTag(tag, useUnmergedTree = true).performTouchInput {
                down(center)
                up()
            }
            var missingFrame = -1
            repeat(60) { i ->
                mainClock.advanceTimeByFrame()
                if (!jumpControlShown() && missingFrame < 0) missingFrame = i + 1
            }
            assertTrue(
                missingFrame < 0,
                "006 control did not stay shown: max=0.0 frame=$missingFrame node=jump-to-latest",
            )
            dragDownToEnd()
            mainClock.advanceTimeBy(SETTLE_MS)
            assertTrue(
                !jumpControlShown(),
                "006 control still shown after drag to end: max=0.0 frame=61 node=jump-to-latest",
            )
        }
    }
}
