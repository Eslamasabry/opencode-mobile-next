package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.transcript.ledger.LedgerSnapshot
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundAgentResult
import io.github.eslamasabry.oc.engine.transcript.turnfacts.isCompactionLineLive

/** The full server-authored thought or notice behind a lightweight engine row. */
public data class TranscriptDetailUi(
    val key: String,
    val part: Part,
    val retryCompaction: Boolean = false,
)

public sealed interface WorkEntryUi {
    public data class Step(val partId: String) : WorkEntryUi

    public data class Detail(val value: TranscriptDetailUi) : WorkEntryUi
}

public data class TurnMetadataUi(
    val models: List<String>,
    val tokens: Long,
    val cost: Double,
    val latest: Boolean,
    /**
     * The footer's model words (Flutter transcript_turns.dart `_messageMeta`): named only when the turn ran on another
     * model than the turn before it, or changed model part-way ("Sonnet 4", "Sonnet 4 → Opus 4.1"); null otherwise.
     */
    val footerModel: String? = null,
)

public enum class TurnEndNote {
    Silent,
    Stopped,
    Failed,

    /** The server paused the reply after an hour with no new work; Continue picks it up (Flutter `inactivity`). */
    PausedInactive,

    /** The server restarted, so the reply stopped; Continue picks it up (Flutter `shutdown`). */
    StoppedServerRestart,

    /** A newer run took the reply's place. */
    Superseded,

    /**
     * B8: the run ended with the reply unfinished and no end was ever said (the server, or the phone's own server with
     * the app, stopped mid-run): Flutter `KitTurnPhase.interrupted`. Words had come: Continue picks the reply up.
     */
    CutOff,

    /** [CutOff] before any words came: the way on is to send the prompt again. */
    CutOffBeforeReply;

    /** The server's own stop, which says why in its own words instead of the generic "Stopped". */
    public val isServerStop: Boolean
        get() =
            this == PausedInactive ||
                this == StoppedServerRestart ||
                this == Superseded ||
                this == CutOff ||
                this == CutOffBeforeReply

    /** Whether the person can carry the reply on from where it stopped. */
    public val canContinue: Boolean
        get() = this == PausedInactive || this == StoppedServerRestart || this == CutOff

    /** Whether the way on is to send the newest prompt again (nothing of the reply came). */
    public val canSendAgain: Boolean
        get() = this == CutOffBeforeReply
}

/** How an interrupted run reads on the newest turn: a server stop names its reason; anything else stays "Stopped". */
internal fun stopNote(reason: io.github.eslamasabry.oc.core.domain.events.InterruptReason): TurnEndNote =
    when (reason) {
        io.github.eslamasabry.oc.core.domain.events.InterruptReason.Inactivity -> TurnEndNote.PausedInactive
        io.github.eslamasabry.oc.core.domain.events.InterruptReason.Shutdown -> TurnEndNote.StoppedServerRestart
        io.github.eslamasabry.oc.core.domain.events.InterruptReason.Superseded -> TurnEndNote.Superseded
        else -> TurnEndNote.Stopped
    }

public data class TranscriptReadingUi(
    val details: Map<String, TranscriptDetailUi> = emptyMap(),
    val hiddenPartKeys: Set<String> = emptySet(),
    val workEntries: Map<String, List<WorkEntryUi>> = emptyMap(),
    val metadata: Map<String, TurnMetadataUi> = emptyMap(),
    val endNotes: Map<String, TurnEndNote> = emptyMap(),
    /** Model and agent switches belong to the message details, never the transcript. */
    val switches: Map<String, List<TranscriptDetailUi>> = emptyMap(),
)

/**
 * Flutter `_markFoldedWork`: only short passing words fold; explanations and the final words stay
 * visible. The live turn keeps the words where they happened. Full thoughts and notices are
 * retained for their own expansion.
 */
internal fun transcriptReading(
    snapshot: LedgerSnapshot,
    busy: Boolean,
    outcome: io.github.eslamasabry.oc.core.domain.events.RunOutcome? = null,
): TranscriptReadingUi {
    val details = LinkedHashMap<String, TranscriptDetailUi>()
    val hidden = HashSet<String>()
    val work = LinkedHashMap<String, List<WorkEntryUi>>()
    val metadata = LinkedHashMap<String, TurnMetadataUi>()
    val endNotes = LinkedHashMap<String, TurnEndNote>()
    val switches = LinkedHashMap<String, List<TranscriptDetailUi>>()
    val newestFailedCompaction =
        snapshot.messages.asReversed().firstNotNullOfOrNull { m ->
            m.parts.asReversed().firstOrNull { it.type == PartType.Compaction }
        }
    var previousModel: String? = null
    for ((turnIndex, turn) in snapshot.turns.withIndex()) {
        val live = busy && turnIndex == snapshot.turns.lastIndex
        val assistants = turn.messages.filter { it.info.role == Role.Assistant }
        if (!live && turnIndex == snapshot.turns.lastIndex) {
            val prompt =
                turn.messages.firstOrNull { m ->
                    m.info.role == Role.User && m.parts.any { it.type == PartType.Text }
                }
            val command =
                prompt?.parts?.any {
                    it.type == PartType.Text && it.text.trimStart().startsWith('/')
                } == true
            val said = assistants.any { m ->
                m.parts.any { it.type == PartType.Text && it.text.isNotBlank() }
            }
            val tools = assistants.any { m -> m.parts.any { it.type == PartType.Tool } }
            val compact = turn.messages.any { m -> m.parts.any { it.type == PartType.Compaction } }
            // A server stop (paused, restarted, replaced) is said and offers Continue even after the reply began.
            val serverStop =
                (outcome as? io.github.eslamasabry.oc.core.domain.events.RunOutcome.Interrupted)
                    ?.let { stopNote(it.reason) }
                    ?.takeIf { it.isServerStop }
            val cutOff = prompt != null && !command && !compact && cutOff(turn, assistants, outcome)
            if (cutOff)
                endNotes[turn.turnId] = if (said) TurnEndNote.CutOff else TurnEndNote.CutOffBeforeReply
            else if (prompt != null && !command && !compact && serverStop != null && said)
                endNotes[turn.turnId] = serverStop
            if (!cutOff && prompt != null && !command && !compact && !said) {
                val last = assistants.lastOrNull()?.info
                val end =
                    when {
                        outcome is
                            io.github.eslamasabry.oc.core.domain.events.RunOutcome.Interrupted ->
                            stopNote(outcome.reason)
                        last?.errorText != null -> null // the failed footer already explains it
                        assistants.isEmpty() &&
                            outcome is
                                io.github.eslamasabry.oc.core.domain.events.RunOutcome.Failed ->
                            TurnEndNote.Failed
                        !tools && (assistants.isEmpty() || last?.time?.completedMs != null) ->
                            TurnEndNote.Silent
                        else -> null
                    }
                if (end != null) endNotes[turn.turnId] = end
            }
        }
        val models =
            assistants
                .mapNotNull { m ->
                    m.info.modelId
                        ?.takeIf { it.isNotBlank() }
                        ?.let { model ->
                            listOfNotNull(m.info.providerId?.takeIf { it.isNotBlank() }, model)
                                .joinToString("/")
                        }
                }
                .fold(mutableListOf<String>()) { out, label ->
                    if (out.lastOrNull() != label) out.add(label)
                    out
                }
        // F: transcript_turns.dart `_messageMeta`: the model is named when it differs from the one before the turn.
        val changed = models.isNotEmpty() && (models.size > 1 || models.first() != previousModel)
        if (!live && assistants.isNotEmpty())
            metadata[turn.turnId] =
                TurnMetadataUi(
                    models,
                    assistants.sumOf { it.info.tokens.total },
                    assistants.sumOf { it.info.cost },
                    turnIndex == snapshot.turns.lastIndex,
                    footerModel =
                        if (changed) models.joinToString(" → ") { ReplyModelNames.fromId(it) } else null,
                )
        if (models.isNotEmpty()) previousModel = models.last()
        val parts =
            turn.messages.flatMap { m ->
                m.parts.filter { p ->
                    m.info.role == Role.Assistant ||
                        p.type in setOf(PartType.Notice, PartType.Compaction, PartType.Switch)
                }
            }
        switches[turn.turnId] =
            parts
                .filter { it.type == PartType.Switch }
                .map {
                    TranscriptDetailUi("switch:${it.messageId}/${it.id}", it)
                }
        val lastWork = parts.indexOfLast {
            it.type == PartType.Tool || it.type == PartType.Reasoning
        }
        var cutoff = lastWork
        if (lastWork >= 0 && parts.drop(lastWork + 1).none { it.type == PartType.Text }) {
            cutoff = parts.indexOfLast { it.type == PartType.Text }
            while (cutoff > 0 && parts[cutoff - 1].type == PartType.Text) cutoff--
        }
        val hasTools = parts.any { it.type == PartType.Tool }
        val entries = ArrayList<WorkEntryUi>()
        for ((index, part) in parts.withIndex()) {
            val identity = "${part.messageId}/${part.id}"
            val notice = part.type in setOf(PartType.Notice, PartType.Compaction, PartType.Switch)
            if (part.type == PartType.Reasoning || notice) {
                val key =
                    if (part.type == PartType.Reasoning) "thought:$identity" else "notice:$identity"
                val stale =
                    part.type == PartType.Compaction &&
                        (isCompactionLineLive(part) || part.toolName == "running") &&
                        (!live || index != parts.lastIndex)
                if (stale) hidden += identity
                details[key] =
                    TranscriptDetailUi(
                        key,
                        part,
                        retryCompaction =
                            !busy &&
                                part === newestFailedCompaction &&
                                (part.noticeMetadata["kind"] ?: part.toolName) == "failed",
                    )
            }
            val foldedText =
                !live && index < cutoff && part.type == PartType.Text && passingWords(part.text)
            val foldedNotice =
                !live &&
                    notice &&
                    part.type == PartType.Notice &&
                    BackgroundAgentResult.fromPart(part) == null
            val foldedThought = hasTools && part.type == PartType.Reasoning
            if ((foldedText || foldedNotice || foldedThought) && hasTools) {
                hidden += identity
                entries += WorkEntryUi.Detail(TranscriptDetailUi("work-detail:$identity", part))
            } else if (part.type == PartType.Tool) entries += WorkEntryUi.Step(part.id)
        }
        if (hasTools) work[turn.turnId] = entries
    }
    return TranscriptReadingUi(details, hidden, work, metadata, endNotes, switches)
}

/**
 * B8 (Flutter message_view.dart `looksCut`: a reply still unfinished in a turn that no longer runs, the server asked
 * and idle): the newest turn's reply was cut off before it finished. Nothing is running ([transcriptReading] reads
 * only a turn that is not live: the chat read the server's status before its history), no end was seen here
 * ([outcome] null: after a reopen, or a run that ended while this chat was closed), and the newest model reply has no
 * end of its own (no completion time, no error). OpenCode 2 keeps only what was durable when its process died (the
 * phone's server dies with a force-stopped app): the reply's shell, its streamed words never come back. A turn that
 * ended on the person's refusal is not cut off (W4: OpenCode 2 ends it as an interruption too).
 */
private fun cutOff(
    turn: io.github.eslamasabry.oc.engine.transcript.ledger.LedgerTurn,
    assistants: List<io.github.eslamasabry.oc.core.model.transcript.MessageWithParts>,
    outcome: io.github.eslamasabry.oc.core.domain.events.RunOutcome?,
): Boolean {
    if (outcome != null) return false
    // A model reply names its model; a server notice, switch or compaction written as a message does not.
    val reply = assistants.lastOrNull { !it.info.modelId.isNullOrBlank() || !it.info.providerId.isNullOrBlank() }
    val info = reply?.info ?: return false
    if (info.time?.completedMs != null || info.errorText != null || info.errorKind != null) return false
    return !io.github.eslamasabry.oc.engine.transcript.ledger.endsOnRefusal(turn)
}

private fun passingWords(text: String): Boolean {
    val words = text.trim()
    return words.isEmpty() ||
        (!words.contains('\n') &&
            !words.contains("```") &&
            (words.length <= 80 || words.endsWith(':') && words.length <= 200))
}
