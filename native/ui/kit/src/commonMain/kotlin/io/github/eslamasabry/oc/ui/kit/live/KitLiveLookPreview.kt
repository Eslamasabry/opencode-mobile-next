package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
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
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.effectsLookPreviewLabel
import io.github.eslamasabry.oc.ui.words.res.effectsLookPreviewOff
import io.github.eslamasabry.oc.ui.words.res.effectsLookPreviewOffLabel
import org.jetbrains.compose.resources.stringResource

/**
 * A short, wide live sample of the person's chosen look behind two mock chat lines, at the top of Settings › Appearance ›
 * Effects (Dart `KitLiveLookPreview`, `lib/ui/kit/kit_live_look_preview.dart`). It follows the Live background switch
 * ([enabled] false: the flat ground and a quiet "Off"), cross-fades when that or the look changes ([lookId]), and opens
 * the look picker when tapped. The look itself is drawn by [KitLiveBackground], which holds a still frame under Calm,
 * Off, reduced motion and screenshots.
 *
 * [lookName] is the chosen look's name, for the tile's spoken label.
 *
 * States: disabled.
 * Cap: reading
 */
@Composable
public fun KitLiveLookPreview(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = false,
    lookName: String = "",
    lookId: String = "",
) {
    val label =
        if (enabled) stringResource(Res.string.effectsLookPreviewLabel, lookName)
        else stringResource(Res.string.effectsLookPreviewOffLabel)
    val fade = KitMotion.specs.enter<Float>()
    // The cross-fade's key: the look while on, nothing while off (kit_live_look_preview.dart stateKey).
    val stateKey = if (enabled) lookId else null
    KitTappable(
        onClick = onTap,
        // A full-width tap area as wide as the reading column, not a compact button (KitEdge.Bleed).
        modifier = modifier.kitMaxWidth(CapRole.Reading).kitEdge(KitEdge.Bleed).semantics { contentDescription = label },
        role = KitTappableRole.Button,
        shape = KitShapeId.Card,
    ) {
        KitSurface(
            modifier = Modifier.fillMaxWidth().height(HEIGHT.dp).clearAndSetSemantics { },
            level = SurfaceLevel.Ground,
            shape = KitShapeId.Card,
            padding = KitSurfacePadding.None,
        ) {
            Crossfade(stateKey, Modifier.fillMaxSize(), animationSpec = fade, label = "look") { key ->
                if (key != null) {
                    KitLiveBackground(Modifier.fillMaxSize()) { MockLines() }
                } else {
                    OffPill()
                }
            }
        }
    }
}

@Composable
private fun OffPill() {
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

/** Two chat lines: a short message from the person and a longer reply (Dart `_MockLines`). Solid surfaces only. */
@Composable
private fun MockLines() {
    val colors = LocalKitColors.current
    Column(
        Modifier.fillMaxSize().padding(Space.S3.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            KitSurface(level = SurfaceLevel.Surface2, shape = KitShapeId.Button, padding = KitSurfacePadding.None, clip = false) {
                Box(Modifier.padding(Space.S2.dp)) { Bar(BUBBLE_BAR, colors.text2) }
            }
        }
        Spacer(Modifier.height(Space.S3.dp))
        Bar(REPLY_BAR, colors.text2)
        Spacer(Modifier.height(Space.S2.dp))
        Bar(SHORT_BAR, colors.text3)
    }
}

@Composable
private fun Bar(width: Int, color: Color) {
    Canvas(Modifier.size(width.dp, BAR_HEIGHT.dp)) {
        drawRoundRect(color, cornerRadius = CornerRadius(Space.S1.dp.toPx()))
    }
}

private const val HEIGHT: Int = 108 // F: lib/ui/kit/kit_live_look_preview.dart:40
private const val BAR_HEIGHT: Int = 6 // F: lib/ui/kit/kit_live_look_preview.dart:194
private const val BUBBLE_BAR: Int = 72 // F: lib/ui/kit/kit_live_look_preview.dart:214
private const val REPLY_BAR: Int = 150 // F: lib/ui/kit/kit_live_look_preview.dart:218
private const val SHORT_BAR: Int = 112 // F: lib/ui/kit/kit_live_look_preview.dart:220
