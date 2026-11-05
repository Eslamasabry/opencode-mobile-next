package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.effectsLookPreviewOff
import io.github.eslamasabry.oc.ui.words.res.effectsLookPreviewOffLabel
import org.jetbrains.compose.resources.stringResource

/**
 * A short, wide sample of the person's chosen look at the top of Settings › Appearance › Effects (Dart
 * `KitLiveLookPreview`). This port draws the disabled state: the flat ground and a quiet "Off" pill; tapping opens the
 * look picker.
 *
 * States: disabled.
 * Cap: reading
 */
@Composable
public fun KitLiveLookPreview(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.effectsLookPreviewOffLabel)
    KitTappable(
        onClick = onTap,
        modifier = modifier.kitMaxWidth(CapRole.Reading).semantics { contentDescription = label },
        role = KitTappableRole.Button,
        shape = KitShapeId.Card,
    ) {
        KitSurface(
            modifier = Modifier.height(HEIGHT.dp).clearAndSetSemantics { },
            level = SurfaceLevel.Ground,
            shape = KitShapeId.Card,
            padding = KitSurfacePadding.None,
            outlined = true,
        ) {
            Box(Modifier.height(HEIGHT.dp), contentAlignment = Alignment.Center) {
                KitSurface(
                    level = SurfaceLevel.Surface1,
                    shape = KitShapeId.Button,
                    padding = KitSurfacePadding.None,
                    clip = false,
                ) {
                    Box(Modifier.padding(horizontal = Space.S3.dp, vertical = Space.S1.dp)) {
                        KitText(stringResource(Res.string.effectsLookPreviewOff), role = TypeRole.Secondary, tone = TextTone.Secondary)
                    }
                }
            }
        }
    }
}

private const val HEIGHT: Int = 108 // F: lib/ui/kit/kit_live_look_preview.dart:40
