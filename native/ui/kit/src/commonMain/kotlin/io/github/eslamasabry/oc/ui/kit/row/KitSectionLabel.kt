package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

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
    KitText(
        text,
        modifier
            .kitMaxWidth(CapRole.List)
            .fillMaxWidth()
            .padding(start = tokens.gutter.dp, end = tokens.gutter.dp, top = Space.S4.dp, bottom = tokens.labelGap.dp)
            .semantics { heading() }
            .testTag("kit-section-label"),
        role = TypeRole.Label,
        tone = TextTone.Secondary,
    )
}
