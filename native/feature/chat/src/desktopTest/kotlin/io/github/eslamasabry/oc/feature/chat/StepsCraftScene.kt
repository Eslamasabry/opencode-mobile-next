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
 * The owner's turn of 2026-11-14 (a long agent turn on the phone), with made-up names of the same shape: eleven commands
 * that all open with `cd <project>; . <toolchain>/env.sh;`, two other steps (a tool search by id, a background task
 * stopped), and the agent's own words between them; then the reply. [running] is the same turn while its eleventh
 * command runs (the words are still the reply's prose then, not steps). [arrived] adds an edit after it, running.
 */
internal object StepsCraftScene {
    private val sid = Ref.sessionId.raw
    const val TURN = "m1"
    const val TURN_OLD = "m0"
    const val ASK = "Look into why the player closes on the TV"
    const val OLD_ASK = "Summarise how the build pipeline works"
    const val REPLY = "The player no longer closes: the codec now starts off the main thread, and the tests pass."

    /** Earlier answer paragraphs above the running turn, tall enough to scroll 40 dp or 500+ dp up. */
    val earlierParagraphs: List<String> =
        (1..14).map { n ->
            "Earlier paragraph $n: the build pipeline compiles each module, runs the desktop checks, " +
                "and writes the report before packing the archive for review."
        }
    private const val CD = "cd /home/dev/projects/demo_app;"
    private const val ENV = ". /home/dev/projects/.toolchain/env.sh;"

    /** The commands in order (c1..c11) and when each ran. */
    val commands: List<String> =
        listOf(
            "$CD $ENV flutter test test/player_test.dart 2>&1 | tail -30",
            "$CD grep -n \"crash\\|onError\" lib/player.dart | head -20",
            "$CD $ENV ./gradlew :app:assembleDebug -q 2>&1 | tail -5",
            "$CD $ENV adb install -r build/app/outputs/app-debug.apk",
            "$CD adb logcat -d | grep -i player | tail -40",
            "$CD pkill -f flutter_tester 2>/dev/null; echo done",
            "cat /tmp/agent-1000/-home-dev-projects-demo-app/acfb6579-2c1d-4e8a.output",
            "pgrep -af \"flutter_teste[r]\" | cut -c1-80 | head -3; echo ---",
            "$CD $ENV flutter test 2>&1 | tail -5",
            "$CD git status --short",
            "$CD grep -n \"crash\\|Crash\\|onError\" lib/player.dart",
        )

    /** The agent's words between steps, whole sentences. */
    val words: List<String> =
        listOf(
            "I'll start by running the player tests to see the crash.",
            "Two findings: the player test fails, and the error handler hides it.",
            "That command printed the crash line, so the cause is the codec setup.",
            "I checked the TV before changing anything: it runs the old build.",
            "My fix moves the codec setup off the main thread.",
            "While the tests run, here is what changed.",
        )

    private fun info(id: String, role: Role, at: Long, done: Long?) =
        MessageInfo(
            id, sid, role,
            providerId = "anthropic", modelId = "claude-demo",
            time = MsgTime(at, done),
            finish = if (done != null && role == Role.Assistant) "stop" else null,
        )

    private fun text(id: String, words: String) = Part(id, "m2", sid, PartType.Text, "text", text = words)

    private fun tool(id: String, name: String, input: Map<String, String>, status: ToolStatus, start: Long, end: Long?) =
        Part(
            id, "m2", sid, PartType.Tool, "tool",
            callId = "call-$id",
            toolName = name,
            tool =
                ToolState(
                    status,
                    input = JsonObject(input.mapValues { JsonPrimitive(it.value) }),
                    output = "ok",
                    startedAtMs = start,
                    completedAtMs = end,
                ),
        )

    private fun bash(n: Int, status: ToolStatus = ToolStatus.Completed): Part {
        val start = 2_000L + n * 5_000L
        return tool("c$n", "bash", mapOf("command" to commands[n - 1]), status, start, if (status == ToolStatus.Completed) start + 2_400 else null)
    }

    private val search = tool("t1", "ToolSearch", mapOf("query" to "select:TaskStop"), ToolStatus.Completed, 31_000, 31_200)
    private val stop = tool("t2", "TaskStop", mapOf("task_id" to "b7x9q2"), ToolStatus.Completed, 31_500, 31_600)

    /** The finished turn: words, commands and the two other steps in the owner's order, then the reply. */
    fun done(): List<MessageWithParts> =
        listOf(
            MessageWithParts(info(TURN, Role.User, 0, 0), persistentListOf(Part("p1", TURN, sid, PartType.Text, "text", text = ASK))),
            MessageWithParts(
                info("m2", Role.Assistant, 1_000, 95_000),
                listOf(
                    text("w1", words[0]), bash(1), bash(2),
                    text("w2", words[1]), bash(3), bash(4), bash(5),
                    text("w3", words[2]), search, stop, bash(6),
                    text("w4", words[3]), bash(7),
                    text("w5", words[4]), bash(8), bash(9),
                    text("w6", words[5]), bash(10), bash(11),
                    text("r", REPLY),
                ).toImmutableList(),
            ),
        )

    /** The same turn while its last command runs ([arrived]: an edit after it, running, the command done). */
    fun running(arrived: Boolean = false): List<MessageWithParts> {
        val steps =
            buildList {
                for (n in 1..5) add(bash(n))
                add(search)
                add(stop)
                for (n in 6..10) add(bash(n))
                add(bash(11, if (arrived) ToolStatus.Completed else ToolStatus.Running))
                if (arrived) add(tool("e1", "edit", mapOf("filePath" to "/home/dev/projects/demo_app/lib/player.dart"), ToolStatus.Running, 63_000, null))
            }
        return listOf(
            MessageWithParts(info(TURN, Role.User, 0, 0), persistentListOf(Part("p1", TURN, sid, PartType.Text, "text", text = ASK))),
            MessageWithParts(info("m2", Role.Assistant, 1_000, null), steps.toImmutableList()),
        )
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    fun doneFacts(): TurnFactsUi {
        val k = TurnFeedKeeper(sid)
        k.history(done(), newest = true)
        k.idle()
        return TurnFactsPresenter(FixedClock).present(k.feed.value, 96_000)
    }

    fun runningFacts(arrived: Boolean = false): TurnFactsUi {
        val k = TurnFeedKeeper(sid)
        k.history(running(arrived))
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 0)
        return TurnFactsPresenter(FixedClock).present(k.feed.value, 64_000)
    }

    /** Alternating commands and edits (1..[steps]), so every step is its own row; [lastRuns] leaves the last live. */
    fun alternating(steps: Int, lastRuns: Boolean): List<MessageWithParts> {
        val parts =
            (1..steps).map { n ->
                val status = if (n == steps && lastRuns) ToolStatus.Running else ToolStatus.Completed
                val start = 2_000L + n * 5_000L
                val end = if (status == ToolStatus.Completed) start + 2_400 else null
                if (n % 2 == 1) bash(n, status)
                else
                    tool(
                        "e$n",
                        "edit",
                        mapOf("filePath" to "/home/dev/projects/demo_app/lib/f$n.dart"),
                        status,
                        start,
                        end,
                    )
            }
        return listOf(
            MessageWithParts(
                info(TURN, Role.User, 0, 0),
                persistentListOf(Part("p1", TURN, sid, PartType.Text, "text", text = ASK)),
            ),
            MessageWithParts(info("m2", Role.Assistant, 1_000, null), parts.toImmutableList()),
        )
    }

    fun alternatingFacts(steps: Int, lastRuns: Boolean): TurnFactsUi {
        val k = TurnFeedKeeper(sid)
        k.history(alternating(steps, lastRuns))
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 0)
        return TurnFactsPresenter(FixedClock).present(k.feed.value, 64_000)
    }

    /**
     * A running turn of [reads] consecutive Read calls followed by [extras] alternating bash/edit steps.
     * When [lastHasOutput] is true and the last step is a running bash step, it carries live output so it
     * opens itself while the model progresses.
     */
    fun readsAndStepsFacts(
        reads: Int,
        extras: Int = 0,
        lastRuns: Boolean = true,
        lastHasOutput: Boolean = false,
    ): TurnFactsUi {
        val total = reads + extras
        val parts =
            (1..total).map { idx ->
                val isLast = idx == total
                val status = if (isLast && lastRuns) ToolStatus.Running else ToolStatus.Completed
                val start = 2_000L + idx * 3_000L
                val end = if (status == ToolStatus.Completed) start + 1_200 else null
                if (idx <= reads) {
                    tool(
                        "r$idx",
                        "read",
                        mapOf("filePath" to "/home/dev/projects/demo_app/lib/file$idx.dart"),
                        status,
                        start,
                        end,
                    )
                } else {
                    val e = idx - reads
                    if (e % 2 == 1) {
                        val cmd = commands[(e - 1) % commands.size]
                        val out =
                            if (isLast && lastHasOutput) (1..6).joinToString("\n") { "test output line $it" }
                            else "ok"
                        Part(
                            "x$e",
                            "m2",
                            sid,
                            PartType.Tool,
                            "tool",
                            callId = "call-x$e",
                            toolName = "bash",
                            tool =
                                ToolState(
                                    status,
                                    input = JsonObject(mapOf("command" to JsonPrimitive(cmd))),
                                    output = out,
                                    startedAtMs = start,
                                    completedAtMs = end,
                                ),
                        )
                    } else {
                        tool(
                            "e$e",
                            "edit",
                            mapOf("filePath" to "/home/dev/projects/demo_app/lib/mod$e.dart"),
                            status,
                            start,
                            end,
                        )
                    }
                }
            }
        val messages =
            listOf(
                MessageWithParts(
                    info(TURN, Role.User, 0, 0),
                    persistentListOf(Part("p1", TURN, sid, PartType.Text, "text", text = ASK)),
                ),
                MessageWithParts(info("m2", Role.Assistant, 1_000, null), parts.toImmutableList()),
            )
        val k = TurnFeedKeeper(sid)
        k.history(messages)
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 0)
        return TurnFactsPresenter(FixedClock).present(k.feed.value, 64_000)
    }

    private fun earlierRows() =
        buildList {
            add(FooterRow("footer:$TURN_OLD", TURN_OLD, failed = false))
            for (i in earlierParagraphs.indices.reversed()) {
                add(block(TURN_OLD, "m0a", "old$i", i, earlierParagraphs[i]))
            }
            add(prompt(TURN_OLD, OLD_ASK))
        }

    fun longRunningState(): ChatUi =
        ChatUi.loading(Ref).copy(
            title = ASK,
            phase = ChatPhase.Live,
            rows =
                (listOf(
                    WorkLineRow("work:$TURN", TURN, calls = 10, failed = 0, state = WorkState.Running),
                    prompt(TURN, ASK),
                ) + earlierRows()).toImmutableList(),
            live = LiveTurnUi(stopping = false),
        )

    fun longDoneState(): ChatUi =
        ChatUi.loading(Ref).copy(
            title = ASK,
            phase = ChatPhase.Live,
            rows =
                (listOf(
                    FooterRow("footer:$TURN", TURN, failed = false),
                    block(TURN, "m2", "r", 0, REPLY),
                    WorkLineRow("work:$TURN", TURN, calls = 10, failed = 0, state = WorkState.Done),
                    prompt(TURN, ASK),
                ) + earlierRows()).toImmutableList(),
        )

    fun doneState(): ChatUi =
        ChatUi.loading(Ref).copy(
            title = ASK,
            phase = ChatPhase.Live,
            rows =
                persistentListOf(
                    FooterRow("footer:$TURN", TURN, failed = false),
                    block(TURN, "m2", "r", 0, REPLY),
                    WorkLineRow("work:$TURN", TURN, calls = 13, failed = 0, state = WorkState.Done),
                    prompt(TURN, ASK),
                ),
        )

    fun runningState(): ChatUi =
        ChatUi.loading(Ref).copy(
            title = ASK,
            phase = ChatPhase.Live,
            rows =
                persistentListOf(
                    WorkLineRow("work:$TURN", TURN, calls = 13, failed = 0, state = WorkState.Running),
                    prompt(TURN, ASK),
                ),
            live = LiveTurnUi(stopping = false),
        )
}
