package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
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
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitEntrance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import org.jetbrains.compose.resources.stringResource

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

/** The tone's default glyph (Dart `KitNotice._iconFor`, kit_notice.dart `_iconFor`). */
internal fun noticeGlyph(tone: StatusTone): ImageVector =
    when (tone) {
        StatusTone.Ok -> KitIcons.checkCircle
        StatusTone.Failure -> KitIcons.error
        StatusTone.Attention -> KitIcons.warning
        else -> KitIcons.info
    }

/**
 * The tone's ink (Dart `KitNotice._tintFor`; LOOK-4, LOOK-5): `text2` when neutral, `accent` while working,
 * `success` when ok, and `text1` for a warning or a failure, never amber or red.
 */
internal fun noticeTint(tone: StatusTone): TextTone =
    when (tone) {
        StatusTone.Neutral -> TextTone.Secondary
        StatusTone.Progress -> TextTone.Accent
        StatusTone.Ok -> TextTone.Success
        StatusTone.Attention,
        StatusTone.Failure -> TextTone.Primary
    }

/**
 * Whether an offer's action moves under its sentence (Dart `KitNotice._offerStacks`): the sentence, the action and
 * the close do not fit on one line of [width].
 */
internal fun offerStacks(width: Dp, message: Dp, action: Dp, lead: Dp, close: Dp): Boolean =
    message + action + lead + Space.S3.dp + close > width // F: lib/ui/kit/kit_notice.dart:570-588

/** How far a stacked offer drops so its first line centres on the close's target (Dart `_offerLine` `drop`). */
internal fun offerDrop(minTarget: Dp, firstLine: Dp, hasClose: Boolean): Dp =
    if (!hasClose) 0.dp else max(0.dp, kotlin.math.floor(((minTarget - firstLine) / 2).value).dp) // F: lib/ui/kit/kit_notice.dart:613-615

/** The leading mark's sizes: the icon at the person's text size (capped) and its slot (Dart `KitNotice.mark`). */
private class NoticeMark(val icon: Dp, val slot: Dp, val height: Dp)

@Composable
private fun noticeMark(firstLineRole: TypeRole): NoticeMark {
    val tokens = LocalKitTokens.current
    val density = LocalDensity.current
    val icon = tokens.smallIcon.dp * minOf(kitTextScale(), tokens.maxIconScale) // F: lib/ui/kit/kit_tokens.dart:755-757
    val line = with(density) { LocalKitTypography.current.style(firstLineRole).lineHeight.toDp() }
    // KitLeadColumn.slotOf: the tile-wide slot, so the words start on a list row's text rail.
    return NoticeMark(icon, max(icon, tokens.iconTile.dp), max(line, icon))
}

@Composable
private fun NoticeMarkBox(mark: NoticeMark, glyph: ImageVector, tint: TextTone, modifier: Modifier = Modifier) {
    Box(modifier.size(mark.slot, mark.height), contentAlignment = Alignment.Center) {
        KitIcon(glyph, size = KitIconSize.Small, tone = tint, growsWithText = true)
    }
}

/** A notice's acts under its words (Dart `_actionsRow`): small neutral tonal buttons, start-aligned, wrapping. */
@Composable
private fun NoticeActions(shown: List<KitLineAction>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        shown.forEach { it.asButton(KitButtonRole.Secondary, Modifier.testTag(it.testTag ?: "kit-notice-action")) }
    }
}

@Composable
private fun KitLineAction.asButton(role: KitButtonRole, modifier: Modifier) {
    KitButton(
        KitAction(
            label,
            onClick,
            enabled = KitEnabled.of(enabled, disabledReason),
            icon = icon,
            semanticsLabel = semanticsLabel,
        ),
        modifier,
        role,
    )
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

/** The notice's frame: its reading cap, a vertical `space1` on the host's rails, one live region, the entrance. */
@Composable
private fun NoticeFrame(
    modifier: Modifier,
    tag: String,
    liveRegion: Boolean,
    key: Any?,
    content: @Composable () -> Unit,
) {
    KitEntrance(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().testTag(tag), key = key) {
        Box(
            Modifier.fillMaxWidth()
                .semantics { if (liveRegion) this.liveRegion = LiveRegionMode.Polite }
                .padding(vertical = Space.S1.dp)
        ) { content() }
    }
}

/**
 * A message about one part of a screen (Dart `KitNotice`): a tone glyph centred on the first line in a tile-wide
 * slot, an optional title, the message, plain notes, and at most two small tonal actions under the words. It sits
 * on the host's rails with no block around it. It never carries raw error text: [KitErrorNotice] takes a `Failure`
 * and redacted details. Attention and failure draw their glyph in `text1`, never amber (LOOK-4) or red (LOOK-5). One
 * polite live region, unless [liveRegion] is off. Dismiss only when dismissing changes nothing real.
 *
 * States: neutral, working, ok, warning, error, cost, offer Text: reading Cap: Reading Motion: fades and rises in
 * (KitEntrance), again when the tone changes; Off = instant Hidden: none Replaces: KitNotice (lib/ui/kit/kit_notice.dart)
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
    NoticeBody(
        message = message,
        modifier = modifier,
        tag = "kit-notice",
        title = title,
        tone = tone,
        glyph = icon ?: noticeGlyph(tone),
        notes = notes,
        shown = actions.take(2), // F: lib/ui/kit/kit_notice.dart:403
        trailingParts = listOfNotNull(trailing, dismiss?.let { d -> @Composable { NoticeClose(d) } }),
        liveRegion = liveRegion,
    )
}

@Composable
private fun NoticeBody(
    message: String,
    modifier: Modifier,
    tag: String,
    title: String?,
    tone: StatusTone,
    glyph: ImageVector,
    notes: List<String>,
    shown: List<KitLineAction>,
    trailingParts: List<@Composable () -> Unit>,
    liveRegion: Boolean,
) {
    val mark = noticeMark(if (title == null) TypeRole.Body else TypeRole.RowTitle)
    NoticeFrame(modifier, tag, liveRegion, key = tone to glyph) {
        Row(verticalAlignment = Alignment.Top) {
            NoticeMarkBox(mark, glyph, noticeTint(tone))
            Spacer(Modifier.width(Space.S3.dp))
            Column(Modifier.weight(1f)) {
                if (title != null) {
                    KitText(title, role = TypeRole.RowTitle)
                    Spacer(Modifier.height((Space.S1 / 2).dp)) // F: lib/ui/kit/kit_notice.dart:518
                }
                KitText(
                    message,
                    Modifier.testTag("kit-notice-message"),
                    role = if (title == null) TypeRole.Body else TypeRole.Secondary,
                    tone = if (title == null) TextTone.Primary else TextTone.Secondary,
                )
                notes.forEach {
                    Spacer(Modifier.height(Space.S1.dp))
                    KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary)
                }
                if (shown.isNotEmpty()) {
                    Spacer(Modifier.height(Space.S2.dp))
                    NoticeActions(shown)
                }
            }
            trailingParts.forEachIndexed { i, part ->
                if (i > 0) Spacer(Modifier.width(Space.S2.dp))
                part()
            }
        }
    }
}

/**
 * The cost of an install or turn-on, stated before its primary (Dart `KitNotice.cost`; KIT-37): an optional title
 * over the items joined with " · ", no glyph. Static text beside a button, so not a live region.
 *
 * States: cost Text: reading Cap: Reading Motion: KitEntrance; Off = instant Hidden: none Replaces: KitNotice.cost
 * (lib/ui/kit/kit_notice.dart)
 */
@Composable
public fun KitCostNotice(
    items: List<String>,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    NoticeFrame(modifier, "kit-notice-cost", liveRegion = false, key = Unit) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            if (title != null) {
                KitText(title, role = TypeRole.RowTitle)
                Spacer(Modifier.height((Space.S1 / 2).dp)) // F: lib/ui/kit/kit_notice.dart:359
            }
            KitText(
                items.joinToString(" · "),
                Modifier.testTag("kit-notice-message"),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        }
    }
}

/**
 * One sentence, one action, one close (Dart `KitNotice.offer`): a one-time tip or a turn-on offer, with the idea
 * glyph. The action is a text button after the sentence; when the sentence, the action and the close do not fit on
 * one line, the close stays at the end of the sentence's first line and the action moves under the sentence,
 * starting at its text inset.
 *
 * States: offer, offer wrapped Text: reading Cap: Reading Motion: KitEntrance; Off = instant Hidden: none Replaces:
 * KitNotice.offer, NudgeCard (lib/ui/kit/kit_notice.dart)
 */
@Composable
public fun KitOfferNotice(
    message: String,
    action: KitLineAction,
    dismiss: KitLineAction,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalKitTokens.current
    val typography = LocalKitTypography.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val mark = noticeMark(TypeRole.Body)
    val glyph = KitIcons.idea
    NoticeFrame(modifier, "kit-notice-offer", liveRegion = true, key = StatusTone.Neutral to glyph) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            fun measure(text: String, role: TypeRole): Dp =
                with(density) {
                    measurer.measure(AnnotatedString(text), typography.style(role), maxLines = 1).size.width.toDp()
                }
            val actionWidth =
                max(
                    measure(action.label, TypeRole.Button) + (Space.S2 * 2).dp + Space.S1.dp, // F: lib/ui/kit/kit_notice.dart:576-578 lib/ui/kit/kit_buttons.dart:307
                    tokens.minTarget.dp,
                )
            val stacked =
                offerStacks(maxWidth, measure(message, TypeRole.Body), actionWidth, mark.slot, tokens.minTarget.dp)
            val text = @Composable { KitText(message, Modifier.testTag("kit-notice-message"), role = TypeRole.Body) }
            if (!stacked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NoticeMarkBox(mark, glyph, noticeTint(StatusTone.Neutral))
                    Spacer(Modifier.width(Space.S3.dp))
                    Box(Modifier.weight(1f)) { text() }
                    // The text button keeps its own side padding beside the sentence (Flutter's tertiary inset).
                    Box(Modifier.padding(horizontal = Space.S2.dp)) {
                        action.asButton(KitButtonRole.Tertiary, Modifier.testTag(action.testTag ?: "kit-notice-action"))
                    }
                    NoticeClose(dismiss)
                }
            } else {
                val firstLine = with(density) { typography.style(TypeRole.Body).lineHeight.toDp() }
                val drop = offerDrop(tokens.minTarget.dp, firstLine, hasClose = true)
                Row(verticalAlignment = Alignment.Top) {
                    NoticeMarkBox(mark, glyph, noticeTint(StatusTone.Neutral), Modifier.padding(top = drop))
                    Spacer(Modifier.width(Space.S3.dp))
                    Column(Modifier.weight(1f).padding(top = drop)) {
                        text()
                        // Flutter puts the button's box flush under the sentence; the native layout rule (LAYOUT-RULES
                        // §5 `tight`) needs one S1 step between words and a control below them.
                        Spacer(Modifier.height(Space.S1.dp))
                        // KitInset: the text button's words start at the sentence's inset.
                        action.asButton(KitButtonRole.Tertiary, Modifier.testTag(action.testTag ?: "kit-notice-action"))
                    }
                    NoticeClose(dismiss)
                }
            }
        }
    }
}

/**
 * A failure about one part (Dart `KitNotice.error`; STATE-20): the failure glyph in `text1`, the words from the
 * caller (from `:ui:words` for the [failure]); the kit picks the fix by [KitErrorKind.of]: network → [retry] and
 * [switchServer]; other → [retry] and [report] (only when the app set a report hook). Whenever [details] (already
 * redacted) are given, a trailing Copy details button copies them; they are never shown. A caller that passes
 * [detailsLabel] also gets them in a `KitDetailsFold` under the notice, last and collapsed.
 *
 * States: error (network), error (other) Text: reading Cap: Reading Motion: KitEntrance; Off = instant Hidden: none
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
    val clipboard = LocalClipboardManager.current
    val copyWords = copyLabel ?: stringResource(Res.string.kitCopyDetails)
    val copy: (@Composable () -> Unit)? =
        details?.let { d ->
            @Composable {
                KitIconButton(
                    KitIcons.copy,
                    copyWords,
                    { clipboard.setText(AnnotatedString(d.text)) },
                    Modifier.testTag("kit-notice-copy-details"),
                    size = LocalKitTokens.current.smallIcon,
                )
            }
        }
    Column(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().testTag("kit-notice-error")) {
        NoticeBody(
            message = message,
            modifier = Modifier,
            tag = "kit-notice",
            title = title,
            tone = StatusTone.Failure,
            glyph = noticeGlyph(StatusTone.Failure),
            notes = emptyList(),
            shown = actions,
            trailingParts = listOfNotNull(copy),
            liveRegion = true,
        )
        if (details != null && detailsLabel != null) {
            KitDetailsFold(label = detailsLabel, copyLabel = copyWords, text = details)
        }
    }
}
