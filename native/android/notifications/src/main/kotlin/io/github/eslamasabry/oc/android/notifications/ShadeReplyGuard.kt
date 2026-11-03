package io.github.eslamasabry.oc.android.notifications

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef

/**
 * What a pending question accepts from the shade (B-ANDR-038, B-ANDR-048, B-AGENT-032). Port of the Flutter
 * `notificationOptionsOf` (lib/background/notification_text.dart) and `_questionSupportsQuickReply`
 * (lib/state/connection/alerts.dart).
 */
public data class QuestionShape(val prompts: List<Prompt>) {
    public data class Prompt(val labels: List<String>, val multiple: Boolean = false, val custom: Boolean = false)

    /**
     * The question's choices fit for the shade: one single-choice prompt with one to three unique labels of at most
     * 24 characters; empty otherwise. As amended by CR11-16 they are one body line ([AlertCopy.choices]), not buttons.
     */
    public val shadeOptions: List<String>
        get() {
            val prompt = prompts.singleOrNull() ?: return emptyList()
            if (prompt.multiple) return emptyList()
            val labels = prompt.labels.map { it.trim() }
            if (labels.isEmpty() || labels.size > MAX_OPTIONS) return emptyList()
            if (labels.any { it.isEmpty() || it.length > MAX_OPTION_LENGTH }) return emptyList()
            if (labels.toSet().size != labels.size) return emptyList()
            return labels
        }

    /** A typed reply needs one prompt that takes custom text. */
    public val takesTypedReply: Boolean get() = prompts.size == 1 && prompts.single().custom

    /** A typed reply answers a custom prompt; otherwise only one of the question's own shade options does. */
    public fun accepts(text: String): Boolean {
        val reply = text.trim()
        if (reply.isEmpty()) return false
        return takesTypedReply || reply in shadeOptions
    }

    public companion object {
        public const val MAX_OPTIONS: Int = 3
        public const val MAX_OPTION_LENGTH: Int = 24
    }
}

/**
 * Wraps the engine's [ShadeAnswerer]: a text answer to a question (a Flutter-era reply or option button still in the
 * shade) is sent only when that exact question accepts it. Free text for a choice-only question, or a question that is
 * no longer pending, is refused as `StaleScope` before any network call, so the body reposts the alert with Open only
 * and the question stays pending for the full screen.
 */
public class GuardedShadeAnswerer(
    /** The pending question this answer names (profile, session and request must match), or null when it is gone. */
    private val questionOf: suspend (ShadeAnswer) -> QuestionShape?,
    private val delegate: ShadeAnswerer,
) : ShadeAnswerer {
    override suspend fun answer(answer: ShadeAnswer): Outcome<Unit> {
        val reply = answer.reply
        if (answer.kind == AlertKind.Question && reply is ShadeReply.Text) {
            val shape = questionOf(answer) ?: return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
            if (!shape.accepts(reply.text)) return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
            return delegate.answer(answer.copy(reply = ShadeReply.Text(reply.text.trim())))
        }
        return delegate.answer(answer)
    }
}

/**
 * Wraps the engine's [ReplyStopper]: **Stop the reply** stops a chat only while it still runs (or waits on a
 * provider); a chat that is no longer running is left alone, with no call to the server (B-ANDR-040).
 */
public class RunningOnlyStopper(
    private val isRunning: (ConversationRef) -> Boolean,
    private val delegate: ReplyStopper,
) : ReplyStopper {
    override suspend fun stop(conversation: ConversationRef): Outcome<Unit> =
        if (isRunning(conversation)) delegate.stop(conversation) else Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
}
