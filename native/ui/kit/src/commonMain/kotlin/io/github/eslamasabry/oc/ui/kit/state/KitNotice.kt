package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.Image
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kit.action.KitInset
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.status.KitStatusGlyph
import io.github.eslamasabry.oc.ui.kit.status.glyphColor
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

private const val LEAD_SLOT =
    28 // NV: native stand-in for KitLeadColumn.slotOf (no literal in Flutter)

/** Network or not (Dart `KitErrorKind`): decides between "Report a problem" and the network fix. */
public enum class KitErrorKind {
    Network,
    Other;

    public companion object {
        /**
         * The native classifier (B-KIT-024). Dart matched exception types: timeouts, sockets,
         * handshakes, HTTP client and WebSocket errors were network. Native code has no raw errors
         * at the kit, only [Failure] values, so the same meaning is read from the flat list: no
         * network or unreachable ([Failure.Network]), a timeout ([Failure.TimedOut]), a refused
         * handshake ([Failure.Certificate] and the transport refusals), a delivery left unknown
         * (`Refused(DispatchUncertain)`, plan 04 §3.8), the Paseo computer not answering and the
         * generic connectivity line are network; a server that answered (busy, rejected, failed,
         * not found…) and every other app-side failure are other. Null → other.
         */
        public fun of(failure: Failure?): KitErrorKind =
            when (failure) {
                is Failure.Network,
                is Failure.TimedOut,
                is Failure.Certificate,
                is Failure.ComputerDidNotAnswer,
                is Failure.Unknown -> Network
                is Failure.Refused -> if (failure.reason in TRANSPORT_REFUSALS) Network else Other
                else -> Other
            }

        /**
         * The refusals that were the old `TrustRefused` and `DeliveryUnknown` leaves: the link, not
         * the app.
         */
        private val TRANSPORT_REFUSALS: Set<RefusalReason> =
            setOf(
                RefusalReason.DispatchUncertain,
                RefusalReason.CleartextNotConfirmed,
                RefusalReason.OriginChanged,
                RefusalReason.CredentialsInUrl,
                RefusalReason.PinNeedsHttps,
            )
    }
}

/**
 * A message about one part of a screen (Dart `KitNotice`): a tone glyph, an optional title, the
 * message, plain notes, and at most two small actions. It never carries raw error text:
 * [KitErrorNotice] takes a `Failure` and redacted details. Attention and failure draw their glyph
 * in `text1`, never amber (LOOK-4) or red (LOOK-5). One polite live region, unless [liveRegion] is
 * off. Dismiss only when dismissing changes nothing real.
 *
 * States: neutral, working, ok, warning, error, cost, offer Text: reading Cap: Reading Motion: none
 * Hidden: none Replaces: KitNotice (lib/ui/kit/kit_notice.dart)
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
    icon: ImageVector? = null,
) {
    Column(
        modifier
            .kitMaxWidth(CapRole.Reading)
            .fillMaxWidth()
            .padding(vertical = Space.S1.dp) // F: lib/ui/kit/kit_notice.dart:542
            .testTag("kit-notice"),
    ) {
        // Flutter `_buildNotice`: mark, space3, the words, then the trailing close with no gap of its own.
        Row(verticalAlignment = Alignment.Top) {
            NoticeMark(tone, icon, firstLine = if (title == null) NOTICE_MESSAGE_ROLE else TypeRole.RowTitle)
            Spacer(Modifier.width(Space.S3.dp)) // F: lib/ui/kit/kit_notice.dart:510
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    if (liveRegion) this.liveRegion = LiveRegionMode.Polite
                },
            ) {
                title?.let {
                    KitText(it, role = TypeRole.RowTitle)
                    Spacer(Modifier.height((Space.S1 / 2).dp)) // F: lib/ui/kit/kit_notice.dart:518
                }
                KitText(
                    message,
                    Modifier.testTag("kit-notice-message"),
                    // Flutter: body without a title, secondary under one (kit_notice.dart:470-472); the message keeps
                    // primary ink either way.
                    role = if (title == null) NOTICE_MESSAGE_ROLE else TypeRole.Secondary,
                    tone = if (title == null) TextTone.Primary else TextTone.Secondary,
                )
                notes.forEach {
                    Spacer(Modifier.height(Space.S1.dp)) // F: lib/ui/kit/kit_notice.dart:522
                    KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary)
                }
                val shown = actions.take(2) // F: lib/ui/kit/kit_notice.dart:403
                if (shown.isNotEmpty()) {
                    FlowRow(
                        Modifier.padding(top = Space.S2.dp), // F: lib/ui/kit/kit_notice.dart:526
                        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
                        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    ) {
                        shown.forEach {
                            KitButton(
                                it.toKitAction(),
                                Modifier.testTag(it.testTag ?: "kit-notice-action"),
                                KitButtonRole.Secondary,
                                expand = false,
                            )
                        }
                    }
                }
            }
            trailing?.invoke()
            dismiss?.let { NoticeClose(it) }
        }
    }
}

/** A notice without a title says its message at the body size (Dart `KitTextRole.body`). */
private val NOTICE_MESSAGE_ROLE: TypeRole = TypeRole.Body // F: lib/ui/kit/kit_notice.dart:470-472

private fun KitLineAction.toKitAction(): KitAction =
    KitAction(label, onClick, enabled = KitEnabled.of(enabled, disabledReason), icon = icon, semanticsLabel = semanticsLabel)

/**
 * The leading column (Dart `KitLeadColumn`): the glyph centred in a tile-wide slot, and centred on the first line of
 * [firstLine] (Flutter `SizedBox(width: max(icon, slot), height: max(firstLine, icon))`).
 */
@Composable
private fun NoticeMark(tone: StatusTone, icon: ImageVector?, firstLine: TypeRole, glyph: ImageVector? = null) {
    val colors = LocalKitColors.current
    val size = LocalKitTokens.current.smallIcon
    val line = with(LocalDensity.current) { LocalKitTypography.current.style(firstLine).lineHeight.toDp() }
    Box(
        Modifier.width(maxOf(LEAD_SLOT, size).dp).height(maxOf(line, size.dp)).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val shown = glyph ?: icon
        if (shown != null || tone == StatusTone.Neutral) {
            Image(
                shown ?: KitIcons.info,
                null,
                Modifier.size(size.dp),
                colorFilter = ColorFilter.tint(glyphColor(tone, colors)), // F: kit_notice.dart _iconFor info
            )
        } else {
            KitStatusGlyph(tone.glyph, glyphColor(tone, colors))
        }
    }
}

@Composable
private fun NoticeClose(dismiss: KitLineAction) {
    KitIconButton(
        KitIcons.close,
        dismiss.label,
        dismiss.onClick,
        Modifier.testTag(dismiss.testTag ?: "kit-notice-dismiss"),
        size = LocalKitTokens.current.smallIcon,
    )
}

/**
 * The cost of an install or turn-on, stated before its primary (Dart `KitNotice.cost`; KIT-37): an optional row title
 * and the items joined with " · " in the secondary size, no glyph. Static text beside a button, so not a live region.
 *
 * States: cost Text: reading Cap: Reading Motion: none Hidden: none Replaces: KitNotice.cost
 * (lib/ui/kit/kit_notice.dart)
 */
@Composable
public fun KitCostNotice(
    items: List<String>,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Column(
        modifier
            .kitMaxWidth(CapRole.Reading)
            .fillMaxWidth()
            .padding(vertical = Space.S1.dp) // F: lib/ui/kit/kit_notice.dart:352
            .semantics(mergeDescendants = true) {}
            .testTag("kit-notice-cost"),
    ) {
        title?.let {
            KitText(it, role = TypeRole.RowTitle)
            Spacer(Modifier.height((Space.S1 / 2).dp)) // F: lib/ui/kit/kit_notice.dart:359
        }
        KitText(items.joinToString(" · "), Modifier.testTag("kit-notice-message"), role = TypeRole.Secondary, tone = TextTone.Secondary)
    }
}

/**
 * One sentence, one action, one close (Dart `KitNotice.offer`): a one-time tip or a turn-on offer. One line (the idea
 * mark, the sentence, the action as words, the close) while it fits; otherwise the action moves under the sentence at
 * its text inset, and the sentence drops so its first line is centred on the close (Dart `_offerLine`).
 *
 * States: offer Text: reading Cap: Reading Motion: none Hidden: none Replaces: KitNotice.offer,
 * NudgeCard (lib/ui/kit/kit_notice.dart)
 */
@Composable
public fun KitOfferNotice(
    message: String,
    action: KitLineAction,
    dismiss: KitLineAction,
    modifier: Modifier = Modifier,
) {
    val typography = LocalKitTypography.current
    val tokens = LocalKitTokens.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier
            .kitMaxWidth(CapRole.Reading)
            .fillMaxWidth()
            .padding(vertical = Space.S1.dp) // F: lib/ui/kit/kit_notice.dart:542
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("kit-notice-offer"),
    ) {
        val density = LocalDensity.current
        fun width(text: String, role: TypeRole) =
            with(density) { measurer.measure(text, typography.style(role), maxLines = 1).size.width.toDp().value }
        // Dart `_offerStacks`: sentence + action words (+ 2 x tertiary inset + space1, at least a target) + mark,
        // space3 and the close's target, against the width.
        val fixed = maxOf(tokens.smallIcon, LEAD_SLOT) + Space.S3 + tokens.minTarget
        val actionWidth = maxOf(width(action.label, TypeRole.Button) + 2 * Space.S2 + Space.S1, tokens.minTarget.toFloat())
        val stacked = constraints.hasBoundedWidth && width(message, NOTICE_MESSAGE_ROLE) + actionWidth + fixed > maxWidth.value
        val words: @Composable (Modifier) -> Unit = {
            KitText(message, it.testTag("kit-notice-message"), role = NOTICE_MESSAGE_ROLE, tone = TextTone.Primary)
        }
        val button: @Composable () -> Unit = {
            KitButton(action.toKitAction(), Modifier.testTag(action.testTag ?: "kit-notice-action"), KitButtonRole.Tertiary, expand = false)
        }
        if (!stacked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NoticeMark(StatusTone.Neutral, null, NOTICE_MESSAGE_ROLE, glyph = KitIcons.idea)
                Spacer(Modifier.width(Space.S3.dp))
                words(Modifier.weight(1f))
                // Inline, the words' button keeps its own 48 dp box (Flutter TextButton), without the column-edge bleed.
                androidx.compose.runtime.CompositionLocalProvider(io.github.eslamasabry.oc.ui.kit.LocalKitStackedAction provides true) { button() }
                NoticeClose(dismiss)
            }
        } else {
            val line = with(density) { typography.style(NOTICE_MESSAGE_ROLE).lineHeight.toDp().value }
            val drop = maxOf(0f, kotlin.math.floor((tokens.minTarget - line) / 2f)).dp // F: lib/ui/kit/kit_notice.dart:617-619
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = drop)) { NoticeMark(StatusTone.Neutral, null, NOTICE_MESSAGE_ROLE, glyph = KitIcons.idea) }
                Spacer(Modifier.width(Space.S3.dp))
                Column(Modifier.weight(1f).padding(top = drop)) {
                    words(Modifier)
                    // Flutter sets the action's 48 dp box flush under the sentence; the native layout rules keep a
                    // control 4 dp from the words above it (LAYOUT-RULES.md §5 `tight`), so it starts one space1 lower.
                    Spacer(Modifier.height(Space.S1.dp))
                    KitInset { button() }
                }
                NoticeClose(dismiss)
            }
        }
    }
}

/**
 * A failure about one part (Dart `KitNotice.error`; STATE-20). The words come from the caller (from
 * `:ui:words` for the [failure]); the kit picks the fix by [KitErrorKind.of]: network → [retry] and
 * [switchServer]; other → [retry] and [report] (only when the app set a report hook). [details]
 * (already redacted) fold under a `KitDetailsFold`, last and collapsed — the only place technical
 * text appears.
 *
 * States: error (network), error (other) Text: reading Cap: Reading Motion: none Hidden: none
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
    val actions = listOfNotNull(retry, if (kind == KitErrorKind.Network) switchServer else report)
    Column(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().testTag("kit-notice-error")) {
        KitNotice(message, title = title, tone = StatusTone.Failure, actions = actions)
        if (details != null && detailsLabel != null && copyLabel != null) {
            KitDetailsFold(label = detailsLabel, copyLabel = copyLabel, text = details)
        }
    }
}
