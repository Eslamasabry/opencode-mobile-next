package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * One muted line under a [KitRowGroup] that says what the group leaves out and offers one way to learn why (Dart
 * `KitGroupNote`): "2 settings aren't available on this server · Why". It sits on the group's label rail (the gutter),
 * so it reads as part of the group above it. The [action] is a tertiary button at the end of the line; when the words
 * and the action do not fit on one line (long words, large text) the action wraps under the words, start-aligned.
 *
 * States: none (a passive line; its one action has no state of its own)
 * Text: reading
 * Cap: Reading
 * Motion: none
 * Hidden: none
 * Replaces: KitGroupNote (lib/ui/kit/kit_group_note.dart)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KitGroupNote(text: String, modifier: Modifier = Modifier, action: KitAction? = null) {
    val tokens = LocalKitTokens.current
    FlowRow(
        modifier
            .kitMaxWidth(CapRole.Reading) // F: lib/ui/kit/kit_group_note.dart:32
            .fillMaxWidth()
            .padding(start = kitRowGutter(), end = kitRowGutter(), top = tokens.labelGap.dp) // F: lib/ui/kit/kit_group_note.dart:39-42
            .testTag("kit-group-note"),
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp), // F: lib/ui/kit/kit_group_note.dart:45
        itemVerticalAlignment = Alignment.CenterVertically, // F: lib/ui/kit/kit_group_note.dart:44
    ) {
        KitText(text, role = TypeRole.Secondary, tone = TextTone.Secondary)
        if (action != null) KitButton(action, role = KitButtonRole.Tertiary)
    }
}
