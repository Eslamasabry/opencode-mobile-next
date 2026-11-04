package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** One Accent colour in a [KitSwatchGrid] (Dart `KitSwatch.accent`; B-KIT-049). [color] null is the "Match theme" tile. */
public data class KitSwatch(
    val id: String,
    val label: String,
    val color: Color?,
    val selected: Boolean,
    /** Null = the swatch cannot be chosen now, and [disabledReason] must say why. */
    val onPressed: (() -> Unit)?,
    val disabledReason: String? = null,
)

private const val ACCENT_TARGET_DP = 56
private const val ACCENT_GAP_DP = 8
private const val DOT_DP = 32

/**
 * The colour swatch picker: a row-wrapped grid of round swatches whose column count follows the width it has (at least
 * one), one tab stop with arrow keys moving focus and skipping disabled swatches, Enter or Space pressing. A tile shows
 * its pressed and selected states in the style's own control look; a swatch that cannot be chosen must give a reason,
 * which a screen reader hears as its hint. [inUseLabel] is the spoken value of the chosen swatch ("In use").
 */
@Composable
public fun KitSwatchGrid(
    label: String,
    swatches: List<KitSwatch>,
    inUseLabel: String,
    modifier: Modifier = Modifier,
) {
    kitAssert(swatches.all { it.onPressed != null || !it.disabledReason.isNullOrBlank() }) {
        "KitSwatchGrid: a swatch with no action must give a reason (B-KIT-049)"
    }
    val requesters = remember(swatches.size) { List(swatches.size) { FocusRequester() } }
    BoxWithConstraints(modifier.fillMaxWidth().semantics { contentDescription = label }) {
        val columns = swatchColumns(maxWidth.value)
        fun move(from: Int, step: Int) {
            var i = from + step
            while (i in swatches.indices) {
                if (swatches[i].onPressed != null) {
                    requesters[i].requestFocus()
                    return
                }
                i += step
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(ACCENT_GAP_DP.dp)) {
            swatches.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(ACCENT_GAP_DP.dp)) {
                    row.forEachIndexed { col, swatch ->
                        val index = rowIndex * columns + col
                        SwatchTile(
                            swatch,
                            inUseLabel,
                            Modifier.focusRequester(requesters[index]).onKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) {
                                    false
                                } else {
                                    when (e.key) {
                                        Key.DirectionRight -> { move(index, 1); true }
                                        Key.DirectionLeft -> { move(index, -1); true }
                                        Key.DirectionDown -> { move(index, columns); true }
                                        Key.DirectionUp -> { move(index, -columns); true }
                                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> swatch.onPressed?.let { it(); true } ?: false
                                        else -> false
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** How many accent swatches fit a row: as many as the width holds, never fewer than one. */
internal fun swatchColumns(widthDp: Float): Int = maxOf(((widthDp + ACCENT_GAP_DP) / (ACCENT_TARGET_DP + ACCENT_GAP_DP)).toInt(), 1)

@Composable
private fun SwatchTile(swatch: KitSwatch, inUseLabel: String, modifier: Modifier) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val interactions = remember { MutableInteractionSource() }
    val focused = interactions.collectIsFocusedAsState().value
    val enabled = swatch.onPressed != null
    val look =
        kitSurfaceLook(
            KitSurfaceInput(SurfaceRole.Control, KitShapeId.Tile, SurfaceLevel.Surface1, ownFill = colors.surface1, selected = enabled && swatch.selected),
        )
    Column(
        modifier
            .sizeIn(minWidth = ACCENT_TARGET_DP.dp, minHeight = maxOf(tokens.minTarget, ACCENT_TARGET_DP).dp)
            .then(look.modifier)
            .border(if (focused) 2.dp else 1.dp, if (focused || (enabled && swatch.selected)) colors.accent else colors.hairline, KitShapeId.Tile.shape())
            .selectable(
                selected = swatch.selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactions,
                indication = null,
                onClick = { swatch.onPressed?.invoke() },
            )
            .semantics {
                contentDescription = swatch.label
                if (swatch.selected) stateDescription = inUseLabel
                else swatch.disabledReason?.let { stateDescription = it }
                if (!enabled) disabled()
            }
            .padding(Space.S2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.S1.dp, Alignment.CenterVertically),
    ) {
        val dot = swatch.color
        Box(
            Modifier.size(DOT_DP.dp).clip(CircleShape).then(
                if (dot != null && enabled) Modifier.background(dot)
                else Modifier.background(colors.surface3).border(1.dp, colors.text3, CircleShape),
            ),
        )
        KitText(swatch.label, role = TypeRole.Caption, tone = if (enabled) TextTone.Secondary else TextTone.Tertiary, kind = KitTextKind.Frame, maxLines = 1)
    }
}
