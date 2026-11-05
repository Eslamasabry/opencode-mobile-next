package io.github.eslamasabry.oc.ui.kit.icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** The three designed sizes (Dart `KitIconSize`, VL §7): 20 inline and in rows, 22 for a card mark, 24 for controls. */
public enum class KitIconSize(public val dp: Int) {
    Small(20),
    Medium(22),
    Large(24),
}

/**
 * The one way a screen draws a glyph (Dart `KitIcon`): a [KitIcons] glyph at a designed size, in a text tone, so an
 * icon always matches the words beside it. Decorative unless [label] is given. It grows with the person's text size
 * up to the frame cap ([io.github.eslamasabry.oc.ui.design.tokens.Tokens.maxIconScale]).
 *
 * States: none (a disabled icon is the host passing [TextTone.Tertiary])
 * Motion: none
 * Replaces: KitIcon (lib/ui/kit/kit_icon.dart)
 */
@Composable
public fun KitIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: KitIconSize = KitIconSize.Small,
    tone: TextTone = TextTone.Secondary,
    label: String? = null,
) {
    val colors = LocalKitColors.current
    val scale = minOf(kitTextScale(), LocalKitTokens.current.maxIconScale)
    Image(
        imageVector = icon,
        contentDescription = null,
        colorFilter = ColorFilter.tint(colors.tone(tone)),
        modifier = modifier.size((size.dp * scale).dp).then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
    )
}
