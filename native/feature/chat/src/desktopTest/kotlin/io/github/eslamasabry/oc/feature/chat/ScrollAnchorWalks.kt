package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
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
import io.github.eslamasabry.oc.feature.chat.ui.ScrollPlaceStore
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.FlickerMode
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiOptions
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.chip.KitPhraseChips
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.tool.STEP_FOLD_ROW_TAG
import io.github.eslamasabry.oc.ui.kitchat.transcript.LocalTranscriptAnchor
import io.github.eslamasabry.oc.ui.kitchat.transcript.LocalTranscriptProbe
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptAnchor
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val PROBE_CAP = 120
private const val PROBE_TAIL = 3

private class ProbeLog {
    private val lines = ArrayDeque<String>(PROBE_CAP)
    private val flips = mutableListOf<String>()
    private var lastOrder: String? = null
    private var lastLine: String? = null

    val sink: (String) -> Unit = { line ->
        if (lines.size >= PROBE_CAP) lines.removeFirst()
        lines.addLast(line)
        val order = line.substringAfter("order=", "").substringBefore(' ')
        if (lastOrder != null && order != lastOrder) {
            flips += "before=${lastLine ?: "none"} -> after=$line"
        }
        lastOrder = order
        lastLine = line
    }

    fun latest(): String? = lines.lastOrNull()

    fun tail(): String = " | probe=" + lines.takeLast(PROBE_TAIL).joinToString(" ; ")

    fun printFlips(label: String) {
        for (f in flips) println("PROBE_FLIP [$label] $f")
    }
}

private object ScrollWalkScene {
    private val sid = Ref.sessionId.raw
    private const val TURN_A = "a-m1"
    private const val MSG_A = "a-m2"
    private const val TURN_B = "b-m1"
    private const val MSG_B = "b-m2"
    private const val ASK_B = "Check the player logs and run the test suite"
    private const val EXTRA_REPLY_BLOCKS = 30

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    val composer =
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
                parts = base[0].parts.map { p -> p.copy(id = "a-${p.id}", messageId = TURN_A) }.toImmutableList(),
            )
        val baseAssistantParts =
            base[1].parts.map { p ->
                p.copy(id = "a-${p.id}", messageId = MSG_A, callId = p.callId?.let { "a-$it" })
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

    fun facts(steps: Int, ended: Boolean): TurnFactsUi {
        val keeper = TurnFeedKeeper(sid)
        keeper.history(doneTurnA() + turnB(steps, ended), newest = ended)
        if (ended) keeper.idle() else keeper.event(GatewayEvent.RunStarted(SessionId(sid)), 100_000L)
        return TurnFactsPresenter(FixedClock).present(keeper.feed.value, 160_000L)
    }

    fun state(steps: Int, ended: Boolean): ChatUi {
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
}

private const val WALK_TOLERANCE_PX = 0.5f

private data class TrackedNode(val id: Int, val label: String, val top: Float)

private data class Drift(val maxPx: Float, val frame: Int, val node: String)

private fun findingsFile(): File {
    val pid = ProcessHandle.current().pid()
    val dir = File(System.getProperty("oc.flicker.dir") ?: "build/flicker")
    return File(dir, "findings-$pid.tsv")
}

private fun readFindingLines(): List<String> =
    findingsFile().takeIf { it.isFile }?.readLines().orEmpty().filter { !it.startsWith("#") && it.isNotBlank() }

private fun withFlickerReport(label: String, block: () -> Unit) {
    val before = readFindingLines().toSet()
    block()
    val added = readFindingLines().filter { it !in before }
    if (added.isEmpty()) {
        println("FLICKER [$label] 0")
    } else {
        val summary =
            added.joinToString("; ") { row ->
                val cols = row.split('\t')
                if (cols.size >= 4) "${cols[1]}:${cols[2]}(x${cols[3]})" else row
            }
        println("FLICKER [$label] ${added.size}: $summary")
    }
}

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

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.headerBottom(): Float =
    onAllNodesWithTag("kit-screen-header", useUnmergedTree = true)
        .fetchSemanticsNodes()
        .firstOrNull()
        ?.boundsInRoot
        ?.bottom ?: 0f

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.composerTop(): Float =
    onAllNodesWithTag("chat-composer-pane", useUnmergedTree = true)
        .fetchSemanticsNodes()
        .firstOrNull()
        ?.boundsInRoot
        ?.top ?: Float.MAX_VALUE

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.newestRowBottom(): Float? {
    val live = onAllNodesWithTag("chat-live-line", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
    if (live != null) return live.boundsInRoot.bottom
    val work = onAllNodesWithTag("chat-work-line", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
    return work?.boundsInRoot?.bottom
}

@OptIn(ExperimentalTestApi::class)
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

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.currentTops(): Map<Int, Float> {
    val listNode = onNodeWithTag("transcript-list", useUnmergedTree = true).fetchSemanticsNode()
    val all = ArrayList<SemanticsNode>()
    collectDescendants(listNode, all)
    val out = HashMap<Int, Float>(all.size * 2)
    for (n in all) out[n.id] = n.boundsInRoot.top
    return out
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.checkFrame(baseline: Map<Int, TrackedNode>, frame: Int, worst: Drift): Drift {
    val now = currentTops()
    var best = worst
    for ((id, tracked) in baseline) {
        val y = now[id] ?: continue
        val delta = abs(y - tracked.top)
        if (delta > best.maxPx) best = Drift(delta, frame, tracked.label)
    }
    return best
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.jumpControlShown(): Boolean =
    onAllNodesWithTag("jump-to-latest", useUnmergedTree = true)
        .fetchSemanticsNodes()
        .any { it.boundsInRoot.height > 0f && it.boundsInRoot.width > 0f }

private fun assertNoDrift(drift: Drift, context: String, probe: ProbeLog) {
    assertTrue(
        drift.maxPx <= WALK_TOLERANCE_PX,
        "$context: max=${"%.1f".format(drift.maxPx)} frame=${drift.frame} node=${drift.node}${probe.tail()}",
    )
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.transcriptScrollAxis(): ScrollAxisRange {
    val node = onNodeWithTag("transcript-list", useUnmergedTree = true).fetchSemanticsNode()
    return node.config[SemanticsProperties.VerticalScrollAxisRange]
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.a11yScroll(amountPx: Float, towardNewer: Boolean, label: String, probe: ProbeLog) {
    val range = transcriptScrollAxis()
    val visual = if (towardNewer) amountPx else -amountPx
    val y = if (range.reverseScrolling) -visual else visual
    val canScroll = if (y > 0f) range.value() < range.maxValue() else range.value() > 0f
    assertTrue(
        canScroll,
        "$label a11y action unavailable (towardNewer=$towardNewer rev=${range.reverseScrolling} " +
            "val=${range.value()} max=${range.maxValue()})${probe.tail()}",
    )
    onNodeWithTag("transcript-list", useUnmergedTree = true)
        .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, y) }
}

@Composable
private fun ScrollWalkRoot(
    anchor: TranscriptAnchor,
    direction: LayoutDirection = LayoutDirection.Ltr,
    probe: ((String) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTranscriptAnchor provides anchor,
        LocalLayoutDirection provides direction,
        LocalTranscriptProbe provides probe,
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

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.captureRender(fileName: String) {
    val out = File("build/s1-o5-render/$fileName")
    out.parentFile.mkdirs()
    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertTodayA11yHeld(
    label: String,
    firstRow: TrackedNode?,
    worst: Drift,
    probe: ProbeLog,
) {
    assertTrue(firstRow != null, "$label first visible row missing${probe.tail()}")
    val endTop = currentTops()[firstRow!!.id]
    val move = if (endTop != null) abs(endTop - firstRow.top) else Float.MAX_VALUE
    assertTrue(
        move <= WALK_TOLERANCE_PX,
        "$label first visible row moved ${"%.1f".format(move)} px (node=${firstRow.label})${probe.tail()}",
    )
    assertNoDrift(worst, "$label today a11y holds still", probe)
    assertTrue(
        probe.latest()?.contains("following=false") == true,
        "$label expected following=false after a11y scroll and new step${probe.tail()}",
    )
}

@OptIn(ExperimentalTestApi::class)
private fun runTodayA11yStep(
    label: String,
    fileName: String,
    assertAfter: Boolean,
    scrollUp: ComposeUiTest.(ProbeLog, CoroutineScope) -> Unit,
) {
    kitUiTest(A11Y_PHONE_W, A11Y_PHONE_H, options = KitUiOptions(mode = FlickerMode.Report)) {
        ScrollPlaceStore.clear()
        val probe = ProbeLog()
        val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        var step by mutableIntStateOf(A11Y_START_STEPS)
        var ended by mutableStateOf(true)
        lateinit var scope: CoroutineScope
        mainClock.autoAdvance = false
        setContent {
            scope = rememberCoroutineScope()
            ScrollWalkRoot(TranscriptAnchor.Today, probe = probe.sink) {
                ChatScreen(
                    ScrollWalkScene.state(step, ended),
                    actions,
                    ScrollWalkScene.composer,
                    facts = ScrollWalkScene.facts(step, ended),
                )
            }
        }
        repeat(A11Y_STEP_FRAMES) { mainClock.advanceTimeByFrame() }
        val initialTop = probe.latest()?.substringAfter("top=", "")
        scrollUp(probe, scope)
        repeat(A11Y_STEP_FRAMES) { mainClock.advanceTimeByFrame() }
        assertTrue(
            probe.latest()?.substringAfter("top=", "") != initialTop,
            "$label did not scroll up before step arrived${probe.tail()}",
        )
        if (!assertAfter) {
            assertTrue(
                probe.latest()?.contains("following=true") == true,
                "$label expected following=true on BEFORE path${probe.tail()}",
            )
        }
        val baseline = visibleNodes()
        val firstRow = baseline.values.minByOrNull { it.top }
        step = A11Y_NEXT_STEPS
        ended = false
        var worst = Drift(0f, 0, "none")
        repeat(A11Y_STEP_FRAMES) { i ->
            mainClock.advanceTimeByFrame()
            if (assertAfter) worst = checkFrame(baseline, i + 1, worst)
        }
        captureRender(fileName)
        if (assertAfter) assertTodayA11yHeld(label, firstRow, worst, probe)
    }
}

private const val A11Y_PHONE_W = 360
private const val A11Y_PHONE_H = 800
private const val A11Y_START_STEPS = 10
private const val A11Y_NEXT_STEPS = 11
private const val A11Y_STEP_FRAMES = 60
private const val A11Y_SCROLL_UP_PX = 480f
private const val GROUP_TAP_LEFT_INSET_PX = 24f
private const val GROUP_TAP_FRAMES = 40
private const val GROUP_SETTLE_MS = 900L

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.findNewestGroupTag(): String {
    val summary = onAllNodesWithTag("chat-work-summary", useUnmergedTree = true).fetchSemanticsNodes()
    if (summary.isNotEmpty()) return "chat-work-summary"
    val fold = onAllNodesWithTag(STEP_FOLD_ROW_TAG, useUnmergedTree = true).fetchSemanticsNodes()
    if (fold.isNotEmpty()) return STEP_FOLD_ROW_TAG
    throw AssertionError("neither chat-work-summary nor $STEP_FOLD_ROW_TAG is on screen")
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.tapNewestGroupOutsideControl(
    label: String,
    tag: String,
    tapNum: Int,
    frame: Int,
    probe: ProbeLog,
) {
    val rowBounds = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val tapX = rowBounds.left + GROUP_TAP_LEFT_INSET_PX
    val tapY = (rowBounds.top + rowBounds.bottom) / 2f
    val jumps = onAllNodesWithTag("jump-to-latest", useUnmergedTree = true).fetchSemanticsNodes()
    for (j in jumps) {
        val jb = j.boundsInRoot
        val inside = tapX in jb.left..jb.right && tapY in jb.top..jb.bottom
        assertTrue(
            !inside,
            "$label tap #$tapNum ($tapX,$tapY) inside jump-to-latest $jb (row $rowBounds): " +
                "max=0.0 frame=$frame node=tap-overlap${probe.tail()}",
        )
    }
    onRoot().performTouchInput {
        down(Offset(tapX, tapY))
        up()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertTapNewestGroupKeepsFollowing(
    label: String,
    tag: String,
    baseline: Map<Int, TrackedNode>,
    probe: ProbeLog,
) {
    var worst = Drift(0f, 0, "none")
    var frame = 0
    var shownControlFrame = -1
    var lostFollowFrame = -1
    fun advanceCheck() {
        repeat(GROUP_TAP_FRAMES) {
            frame++
            mainClock.advanceTimeByFrame()
            worst = checkFrame(baseline, frame, worst)
            if (jumpControlShown() && shownControlFrame < 0) shownControlFrame = frame
            if (probe.latest()?.contains("following=true") != true && lostFollowFrame < 0) lostFollowFrame = frame
        }
    }
    tapNewestGroupOutsideControl(label, tag, tapNum = 1, frame = frame, probe = probe)
    advanceCheck()
    assertTrue(
        shownControlFrame < 0,
        "$label jump-to-latest mounted while opening newest group: " +
            "max=0.0 frame=$shownControlFrame node=jump-to-latest${probe.tail()}",
    )
    assertTrue(
        lostFollowFrame < 0,
        "$label following turned false while opening newest group: " +
            "max=0.0 frame=$lostFollowFrame node=following${probe.tail()}",
    )
    tapNewestGroupOutsideControl(label, tag, tapNum = 2, frame = frame, probe = probe)
    advanceCheck()
    assertTrue(
        shownControlFrame < 0,
        "$label jump-to-latest mounted while closing newest group: " +
            "max=0.0 frame=$shownControlFrame node=jump-to-latest${probe.tail()}",
    )
    assertTrue(
        lostFollowFrame < 0,
        "$label following turned false while closing newest group: " +
            "max=0.0 frame=$lostFollowFrame node=following${probe.tail()}",
    )
    assertNoDrift(worst, "$label tap group", probe)
}

@OptIn(ExperimentalTestApi::class)
private fun runOpenNewestGroupKeepsFollowing(anchor: TranscriptAnchor) {
    withFlickerReport("018") {
        kitUiTest(A11Y_PHONE_W, A11Y_PHONE_H, options = KitUiOptions(mode = FlickerMode.Report)) {
            ScrollPlaceStore.clear()
            val probe = ProbeLog()
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            mainClock.autoAdvance = false
            setContent {
                ScrollWalkRoot(anchor, probe = probe.sink) {
                    ChatScreen(
                        ScrollWalkScene.state(A11Y_START_STEPS, ended = true),
                        actions,
                        ScrollWalkScene.composer,
                        facts = ScrollWalkScene.facts(A11Y_START_STEPS, ended = true),
                    )
                }
            }
            mainClock.advanceTimeBy(GROUP_SETTLE_MS)
            assertTrue(
                probe.latest()?.contains("following=true") == true,
                "018 expected following=true before tap${probe.tail()}",
            )
            assertTrue(!jumpControlShown(), "018 expected jump-to-latest hidden before tap${probe.tail()}")
            val tag = findNewestGroupTag()
            val baseline = visibleNodes()
            assertTrue(baseline.isNotEmpty(), "018 visible set must not be empty before tap${probe.tail()}")
            assertTapNewestGroupKeepsFollowing("018", tag, baseline, probe)
            probe.printFlips("018")
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal object ScrollAnchorWalks {
    private const val WIDE_MIN_EXTRA_PX = 200f
    private const val TOTAL_STEPS = 10
    private const val FRAMES_PER_STEP = 12
    private const val END_FRAMES = 40
    private const val GLIDE_FRAMES = 60
    private const val GLIDE_DRAG_DP = 200f
    private const val SETTLE_MS = 900L
    private const val TOLERANCE_PX = WALK_TOLERANCE_PX
    private const val PHONE_W = 360
    private const val PHONE_H = 800
    private const val WIDE_W = 840
    private const val SMALL_LEAVE_DRAG_DP = 40f
    private const val A11Y_UP_PX = 480f
    private const val A11Y_DOWN_PX = 2400f
    private const val A11Y_INDEX = 6
    private val REPORT_OPTIONS = KitUiOptions(mode = FlickerMode.Report)

    @Composable
    private fun Root(
        anchor: TranscriptAnchor,
        direction: LayoutDirection = LayoutDirection.Ltr,
        probe: ((String) -> Unit)? = null,
        content: @Composable () -> Unit,
    ) = ScrollWalkRoot(anchor, direction, probe, content)

    private val composer: ComposerHost = ScrollWalkScene.composer

    private fun sceneFacts(steps: Int, ended: Boolean): TurnFactsUi = ScrollWalkScene.facts(steps, ended)

    private fun sceneState(steps: Int, ended: Boolean): ChatUi = ScrollWalkScene.state(steps, ended)

    private fun assertPageSpan(anchor: TranscriptAnchor, width: Int, required: Float) {
        kitUiTest(width, 4000, options = REPORT_OPTIONS) {
            ScrollPlaceStore.clear()
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
        mainClock.advanceTimeBy(SETTLE_MS)
    }

    private fun ComposeUiTest.assertDragPrecondition(context: String, probe: ProbeLog) {
        assertTrue(
            jumpControlShown(),
            "$context: jump-to-latest hidden after drag: max=0.0 frame=0 node=precondition${probe.tail()}",
        )
        val live = onAllNodesWithTag("chat-live-line", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
        val boxTop = composerTop()
        if (live != null) {
            assertTrue(
                live.boundsInRoot.bottom > boxTop,
                "$context: live bottom ${live.boundsInRoot.bottom} <= composerTop $boxTop: " +
                    "max=0.0 frame=0 node=precondition${probe.tail()}",
            )
        }
    }

    private fun ComposeUiTest.dragDownToEndCheckingBlank(baseBottom: Float, probe: ProbeLog) {
        var minBottom = Float.MAX_VALUE
        var worstFrame = 0
        var frame = 0
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
            repeat(5) {
                frame++
                mainClock.advanceTimeByFrame()
                val cur = newestRowBottom()
                if (cur != null && cur < minBottom) {
                    minBottom = cur
                    worstFrame = frame
                }
            }
        }
        if (minBottom != Float.MAX_VALUE) {
            val blankPast = (baseBottom - minBottom).coerceAtLeast(0f)
            assertTrue(
                blankPast <= TOLERANCE_PX,
                "006 blank past newest during drag: max=${"%.1f".format(blankPast)} " +
                    "frame=$worstFrame node=chat-work-line${probe.tail()}",
            )
        }
    }

    private fun ComposeUiTest.captureO5BlankRoom() {
        val out = File("build/s1-o5-render/o5-blank-room-360.png")
        out.parentFile.mkdirs()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
    }

    private fun probeHasOrderAndRoom(line: String?): Boolean {
        if (line == null) return false
        val isNormal = "order=N" in line
        val room = line.substringAfter("room=", "0").substringBefore(' ').toIntOrNull() ?: 0
        return isNormal && room > 0
    }

    fun walk001Following(anchor: TranscriptAnchor) {
        assertPageTwoScreensTall(anchor)
        withFlickerReport("001") {
            kitUiTest(PHONE_W, PHONE_H, options = REPORT_OPTIONS) {
                ScrollPlaceStore.clear()
                val probe = ProbeLog()
                val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
                var step by mutableIntStateOf(1)
                mainClock.autoAdvance = false
                setContent {
                    Root(anchor, probe = probe.sink) {
                        ChatScreen(sceneState(step, ended = false), actions, composer, facts = sceneFacts(step, false))
                    }
                }
                mainClock.advanceTimeBy(SETTLE_MS)
                val baseBottom = boundsOfTag("chat-live-line").bottom
                var worst = Drift(0f, 0, "chat-live-line")
                var badOrderFrame = -1
                var frame = 0
                for (k in 2..TOTAL_STEPS) {
                    step = k
                    repeat(FRAMES_PER_STEP) {
                        frame++
                        mainClock.advanceTimeByFrame()
                        val now = boundsOfTag("chat-live-line").bottom
                        val delta = abs(now - baseBottom)
                        if (delta > worst.maxPx) worst = Drift(delta, frame, "chat-live-line")
                        val p = probe.latest()
                        if (p != null && "order=R" !in p && badOrderFrame < 0) badOrderFrame = frame
                    }
                }
                assertNoDrift(worst, "001 following", probe)
                assertTrue(
                    badOrderFrame < 0,
                    "001 expected order=R: max=0.0 frame=$badOrderFrame node=probe-order${probe.tail()}",
                )
            }
        }
    }

    private class WalkRunState(val captureBlank: Boolean) {
        var worst = Drift(0f, 0, "none")
        var frame = 0
        var missingControlFrame = -1
        var badProbeFrame = -1
        var maxBlank = 0f
        var captured = false
    }

    private fun ComposeUiTest.stepReadingFrame(
        baseline: Map<Int, TrackedNode>,
        probe: ProbeLog,
        run: WalkRunState,
    ) {
        run.frame++
        mainClock.advanceTimeByFrame()
        run.worst = checkFrame(baseline, run.frame, run.worst)
        if (!jumpControlShown() && run.missingControlFrame < 0) run.missingControlFrame = run.frame
        if (!probeHasOrderAndRoom(probe.latest()) && run.badProbeFrame < 0) run.badProbeFrame = run.frame
        val bottom = newestRowBottom() ?: return
        val blank = (composerTop() - bottom).coerceAtLeast(0f)
        if (blank > run.maxBlank) run.maxBlank = blank
        val wantPng = run.captureBlank && !run.captured
        if (wantPng && run.frame == 1 && blank > 1f) {
            captureO5BlankRoom()
            run.captured = true
        }
    }

    fun walk002ScrolledUp40(
        anchor: TranscriptAnchor,
        width: Int = PHONE_W,
        height: Int = PHONE_H,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        if (width == PHONE_W && direction == LayoutDirection.Ltr) assertPageTwoScreensTall(anchor)
        val label = "002-${width}x$height-$direction"
        withFlickerReport(label) {
            kitUiTest(width, height, options = REPORT_OPTIONS) {
                ScrollPlaceStore.clear()
                val probe = ProbeLog()
                val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
                var step by mutableIntStateOf(1)
                var ended by mutableStateOf(false)
                mainClock.autoAdvance = false
                setContent {
                    Root(anchor, direction, probe.sink) {
                        ChatScreen(sceneState(step, ended), actions, composer, facts = sceneFacts(step, ended))
                    }
                }
                mainClock.advanceTimeBy(SETTLE_MS)
                dragUp(40f)
                assertDragPrecondition(label, probe)
                val baseline = visibleNodes()
                assertTrue(baseline.isNotEmpty(), "visible set must not be empty after 40 dp drag${probe.tail()}")
                val run = WalkRunState(captureBlank = width == PHONE_W && direction == LayoutDirection.Ltr)
                for (k in 2..TOTAL_STEPS) {
                    step = k
                    repeat(FRAMES_PER_STEP) { stepReadingFrame(baseline, probe, run) }
                }
                ended = true
                repeat(END_FRAMES) { stepReadingFrame(baseline, probe, run) }
                println("ScrollAnchorWalks blank ($label)=${"%.1f".format(run.maxBlank)} px")
                probe.printFlips(label)
                assertNoDrift(run.worst, "002 scrolled up 40dp (${width}x$height $direction)", probe)
                assertTrue(
                    run.missingControlFrame < 0,
                    "002 jump-to-latest hidden: max=0.0 frame=${run.missingControlFrame} " +
                        "node=jump-to-latest${probe.tail()}",
                )
                assertTrue(
                    run.badProbeFrame < 0,
                    "002 expected order=N room>0: max=0.0 frame=${run.badProbeFrame} node=probe${probe.tail()}",
                )
            }
        }
    }

    fun walk003ScrolledUpScreen(anchor: TranscriptAnchor) {
        assertPageTwoScreensTall(anchor)
        withFlickerReport("003") {
            kitUiTest(PHONE_W, PHONE_H, options = REPORT_OPTIONS) {
                ScrollPlaceStore.clear()
                val probe = ProbeLog()
                val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
                var step by mutableIntStateOf(1)
                var ended by mutableStateOf(false)
                mainClock.autoAdvance = false
                setContent {
                    Root(anchor, probe = probe.sink) {
                        ChatScreen(sceneState(step, ended), actions, composer, facts = sceneFacts(step, ended))
                    }
                }
                mainClock.advanceTimeBy(SETTLE_MS)
                dragUp(PHONE_H.toFloat())
                assertDragPrecondition("003", probe)
                val baseline = visibleNodes()
                assertTrue(baseline.isNotEmpty(), "visible set must not be empty after full-screen drag${probe.tail()}")
                var worst = Drift(0f, 0, "none")
                var frame = 0
                var missingControlFrame = -1
                var badProbeFrame = -1
                fun stepFrame() {
                    frame++
                    mainClock.advanceTimeByFrame()
                    worst = checkFrame(baseline, frame, worst)
                    if (!jumpControlShown() && missingControlFrame < 0) missingControlFrame = frame
                    if (!probeHasOrderAndRoom(probe.latest()) && badProbeFrame < 0) badProbeFrame = frame
                }
                for (k in 2..TOTAL_STEPS) {
                    step = k
                    repeat(FRAMES_PER_STEP) { stepFrame() }
                }
                ended = true
                repeat(END_FRAMES) { stepFrame() }
                assertNoDrift(worst, "003 scrolled up screen", probe)
                assertTrue(
                    missingControlFrame < 0,
                    "003 jump-to-latest hidden: max=0.0 frame=$missingControlFrame node=jump-to-latest${probe.tail()}",
                )
                assertTrue(
                    badProbeFrame < 0,
                    "003 expected order=N room>0: max=0.0 frame=$badProbeFrame node=probe${probe.tail()}",
                )
            }
        }
    }

    fun walk004TapGroup(anchor: TranscriptAnchor, width: Int = PHONE_W, height: Int = PHONE_H) {
        if (width == PHONE_W) assertPageTwoScreensTall(anchor)
        val label = "004-${width}x$height"
        withFlickerReport(label) {
            kitUiTest(width, height, options = REPORT_OPTIONS) {
                ScrollPlaceStore.clear()
                val probe = ProbeLog()
                val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
                mainClock.autoAdvance = false
                setContent {
                    Root(anchor, probe = probe.sink) {
                        ChatScreen(
                            sceneState(TOTAL_STEPS, ended = true),
                            actions,
                            composer,
                            facts = sceneFacts(TOTAL_STEPS, ended = true),
                        )
                    }
                }
                mainClock.advanceTimeBy(SETTLE_MS)
                val tag = findNewestGroupTag()
                val baseBottom = boundsOfTag("chat-work-line").bottom
                val baseline = visibleNodes()
                assertTrue(baseline.isNotEmpty(), "visible set must not be empty before tap${probe.tail()}")
                assertTapNewestGroupKeepsFollowing("004 (${width}x$height)", tag, baseline, probe)
                verifyJumpControlGlide(label, baseBottom, probe)
                probe.printFlips(label)
            }
        }
    }

    private fun ComposeUiTest.workLineBottom(): Float? =
        onAllNodesWithTag("chat-work-line", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot
            ?.bottom

    private fun ComposeUiTest.verifyJumpControlGlide(label: String, baseBottom: Float, probe: ProbeLog) {
        dragUp(GLIDE_DRAG_DP)
        assertTrue(
            jumpControlShown(),
            "004 jump-to-latest hidden after 200 dp drag: max=0.0 frame=0 node=jump-to-latest${probe.tail()}",
        )
        val jumpNode = onNodeWithTag("jump-to-latest", useUnmergedTree = true).fetchSemanticsNode()
        val startBottom = workLineBottom()
        onRoot().performTouchInput {
            down(jumpNode.boundsInRoot.center)
            up()
        }
        var shownFrame = -1
        val bottoms = ArrayList<Float?>(GLIDE_FRAMES)
        repeat(GLIDE_FRAMES) { i ->
            mainClock.advanceTimeByFrame()
            if (jumpControlShown() && shownFrame < 0) shownFrame = i + 1
            bottoms += workLineBottom()
        }
        assertTrue(
            shownFrame < 0,
            "004 control still shown after jump tap: max=0.0 frame=$shownFrame node=jump-to-latest${probe.tail()}",
        )
        val endBottom = bottoms.lastOrNull()
        assertTrue(
            endBottom != null,
            "004 chat-work-line missing after glide: max=0.0 frame=$GLIDE_FRAMES node=chat-work-line${probe.tail()}",
        )
        val endDelta = abs(endBottom!! - baseBottom)
        assertTrue(
            endDelta <= TOLERANCE_PX,
            "004 newest row not back on box: max=${"%.1f".format(endDelta)} " +
                "frame=$GLIDE_FRAMES node=chat-work-line${probe.tail()}",
        )
        val midFrames = countGlideMidFrames(startBottom, endBottom, bottoms)
        println("ScrollAnchorWalks glide ($label): start=$startBottom end=$endBottom midFrames=$midFrames")
        assertTrue(
            midFrames >= 2,
            "004 jump did not glide (start=$startBottom end=$endBottom midFrames=$midFrames): " +
                "max=0.0 frame=1 node=jump-to-latest${probe.tail()}",
        )
    }

    private fun countGlideMidFrames(startBottom: Float?, endBottom: Float, bottoms: List<Float?>): Int =
        if (startBottom != null) {
            val lo = minOf(startBottom, endBottom) + TOLERANCE_PX
            val hi = maxOf(startBottom, endBottom) - TOLERANCE_PX
            bottoms.count { it != null && it > lo && it < hi }
        } else {
            bottoms.count { it != null && it > endBottom + TOLERANCE_PX }
        }

    fun walk005WideAndRtl(anchor: TranscriptAnchor) {
        assertPageSpan(anchor, WIDE_W, PHONE_H + WIDE_MIN_EXTRA_PX)
        walk002ScrolledUp40(anchor, width = WIDE_W, height = PHONE_H)
        walk004TapGroup(anchor, width = WIDE_W, height = PHONE_H)
        walk002ScrolledUp40(anchor, width = PHONE_W, height = PHONE_H, direction = LayoutDirection.Rtl)
    }

    fun walk006OnlyPersonResumesFollow(anchor: TranscriptAnchor) {
        assertPageTwoScreensTall(anchor)
        withFlickerReport("006") {
            kitUiTest(PHONE_W, PHONE_H, options = REPORT_OPTIONS) {
                ScrollPlaceStore.clear()
                val probe = ProbeLog()
                val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
                mainClock.autoAdvance = false
                setContent {
                    Root(anchor, probe = probe.sink) {
                        ChatScreen(
                            sceneState(TOTAL_STEPS, ended = true),
                            actions,
                            composer,
                            facts = sceneFacts(TOTAL_STEPS, ended = true),
                        )
                    }
                }
                mainClock.advanceTimeBy(SETTLE_MS)
                val baseBottom = boundsOfTag("chat-work-line").bottom
                dragUp(SMALL_LEAVE_DRAG_DP)
                var missingFrame = -1
                repeat(GLIDE_FRAMES) { i ->
                    mainClock.advanceTimeByFrame()
                    if (!jumpControlShown() && missingFrame < 0) missingFrame = i + 1
                }
                assertTrue(
                    missingFrame < 0,
                    "006 control did not stay shown: max=0.0 frame=$missingFrame node=jump-to-latest${probe.tail()}",
                )
                dragDownToEndCheckingBlank(baseBottom, probe)
                mainClock.advanceTimeBy(SETTLE_MS)
                assertTrue(
                    !jumpControlShown(),
                    "006 control still shown after drag to end: max=0.0 frame=61 node=jump-to-latest${probe.tail()}",
                )
                val endBottom = boundsOfTag("chat-work-line").bottom
                val endDelta = abs(endBottom - baseBottom)
                assertTrue(
                    endDelta <= TOLERANCE_PX,
                    "006 newest row not on box after drag: max=${"%.1f".format(endDelta)} " +
                        "frame=61 node=chat-work-line${probe.tail()}",
                )
            }
        }
    }

    fun walk007ScreenReaderScrollBy(anchor: TranscriptAnchor) {
        walkScreenReader(anchor, "007") { probe ->
            a11yScroll(A11Y_UP_PX, towardNewer = false, label = "007", probe = probe)
        }
    }

    fun walk008ScreenReaderScrollToIndex(anchor: TranscriptAnchor) {
        walkScreenReader(anchor, "008") { _ ->
            onNodeWithTag("transcript-list", useUnmergedTree = true).performScrollToIndex(A11Y_INDEX)
        }
    }

    private fun walkScreenReader(
        anchor: TranscriptAnchor,
        label: String,
        firstMove: ComposeUiTest.(ProbeLog) -> Unit,
    ) {
        withFlickerReport(label) {
            kitUiTest(PHONE_W, PHONE_H, options = REPORT_OPTIONS) {
                ScrollPlaceStore.clear()
                val probe = ProbeLog()
                val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
                mainClock.autoAdvance = false
                setContent {
                    Root(anchor, probe = probe.sink) {
                        ChatScreen(
                            sceneState(TOTAL_STEPS, ended = true),
                            actions,
                            composer,
                            facts = sceneFacts(TOTAL_STEPS, ended = true),
                        )
                    }
                }
                mainClock.advanceTimeBy(SETTLE_MS)
                val baseBottom = boundsOfTag("chat-work-line").bottom
                assertTrue(
                    transcriptScrollAxis().reverseScrolling,
                    "$label expected reverseScrolling=true while following${probe.tail()}",
                )
                val beforeTop = probe.latest()?.substringAfter("top=", "")
                firstMove(probe)
                repeat(GLIDE_FRAMES) { mainClock.advanceTimeByFrame() }
                assertTrue(
                    jumpControlShown(),
                    "$label control hidden after first move: " +
                        "max=0.0 frame=$GLIDE_FRAMES node=jump-to-latest${probe.tail()}",
                )
                assertTrue(
                    probe.latest()?.substringAfter("top=", "") != beforeTop,
                    "$label first visible row did not move after scroll up${probe.tail()}",
                )
                assertTrue(
                    !transcriptScrollAxis().reverseScrolling,
                    "$label expected reverseScrolling=false while reading${probe.tail()}",
                )
                a11yScroll(A11Y_DOWN_PX, towardNewer = true, label = label, probe = probe)
                verifyScreenReaderReturn(label, baseBottom, probe)
                assertTrue(
                    transcriptScrollAxis().reverseScrolling,
                    "$label expected reverseScrolling=true after return to following${probe.tail()}",
                )
            }
        }
    }

    private fun ComposeUiTest.verifyScreenReaderReturn(label: String, baseBottom: Float, probe: ProbeLog) {
        var minBottom = Float.MAX_VALUE
        var worstFrame = 0
        repeat(GLIDE_FRAMES) { i ->
            mainClock.advanceTimeByFrame()
            val cur = newestRowBottom()
            if (cur != null && cur < minBottom) {
                minBottom = cur
                worstFrame = i + 1
            }
        }
        if (minBottom != Float.MAX_VALUE) {
            val blankPast = (baseBottom - minBottom).coerceAtLeast(0f)
            assertTrue(
                blankPast <= TOLERANCE_PX,
                "$label newest row rose into room: max=${"%.1f".format(blankPast)} " +
                    "frame=$worstFrame node=chat-work-line${probe.tail()}",
            )
        }
        assertTrue(
            !jumpControlShown(),
            "$label control still shown after return: " +
                "max=0.0 frame=$GLIDE_FRAMES node=jump-to-latest${probe.tail()}",
        )
        val endBottom = boundsOfTag("chat-work-line").bottom
        val endDelta = abs(endBottom - baseBottom)
        assertTrue(
            endDelta <= TOLERANCE_PX,
            "$label newest row not back on box: max=${"%.1f".format(endDelta)} " +
                "frame=$GLIDE_FRAMES node=chat-work-line${probe.tail()}",
        )
    }

    fun walk017TodayScreenReaderHoldsStill() {
        withFlickerReport("017") {
            runTodayA11yStep("017-before", "today-a11y-before-360.png", assertAfter = false) { _, scope ->
                val scrollByOffset =
                    onNodeWithTag("transcript-list", useUnmergedTree = true)
                        .fetchSemanticsNode()
                        .config[SemanticsActions.ScrollByOffset]
                scope.launch { scrollByOffset(Offset(0f, A11Y_SCROLL_UP_PX)) }
            }
            runTodayA11yStep("017-after", "today-a11y-after-360.png", assertAfter = true) { probe, _ ->
                a11yScroll(A11Y_SCROLL_UP_PX, towardNewer = false, label = "017-after", probe = probe)
            }
        }
    }

    fun walk018OpenNewestGroupKeepsFollowing(anchor: TranscriptAnchor) {
        runOpenNewestGroupKeepsFollowing(anchor)
    }
}
