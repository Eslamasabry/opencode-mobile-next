package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.frame.ExitGlyph
import io.github.eslamasabry.oc.ui.kit.frame.KitExitGlyph
import io.github.eslamasabry.oc.ui.kit.logo.KitProviderLogo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** What starts a row (Dart `KitRow.icon`, the lead column). */
@Immutable
public sealed interface KitRowLeading {
    /** A kit glyph in its 28 dp `surface3` tile (Dart `KitRow.icon`). */
    public data class Glyph(val icon: ImageVector) : KitRowLeading

    /**
     * Two letters in the tile, for a thing with no glyph (a server, a project). Never coloured by
     * identity.
     */
    public data class Initials(val text: String) : KitRowLeading

    public data class Logo(val id: String, val name: String, val agent: Boolean = false) :
        KitRowLeading
}

/** What ends a row (Dart `KitRowValue`, `KitChevron`, the row's own action). */
@Immutable
public sealed interface KitRowTrailing {
    public data object None : KitRowTrailing

    /** The row opens a page. */
    public data object Chevron : KitRowTrailing

    /**
     * A value in quiet words, optionally with the chevron. It goes under the title when it cannot
     * share the line.
     */
    public data class Value(val text: String, val chevron: Boolean = true) : KitRowTrailing

    /**
     * The row's one act ("Turn on"): a trailing tertiary; under the supporting line from 1.3× text.
     */
    public data class Action(val action: KitLineAction) : KitRowTrailing
}

@Composable
internal fun RowLeadingTile(leading: KitRowLeading, dim: Boolean) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val tone = if (dim) TextTone.Tertiary else TextTone.Primary
    val tile = tokens.iconTile * minOf(kitTextScale(), TextScalePolicy.ICON_CAP)
    Box(
        Modifier.size(tile.dp)
            .background(colors.surface3, KitShapeId.Tile.toShape())
            .clearAndSetSemantics {},
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
            is KitRowLeading.Logo -> KitProviderLogo(leading.id, leading.name, leading.agent)
            is KitRowLeading.Initials ->
                KitText(leading.text.take(2), role = TypeRole.Label, tone = tone, maxLines = 1)
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
        KitText(value.text, role = TypeRole.Secondary, tone = TextTone.Tertiary, maxLines = 1)
        if (value.chevron) RowChevron()
    }
}

@Composable
internal fun RowChevron() {
    KitExitGlyph(
        ExitGlyph.Chevron,
        LocalKitColors.current.text3,
        Modifier.testTag("kit-row-chevron"),
    )
}
