package io.github.eslamasabry.oc.engine.composer.phrases

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What the chat says about the moment, for the phrase chips (Flutter `PhraseMomentState`). [moment] null: no chips.
 * [continueShown]: the transcript shows its own Continue for a reply cut off by length. [errorShown]: an error notice
 * with its own way on is shown, so no chip row sits under it.
 */
public data class PhraseMomentState(
    val moment: PhraseMoment?,
    val continueShown: Boolean = false,
    val errorShown: Boolean = false,
)

/** Port of `lib/state/phrase_moment.dart` (B-CHAT-085, B-CHAT-086). */
public object PhraseMomentRules {
    private val EDIT_TOOLS = setOf("edit", "write", "multiedit", "patch", "apply_patch")

    /**
     * The moment from the loaded transcript ([messages], oldest first) without server calls. [busy]: the agent works
     * (server word or this phone's send in flight). [stoppedByPerson]: Stop was used on the newest turn here.
     */
    public fun derive(
        messages: List<MessageWithParts>,
        busy: Boolean,
        nowMs: Long,
        stoppedByPerson: Boolean = false,
        offline: Boolean = false,
        interruptedAfter: Duration = 30.seconds,
    ): PhraseMomentState {
        if (busy) return PhraseMomentState(PhraseMoment.Working)
        val real = messages.filterNot { it.info.id.startsWith("local-") }
        if (real.isEmpty()) return PhraseMomentState(null)
        val lastUser = real.indexOfLast { it.info.role == Role.User }
        val turn = real.subList(if (lastUser < 0) 0 else lastUser + 1, real.size)
        val assistants = turn.filter { it.info.role == Role.Assistant }
        if (assistants.isEmpty()) {
            // Prompt sent, nothing came back, nothing is running.
            return PhraseMomentState(if (stoppedByPerson) PhraseMoment.StoppedPartway else PhraseMoment.Idle)
        }
        val last = assistants.last().info
        val kind = last.errorText?.let { refineFromText(last.errorKind ?: MessageErrorKind.Unknown, it) }
        val cutByLength = kind == MessageErrorKind.OutputLength || (last.finish == "length" && last.errorText == null)
        val created = last.time?.createdMs
        val interrupted =
            last.errorText == null && last.time != null && last.time?.completedMs == null && created != null &&
                nowMs - created > interruptedAfter.inWholeMilliseconds
        if (cutByLength || kind != null || stoppedByPerson || interrupted) {
            return PhraseMomentState(
                PhraseMoment.StoppedPartway,
                continueShown = kind == MessageErrorKind.OutputLength && !offline,
                errorShown =
                    kind != null && kind != MessageErrorKind.OutputLength && kind != MessageErrorKind.Aborted && !stoppedByPerson,
            )
        }
        val edited =
            assistants.flatMap { it.parts }.any {
                it.type == PartType.Tool && it.toolName in EDIT_TOOLS && it.tool?.status == ToolStatus.Completed
            }
        return PhraseMomentState(if (edited) PhraseMoment.FinishedWithChanges else PhraseMoment.Idle)
    }

    /** Dart `MessageErrorKind.fromName`. TODO(move-to-core): core:model owns this port once it lands there. */
    internal fun fromName(name: String?): MessageErrorKind? {
        val trimmed = name?.trim()
        if (trimmed.isNullOrEmpty()) return null
        val key = trimmed.lowercase().replace(Regex("[^a-z]"), "")
        return when {
            "contextoverflow" in key || "contextlength" in key -> MessageErrorKind.ContextOverflow
            "outputlength" in key || key == "length" -> MessageErrorKind.OutputLength
            "providerauth" in key || "needsauth" in key || "unauthorized" in key || "authentication" in key ->
                MessageErrorKind.ProviderAuth
            "contentfilter" in key -> MessageErrorKind.ContentFilter
            "abort" in key -> MessageErrorKind.Aborted
            "modelnotfound" in key -> MessageErrorKind.ModelNotFound
            else -> MessageErrorKind.Unknown
        }
    }

    private val SIGN_IN_GONE =
        Regex(
            "not logged in|please run /login|failed to authenticate|oauth (session|token) (has )?expired|" +
                "could not be refreshed|invalid api key|authentication_error",
            RegexOption.IGNORE_CASE,
        )

    /** Dart `MessageErrorKind.refineFromText`. */
    internal fun refineFromText(kind: MessageErrorKind, text: String?): MessageErrorKind {
        if (kind != MessageErrorKind.Unknown || text == null) return kind
        if (SIGN_IN_GONE.containsMatchIn(text)) return MessageErrorKind.ProviderAuth
        val head = text.trimStart().split(Regex("[:\\s]")).first()
        return fromName(head) ?: MessageErrorKind.Unknown
    }
}
