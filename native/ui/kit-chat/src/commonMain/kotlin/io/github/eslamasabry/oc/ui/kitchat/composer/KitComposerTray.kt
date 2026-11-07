package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/** What a tray choice does; each draws its own Phosphor glyph ([icon]), so no two choices look alike (P-004). */
public enum class KitTrayKind(internal val icon: ImageVector) {
    /** Photo library (Flutter's tray: `images`). */
    Photo(KitIcons.images),
    /** Take a photo. */
    Camera(KitIcons.camera),
    /** Attach a file (Flutter's tray: the paper clip). */
    File(KitIcons.attach),
    /** Paste an image from the clipboard. */
    Clipboard(KitIcons.paste),
    /** A recent prompt to reuse (the history clock). */
    History(KitIcons.history),
    /** A saved prompt (a bookmark), apart from the recent ones in [History]. */
    Saved(KitIcons.bookmark),
    /** The commands launcher. */
    Commands(KitIcons.terminal),
    /** Clear the draft words (a cross). */
    Clear(KitIcons.close),
    /** Send later or repeat (a clock face). */
    Schedule(KitIcons.clock),
}

private const val PER_ROW = 4

/** P-004: at large text a quarter of the phone is too narrow for a whole word ("Commands"); three to a row. */
private const val PER_ROW_LARGE = 3

/** How many choices share a row: whole words fit their cell at every text size. */
internal fun trayPerRow(textScale: Float): Int =
    if (TextScalePolicy.isLargeText(textScale)) PER_ROW_LARGE else PER_ROW

/** The caller closes the tray before running an action, preserving the editor and its keyboard. */
public class KitTrayAction(
    public val label: String,
    public val kind: KitTrayKind,
    public val onPick: () -> Unit,
)

/** The attachment choices attached to the editor itself; whole labels wrap at large text sizes. */
@Composable
public fun KitComposerTray(actions: List<KitTrayAction>, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    Column(modifier.fillMaxWidth().testTag("kit-composer-tray")) {
        // Four to a row (three at large text): every label keeps whole words and every target 48 dp.
        val perRow = trayPerRow((LocalKitAppDensity.current ?: LocalDensity.current).fontScale)
        actions.chunked(perRow).forEach { rowActions ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = Space.S2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            rowActions.forEach { action ->
                KitTappable(action.onPick, Modifier.weight(1f), onClickLabel = action.label) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
                    ) {
                        Box(
                            Modifier.size(48.dp).background(colors.surface3, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            KitIcon(action.kind.icon, size = KitIconSize.Medium, tone = TextTone.Primary)
                        }
                        // P-004: every label is centred in its cell, one line or wrapped, and keeps 4 dp from the cell's
                        // sides so a long word never reaches the next cell or loses a letter at the edge.
                        KitText(
                            action.label,
                            Modifier.fillMaxWidth().padding(horizontal = Space.S1.dp),
                            role = TypeRole.Secondary,
                            tone = TextTone.Primary,
                            maxLines = 3,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            repeat(perRow - rowActions.size) { Box(Modifier.weight(1f)) }
        }
        }
        Box(Modifier.fillMaxWidth().size(1.dp).background(colors.hairline))
    }
}
