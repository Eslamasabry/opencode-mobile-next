package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** Dart `KitChipTone`: neutral; attention (amber, "needs you" only); active (the chosen filter). */
public enum class KitChipTone { Neutral, Attention, Active }

/** Dart `KitChipKind`. Plain is a word, never a control; action and removable act; count adds a number. */
public enum class KitChipKind { Plain, Action, Removable, Count }

/**
 * A small pill with a word (Dart `KitChip`; plan 06 §7.5): 32 dp tall inside a 48 dp target, always carrying a word,
 * frame text. [KitChipKind.Removable] adds a remove control with its own label and the `kit-chip-remove` tag.
 * [selected] non-null makes it a toggle a screen reader hears as selected.
 *
 * States: default, pressed, selected, attention
 * Text: frame
 * Cap: Chip
 * Motion: none of its own (press feedback from KitTappable)
 * Replaces: KitChip, KitChip.action/.removable/.count (lib/ui/kit/kit_chip.dart)
 */
@Composable
public fun KitChip(
    label: String,
    modifier: Modifier = Modifier,
    kind: KitChipKind = KitChipKind.Plain,
    tone: KitChipTone = KitChipTone.Neutral,
    icon: ImageVector? = null,
    count: Int? = null,
    selected: Boolean? = null,
    removeLabel: String? = null,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    kitAssert(label.isNotBlank()) { "KitChip: a chip always carries a word" }
    kitAssert(kind != KitChipKind.Removable || (onRemove != null && !removeLabel.isNullOrBlank())) {
        "KitChip: a removable chip needs onRemove and its spoken label"
    }
    kitAssert(kind != KitChipKind.Action || onClick != null) { "KitChip: an action chip needs onClick" }
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val (fill: Color, words: TextTone) =
        when {
            tone == KitChipTone.Attention -> colors.attentionSurface to TextTone.Attention
            tone == KitChipTone.Active || selected == true -> colors.accent to TextTone.OnAccent
            else -> colors.surface3 to TextTone.Primary
        }
    val edge = if (tone == KitChipTone.Attention) colors.attentionLine else Color.Transparent
    val shape = KitShapeId.Pill.shape()
    val chosen = tone == KitChipTone.Active || selected == true
    // The style draws the chip (B-KIT-043..045); Solid keeps the fill above.
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                role = SurfaceRole.Control,
                shape = KitShapeId.Pill,
                level = SurfaceLevel.Surface3,
                ownFill = fill,
                color = if (chosen || tone == KitChipTone.Attention) fill else null,
                selected = chosen,
            ),
        )
    val inkColors = look.words?.let { w -> colors.copy(text1 = w, onAccent = w, attention = w) } ?: colors
    val face: @Composable () -> Unit = {
      CompositionLocalProvider(LocalKitColors provides inkColors) {
        Row(
            Modifier.heightIn(min = tokens.chipHeight.dp)
                .then(look.modifier)
                .border(1.dp, edge, shape)
                .padding(horizontal = Space.S3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S1.dp),
        ) {
            icon?.let { KitGlyph(it, inkColors.tone(words), (tokens.smallIcon - 4).dp) }
            KitText(label, role = TypeRole.Label, tone = words, kind = KitTextKind.Frame, maxLines = 1)
            if (kind == KitChipKind.Count && count != null) {
                KitText(count.toString(), role = TypeRole.Label, tone = words, kind = KitTextKind.Frame)
            }
            if (kind == KitChipKind.Removable && onRemove != null) {
                // The pill stays 32 dp; the remove target keeps its 48 dp and overhangs the pill evenly.
                Box(Modifier.size(tokens.chipHeight.dp).wrapContentSize(unbounded = true), contentAlignment = Alignment.Center) {
                KitTappable(
                    onClick = onRemove,
                    modifier =
                        Modifier.sizeIn(minWidth = tokens.chipHeight.dp, minHeight = tokens.chipHeight.dp)
                            .testTag("kit-chip-remove")
                            .semantics { contentDescription = removeLabel.orEmpty() },
                    shape = KitShapeId.Circle,
                ) { KitGlyph(KitPartGlyphs.Close, inkColors.tone(words), (tokens.smallIcon - 6).dp) }
                }
            }
        }
      }
    }
    val target = modifier.heightIn(min = tokens.minTarget.dp)
    if (onClick != null) {
        KitTappable(
            onClick = onClick,
            modifier = target.semantics {
                if (selected != null) {
                    this.selected = selected
                    role = Role.Checkbox
                }
            },
            shape = KitShapeId.Pill,
            ink = words,
        ) { face() }
    } else {
        Box(target, contentAlignment = Alignment.Center) { face() }
    }
}

/**
 * Chips that wrap onto as many lines as they need (Dart `KitChipWrap`), with run spacing that keeps each 48 dp target
 * clear of the next.
 *
 * Cap: Reading. Replaces: KitChipWrap (lib/ui/kit/kit_chip.dart).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KitChipWrap(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) { content() }
}
