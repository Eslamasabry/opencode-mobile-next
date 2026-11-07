package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import io.github.eslamasabry.oc.ui.kit.foundation.kitHairline
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.design.style.SurfaceRequest
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitList
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** The quiet line under [KitChoiceTiles] that opens everything else: "All models (42) ›" (Dart `KitChoiceMore`). */
@Immutable
public data class KitChoiceMore(val label: String, val onTap: () -> Unit)

/**
 * A few big choices that lead with the reason ("Default", "Cheapest"), each its own tile, with one quiet "All models"
 * line under them (Dart `KitChoiceTiles`, lib/ui/kit/kit_choice_tiles.dart). A tap chooses and the host closes its
 * sheet: there is no Apply. The chosen tile has a check and the style's chosen control (Solid lists are flat: one row
 * per choice parted by inset hairlines, the chosen one with the accent check); while the host applies a tap, [busy]
 * shows a small progress mark where the check goes and every tile waits.
 *
 * States: default, selected, disabled (the reason first on the supporting line), working.
 * Text: reading (titles and lines wrap, never truncate). Cap: Reading.
 * Motion: the check scales in over QUICK; Off = at once.
 */
@Composable
public fun <T> KitChoiceTiles(
    choices: List<KitChoice<T>>,
    selected: T?,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    busy: T? = null,
    more: KitChoiceMore? = null,
    semanticsLabel: String? = null,
) {
    val waiting = busy != null
    // Flat lists (Solid): one row per choice on the sheet itself. Outside a sheet's reach the words start where a
    // tile's would (the native kit has no sheet reach yet, so the reach is zero).
    val flat = LocalKitStyle.current.spec.tokens.flatLists
    val words = wordsStart()
    Column(
        modifier
            .kitMaxWidth(CapRole.Reading)
            .fillMaxWidth()
            .semantics { semanticsLabel?.let { contentDescription = it } }
            .then(if (flat) Modifier.kitList("choice-tiles") else Modifier)
            .selectableGroup(),
    ) {
        val rule: @Composable () -> Unit = { ChoiceRule(words) }
        choices.forEachIndexed { i, c ->
            if (i > 0) if (flat) rule() else Spacer(Modifier.height(Space.S3.dp))
            ChoiceTile(
                choice = c,
                selected = c.value == selected,
                busy = ChoiceCardRules.showsBusy(c.value, busy),
                tap = ChoiceCardRules.tappable(c.enabled, waiting),
                flat = flat,
                inset = words,
                onTap = { onSelected(c.value) },
            )
        }
        if (more != null) {
            if (flat && choices.isNotEmpty()) rule() else Spacer(Modifier.height(Space.S1.dp))
            ChoiceMoreLine(more, enabled = !waiting, words = words)
        }
    }
}

/**
 * The inset hairline between flat rows (Dart `KitDivider` under a start padding), drawn at the kit's true hairline:
 * Flutter lays it out a third of a pixel tall, which the rows below keep; Compose lays out whole pixels, so the rule
 * takes no room and paints its hairline at its top.
 */
@Composable
private fun ChoiceRule(start: Dp) {
    val style = LocalKitStyle.current
    val roles = LocalKitRoles.current
    val look = style.spec.resolve(SurfaceRequest(roles, SurfaceRole.Divider, style.knobs))
    val color = Color((look?.fill ?: roles.hairline).v)
    val thick = style.spec.tokens.dividerWidthDp?.dp ?: kitHairline()
    Box(
        Modifier.padding(start = start).fillMaxWidth().height(thick).clearAndSetSemantics {}.drawBehind {
            drawRect(color, size = Size(size.width, thick.toPx()))
        },
    )
}

/** Where a tile's words (and the "more" line's) start inside it. */
private fun wordsStart(): Dp = (Space.S4 + Space.S1).dp

@Composable
private fun <T> ChoiceTile(
    choice: KitChoice<T>,
    selected: Boolean,
    busy: Boolean,
    tap: Boolean,
    flat: Boolean,
    inset: Dp,
    onTap: () -> Unit,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val on = choice.enabled.isEnabled
    val supporting = ChoiceCardRules.line(choice.supporting, choice.enabled)
    val light = !colors.dark
    val markSize = (tokens.smallIcon * minOf(kitTextScale(), tokens.maxIconScale)).dp
    val markScale by animateFloatAsState(if (selected) 1f else 0f, specs.quick(), label = "kit-choice-tile-mark")
    val level = if (light) SurfaceLevel.Surface1 else SurfaceLevel.Surface3
    val enabled = if (tap) KitEnabled.Enabled else KitEnabled.Disabled(choice.enabled.reason.orEmpty())
    val label = ChoiceCardRules.label(choice.title, supporting)
    val mark: @Composable () -> Unit = {
        Box(Modifier.clearAndSetSemantics {}) {
            when {
                busy -> KitChoiceBusyMark(markSize, 0.dp, Modifier.testTag("kit-choice-tile-busy"))
                flat ->
                    // The platform's chosen-row mark: the check alone, in the accent.
                    KitIcon(
                        KitIcons.check,
                        Modifier.graphicsLayer {
                            scaleX = markScale
                            scaleY = markScale
                        },
                        size = KitIconSize.Large,
                        tone = TextTone.Accent,
                        growsWithText = true,
                    )
                else ->
                    // The check on an accent disc: the one solid accent on the tile.
                    Box(
                        Modifier.size(markSize + Space.S1.dp)
                            .graphicsLayer {
                                scaleX = markScale
                                scaleY = markScale
                            }
                            .background(colors.accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { KitGlyph(KitIcons.check, colors.onAccent, markSize - Space.S1.dp) }
            }
        }
    }
    val look =
        kitSurfaceLook(KitSurfaceInput(SurfaceRole.Control, KitShapeId.Panel, level, ownFill = if (light) colors.surface1 else colors.surface3, selected = selected))
    // Hook H3: the chosen tile's words take the colour the style says reads on its chosen control.
    val ink = if (selected) look.words else null
    val inkColors = if (ink != null) colors.copy(text1 = ink, text2 = ink, text3 = ink) else colors
    val words: @Composable () -> Unit = {
        CompositionLocalProvider(LocalKitColors provides inkColors) {
            Column(Modifier.clearAndSetSemantics {}) {
                KitText(choice.title, role = TypeRole.Headline, tone = if (on) TextTone.Primary else TextTone.Tertiary)
                if (supporting.isNotEmpty()) {
                    Spacer(Modifier.height(Space.S1.dp))
                    KitText(supporting, role = TypeRole.Body, tone = TextTone.Secondary)
                }
            }
        }
    }
    if (flat) {
        Row(
            Modifier.fillMaxWidth()
                .kitEdge(KitEdge.Bleed) // a flat row's highlight runs edge to edge (native/docs/kit/LAYOUT-RULES.md §2)
                .kitSelectTap(selected, Role.RadioButton, enabled, KitHaptic.Select, KitShapeId.Panel, onTap)
                .semantics(mergeDescendants = true) { contentDescription = label }
                .heightIn(min = tokens.rowHeightTwo.dp)
                .padding(start = inset, top = Space.S3.dp, end = Space.S4.dp, bottom = Space.S3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { words() }
            Spacer(Modifier.width(Space.S3.dp))
            mark()
        }
        return
    }
    val solid = LocalKitStyle.current.style == Style.Solid
    val edge = when {
        selected -> colors.accent
        light -> colors.hairline
        else -> colors.surface3
    }
    Row(
        Modifier.fillMaxWidth()
            .then(look.modifier)
            // The kit's selection ring; unselected tiles keep the same width in their own colour.
            .then(if (solid) Modifier.choiceEdge(kitFocusRingWidth(), edge, look.shape) else Modifier)
            .clip(look.shape)
            .kitSelectTap(selected, Role.RadioButton, enabled, KitHaptic.Select, KitShapeId.Panel, onTap)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .heightIn(min = (tokens.rowHeightTwo + Space.S3).dp)
            .padding(start = (Space.S4 + Space.S1).dp, top = Space.S4.dp, end = Space.S4.dp, bottom = Space.S4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { words() }
        Spacer(Modifier.width(Space.S3.dp))
        mark()
    }
}

@Composable
private fun ChoiceMoreLine(more: KitChoiceMore, enabled: Boolean, words: Dp) {
    val tokens = LocalKitTokens.current
    KitTappable(
        onClick = more.onTap,
        modifier = Modifier.fillMaxWidth().kitEdge(KitEdge.Bleed).heightIn(min = (tokens.minTarget + Space.S2).dp),
        enabled = enabled,
        disabledReason = if (enabled) null else "",
        shape = KitShapeId.Panel,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = words, top = Space.S2.dp, end = Space.S4.dp, bottom = Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KitText(more.label, Modifier.weight(1f), role = TypeRole.RowTitle, tone = TextTone.Secondary)
            KitIcon(KitIcons.chevronRight, size = KitIconSize.Small, tone = TextTone.Tertiary)
        }
    }
}
