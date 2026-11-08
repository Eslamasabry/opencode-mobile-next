package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.getValue
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.logo.KitProviderLogo
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** What starts a row (Dart `KitRow.icon`, the lead column). */
@Immutable
public sealed interface KitRowLeading {
    /**
     * A kit glyph (Dart `KitRow.icon`): a bare 20 dp Phosphor glyph in the secondary ink, in the lead column, the way
     * the Flutter rows draw it. [tile] keeps the old 28 dp `surface3` tile for a row that sits among tiled rows.
     */
    public data class Glyph(val icon: ImageVector, val tile: Boolean = false, val current: Boolean = false) : KitRowLeading

    /**
     * A bounded static provider mark. A missing or refused picture leaves the tile empty at the same size: never a
     * stand-in letter (owner 2026-11-06).
     */
    public data class Svg(val text: String) : KitRowLeading

    /** [bare] draws the mark alone at the list icon size, with no tile behind it (Search's rows). */
    public data class Logo(val id: String, val name: String, val agent: Boolean = false, val bare: Boolean = false) :
        KitRowLeading
}

/** The live state a feed row leads with (Dart `KitFeedItem`'s mark and status word). */
public enum class KitRowStateTone {
    /** Waiting for the person: an amber dot and the word in amber. */
    NeedsYou,

    /** Working now: a mint ring and the word in mint. */
    Running,

    /** Ended badly: the word in the danger ink, no mark. */
    Failed,

    /** Finished or idle: the word in the quiet ink, no mark. */
    Quiet,
}

/**
 * A feed row's state: [word] leads the supporting line in its tone; NeedsYou and Running also mark the lead column.
 * A row with nothing to say passes a null word, so it still keeps the lead column and lines up with its neighbours.
 */
@Immutable
public data class KitRowState(val word: String?, val tone: KitRowStateTone)

/** What ends a row (Dart `KitRowValue`, `KitChevron`, the row's own action). */
@Immutable
public sealed interface KitRowTrailing {
    public data object None : KitRowTrailing

    /** The row opens a page. */
    public data object Chevron : KitRowTrailing

    /** Flutter KitIconButton.copy, for an app-authored value such as the build version. */
    public data class Copy(val text: String, val label: String) : KitRowTrailing

    /** A chosen row, with the check after its words (Flutter project/model choices). */
    public data object Check : KitRowTrailing

    /**
     * A quiet fact pill before the row's end, and the check when [checked] (Flutter chats_project_sheet.dart:96-107:
     * `Row(spacing: space2, [KitChip(label, icon), chosen])`, the project sheet's "Git").
     */
    public data class Chip(val label: String, val icon: ImageVector? = null, val checked: Boolean = false) : KitRowTrailing

    /** The lock glyph and explanation of an always-on setting. */
    public data class Locked(val text: String) : KitRowTrailing

    /**
     * A value in quiet words, optionally with the chevron. It goes under the title when it cannot
     * share the line.
     */
    public data class Value(val text: String, val chevron: Boolean = true, val prominent: Boolean = false) : KitRowTrailing

    /**
     * The row's one act ("Turn on"): a trailing tertiary; under the supporting line from 1.3× text.
     */
    public data class Action(val action: KitLineAction) : KitRowTrailing

    /**
     * The row's own ⋯ menu, drawn inside the row so a selected or pressed highlight spans the whole row, menu
     * included (owner 2026-11-06). [label] names the button for screen readers (usually the row's name); [title]
     * heads the menu on a phone.
     */
    public data class Menu(val label: String, val items: List<KitMenuItem>, val title: String? = null) : KitRowTrailing

    /** A separately labelled glyph target, as in Flutter's timeline Fork control. */
    public data class IconAction(val action: KitLineAction) : KitRowTrailing {
        init { require(action.icon != null) { "An icon action needs a glyph" } }
    }
}

@Composable
internal fun RowLeadingTile(leading: KitRowLeading, dim: Boolean, selected: Boolean = false) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val tone = if (dim) TextTone.Tertiary else TextTone.Primary
    val tile = tokens.iconTile * minOf(kitTextScale(), TextScalePolicy.ICON_CAP)
    if (leading is KitRowLeading.Glyph && !leading.tile) {
        Box(Modifier.size(tile.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Image(
                leading.icon,
                null,
                Modifier.size((tokens.smallIcon * minOf(kitTextScale(), TextScalePolicy.ICON_CAP)).dp),
                colorFilter = ColorFilter.tint(if (dim) colors.text3 else if (leading.current) colors.accent else colors.text2),
            )
        }
        return
    }
    // P-113: a selected row is filled with surface3, the tile's own colour, so its tile vanished ("This phone" read as
    // tileless). On the highlight the tile steps to the panel's surface1; the change eases with the selection.
    val fill by animateColorAsState(
        when (rowTileLevel(selected)) {
            SurfaceLevel.Surface1 -> colors.surface1
            else -> colors.surface3
        },
        KitMotion.specs.quick(),
        label = "kit-row-tile",
    )
    Box(
        Modifier.size(tile.dp).background(fill, KitShapeId.Tile.shape()).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        when (leading) {
            is KitRowLeading.Glyph ->
                Image(
                    leading.icon,
                    null,
                    Modifier.size(
                        (tokens.smallIcon * minOf(kitTextScale(), TextScalePolicy.ICON_CAP)).dp
                    ),
                    colorFilter = ColorFilter.tint(colors.tone(tone)),
                )
            is KitRowLeading.Logo ->
                KitProviderLogo(
                    leading.id,
                    leading.name,
                    leading.agent,
                    Modifier.size(tile.dp),
                    markSize = (tokens.smallIcon * minOf(kitTextScale(), TextScalePolicy.ICON_CAP)).dp,
                )
            is KitRowLeading.Svg -> {
                val platform = LocalKitPlatform.current
                val size = (tokens.smallIcon * minOf(kitTextScale(), TextScalePolicy.ICON_CAP)).dp
                val pixels = with(LocalDensity.current) { size.roundToPx() }.coerceIn(1, 256)
                // One mark per row composition. No process-wide cache retains server-controlled
                // text.
                val painter =
                    remember(leading.text, platform, pixels) {
                        platform.svgPainter(leading.text, pixels)
                    }
                if (painter != null)
                    Image(
                        painter,
                        null,
                        Modifier.size(size),
                        colorFilter = ColorFilter.tint(colors.tone(tone)),
                    )
            }
        }
    }
}

@Composable
internal fun RowValue(value: KitRowTrailing.Value, modifier: Modifier = Modifier) {
    Row(
        modifier.testTag("kit-row-value"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        KitText(value.text, role = if (value.prominent) TypeRole.Body else TypeRole.Secondary, tone = if (value.prominent) TextTone.Secondary else TextTone.Tertiary, maxLines = 1)
        if (value.chevron) RowChevron()
    }
}

@Composable
internal fun RowLockedValue(value: KitRowTrailing.Locked) {
    val tokens = LocalKitTokens.current
    Row(
        Modifier.widthIn(max = (LocalKitWindow.current.widthDp / 2).dp) // F: lib/ui/kit/kit_row.dart:627
            // Flutter: the row's space2 gap and the part's own space2 before the lock, and the part's space3 after it
            // on the row's space1 end (kit_row_parts.dart:431-436, kit_row.dart:507-512, 618). Here the row already
            // keeps space3 before it and the gutter after it, so the part adds space1 before and nothing after.
            .padding(start = Space.S1.dp).testTag("kit-row-locked-value"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        KitGlyph(KitIcons.locked, LocalKitColors.current.text3, tokens.smallIcon.dp)
        KitText(value.text, Modifier.weight(1f, fill = false), role = TypeRole.Secondary, tone = TextTone.Tertiary, maxLines = 1)
    }
}

@Composable
internal fun RowChevron() {
    val size = LocalKitTokens.current.smallIcon * minOf(kitTextScale(), TextScalePolicy.FRAME_CAP)
    KitGlyph(KitIcons.chevronRight, LocalKitColors.current.text3, size.dp, Modifier.testTag("kit-row-chevron"))
}

/** The lead-column mark of a live row: an 8 dp amber dot for "needs you", a 12 dp mint ring for "running". */
@Composable
internal fun RowStateMark(tone: KitRowStateTone) {
    val colors = LocalKitColors.current
    val width = LocalKitTokens.current.iconTile
    Box(Modifier.size(width.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        when (tone) {
            KitRowStateTone.NeedsYou -> Box(Modifier.size(8.dp).background(colors.attention, CircleShape))
            KitRowStateTone.Running -> Box(Modifier.size(12.dp).border(2.dp, colors.live, CircleShape))
            else -> Unit
        }
    }
}

/**
 * The surface a row's leading tile is drawn in (P-113): surface3 at rest, and the panel's surface1 on a selected row,
 * whose highlight is surface3 ([SELECTED_ROW_LEVEL]) and would swallow a surface3 tile.
 */
internal fun rowTileLevel(selected: Boolean): SurfaceLevel = if (selected) SurfaceLevel.Surface1 else SurfaceLevel.Surface3

/** The surface a selected row is filled with (E20-S08; Flutter's chosen-row surface3). */
internal val SELECTED_ROW_LEVEL: SurfaceLevel = SurfaceLevel.Surface3
