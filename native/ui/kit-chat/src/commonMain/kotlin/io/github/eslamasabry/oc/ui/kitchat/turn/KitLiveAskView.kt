package io.github.eslamasabry.oc.ui.kitchat.turn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePanel
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/** How the one-time ask is drawn: a compact card, a one-line pill, or a notice row. */
public enum class KitLiveAskLook { Card, Pill, Notice }

/**
 * A friendly one-time ask that rides under the live line while the person waits (the GitHub star request). It folds into
 * the line, so the transcript gains no bar or row level of its own. [title] and [body] are the words, [actionLabel] the
 * main action (the pill and the card say it in full), [shortActionLabel] the notice row's one-word action, and
 * [dismissLabel] names the close (never drawn as words: a quiet x).
 */
public class KitLiveAsk(
    public val title: String,
    public val body: String,
    public val actionLabel: String,
    public val shortActionLabel: String,
    public val onAction: () -> Unit,
    public val dismissLabel: String,
    public val onDismiss: () -> Unit,
    public val look: KitLiveAskLook = KitLiveAskLook.Card,
)

/** Test handle of the live line's one-time ask. */
public const val LIVE_ASK_TAG: String = "kit-turn-live-ask"

/** Test handle of the ask's main action. */
public const val LIVE_ASK_ACTION_TAG: String = "kit-turn-live-ask-action"

/** Test handle of the ask's dismiss. */
public const val LIVE_ASK_DISMISS_TAG: String = "kit-turn-live-ask-dismiss"

/** The ask in its [KitLiveAsk.look], on the transcript's text column (no indent under the live dot). */
@Composable
public fun KitLiveAskView(ask: KitLiveAsk, modifier: Modifier = Modifier) {
    when (ask.look) {
        KitLiveAskLook.Card -> AskCard(ask, modifier)
        KitLiveAskLook.Pill -> AskPill(ask, modifier)
        KitLiveAskLook.Notice -> AskNotice(ask, modifier)
    }
}

private fun Modifier.polite(): Modifier = semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }

@Composable
private fun AskClose(ask: KitLiveAsk, modifier: Modifier = Modifier) {
    KitIconButton(
        KitIcons.close,
        ask.dismissLabel,
        ask.onDismiss,
        modifier.testTag(LIVE_ASK_DISMISS_TAG),
        tone = TextTone.Secondary,
        size = 20,
    )
}

/** A: a rounded card, the star mark, title and one line, a filled accent action and a quiet close at the top end. */
@Composable
private fun AskCard(ask: KitLiveAsk, modifier: Modifier) {
    val colors = LocalKitColors.current
    KitSurfacePanel(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(end = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).clip(KitShapeId.Tile.toShape()).background(colors.accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        KitIcon(KitIcons.starFilled, size = KitIconSize.Small, tone = TextTone.Accent)
                    }
                    Spacer(Modifier.width(Space.S3.dp))
                    Column(Modifier.polite()) {
                        KitText(ask.title, role = TypeRole.RowTitle, tone = TextTone.Primary)
                        KitText(ask.body, role = TypeRole.Secondary, tone = TextTone.Secondary)
                    }
                }
                Spacer(Modifier.height(Space.S3.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    KitButton(
                        ask.actionLabel,
                        ask.onAction,
                        role = KitButtonRole.Primary,
                        icon = KitIcons.starFilled,
                        expand = false,
                        modifier = Modifier.testTag(LIVE_ASK_ACTION_TAG),
                    )
                }
            }
            AskClose(ask, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** B: one tappable chip, "Star on GitHub" with its star and a small x. */
@Composable
private fun AskPill(ask: KitLiveAsk, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KitChip(
            ask.actionLabel,
            Modifier.testTag(LIVE_ASK_ACTION_TAG).polite(),
            kind = KitChipKind.Removable,
            tone = KitChipTone.Active,
            icon = KitIcons.starFilled,
            removeLabel = ask.dismissLabel,
            onClick = ask.onAction,
            onRemove = ask.onDismiss,
        )
    }
}

/** C: a notice row: a GitHub mark tile, title and supporting words, a one-word action and a close. */
@Composable
private fun AskNotice(ask: KitLiveAsk, modifier: Modifier) {
    val colors = LocalKitColors.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(KitShapeId.Tile.toShape()).background(colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            KitIcon(KitIcons.github, size = KitIconSize.Medium, tone = TextTone.Primary)
        }
        Spacer(Modifier.width(Space.S3.dp))
        Column(Modifier.weight(1f).polite()) {
            KitText(ask.title, role = TypeRole.RowTitle, tone = TextTone.Primary)
            KitText(ask.body, role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
        KitButton(
            ask.shortActionLabel,
            ask.onAction,
            role = KitButtonRole.Tertiary,
            expand = false,
            modifier = Modifier.testTag(LIVE_ASK_ACTION_TAG),
        )
        AskClose(ask)
    }
}
