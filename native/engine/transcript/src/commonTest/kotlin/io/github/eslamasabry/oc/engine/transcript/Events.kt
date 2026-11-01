package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.PartId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.NoticeKind
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.internal.RowBuilder
import io.github.eslamasabry.oc.engine.transcript.internal.TranscriptModel
import io.github.eslamasabry.oc.engine.transcript.internal.VersionFence
import io.github.eslamasabry.oc.engine.transcript.internal.mergeHydrated
import io.github.eslamasabry.oc.engine.transcript.state.OlderState
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptPhase
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred

/** Small builders for transcript tests (session "s"), on the core vocabulary (plan 09 §2.2, plan 04 §11.2). */
internal object Ev {
    const val S = "s"
    val SID = SessionId(S)

    /** A realistic clock origin, never 0: a clock at 0 hides overflow bugs (plan 09 §14.2). */
    const val T0 = 1_760_000_000_000L

    fun info(id: String, role: Role, created: Long? = null, completed: Long? = null, error: String? = null, session: String = S) =
        MessageInfo(id, session, role, time = if (created == null && completed == null) null else MsgTime(created, completed), errorText = error)

    fun user(id: String, created: Long? = null) = GatewayEvent.MessageUpserted(info(id, Role.User, created))

    fun assistant(id: String, completed: Long? = null, error: String? = null, created: Long? = null) =
        GatewayEvent.MessageUpserted(info(id, Role.Assistant, created, completed, error))

    fun textPart(msg: String, part: String, text: String = "", ended: Boolean = false, session: String = S) =
        Part(part, msg, session, PartType.Text, rawType = "text", text = text, ended = ended)

    fun text(msg: String, part: String, text: String = "", ended: Boolean = false) =
        GatewayEvent.PartUpserted(textPart(msg, part, text, ended))

    fun reasoning(msg: String, part: String, text: String) =
        GatewayEvent.PartUpserted(Part(part, msg, S, PartType.Reasoning, rawType = "reasoning", text = text))

    fun toolPart(msg: String, call: String, status: ToolStatus, name: String = "read", output: String? = null) =
        Part(call, msg, S, PartType.Tool, rawType = "tool", callId = call, toolName = name, tool = ToolState(status, output = output))

    fun tool(msg: String, call: String, status: ToolStatus, name: String = "read", output: String? = null) =
        GatewayEvent.PartUpserted(toolPart(msg, call, status, name, output))

    fun delta(msg: String, part: String, d: String, session: String = S, field: DeltaField = DeltaField.Text) =
        GatewayEvent.PartDelta(SessionId(session), MessageId(msg), PartId(part), field, d)

    fun notice(msg: String, kind: NoticeKind = NoticeKind.InstructionsUpdated) = GatewayEvent.Notice(SID, MessageId(msg), kind)

    val runStarted = GatewayEvent.RunStarted(SID)

    fun runEnded(outcome: RunOutcome = RunOutcome.Succeeded) = GatewayEvent.RunEnded(SID, outcome)

    fun prompt(id: String, words: String): List<GatewayEvent> = listOf(user(id), text(id, "$id-t", words, ended = true))

    fun msg(info: MessageInfo, vararg parts: Part) = MessageWithParts(info, parts.toList().toImmutableList())

    /**
     * Flutter perf_chat_test / chat_rebuild_budget_test `_history(count, fragmented)`: [count] parts as one fragmented
     * reply, or as [count] plain messages alternating user/assistant (so count/2 turns).
     */
    fun history(count: Int, fragmented: Boolean): List<MessageWithParts> =
        if (fragmented) {
            listOf(
                MessageWithParts(
                    info(TAIL, Role.Assistant),
                    (0 until count).map { textPart(TAIL, "p$it", "Part $it.") }.toImmutableList(),
                ),
            )
        } else {
            (0 until count).map { i ->
                val id = if (i == count - 1) TAIL else "m$i"
                val role = if (i % 2 == 0) Role.User else Role.Assistant
                MessageWithParts(info(id, role, created = i + 1L, completed = i + 2L), listOf(textPart(id, "p$i", "Part $i.")).toImmutableList())
            }
        }

    const val TAIL = "tail"

    fun page(items: List<MessageWithParts>, next: String? = null): Outcome<ServerPage<MessageWithParts>> =
        Outcome.Ok(ServerPage(items.toImmutableList(), next))
}

/** The model and row builder without the lane: what the handle does on each apply, load and publish. */
internal class Harness(session: String = Ev.S) {
    val counters = TranscriptCounters()
    val refreshes = ArrayList<RefreshReason>()
    val model = TranscriptModel(session, counters) { refreshes += it }
    private val rows = RowBuilder(model, counters)
    private var revision = 0L

    fun apply(vararg events: GatewayEvent): Boolean = events.map { model.apply(it) }.any { it }

    fun apply(events: List<GatewayEvent>) = events.forEach { model.apply(it) }

    fun derive(older: OlderState = OlderState.None): TranscriptState = rows.derive(++revision, TranscriptPhase.Live, older)

    /** A newest-page read that started at [versionAtStart] and lands now (the handle's merge, without the I/O). */
    fun load(page: List<MessageWithParts>, versionAtStart: Long = model.eventVersion, preserveUnseen: Boolean = false) {
        val fence =
            object : VersionFence {
                override fun message(id: String) = model.messageVersion(id)

                override fun part(messageId: String, partId: String) = model.partVersion(messageId, partId)

                override fun deferredParts(messageId: String) = model.takeDeferredParts(messageId)

                override fun deferredMessage(messageId: String) = model.takeDeferredMessage(messageId)
            }
        val merged = mergeHydrated(model.snapshots(), page, versionAtStart, fence, preserveUnseen = preserveUnseen)
        model.adoptHydrated(merged, page.mapTo(HashSet()) { it.info.id }, versionAtStart)
    }

    fun keys(state: TranscriptState) = state.rows.map { it.key }
}

/** A history source the test answers by hand: each read waits until the test completes it (version-fence tests). */
internal class ScriptedHistory {
    class Read(val cursor: String?, val answer: CompletableDeferred<Outcome<ServerPage<MessageWithParts>>>)

    val reads = ArrayList<Read>()

    /** Answers given at once, in order, before any manual one. */
    val queued = ArrayDeque<Outcome<ServerPage<MessageWithParts>>>()

    val source =
        HistorySource { _, cursor, _ ->
            queued.removeFirstOrNull() ?: CompletableDeferred<Outcome<ServerPage<MessageWithParts>>>().let {
                reads += Read(cursor, it)
                it.await()
            }
        }
}
