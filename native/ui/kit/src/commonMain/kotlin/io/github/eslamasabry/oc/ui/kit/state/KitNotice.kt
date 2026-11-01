package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitInlineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.status.KitStatusGlyph
import io.github.eslamasabry.oc.ui.kit.status.glyphColor
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/** Network or not (Dart `KitErrorKind`): decides between "Report a problem" and the network fix. */
public enum class KitErrorKind {
    Network,
    Other,
    ;

    public companion object {
        /**
         * The native classifier (B-KIT-024). Dart matched exception types: timeouts, sockets, handshakes, HTTP client and
         * WebSocket errors were network. Native code has no raw errors at the kit, only [Failure] values, so the same
         * meaning is read from the flat list: no network or unreachable ([Failure.Network]), a timeout
         * ([Failure.TimedOut]), a refused handshake ([Failure.Certificate] and the transport refusals), a delivery left
         * unknown (`Refused(DispatchUncertain)`, plan 04 §3.8), the Paseo computer not answering and the generic
         * connectivity line are network; a server that answered (busy, rejected, failed, not found…) and every other
         * app-side failure are other. Null → other.
         */
        public fun of(failure: Failure?): KitErrorKind =
            when (failure) {
                is Failure.Network, is Failure.TimedOut, is Failure.Certificate, is Failure.ComputerDidNotAnswer,
                is Failure.Unknown,
                -> Network
                is Failure.Refused -> if (failure.reason in TRANSPORT_REFUSALS) Network else Other
                else -> Other
            }

        /** The refusals that were the old `TrustRefused` and `DeliveryUnknown` leaves: the link, not the app. */
        private val TRANSPORT_REFUSALS: Set<RefusalReason> =
            setOf(
                RefusalReason.DispatchUncertain, RefusalReason.CleartextNotConfirmed, RefusalReason.OriginChanged,
                RefusalReason.CredentialsInUrl, RefusalReason.PinNeedsHttps,
            )
    }
}

/**
 * A message about one part of a screen (Dart `KitNotice`): a tone glyph, an optional title, the message, plain notes,
 * and at most two small actions. It never carries raw error text: [KitErrorNotice] takes a `Failure` and redacted
 * details. Attention and failure draw their glyph in `text1`, never amber (LOOK-4) or red (LOOK-5). One polite live
 * region, unless [liveRegion] is off. Dismiss only when dismissing changes nothing real.
 *
 * States: neutral, working, ok, warning, error, cost, offer
 * Text: reading
 * Cap: Reading
 * Motion: none
 * Hidden: none
 * Replaces: KitNotice (lib/ui/kit/kit_notice.dart)
 */
@Composable
public fun KitNotice(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: StatusTone = StatusTone.Neutral,
    notes: List<String> = emptyList(),
    actions: List<KitLineAction> = emptyList(),
    dismiss: KitLineAction? = null,
    liveRegion: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalKitColors.current
    Column(
        modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().padding(vertical = Space.S1.dp).testTag("kit-notice"),
        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            KitStatusGlyph(tone.glyph, glyphColor(tone, colors), Modifier.padding(top = 2.dp))
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) { if (liveRegion) this.liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                title?.let { KitText(it, role = TypeRole.RowTitle) }
                KitText(
                    message,
                    Modifier.testTag("kit-notice-message"),
                    role = if (title == null) TypeRole.Body else TypeRole.Secondary,
                    tone = if (title == null) TextTone.Primary else TextTone.Secondary,
                )
                notes.forEach { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
            }
            trailing?.invoke()
            dismiss?.let { KitInlineAction(it.copy(testTag = it.testTag ?: "kit-notice-dismiss")) }
        }
        val shown = actions.take(2)
        if (shown.isNotEmpty()) {
            FlowRow(Modifier.padding(start = (20 + Space.S3).dp), horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                shown.forEach { KitInlineAction(it) }
            }
        }
    }
}

/**
 * The cost of an install or turn-on, stated before its primary (Dart `KitNotice.cost`; KIT-37): the items joined with
 * " · ". Static text beside a button, so not a live region.
 *
 * States: cost
 * Text: reading
 * Cap: Reading
 * Motion: none
 * Hidden: none
 * Replaces: KitNotice.cost (lib/ui/kit/kit_notice.dart)
 */
@Composable
public fun KitCostNotice(items: List<String>, modifier: Modifier = Modifier, title: String? = null) {
    KitNotice(items.joinToString(" · "), modifier.testTag("kit-notice-cost"), title = title, liveRegion = false)
}

/**
 * One sentence, one action, one close (Dart `KitNotice.offer`): a one-time tip or a turn-on offer.
 *
 * States: offer
 * Text: reading
 * Cap: Reading
 * Motion: none
 * Hidden: none
 * Replaces: KitNotice.offer, NudgeCard (lib/ui/kit/kit_notice.dart)
 */
@Composable
public fun KitOfferNotice(message: String, action: KitLineAction, dismiss: KitLineAction, modifier: Modifier = Modifier) {
    KitNotice(message, modifier.testTag("kit-notice-offer"), tone = StatusTone.Neutral, actions = listOf(action), dismiss = dismiss)
}

/**
 * A failure about one part (Dart `KitNotice.error`; STATE-20). The words come from the caller (from `:ui:words` for the
 * [failure]); the kit picks the fix by [KitErrorKind.of]: network → [retry] and [switchServer]; other → [retry] and
 * [report] (only when the app set a report hook). [details] (already redacted) fold under a `KitDetailsFold`, last and
 * collapsed — the only place technical text appears.
 *
 * States: error (network), error (other)
 * Text: reading
 * Cap: Reading
 * Motion: none
 * Hidden: none
 * Replaces: KitNotice.error (lib/ui/kit/kit_notice.dart)
 */
@Composable
public fun KitErrorNotice(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    failure: Failure? = null,
    details: Redacted? = failure?.details,
    detailsLabel: String? = null,
    copyLabel: String? = null,
    retry: KitLineAction? = null,
    switchServer: KitLineAction? = null,
    report: KitLineAction? = null,
) {
    val kind = KitErrorKind.of(failure)
    val actions =
        listOfNotNull(retry, if (kind == KitErrorKind.Network) switchServer else report)
    Column(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().testTag("kit-notice-error")) {
        KitNotice(message, title = title, tone = StatusTone.Failure, actions = actions)
        if (details != null && detailsLabel != null && copyLabel != null) {
            KitDetailsFold(label = detailsLabel, copyLabel = copyLabel, text = details)
        }
    }
}
