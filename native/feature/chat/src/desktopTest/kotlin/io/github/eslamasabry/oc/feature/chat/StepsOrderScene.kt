package io.github.eslamasabry.oc.feature.chat

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
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.ThoughtRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Scenes for reply step ordering (stretches between visible paragraphs) and live folded-group identity.
 */
internal object StepsOrderScene {
    private val sid = Ref.sessionId.raw
    const val TURN = "m1"
    const val ASK = "Fix why the player freezes on Android TV"
    const val THOUGHT = "Look at the player code first."
    const val A1 =
        "I found the cause: the player starts its codec on the main thread, " +
            "so the screen freezes and Android closes it."
    const val A2 =
        "The codec now starts in the background and the player stays open; " +
            "the tests pass on the TV build as well."

    private val readFiles =
        listOf(
            "/work/demo/CLAUDE.md",
            "/work/demo/pubspec.yaml",
            "/work/demo/README.md",
            "/work/demo/main.kt",
            "/work/demo/app.kt",
            "/work/demo/player.kt",
            "/work/demo/codec.kt",
            "/work/demo/theme.kt",
            "/work/demo/audio.kt",
            "/work/demo/video.kt",
            "/work/demo/surface.kt",
            "/work/demo/buffer.kt",
            "/work/demo/clock.kt",
            "/work/demo/render.kt",
            "/work/demo/tests.kt",
        )

    private fun info(id: String, role: Role, at: Long, done: Long?) =
        MessageInfo(
            id,
            sid,
            role,
            providerId = "anthropic",
            modelId = "claude-demo",
            time = MsgTime(at, done),
            finish = if (done != null && role == Role.Assistant) "stop" else null,
        )

    private fun thought(id: String, words: String) =
        Part(id, "m2", sid, PartType.Reasoning, "reasoning", text = words, ended = true)

    private fun text(id: String, words: String) = Part(id, "m2", sid, PartType.Text, "text", text = words)

    private fun tool(
        id: String,
        name: String,
        input: Map<String, String>,
        status: ToolStatus,
        span: Pair<Long, Long?>,
    ) =
        Part(
            id,
            "m2",
            sid,
            PartType.Tool,
            "tool",
            callId = "call-$id",
            toolName = name,
            tool =
                ToolState(
                    status,
                    input = JsonObject(input.mapValues { JsonPrimitive(it.value) }),
                    output = "ok",
                    startedAtMs = span.first,
                    completedAtMs = span.second,
                ),
        )

    private val promptMsg =
        MessageWithParts(
            info(TURN, Role.User, 0, 0),
            persistentListOf(Part("p1", TURN, sid, PartType.Text, "text", text = ASK)),
        )

    private fun c1() =
        tool("c1", "bash", mapOf("command" to "git status --short"), ToolStatus.Completed, 2_000L to 3_000L)

    private fun f1() =
        tool("f1", "read", mapOf("filePath" to "/work/demo/README.md"), ToolStatus.Completed, 4_000L to 5_000L)

    private fun e1(status: ToolStatus = ToolStatus.Completed) =
        tool(
            "e1",
            "edit",
            mapOf("filePath" to "/work/demo/player.kt"),
            status,
            6_000L to if (status == ToolStatus.Completed) 7_000L else null,
        )

    private fun c2(status: ToolStatus = ToolStatus.Completed) =
        tool(
            "c2",
            "bash",
            mapOf("command" to "./gradlew test"),
            status,
            8_000L to if (status == ToolStatus.Completed) 9_000L else null,
        )

    private fun readStep(n: Int): Part {
        val start = 2_000L + n * 1_000L
        return tool("r$n", "read", mapOf("filePath" to readFiles[n - 1]), ToolStatus.Completed, start to (start + 400))
    }

    fun done(): List<MessageWithParts> =
        listOf(
            promptMsg,
            MessageWithParts(
                info("m2", Role.Assistant, 1_000, 12_000),
                listOf(
                    thought("r0", THOUGHT),
                    c1(),
                    f1(),
                    text("a1", A1),
                    e1(),
                    c2(),
                    text("a2", A2),
                ).toImmutableList(),
            ),
        )

    fun running(): List<MessageWithParts> =
        listOf(
            promptMsg,
            MessageWithParts(
                info("m2", Role.Assistant, 1_000, null),
                listOf(
                    thought("r0", THOUGHT),
                    c1(),
                    f1(),
                    text("a1", A1),
                    e1(ToolStatus.Running),
                ).toImmutableList(),
            ),
        )

    fun runningDoneSteps(): List<MessageWithParts> =
        listOf(
            promptMsg,
            MessageWithParts(
                info("m2", Role.Assistant, 1_000, null),
                listOf(c1(), f1()).toImmutableList(),
            ),
        )

    fun thoughtAlone(): List<MessageWithParts> =
        listOf(
            promptMsg,
            MessageWithParts(
                info("m2", Role.Assistant, 1_000, 12_000),
                listOf(
                    thought("r0", THOUGHT),
                    text("a1", A1),
                    c1(),
                    text("a2", A2),
                ).toImmutableList(),
            ),
        )

    /**
     * Replays the owner's recording of a running reply:
     * stage 0 = 8 completed reads while the model pauses to think (no active tool);
     * stage 1 = 15 completed reads + a running command;
     * stage 2 = 15 completed reads + the command finished, model thinking again.
     */
    fun liveReads(stage: Int): List<MessageWithParts> {
        val parts =
            buildList {
                val count = if (stage == 0) 8 else 15
                for (n in 1..count) add(readStep(n))
                if (stage == 1) add(c2(ToolStatus.Running))
                if (stage == 2) add(c2(ToolStatus.Completed))
            }
        return listOf(
            promptMsg,
            MessageWithParts(info("m2", Role.Assistant, 1_000, null), parts.toImmutableList()),
        )
    }

    fun doneSingleRun(): List<MessageWithParts> =
        listOf(
            promptMsg,
            MessageWithParts(
                info("m2", Role.Assistant, 1_000, 12_000),
                buildList {
                    for (n in 1..8) add(readStep(n))
                    add(text("a2", A2))
                }.toImmutableList(),
            ),
        )

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    fun doneFacts(): TurnFactsUi {
        val k = TurnFeedKeeper(sid)
        k.history(done(), newest = true)
        k.idle()
        return TurnFactsPresenter(FixedClock).present(k.feed.value, 13_000)
    }

    fun runningFacts(): TurnFactsUi {
        val k = TurnFeedKeeper(sid)
        k.history(running())
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 0)
        return TurnFactsPresenter(FixedClock).present(k.feed.value, 10_000)
    }

    fun runningDoneStepsFacts(): TurnFactsUi {
        val k = TurnFeedKeeper(sid)
        k.history(runningDoneSteps())
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 0)
        return TurnFactsPresenter(FixedClock).present(k.feed.value, 10_000)
    }

    fun thoughtAloneFacts(): TurnFactsUi {
        val k = TurnFeedKeeper(sid)
        k.history(thoughtAlone(), newest = true)
        k.idle()
        return TurnFactsPresenter(FixedClock).present(k.feed.value, 13_000)
    }

    fun liveReadsFacts(stage: Int): TurnFactsUi {
        val k = TurnFeedKeeper(sid)
        k.history(liveReads(stage))
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 0)
        return TurnFactsPresenter(FixedClock).present(k.feed.value, 33_000)
    }

    fun doneSingleRunFacts(): TurnFactsUi {
        val k = TurnFeedKeeper(sid)
        k.history(doneSingleRun(), newest = true)
        k.idle()
        return TurnFactsPresenter(FixedClock).present(k.feed.value, 13_000)
    }

    fun doneState(): ChatUi =
        ChatUi.loading(Ref).copy(
            title = ASK,
            phase = ChatPhase.Live,
            rows =
                persistentListOf(
                    FooterRow("footer:$TURN", TURN, failed = false),
                    block(TURN, "m2", "a2", 0, A2),
                    block(TURN, "m2", "a1", 0, A1),
                    WorkLineRow("work:$TURN", TURN, calls = 4, failed = 0, state = WorkState.Done),
                    ThoughtRow("thought:m2/r0", TURN, THOUGHT, THOUGHT),
                    prompt(TURN, ASK),
                ),
        )

    fun runningState(): ChatUi =
        ChatUi.loading(Ref).copy(
            title = ASK,
            phase = ChatPhase.Live,
            rows =
                persistentListOf(
                    block(TURN, "m2", "a1", 0, A1),
                    WorkLineRow("work:$TURN", TURN, calls = 3, failed = 0, state = WorkState.Running),
                    ThoughtRow("thought:m2/r0", TURN, THOUGHT, THOUGHT),
                    prompt(TURN, ASK),
                ),
            live = LiveTurnUi(stopping = false),
        )

    fun liveReadsState(stage: Int): ChatUi =
        ChatUi.loading(Ref).copy(
            title = ASK,
            phase = ChatPhase.Live,
            rows =
                persistentListOf(
                    WorkLineRow(
                        "work:$TURN",
                        TURN,
                        calls = if (stage == 0) 8 else 16,
                        failed = 0,
                        state = if (stage == 1) WorkState.Running else WorkState.Done,
                    ),
                    prompt(TURN, ASK),
                ),
            live = LiveTurnUi(stopping = false),
        )

    fun doneSingleRunState(): ChatUi =
        ChatUi.loading(Ref).copy(
            title = ASK,
            phase = ChatPhase.Live,
            rows =
                persistentListOf(
                    FooterRow("footer:$TURN", TURN, failed = false),
                    block(TURN, "m2", "a2", 0, A2),
                    WorkLineRow("work:$TURN", TURN, calls = 8, failed = 0, state = WorkState.Done),
                    prompt(TURN, ASK),
                ),
        )
}
