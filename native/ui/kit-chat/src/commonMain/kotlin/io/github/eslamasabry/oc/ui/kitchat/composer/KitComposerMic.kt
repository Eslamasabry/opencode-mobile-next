package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** Flutter's tap-to-talk circle; the injected voice slot supplies hold-to-talk when supported. */
@Composable
public fun KitComposerMic(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    quiet: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                role = SurfaceRole.Control,
                shape = KitShapeId.Circle,
                level = SurfaceLevel.Surface3,
                ownFill = if (quiet) colors.surface3 else colors.accent,
                color = if (quiet) null else colors.accent,
                action = true,
            )
        )
    val ink = if (quiet) colors.text2 else look.words ?: colors.onAccent
    // The disc (fill and the style's shadow) sits under the press target, outside its clip, so its shadow is whole.
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(FlutterKitTokens.COMPOSER_ACTION_SIZE.dp)
                .then(look.modifier)
                .testTag("kit-composer-mic-disc")
        )
        KitTappable(
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = longClickLabel,
            modifier = Modifier.semantics { contentDescription = label },
            shape = KitShapeId.Circle,
            ink = if (quiet) TextTone.Secondary else TextTone.OnAccent,
        ) {
            Image(
                KitIcons.mic,
                null,
                Modifier.size(tokens.smallIcon.dp),
                colorFilter = ColorFilter.tint(ink),
            )
        }
    }
}
