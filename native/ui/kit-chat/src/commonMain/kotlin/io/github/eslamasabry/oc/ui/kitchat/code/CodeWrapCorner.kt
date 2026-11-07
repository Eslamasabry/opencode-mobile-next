package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitWrapLines
import org.jetbrains.compose.resources.stringResource

/** The Wrap toggle's test handle (Dart `ValueKey('kit-code-wrap')`). */
public const val CODE_WRAP_TAG: String = "kit-code-wrap"

/**
 * One 48 dp circular target holding a 24 dp kit glyph (the Phosphor font glyph, as Flutter's `Icon`), named [label]:
 * the shape of Flutter's `_WrapToggle` (`KitTappable` + `Icon`, kit_code_block_parts.dart) and of the Copy target.
 */
@Composable
internal fun CodeGlyphTarget(
    glyph: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tone: TextTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        KitTappable(
            onClick,
            Modifier.size(FlutterKitTokens.MIN_TARGET.dp).semantics { contentDescription = label },
            shape = KitShapeId.Circle,
            ink = tone,
        ) {
            KitIcon(glyph, Modifier.align(Alignment.Center), tone = tone)
        }
    }
}

/**
 * The block's Wrap toggle (Dart `_WrapToggle`, kit_code_block_parts.dart): the wrap glyph at 24 dp on a 48 dp target,
 * in `accent` while lines wrap and `text2` otherwise, never a filled circle; a toggle to assistive tech ("Wrap lines").
 */
@Composable
internal fun CodeWrapToggle(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    CodeGlyphTarget(
        KitIcons.wrapText,
        stringResource(Res.string.kitWrapLines),
        if (on) TextTone.Accent else TextTone.Secondary,
        onToggle,
        modifier.testTag(CODE_WRAP_TAG).semantics {
            toggleableState = if (on) ToggleableState.On else ToggleableState.Off
        },
    )
}
