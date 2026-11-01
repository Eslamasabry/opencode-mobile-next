package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitReceiptAnsweredElsewhere
import io.github.eslamasabry.oc.ui.words.res.kitReceiptAnsweredElsewhereUnknown
import io.github.eslamasabry.oc.ui.words.res.kitReceiptConfirmed
import io.github.eslamasabry.oc.ui.words.res.kitReceiptNotConfirmed
import io.github.eslamasabry.oc.ui.words.res.kitReceiptRefused
import io.github.eslamasabry.oc.ui.words.res.kitReceiptRefusedReason
import io.github.eslamasabry.oc.ui.words.res.kitReceiptSending
import io.github.eslamasabry.oc.ui.words.res.kitReceiptSent
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.resources.stringResource

/** "Sent" becomes "Not confirmed yet" when nothing confirms it within 8 s (Dart `KitReceipt`, STATE-10). */
public val RECEIPT_UNCONFIRMED_AFTER: kotlin.time.Duration = 8.seconds

/** Where an answer or a send stands (Dart `KitReceiptState`). "Sent" is never "Done". */
@Immutable
public sealed interface KitReceiptState {
    public data object Sending : KitReceiptState

    /** Left this phone; the other side has not confirmed it yet. */
    public data object Sent : KitReceiptState

    /** The other side confirmed it ("Done"). */
    public data object Confirmed : KitReceiptState

    /** The other side refused it; [reason] is words for people, already resolved. */
    public data class Refused(val reason: String? = null) : KitReceiptState

    /** Someone answered it on another device; [where] names it when known. */
    public data class AnsweredElsewhere(val where: String? = null) : KitReceiptState
}

/** What a receipt says (pure; the words are resolved by [KitReceipt]). */
public enum class ReceiptWords { Sending, Sent, NotConfirmed, Confirmed, Refused, RefusedReason, AnsweredOn, AnsweredElsewhere }

/** Pure rules of [KitReceipt]. */
public object ReceiptRules {
    public fun words(state: KitReceiptState, unconfirmedTooLong: Boolean): ReceiptWords =
        when (state) {
            KitReceiptState.Sending -> ReceiptWords.Sending
            KitReceiptState.Sent -> if (unconfirmedTooLong) ReceiptWords.NotConfirmed else ReceiptWords.Sent
            KitReceiptState.Confirmed -> ReceiptWords.Confirmed
            is KitReceiptState.Refused -> if (state.reason == null) ReceiptWords.Refused else ReceiptWords.RefusedReason
            is KitReceiptState.AnsweredElsewhere -> if (state.where == null) ReceiptWords.AnsweredElsewhere else ReceiptWords.AnsweredOn
        }
}

/**
 * The line under something the person sent or answered (Dart `KitReceipt`; plan 06 §7.6): Sending… → Sent → Done, or
 * Not accepted (with [onRetry] offering Try again), or Answered on another device. "Sent" is never "Done": only the
 * other side's confirmation says Done, and an unconfirmed send says "Not confirmed yet" after 8 s. A polite live region,
 * one glyph per state (never colour alone).
 *
 * States: sending, sent, not confirmed, confirmed, refused, answered elsewhere
 * Text: frame
 * Cap: Reading
 * Motion: the sending mark turns only when loops are allowed
 * Replaces: KitReceipt, KitReceiptState (lib/ui/kit/kit_receipt.dart)
 */
@Composable
public fun KitReceipt(state: KitReceiptState, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    var late by remember(state) { mutableStateOf(false) }
    if (state == KitReceiptState.Sent) {
        LaunchedEffect(state) {
            delay(RECEIPT_UNCONFIRMED_AFTER)
            late = true
        }
    }
    val kind = ReceiptRules.words(state, late)
    val words =
        when (kind) {
            ReceiptWords.Sending -> stringResource(Res.string.kitReceiptSending)
            ReceiptWords.Sent -> stringResource(Res.string.kitReceiptSent)
            ReceiptWords.NotConfirmed -> stringResource(Res.string.kitReceiptNotConfirmed)
            ReceiptWords.Confirmed -> stringResource(Res.string.kitReceiptConfirmed)
            ReceiptWords.Refused -> stringResource(Res.string.kitReceiptRefused)
            ReceiptWords.RefusedReason ->
                stringResource(Res.string.kitReceiptRefusedReason, (state as KitReceiptState.Refused).reason.orEmpty())
            ReceiptWords.AnsweredOn ->
                stringResource(Res.string.kitReceiptAnsweredElsewhere, (state as KitReceiptState.AnsweredElsewhere).where.orEmpty())
            ReceiptWords.AnsweredElsewhere -> stringResource(Res.string.kitReceiptAnsweredElsewhereUnknown)
        }
    val tone = if (kind == ReceiptWords.Confirmed) TextTone.Success else TextTone.Secondary
    val glyphSize = (tokens.smallIcon - 4).dp
    Row(
        modifier.heightIn(min = 24.dp).testTag("kit-receipt").semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        when (kind) {
            ReceiptWords.Sending -> KitWorkingMark(colors.text2, glyphSize)
            ReceiptWords.Confirmed -> KitGlyph(KitPartGlyphs.Check, colors.success, glyphSize)
            ReceiptWords.Refused, ReceiptWords.RefusedReason, ReceiptWords.NotConfirmed -> KitGlyph(KitPartGlyphs.Warning, colors.text1, glyphSize)
            else -> KitGlyph(KitPartGlyphs.Ring, colors.text2, glyphSize)
        }
        KitText(words, role = TypeRole.Secondary, tone = tone, kind = KitTextKind.Frame, maxLines = 2)
        if (onRetry != null && (kind == ReceiptWords.Refused || kind == ReceiptWords.RefusedReason || kind == ReceiptWords.NotConfirmed)) {
            KitButton(label = stringResource(Res.string.kitTryAgain), onClick = onRetry, role = KitButtonRole.Tertiary)
        }
    }
}
