package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

private const val HALF_TURN = 180f

/**
 * A row that unfolds more below it (Dart `KitExpandRow`; plan 06 §7.5): the title and one supporting line, a chevron at
 * the end that turns as it opens, and [content] revealed under it (the one layout animation the kit allows, `KitReveal`).
 * The screen holds [expanded]; a screen reader hears expand / collapse actions.
 *
 * States: collapsed, expanded
 * Text: reading
 * Cap: List
 * Motion: reveal (enter / exit specs), chevron turn; Off = instant
 * Replaces: KitExpandRow (lib/ui/kit/kit_row_parts.dart)
 */
@Composable
public fun KitExpandRow(
    title: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val turn by animateFloatAsState(if (expanded) HALF_TURN else 0f, specs.quick(), label = "expand-chevron")
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = tokens.rowHeightOne.dp)
                .kitTappable(onClick = { onExpandedChange(!expanded) }, role = KitTappableRole.Row, shape = KitShapeId.Square)
                .semantics {
                    if (expanded) collapse { onExpandedChange(false); true } else expand { onExpandedChange(true); true }
                }
                .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            Column(Modifier.weight(1f)) {
                KitText(title, role = TypeRole.RowTitle)
                supporting?.let { KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, maxLines = 3) }
            }
            KitGlyph(KitPartGlyphs.ChevronDown, colors.text2, tokens.smallIcon.dp, Modifier.graphicsLayer { rotationZ = turn })
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(specs.enter()) + fadeIn(specs.enter()),
            exit = shrinkVertically(specs.exit()) + fadeOut(specs.exit()),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                content = content,
            )
        }
    }
}
