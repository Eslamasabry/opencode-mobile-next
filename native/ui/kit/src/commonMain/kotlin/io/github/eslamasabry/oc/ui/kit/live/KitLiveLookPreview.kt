package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
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
import io.github.eslamasabry.oc.ui.kit.motion.KitEasing
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.effectsLookPreviewLabel
import io.github.eslamasabry.oc.ui.words.res.effectsLookPreviewOff
import io.github.eslamasabry.oc.ui.words.res.effectsLookPreviewOffLabel
import org.jetbrains.compose.resources.stringResource

/**
 * A short, wide live sample of the person's chosen look behind two mock chat lines, at the top of Settings › Appearance
 * (Dart `KitLiveLookPreview`, `lib/ui/kit/kit_live_look_preview.dart`). With [look] null (Live background off) it shows
 * the flat ground and a quiet "Off"; with a drawn look it shows that look at [strength], moving on the shared clock
 * while motion is Full (a still frame under Calm, Off or reduced motion). It cross-fades when the look or the switch
 * changes ([MotionTokens.STANDARD], none under reduced motion) and opens the look picker when tapped.
 *
 * States: disabled, on.
 * Cap: reading
 */
@Composable
public fun KitLiveLookPreview(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    look: String? = null,
    strength: Float = 1f,
) {
    val style = look?.takeIf { it != AuroraImage.ID }?.let { kitLiveStyleOrNull(it) }
    val label =
        if (style == null) stringResource(Res.string.effectsLookPreviewOffLabel)
        else stringResource(Res.string.effectsLookPreviewLabel, stringResource(style.label))
    val motion = LocalKitMotion.current
    KitTappable(
        onClick = onTap,
        modifier = modifier.kitMaxWidth(CapRole.Reading).semantics { contentDescription = label },
        role = KitTappableRole.Button,
        shape = KitShapeId.Card,
    ) {
        KitSurface(
            modifier = Modifier.fillMaxWidth().height(HEIGHT.dp).clearAndSetSemantics { },
            level = SurfaceLevel.Ground,
            shape = KitShapeId.Card,
            padding = KitSurfacePadding.None,
        ) {
            val fade = if (motion.reduced || motion.stillFrame) snap<Float>() else tween(MotionTokens.STANDARD, easing = KitEasing.Enter)
            Crossfade(targetState = style?.let { LiveState(it, strength) }, animationSpec = fade, label = "look") { state ->
                if (state == null) Off() else On(state, moves = motion.ambientLoops)
            }
        }
    }
}

private data class LiveState(val style: KitLiveStyle, val strength: Float)

@Composable
private fun Off() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

/** The look moving behind two chat lines: a short message from the person and a longer reply (Dart `_MockLines`). */
@Composable
private fun On(state: LiveState, moves: Boolean) {
    val colors = LocalKitColors.current
    Box(Modifier.fillMaxSize()) {
        KitLiveLayer(state.style, state.strength.toDouble(), moves = moves, running = false, modifier = Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(Space.S3.dp), // F: lib/ui/kit/kit_live_look_preview.dart:201
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.align(Alignment.End)
                    .background(colors.surface2, RoundedCornerShape(FlutterKitTokens.BUTTON_RADIUS.dp))
                    .padding(Space.S2.dp) // F: lib/ui/kit/kit_live_look_preview.dart:209
            ) {
                MockLine(72f, colors.text2) // F: lib/ui/kit/kit_live_look_preview.dart:214
            }
            Spacer(Modifier.height(Space.S3.dp))
            MockLine(150f, colors.text2) // F: lib/ui/kit/kit_live_look_preview.dart:218
            Spacer(Modifier.height(Space.S2.dp))
            MockLine(112f, colors.text3) // F: lib/ui/kit/kit_live_look_preview.dart:220
        }
    }
}

@Composable
private fun MockLine(w: Float, color: Color) {
    Box(Modifier.width(w.dp).height(6.dp).background(color, RoundedCornerShape(Space.S1.dp))) // F: lib/ui/kit/kit_live_look_preview.dart:194,197
}

private const val HEIGHT: Int = 108 // F: lib/ui/kit/kit_live_look_preview.dart:40
