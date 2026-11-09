package io.github.eslamasabry.oc.ui.kitchat.turn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons

/**
 * A friendly one-time ask that rides under the live line while the person waits (the GitHub star request). It folds into
 * the line, so the transcript gains no bar or row level of its own. [title] and [body] are the words, [actionLabel] the
 * main action, and [dismissLabel] names the close (never drawn as words: a quiet x).
 */
public class KitLiveAsk(
    public val title: String,
    public val body: String,
    public val actionLabel: String,
    public val onAction: () -> Unit,
    public val dismissLabel: String,
    public val onDismiss: () -> Unit,
)

/** Test handle of the live line's one-time ask. */
public const val LIVE_ASK_TAG: String = "kit-turn-live-ask"

/** Test handle of the ask's main action. */
public const val LIVE_ASK_ACTION_TAG: String = "kit-turn-live-ask-action"

/** Test handle of the ask's dismiss. */
public const val LIVE_ASK_DISMISS_TAG: String = "kit-turn-live-ask-dismiss"

/** Side of the tile and of the close's drawn box: the row is symmetric. */
private val ASK_BOX = 40.dp

/** The close button's whole touch target; it reaches past its drawn box by the difference, never drawn. */
private val ASK_TOUCH = 48.dp

/**
 * The ask as one card on the transcript's text column (no indent under the live dot): the kit card, 16 dp inside on
 * every side. Top row, centred: a 40 dp tile with the GitHub mark, 12 dp, the words (weight 1), 12 dp, the close in a
 * 40 dp drawn box (its 48 dp target spills into the padding). 12 dp under it, the filled accent action on the full
 * inner width with the star before its label. Everything mirrors in a right-to-left language.
 */
@Composable
public fun KitLiveAskView(ask: KitLiveAsk, modifier: Modifier = Modifier) {
    KitSurface(modifier.fillMaxWidth(), SurfaceLevel.Surface1, KitShapeId.Card, KitSurfacePadding.None) {
        Column(Modifier.fillMaxWidth().padding(Space.S4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KitSurface(Modifier.size(ASK_BOX), SurfaceLevel.Surface3, KitShapeId.Tile, KitSurfacePadding.None) {
                    Box(Modifier.size(ASK_BOX), contentAlignment = Alignment.Center) {
                        KitIcon(KitIcons.githubMark, size = KitIconSize.Medium, tone = TextTone.Primary)
                    }
                }
                Spacer(Modifier.width(Space.S3.dp))
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    KitText(ask.title, role = TypeRole.RowTitle, tone = TextTone.Primary)
                    KitText(ask.body, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 2)
                }
                // The drawn box is 40 dp; the 48 dp target takes 4 dp of the gap and 4 dp of the card's padding.
                Spacer(Modifier.width(Space.S3.dp - (ASK_TOUCH - ASK_BOX) / 2))
                KitIconButton(
                    KitIcons.close,
                    ask.dismissLabel,
                    ask.onDismiss,
                    Modifier.offset(x = (ASK_TOUCH - ASK_BOX) / 2).testTag(LIVE_ASK_DISMISS_TAG),
                    tone = TextTone.Secondary,
                    size = 18,
                )
            }
            Spacer(Modifier.height(Space.S3.dp))
            KitButton(
                ask.actionLabel,
                ask.onAction,
                Modifier.fillMaxWidth().testTag(LIVE_ASK_ACTION_TAG),
                role = KitButtonRole.Primary,
                icon = KitIcons.starFilled,
            )
        }
    }
}
