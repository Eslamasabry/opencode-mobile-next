package io.github.eslamasabry.oc.ui.kit.request

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState

/** What another conversation asks the person (plain values): a permission (Allow once / Deny) or a question (Answer…). */
public enum class KitRequestKind { Permission, Question }

/** One request: [words] are what it asks in plain words ("Asks to edit: wait7.txt"), [key] tells requests apart. */
@Immutable
public data class KitRequest(val key: Any, val words: String, val kind: KitRequestKind = KitRequestKind.Permission)

/**
 * An answer or a stop held for HELD_ANSWER before it is sent: [label] is the receipt's words ("Allowed", "Denied",
 * "Stopped “Bank API migration”"), [allowed] false for a denial or a stop. The hold store is the caller's; the receipt
 * only shows its countdown and Undo.
 */
@Immutable
public data class KitHeld(val key: Any, val label: String, val allowed: Boolean = true, val sent: Boolean = false)

/** The answers' words; the `…On` forms name the target for TalkBack ("Allow once: ‹title›"). */
public class KitRequestWords(
    public val allowOnce: String,
    public val deny: String,
    public val answer: String,
    public val allowOn: (String) -> String,
    public val denyOn: (String) -> String,
    public val answerOn: (String) -> String,
)

/** The answers' measures (LAMP-CRAFT §4). */
public object RequestAnswersDims {
    /** Allow once and Deny are two half-width buttons this tall (`Space.S6` × 2 + `Space.S1`). */
    public const val BUTTON_H: Int = Space.S6 * 2 + Space.S1

    /** Between the two buttons (`Space.S2`). */
    public const val GAP: Int = Space.S2
}

/**
 * A request answered in place (LAMP-PLAN §5, LAMP-CRAFT §4 "Allow once / Deny"): the request's [KitRequest.words] in
 * the attention tone (unless [showWords] is false, where the row already says them), then Allow once | Deny as two
 * half-width 52 dp buttons, or one full-width Answer… for a question. Each names its [target] for TalkBack. A tap plays
 * [KitHaptic.Commit]; the caller holds the answer and passes [held]: the buttons cross-fade into the receipt "‹label› ·
 * Undo", the check growing from MARK_FROM (`KitSwap.grows`), its fill draining over HELD_ANSWER. Undo calls [onUndo];
 * the drained end calls [onHoldEnd] (the caller's hold sends; closing the list sends at once there).
 *
 * States: asking (permission / question), held (receipt with countdown)
 * Text: frame (the request's words Secondary in Attention; buttons Button)
 * Cap: Control (each answer), Reading (the words)
 * Motion: buttons ↔ receipt QUICK with the receipt growing from MARK_FROM; Off = instant, no drain
 * Replaces: the home row's own Allow / Deny pair (M2 switches it over)
 */
@Composable
public fun KitRequestAnswers(
    request: KitRequest,
    target: String,
    words: KitRequestWords,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    onAnswer: () -> Unit,
    modifier: Modifier = Modifier,
    held: KitHeld? = null,
    onUndo: () -> Unit = {},
    onHoldEnd: () -> Unit = {},
    showWords: Boolean = true,
) {
    val specs = KitMotion.specs
    Column(modifier.kitMaxWidth(CapRole.Reading), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        if (showWords) KitText(request.words, role = TypeRole.Secondary, tone = TextTone.Attention, kind = KitTextKind.Frame, maxLines = 3)
        AnimatedContent(
            held,
            transitionSpec = { (fadeIn(specs.quick()) + scaleIn(specs.quick(), initialScale = MotionTokens.MARK_FROM)) togetherWith fadeOut(specs.quick()) },
            contentKey = { it?.key },
            label = "kit-request-answers",
        ) { shown ->
            if (shown != null) {
                // Sent: the answer is on its way, so no Undo and no countdown; the words stay until the request leaves.
                if (shown.sent) {
                    KitReceipt(KitReceiptState.Confirmed, Modifier.fillMaxWidth(), label = shown.label)
                } else {
                    KitReceipt(
                        KitReceiptState.Confirmed,
                        Modifier.fillMaxWidth(),
                        label = shown.label,
                        onUndo = onUndo,
                        countdownMs = MotionTokens.HELD_ANSWER,
                        onHoldEnd = onHoldEnd,
                    )
                }
            } else if (request.kind == KitRequestKind.Question) {
                KitButton(
                    words.answer,
                    onAnswer,
                    Modifier.fillMaxWidth().heightIn(min = RequestAnswersDims.BUTTON_H.dp),
                    role = KitButtonRole.Primary,
                    semanticsLabel = words.answerOn(target),
                )
            } else {
                AnswerButtons {
                    KitButton(
                        words.allowOnce,
                        onAllow,
                        Modifier.heightIn(min = RequestAnswersDims.BUTTON_H.dp),
                        role = KitButtonRole.Primary,
                        semanticsLabel = words.allowOn(target),
                        haptic = KitHaptic.Commit,
                    )
                    KitButton(
                        words.deny,
                        onDeny,
                        Modifier.heightIn(min = RequestAnswersDims.BUTTON_H.dp),
                        role = KitButtonRole.Secondary,
                        semanticsLabel = words.denyOn(target),
                        haptic = KitHaptic.Commit,
                    )
                }
            }
        }
    }
}

/**
 * Allow once and Deny: two equal half-width buttons while each label fits on one line in its half; when either would
 * not (a large text size, a long translation) they stack full width, Allow once on top, so a label is never broken
 * over two lines and the buttons are never unequal.
 */
@Composable
private fun AnswerButtons(content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxWidth()) { measurables, constraints ->
        val full = constraints.maxWidth
        val gap = RequestAnswersDims.GAP.dp.roundToPx()
        val half = (full - gap) / 2
        // The natural, one-line width of each button (intrinsics: each child is measured once).
        val natural = measurables.map { it.maxIntrinsicWidth(full) }
        val side = natural.all { it <= half }
        val w = if (side) half else full
        val h = measurables.maxOf { it.minIntrinsicHeight(w) }
        val placed = measurables.map { it.measure(Constraints(minWidth = w, maxWidth = w, minHeight = if (side) h else 0)) }
        if (side) {
            layout(full, placed.maxOf { it.height }) {
                placed[0].place(0, 0)
                placed[1].place(half + gap, 0)
            }
        } else {
            layout(full, placed.sumOf { it.height } + gap * (placed.size - 1)) {
                var y = 0
                placed.forEach { p ->
                    p.place(0, y)
                    y += p.height + gap
                }
            }
        }
    }
}
