package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.effectsLookPreviewLabel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * `KitLiveLookPreview`, `lib/ui/kit/kit_live_look_preview.dart`). With Background off ([look] null) it shows the flat
 * ground and a quiet "Off" pill; with it on, the chosen [look] moving behind two mock chat lines at [strength] (a still
 * frame under reduced motion, Calm or Off). The two cross-fade over 250 ms, at once under reduced motion. Tapping opens
 * the look picker. A look the native app does not draw yet shows the flat ground behind the lines.
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
    val name = look?.let { kitLiveStyleLabel(it) }?.let { stringResource(it) } ?: look.orEmpty()
    val label =
        if (look == null) stringResource(Res.string.effectsLookPreviewOffLabel) else stringResource(Res.string.effectsLookPreviewLabel, name)
    val motion = KitMotion.state
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
            Crossfade(targetState = look, animationSpec = KitMotion.specs.enter(), label = "look") { shown ->
                if (shown == null) {
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
                } else {
                    Box(Modifier.fillMaxSize()) {
                        kitLiveStyleById(shown)?.let { style ->
                            KitLiveLayer(style, strength, animate = motion.ambientLoops, modifier = Modifier.matchParentSize())
                        }
                        MockLines()
                    }
                }
            }
        }
    }
}

/** Dart `_MockLines` (kit_live_look_preview.dart:187-224): a short message from the person and a longer reply. */
@Composable
private fun MockLines() {
    val colors = LocalKitColors.current
    Column(
        Modifier.fillMaxSize().padding(Space.S3.dp), // F: lib/ui/kit/kit_live_look_preview.dart:201
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.align(Alignment.End)) {
            Box(
                Modifier.background(colors.surface2, RoundedCornerShape(FlutterKitTokens.BUTTON_RADIUS.dp)).padding(Space.S2.dp) // F: lib/ui/kit/kit_live_look_preview.dart:209,212
            ) { MockBar(72f, colors.text2) } // F: lib/ui/kit/kit_live_look_preview.dart:214
        }
        Spacer(Modifier.height(Space.S3.dp)) // F: lib/ui/kit/kit_live_look_preview.dart:217
        MockBar(150f, colors.text2) // F: lib/ui/kit/kit_live_look_preview.dart:218
        Spacer(Modifier.height(Space.S2.dp)) // F: lib/ui/kit/kit_live_look_preview.dart:219
        MockBar(112f, colors.text3) // F: lib/ui/kit/kit_live_look_preview.dart:220
    }
}

/** A line of words: a 6 dp pill (kit_live_look_preview.dart:192-198). */
@Composable
private fun MockBar(w: Float, color: Color) {
    Box(Modifier.width(w.dp).height(6.dp).background(color, RoundedCornerShape(Space.S1.dp))) // F: lib/ui/kit/kit_live_look_preview.dart:194,197
}

private const val HEIGHT: Int = 108 // F: lib/ui/kit/kit_live_look_preview.dart:40
