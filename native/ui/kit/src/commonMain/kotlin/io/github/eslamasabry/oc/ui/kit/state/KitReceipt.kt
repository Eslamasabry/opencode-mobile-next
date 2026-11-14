package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.rememberUpdatedState
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.words.res.kitReceiptCountdownSpoken
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitReceiptActRefusedReason
import io.github.eslamasabry.oc.ui.words.res.kitReceiptAnsweredElsewhere
import io.github.eslamasabry.oc.ui.words.res.kitReceiptAnsweredElsewhereUnknown
import io.github.eslamasabry.oc.ui.words.res.kitReceiptAt
import io.github.eslamasabry.oc.ui.words.res.kitReceiptConfirmed
import io.github.eslamasabry.oc.ui.words.res.kitReceiptNotConfirmed
import io.github.eslamasabry.oc.ui.words.res.kitReceiptRefused
import io.github.eslamasabry.oc.ui.words.res.kitReceiptRefusedReason
import io.github.eslamasabry.oc.ui.words.res.kitReceiptSending
import io.github.eslamasabry.oc.ui.words.res.kitReceiptSent
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.kitUndoAction
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** "Sent" becomes "Not confirmed yet" when nothing confirms it within 8 s (Dart `KitMotion.escalateAfter`, STATE-10). */
public val RECEIPT_UNCONFIRMED_AFTER: Duration = 8.seconds // F: lib/ui/kit/kit_motion.dart:42

/** Where an answer or a send stands (Dart `KitReceiptState`). "Sent" is never "Done". */
@Immutable
public sealed interface KitReceiptState {
    public data object Sending : KitReceiptState

    /** Left this phone; the other side has not confirmed it yet. */
    public data object Sent : KitReceiptState

    /** The other side confirmed it ("Done"). */
    public data object Confirmed : KitReceiptState

    /** No echo in time, or the echo was lost: Try again. */
    public data object NotConfirmed : KitReceiptState

    /** The other side refused it; [reason] is words for people, already resolved. */
    public data class Refused(val reason: String? = null) : KitReceiptState

    /** Someone answered it on another device; [where] names it when known. */
    public data class AnsweredElsewhere(val where: String? = null) : KitReceiptState
}

/** What a receipt says (pure; the words are resolved by [KitReceipt]). */
public enum class ReceiptWords { Sending, Sent, NotConfirmed, Confirmed, Refused, RefusedReason, AnsweredOn, AnsweredElsewhere }

/** Pure rules of [KitReceipt]. */
public object ReceiptRules {
    /** The words for [state]; a waiting receipt (sending or sent) that turned slow reads "Not confirmed yet". */
    public fun words(state: KitReceiptState, unconfirmedTooLong: Boolean): ReceiptWords =
        when (state) {
            KitReceiptState.Sending -> if (unconfirmedTooLong) ReceiptWords.NotConfirmed else ReceiptWords.Sending
            KitReceiptState.Sent -> if (unconfirmedTooLong) ReceiptWords.NotConfirmed else ReceiptWords.Sent
            KitReceiptState.Confirmed -> ReceiptWords.Confirmed
            KitReceiptState.NotConfirmed -> ReceiptWords.NotConfirmed
            is KitReceiptState.Refused ->
                if (state.reason.isNullOrBlank()) ReceiptWords.Refused else ReceiptWords.RefusedReason
            is KitReceiptState.AnsweredElsewhere ->
                if (state.where.isNullOrBlank()) ReceiptWords.AnsweredElsewhere else ReceiptWords.AnsweredOn
        }

    /** The words that ask the person to act are drawn in `text1`, the rest in `text2` (Dart `_wordColor`; LOOK-4/5). */
    public fun asksToAct(words: ReceiptWords): Boolean =
        words == ReceiptWords.NotConfirmed || words == ReceiptWords.Refused || words == ReceiptWords.RefusedReason

    /**
     * Try again shows on a not-confirmed receipt with a retry (Dart `KitReceipt`). A refused one keeps it too when its
     * caller offers one (the native chat answer line retries a refused answer); no Flutter scene passes it there.
     */
    public fun retryShown(words: ReceiptWords, canRetry: Boolean): Boolean = asksToAct(words) && canRetry

    /** The whole seconds a hold of [ms] lasts, rounded up: what TalkBack says Undo lasts. */
    public fun holdSeconds(ms: Long): Int = ((ms + MS_PER_SECOND - 1) / MS_PER_SECOND).toInt()

    private const val MS_PER_SECOND: Long = 1_000L // NV: milliseconds in a second

    /** Undo only on a confirmed receipt inside its window. */
    public fun undoShown(words: ReceiptWords, canUndo: Boolean, windowOpen: Boolean): Boolean =
        words == ReceiptWords.Confirmed && canUndo && windowOpen
}

/**
 * A write's receipt, shown in place as a mark and a word (Dart `KitReceipt`): "Sending…" (or [sendingLabel]), "Sent",
 * "Done" (or the act, [label]), "Not confirmed yet" with Try again ([onRetry]), "Not accepted: {reason}", "Answered on
 * {where}". With [automatic] it is the automation line: "{label} at {time} · Undo", the state's own mark beside it.
 * [at] is the clock time of the latest transition, already in the person's 12/24-hour words ("10:42 AM"). A waiting
 * receipt (sending, or sent) turns into "Not confirmed yet" 8 s after [since] (a sent one without [since] after 8 s
 * shown). Undo shows only with [onUndo], only once confirmed, and only until [undoUntil] passes. [onTap] makes the
 * receipt one 48 dp button. One polite live region carries the words; the mark is decoration.
 *
 * States: sending, sent, confirmed, not confirmed, refused, answered elsewhere, automatic Text: reading Cap: Reading
 * Motion: the mark and the actions cross-fade QUICK; Off = instant, and the sending mark is the still dot Hidden: none
 * Replaces: KitReceipt, KitReceiptState (lib/ui/kit/kit_receipt.dart)
 */
@Composable
public fun KitReceipt(
    state: KitReceiptState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    label: String? = null,
    sendingLabel: String? = null,
    at: String? = null,
    since: TimeMark? = null,
    onUndo: (() -> Unit)? = null,
    undoUntil: TimeMark? = null,
    onTap: (() -> Unit)? = null,
    automatic: Boolean = false,
    /**
     * A held answer or stop ("Allowed · Undo", "Stopping ‹title›… · Undo"): the receipt is a pill whose tonal fill
     * drains from the end toward the start over this hold (HELD_ANSWER, 3 s), stretched by the person's accessibility
     * timeout; TalkBack hears the words and how long Undo lasts. Only a confirmed receipt holds. Off = the fill stays.
     */
    countdownMs: Int? = null,
    /**
     * Called once when the hold ends without Undo. Undo, or the receipt leaving the screen, cancels it: a caller whose
     * hold must outlive the receipt (the list closing) keeps its own timer and treats this as the visual end.
     */
    onHoldEnd: (() -> Unit)? = null,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val waits = state == KitReceiptState.Sending || state == KitReceiptState.Sent
    val slow = rememberPassed(if (waits) since else null, RECEIPT_UNCONFIRMED_AFTER, fallback = state == KitReceiptState.Sent)
    val words = ReceiptRules.words(state, slow)
    val undoClosed = rememberPassed(undoUntil, Duration.ZERO, fallback = false)
    val word = receiptWord(words, state, label, sendingLabel, automatic)
    val atWords = at?.let { stringResource(Res.string.kitReceiptAt, it) }
    val hold = rememberHold(countdownMs, words, onHoldEnd)
    val undo = onUndo?.let { act -> { hold?.undone = true; act() } }
    val retryShown = ReceiptRules.retryShown(words, onRetry != null)
    // A hold that has ended was sent: its Undo goes with it, so it can never promise what it cannot take back.
    val undoShown = ReceiptRules.undoShown(words, undo != null, !undoClosed && hold?.fired != true)
    val hasActions = retryShown || undoShown
    val plain = listOfNotNull(receiptPlainWord(words, state, label, sendingLabel, automatic), (state as? KitReceiptState.Refused)?.reason?.trim()?.takeIf { words == ReceiptWords.RefusedReason }, atWords).joinToString(", ")
    val spoken = if (hold != null) stringResource(Res.string.kitReceiptCountdownSpoken, plain, Numbers.format(ReceiptRules.holdSeconds(hold.ms))) else plain
    val text =
        buildAnnotatedString {
            withStyle(SpanStyle(color = wordColor(colors, words))) { append(word) }
            if (atWords != null) withStyle(SpanStyle(color = colors.text2)) { append(" $atWords") }
            // The automation line: "{label} at {time} · Undo"; the dot ends the words.
            if (automatic && hasActions) withStyle(SpanStyle(color = colors.text2)) { append(" ·") }
        }
    val glyph = receiptGlyphSize()
    val line: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(glyph), contentAlignment = Alignment.Center) {
                KitFade(words) { ReceiptMark(it, glyph, KitMotion.state.reduced) }
            }
            Spacer(Modifier.width(Space.S2.dp))
            BasicText(text, Modifier.weight(1f, fill = false), style = LocalKitTypography.current.style(TypeRole.Secondary))
        }
    }
    val row: @Composable (Modifier) -> Unit = { rowModifier -> FlowRow(
        rowModifier,
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .then(
                    if (onTap != null)
                        Modifier.kitTappable(onClick = onTap, shape = KitShapeId.Code).heightIn(min = tokens.minTarget.dp)
                    else Modifier
                )
                .clearAndSetSemantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.CenterStart,
        ) { line() }
        val undoAction = undo
        KitFade(retryShown to undoShown) { (retry, shownUndo) ->
            val undo = if (shownUndo) undoAction else null
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (retry && onRetry != null) ReceiptAction(stringResource(Res.string.kitTryAgain), onRetry, "kit-receipt-retry")
                if (undo != null) ReceiptAction(stringResource(Res.string.kitUndoAction), undo, "kit-receipt-undo")
            }
        }
    } }
    val outer = modifier.kitMaxWidth(CapRole.Reading).testTag("kit-receipt")
    // A held receipt is a pill around the same row; without a hold the row is the receipt, as before.
    if (hold == null) row(outer) else Box(outer) { row(Modifier.receiptHold(hold).testTag(RECEIPT_HOLD_TAG)) }
}

/** A tertiary action in its own padding, as Flutter's padded text button sits in the receipt's wrap. */
@Composable
private fun ReceiptAction(label: String, onClick: () -> Unit, tag: String) {
    Box(Modifier.padding(horizontal = Space.S2.dp)) {
        KitButton(label, onClick, Modifier.testTag(tag), role = KitButtonRole.Tertiary)
    }
}

/** A QUICK cross-fade between [target]s; Off = instant (Dart `AnimatedSwitcher(duration: fade)`). */
@Composable
private fun <T> KitFade(target: T, content: @Composable (T) -> Unit) {
    val specs = KitMotion.specs
    AnimatedContent(target, transitionSpec = { fadeIn(specs.quick()) togetherWith fadeOut(specs.quick()) }, label = "kit-receipt") {
        content(it)
    }
}

/** True once [mark] lies [after] back; flips by itself when it crosses (Dart `KitSince`). Without a mark: [fallback] after [after] shown. */
@Composable
private fun rememberPassed(mark: TimeMark?, after: Duration, fallback: Boolean): Boolean {
    var passed by remember(mark) { mutableStateOf(mark != null && mark.elapsedNow() >= after) }
    if (!passed && (mark != null || fallback)) {
        LaunchedEffect(mark, fallback) {
            delay(if (mark == null) after else (after - mark.elapsedNow()).coerceAtLeast(Duration.ZERO))
            passed = true
        }
    }
    return passed
}

/** The small-icon size grown with the person's text up to the cap, on whole pixels (Dart `_glyphSize`). */
@Composable
private fun receiptGlyphSize(): Dp {
    val tokens = LocalKitTokens.current
    val density = LocalDensity.current
    val grown = tokens.smallIcon * minOf(density.fontScale, tokens.maxIconScale)
    return (kotlin.math.round(grown * density.density) / density.density).dp
}

/** The state's mark (Dart `_mark`): decoration, the word already says it. */
@Composable
private fun ReceiptMark(words: ReceiptWords, size: Dp, reduced: Boolean) {
    val colors = LocalKitColors.current
    when (words) {
        ReceiptWords.Sending ->
            if (reduced) KitGlyph(KitIcons.statusDot, colors.accent, FlutterKitTokens.MARK_DOT_SIZE.dp)
            else {
                val ring = kitFocusRingWidth()
                KitWorkingMark(colors.accent, size - ring - ring, Modifier.padding(ring), strokeWidth = ring)
            }
        ReceiptWords.Sent, ReceiptWords.AnsweredOn, ReceiptWords.AnsweredElsewhere ->
            KitIcon(KitIcons.check, size = KitIconSize.Small, tone = TextTone.Secondary, growsWithText = true)
        ReceiptWords.Confirmed -> KitIcon(KitIcons.check, size = KitIconSize.Small, tone = TextTone.Success, growsWithText = true)
        ReceiptWords.NotConfirmed -> KitIcon(KitIcons.warning, size = KitIconSize.Small, tone = TextTone.Primary, growsWithText = true)
        ReceiptWords.Refused, ReceiptWords.RefusedReason ->
            KitIcon(KitIcons.error, size = KitIconSize.Small, tone = TextTone.Primary, growsWithText = true)
    }
}

/** Words in `text2`, and in `text1` where the person may need to act (Dart `_wordColor`). */
private fun wordColor(colors: KitColors, words: ReceiptWords): Color =
    if (ReceiptRules.asksToAct(words)) colors.text1 else colors.text2

/** The act an automatic line names in place of the state word, if any (Dart `_automaticAct`). */
private fun automaticAct(label: String?, automatic: Boolean): String? =
    if (!automatic) null else label?.trim()?.takeIf { it.isNotEmpty() }

/** The word a receipt shows: the state word, or the act (Dart `_visibleWord`). */
@Composable
private fun receiptWord(words: ReceiptWords, state: KitReceiptState, label: String?, sendingLabel: String?, automatic: Boolean): String {
    val reason = (state as? KitReceiptState.Refused)?.reason?.trim()
    if (words == ReceiptWords.RefusedReason && reason != null) {
        val act = automaticAct(label, automatic)
        return if (act == null) stringResource(Res.string.kitReceiptRefusedReason, reason)
        else stringResource(Res.string.kitReceiptActRefusedReason, act, reason)
    }
    return receiptPlainWord(words, state, label, sendingLabel, automatic)
}

/** The word without the refusal's reason (Dart `_plainWord`). */
@Composable
private fun receiptPlainWord(words: ReceiptWords, state: KitReceiptState, label: String?, sendingLabel: String?, automatic: Boolean): String {
    automaticAct(label, automatic)?.let { return it }
    val act = label?.trim()?.takeIf { it.isNotEmpty() }
    return when (words) {
        ReceiptWords.Sending -> sendingLabel?.trim()?.takeIf { it.isNotEmpty() } ?: stringResource(Res.string.kitReceiptSending)
        ReceiptWords.Sent -> stringResource(Res.string.kitReceiptSent)
        ReceiptWords.Confirmed -> act ?: stringResource(Res.string.kitReceiptConfirmed)
        ReceiptWords.NotConfirmed -> stringResource(Res.string.kitReceiptNotConfirmed)
        ReceiptWords.Refused, ReceiptWords.RefusedReason -> stringResource(Res.string.kitReceiptRefused)
        ReceiptWords.AnsweredOn ->
            stringResource(Res.string.kitReceiptAnsweredElsewhere, (state as KitReceiptState.AnsweredElsewhere).where.orEmpty().trim())
        ReceiptWords.AnsweredElsewhere -> stringResource(Res.string.kitReceiptAnsweredElsewhereUnknown)
    }
}

/**
 * A row's supporting line part (Dart `KitReceipt.span`): "Sent · ", "Not confirmed yet · ", "Not accepted: {reason} · ",
 * "Answered on {where} · ", in the receipt's word colour. No actions: a row's actions live in its menu or its sheet.
 */
@Composable
public fun kitReceiptSpan(
    state: KitReceiptState,
    label: String? = null,
    sendingLabel: String? = null,
): AnnotatedString {
    val words = ReceiptRules.words(state, unconfirmedTooLong = false)
    val word = receiptWord(words, state, label, sendingLabel, automatic = false)
    val color = wordColor(LocalKitColors.current, words)
    return buildAnnotatedString { withStyle(SpanStyle(color = color)) { append("$word · ") } }
}

/** One hold's countdown: its stretched length, the draining fill (1 = full, 0 = ended), and whether Undo ran. */
private class ReceiptHold(val ms: Long) {
    val fill = Animatable(1f)
    var undone = false
    var fired by mutableStateOf(false)
}

/**
 * The hold of a confirmed receipt with [countdownMs], null otherwise: the accessibility timeout stretches it, the fill
 * drains linearly over it (not under Off), and [onHoldEnd] runs once at its end unless Undo ran first.
 */
@Composable
private fun rememberHold(countdownMs: Int?, words: ReceiptWords, onHoldEnd: (() -> Unit)?): ReceiptHold? {
    if (countdownMs == null || words != ReceiptWords.Confirmed) return null
    val a11y = LocalAccessibilityManager.current
    val ms = remember(countdownMs, a11y) {
        a11y?.calculateRecommendedTimeoutMillis(countdownMs.toLong(), containsIcons = true, containsText = true, containsControls = true)
            ?: countdownMs.toLong()
    }
    val hold = remember(ms) { ReceiptHold(ms) }
    val ended by rememberUpdatedState(onHoldEnd)
    val specs = KitMotion.specs
    val reduced = KitMotion.state.reduced
    LaunchedEffect(hold) {
        coroutineScope {
            if (!reduced) launch { hold.fill.animateTo(0f, specs.countdown(ms.toInt())) }
            delay(ms)
            if (!hold.undone && !hold.fired) {
                hold.fired = true
                ended?.invoke()
            }
        }
    }
    return hold
}

/** The held receipt's pill: the style's control face by role, the tonal fill drained from the end toward the start. */
@Composable
private fun Modifier.receiptHold(hold: ReceiptHold): Modifier {
    val colors = LocalKitColors.current
    val look = kitSurfaceLook(KitSurfaceInput(role = SurfaceRole.Control, shape = KitShapeId.Pill, level = SurfaceLevel.Surface1, ownFill = colors.surface1))
    val tonal = colors.accent.copy(alpha = FlutterKitTokens.MARK_TINT_ALPHA)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return this
        .kitSurface(look)
        .drawBehind {
            val w = size.width * hold.fill.value.coerceIn(0f, 1f)
            if (w > 0f) drawRect(tonal, Offset(if (rtl) size.width - w else 0f, 0f), Size(w, size.height))
        }
        .padding(horizontal = Space.S3.dp, vertical = Space.S1.dp)
}

/** The held receipt's pill, for tests. */
public const val RECEIPT_HOLD_TAG: String = "kit-receipt-hold"
