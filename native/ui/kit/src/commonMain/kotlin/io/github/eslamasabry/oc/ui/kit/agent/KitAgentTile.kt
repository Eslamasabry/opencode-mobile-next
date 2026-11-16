package io.github.eslamasabry.oc.ui.kit.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairlineBorder
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** How a tile's state line reads: calm, a thing that needs the person, or out of reach (dimmed mark and words). */
public enum class KitAgentTileTone { Calm, NeedsYou, Dimmed }

/**
 * A tile's one small act (Install, Sign in): [label] is the act alone, [spoken] names its target for screen readers
 * ("Install Gemini CLI"), as Flutter's row chip.
 */
public data class KitAgentTileAction(
    val label: String,
    val spoken: String,
    val icon: ImageVector? = null,
    /** The one act the page most wants done (Sign in): the accent chip. Every other act is a plain chip. */
    val prominent: Boolean = false,
    val onClick: () -> Unit,
)

/** What a screen reader hears for the tile itself: the name, then the state line. */
internal fun agentTileLabel(name: String, state: String): String = if (state.isEmpty()) name else "$name. $state"

/**
 * One agent as a tile in the Agents page's bento grid ([KitAgentTiles]): its large brand mark (the bundled monochrome
 * mark for [markId], drawn from the app's logo loader; the agent's first letter on a neutral face when it has none,
 * never an empty box), its [name] and one [state] line ("Ready", "Sign in needed", "Not installed · 340 MB",
 * "Installing…"). While something runs, [progress] draws its bar under the line. At most one small [action] sits at the
 * tile's foot as a compact chip, only when it matters; tapping anywhere else on the tile opens the agent ([onClick]),
 * where every other act lives. The surface comes from the style resolver (as [KitAgentChoice]): Solid's matte face and
 * hairline, every other style its own card, on the corners knob's card radius. Spanning a whole row it lies sideways,
 * the mark beside the words and the chip at the end.
 *
 * States: calm, needs you, dimmed ([KitAgentTileTone]); with or without progress; with or without its act.
 */
@Composable
public fun KitAgentTile(
    name: String,
    state: String,
    markId: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: KitAgentTileTone = KitAgentTileTone.Calm,
    progress: KitProgress? = null,
    action: KitAgentTileAction? = null,
    /** Ready to use: a small check in the corner, so ready tiles read at a glance. */
    ready: Boolean = false,
) {
    val colors = LocalKitColors.current
    val dimmed = tone == KitAgentTileTone.Dimmed
    val large = kitTextScale() > 1.3f
    val wide = LocalKitAgentGridSpan.current
    val look =
        kitSurfaceLook(
            KitSurfaceInput(SurfaceRole.Content, KitShapeId.Card, level = SurfaceLevel.Surface2, ownFill = colors.surface1)
        )
    val solid = LocalKitStyle.current.style == Style.Solid
    val hairline = kitHairline()
    val edge = if (solid) Modifier.kitHairlineBorder(hairline, colors.hairline, look.shape) else Modifier
    val inset = Space.S4.dp + if (solid) hairline else 0.dp
    val markSize = (FlutterKitTokens.MARK_SIZE + Space.S2).dp // owner 2026-11-09: bigger logos
    val lineTone =
        when (tone) {
            KitAgentTileTone.Calm -> TextTone.Secondary
            KitAgentTileTone.NeedsYou -> TextTone.Attention
            KitAgentTileTone.Dimmed -> TextTone.Tertiary
        }
    val mark: @Composable () -> Unit = {
        AgentMark(markId, if (dimmed) colors.text3 else colors.text1, markSize, monogram = name, dimmed = dimmed)
    }
    val words: @Composable () -> Unit = {
        Column(Modifier.clearAndSetSemantics {}) {
            KitText(
                name,
                role = if (large) TypeRole.Headline else TypeRole.Title,
                tone = if (dimmed) TextTone.Secondary else TextTone.Primary,
                maxLines = 2,
            )
            if (state.isNotEmpty()) {
                Spacer(Modifier.height(Space.S1.dp))
                KitText(state, role = TypeRole.Secondary, tone = lineTone, maxLines = 3)
            }
            if (progress != null) {
                Spacer(Modifier.height(Space.S2.dp))
                KitProgressView(progress)
            }
        }
    }
    val chipHeight = (LocalKitTokens.current.minTarget * kitTextScale().coerceAtLeast(1f)).dp
    val chip: @Composable () -> Unit = {
        if (action != null) ChipSlot(chipHeight) {
            KitChip(
                action.label,
                kind = KitChipKind.Action,
                tone = if (action.prominent) KitChipTone.Active else KitChipTone.Neutral,
                icon = action.icon,
                onClick = action.onClick,
                spoken = action.spoken,
            )
        }
    }
    KitTappable(
        onClick,
        modifier =
            modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().semantics {
                contentDescription = agentTileLabel(name, state)
                testTag = "kit-agent-tile"
            },
        haptic = KitHaptic.Select,
        shape = KitShapeId.Card,
    ) {
        Box(
            Modifier.fillMaxWidth()
                .fillMaxHeight()
                .heightIn(min = agentChoiceMinHeight(wide, large))
                .then(look.modifier)
                .then(edge)
                .padding(inset)
        ) {
            if (wide) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    mark()
                    Spacer(Modifier.width(Space.S4.dp))
                    Box(Modifier.weight(1f)) { words() }
                    // Lying sideways the check sits after the words, never over the mark.
                    if (ready) {
                        Spacer(Modifier.width(Space.S2.dp))
                        ReadyCheck()
                    }
                    if (action != null) {
                        Spacer(Modifier.width(Space.S2.dp))
                        chip()
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        mark()
                        Spacer(Modifier.weight(1f))
                        if (ready) ReadyCheck()
                    }
                    Spacer(Modifier.height(Space.S4.dp))
                    words()
                    if (action != null) {
                        // The chips of a row of tiles sit on one line at the tiles' foot.
                        Spacer(Modifier.weight(1f).heightIn(min = Space.S3.dp))
                        chip()
                    }
                }
            }
        }
    }
}

/** A small filled check, the tile's "ready" mark (a shape, so never colour alone). */
@Composable
private fun ReadyCheck() {
    val ok = LocalKitColors.current.success
    Box(
        Modifier.size((FlutterKitTokens.ICON_TILE_SIZE - Space.S2).dp).drawBehind { drawCircle(ok) }.clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        KitIcon(KitIcons.check, size = KitIconSize.Small, tone = TextTone.OnAccent)
    }
}

/**
 * The chip's place in a tile. The tile grid sizes each row to its tallest tile (IntrinsicSize.Min), and a chip measures
 * itself with BoxWithConstraints, which has no intrinsic size: this slot answers for it with the chip's [height] (one
 * tap target, grown with the text) and lays the chip out as usual.
 */
@Composable
private fun ChipSlot(height: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(
        content,
        measurePolicy =
            object : androidx.compose.ui.layout.MeasurePolicy {
                override fun androidx.compose.ui.layout.MeasureScope.measure(
                    measurables: List<androidx.compose.ui.layout.Measurable>,
                    constraints: androidx.compose.ui.unit.Constraints,
                ): androidx.compose.ui.layout.MeasureResult {
                    val parts = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
                    val w = (parts.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
                    val h = (parts.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
                    return layout(w, h) { parts.forEach { it.placeRelative(0, 0) } }
                }

                override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicHeight(
                    measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
                    width: Int,
                ): Int = height.roundToPx()

                override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicHeight(
                    measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
                    width: Int,
                ): Int = height.roundToPx()

                override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicWidth(
                    measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
                    height: Int,
                ): Int = 0

                override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicWidth(
                    measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
                    height: Int,
                ): Int = 0
            },
    )
}
