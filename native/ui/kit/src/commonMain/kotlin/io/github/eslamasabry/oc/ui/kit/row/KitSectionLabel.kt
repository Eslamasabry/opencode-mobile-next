package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.design.effects.SurfaceStyle

/**
 * A group's heading (Dart `KitSectionLabel`): label type in `text2`, sentence case, a heading for TalkBack. It sits a
 * section gap below what came before and a label gap above its rows.
 *
 * States: default
 * Text: reading
 * Cap: List
 * Motion: none
 * Hidden: none
 * Replaces: KitSectionLabel (lib/ui/kit/kit_section_label.dart)
 */
@Composable
public fun KitSectionLabel(text: String, modifier: Modifier = Modifier) {
    val tokens = LocalKitTokens.current
    val style = LocalKitStyle.current
    val colors = LocalKitColors.current
    val caps = style.engravedLabels && !LocalKitTypography.current.isArabic
    KitText(
        if (caps) text.uppercase() else text,
        modifier
            .kitMaxWidth(CapRole.List)
            .fillMaxWidth()
            .padding(start = tokens.gutter.dp, end = tokens.gutter.dp, top = Space.S4.dp, bottom = tokens.labelGap.dp)
            .then(if (style.style == SurfaceStyle.Paper) Modifier.drawBehind { drawLine(colors.text1.copy(alpha = .9f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) } else Modifier)
            .clearAndSetSemantics { heading(); contentDescription = text }
            .testTag("kit-section-label"),
        role = TypeRole.Label,
        tone = TextTone.Secondary,
        sectionLabel = true,
    )
}
