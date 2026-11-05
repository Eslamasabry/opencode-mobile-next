package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * One choice of an either/or that deserves a sentence each ("On this phone" / "On my computer"; Dart
 * `KitChoiceCards`, `KitOptionCard`). A card with an optional glyph, the title, a quiet [tag] ("Recommended"), one
 * supporting line, and a round mark at the end: an empty ring, or a filled mint disc with a check when [selected].
 * The chosen card takes a 2 dp accent edge and a faint accent wash; the rest keep a hairline. Heard as a radio
 * button; plays the selection tick. Never a switch: two switches for one choice read as two settings.
 *
 * States: default, pressed, selected
 * Text: reading
 * Cap: Reading
 * Motion: edge and wash cross-fade QUICK; Off = instant
 * Replaces: KitChoiceCards, KitOptionCard (lib/ui/kit/kit_choice_list.dart), the first run's place choice
 */
@Composable
public fun KitChoiceCard(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    tag: String? = null,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val shape = KitShapeId.Card.shape()
    val edge by animateColorAsState(if (selected) colors.accent else colors.hairline, specs.quick(), label = "kit-choice-edge")
    val wash by animateColorAsState(
        if (selected) colors.accent.copy(alpha = if (colors.dark) 0.07f else 0.05f) else colors.ground.copy(alpha = 0f),
        specs.quick(),
        label = "kit-choice-wash",
    )
    Row(
        modifier
            .kitMaxWidth(CapRole.Reading)
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .kitTappable(onClick = onSelect, role = KitTappableRole.Row, haptic = KitHaptic.Select, shape = KitShapeId.Card)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .background(if (colors.dark) colors.surface1 else colors.surface2, shape)
            .background(wash, shape)
            .border(if (selected) 2.dp else 1.dp, edge, shape)
            .padding(horizontal = Space.S4.dp, vertical = Space.S4.dp)
            .testTag("kit-choice-card"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        icon?.let { KitGlyph(it, if (selected) colors.accent else colors.text2, (tokens.smallIcon + 4).dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                KitText(title, Modifier.weight(1f, fill = false), role = TypeRole.RowTitle)
                tag?.let {
                    Box(Modifier.background(colors.surface3, RoundedCornerShape(50)).padding(horizontal = Space.S2.dp, vertical = 2.dp)) {
                        KitText(it, role = TypeRole.Caption, tone = TextTone.Secondary, maxLines = 1)
                    }
                }
            }
            supporting?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary) }
        }
        ChoiceMark(selected)
    }
}

/** The round mark at a choice card's end: a 1.5 dp ring, or the mint disc with a check. Decorative. */
@Composable
private fun ChoiceMark(selected: Boolean) {
    val colors = LocalKitColors.current
    Box(
        Modifier.size(24.dp).clearAndSetSemantics {}.then(
            if (selected) Modifier.background(colors.accent, CircleShape) else Modifier.border(1.5.dp, colors.text3, CircleShape)
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) KitGlyph(KitIcons.check, colors.onAccent, 16.dp)
    }
}

/** Choice cards stacked 12 dp apart and heard as one group (Dart `KitChoiceCards`). */
@Composable
public fun KitChoiceCards(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) { content() }
}
