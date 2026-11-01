package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * One quiet sentence under a group that explains it (Dart `KitGroupNote`): secondary type in `text2`, wrapping in full.
 *
 * States: default
 * Text: reading
 * Cap: List
 * Motion: none
 * Hidden: none
 * Replaces: KitGroupNote (lib/ui/kit/kit_group_note.dart)
 */
@Composable
public fun KitGroupNote(text: String, modifier: Modifier = Modifier) {
    val tokens = LocalKitTokens.current
    KitText(
        text,
        modifier
            .kitMaxWidth(CapRole.List)
            .fillMaxWidth()
            .padding(start = tokens.gutter.dp, end = tokens.gutter.dp, top = tokens.labelGap.dp)
            .testTag("kit-group-note"),
        role = TypeRole.Secondary,
        tone = TextTone.Secondary,
    )
}
