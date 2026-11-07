package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.min

/** Dart `liveMockChat` (`live_mock_chat.dart:15-89`): a message, some lines, the message box. */
@Composable
internal fun LiveMockChat(scale: Float) {
    val colors = LocalKitColors.current
    val gap = (Space.S2 * scale).dp // FG: lib/ui/kit/live_background/live_mock_chat.dart:18
    Column(Modifier.fillMaxSize().padding((Space.S3 * scale).dp)) { // FG: lib/ui/kit/live_background/live_mock_chat.dart:55
        // A control, not content: the tile is itself a content surface (live_mock_chat.dart:61-68).
        val message =
            kitSurfaceLook(
                KitSurfaceInput(
                    SurfaceRole.Control,
                    KitShapeId.Button,
                    SurfaceLevel.Surface2,
                    ownFill = colors.surface2,
                    radius = (FlutterKitTokens.BUTTON_RADIUS * scale).dp,
                )
            )
        Box(Modifier.align(Alignment.End).then(message.modifier).padding((Space.S2 * scale).dp)) { // FG: lib/ui/kit/live_background/live_mock_chat.dart:66
            MockBar(70f, 6f, scale, colors.text2) // FG: lib/ui/kit/live_background/live_mock_chat.dart:67
        }
        Spacer(Modifier.height(gap * 1.5f)) // FG: lib/ui/kit/live_background/live_mock_chat.dart:70
        MockBar(90f, 5f, scale, colors.text3) // FG: lib/ui/kit/live_background/live_mock_chat.dart:71
        Spacer(Modifier.height(gap))
        MockBar(130f, 6f, scale, colors.text2) // FG: lib/ui/kit/live_background/live_mock_chat.dart:73
        Spacer(Modifier.height(gap))
        MockBar(110f, 6f, scale, colors.text2) // FG: lib/ui/kit/live_background/live_mock_chat.dart:75
        Spacer(Modifier.height(gap))
        MockBar(80f, 6f, scale, colors.text2) // FG: lib/ui/kit/live_background/live_mock_chat.dart:77
        Spacer(Modifier.weight(1f))
        val box =
            kitSurfaceLook(
                KitSurfaceInput(
                    SurfaceRole.Floating,
                    KitShapeId.Pill,
                    SurfaceLevel.Surface2,
                    ownFill = colors.surface2,
                    radius = (FlutterKitTokens.COMPOSER_RADIUS * scale).dp,
                )
            )
        Box(
            Modifier.fillMaxWidth()
                .height((Space.S6 * scale).dp) // FG: lib/ui/kit/live_background/live_mock_chat.dart:82
                .then(box.modifier)
                .padding(horizontal = (Space.S3 * scale).dp), // FG: lib/ui/kit/live_background/live_mock_chat.dart:83
            contentAlignment = Alignment.CenterStart,
        ) {
            MockBar(60f, 5f, scale, colors.text3) // FG: lib/ui/kit/live_background/live_mock_chat.dart:85
        }
    }
}

/** A line of words: a pill (`live_mock_chat.dart:20-28`). */
@Composable
private fun MockBar(w: Float, h: Float, scale: Float, color: Color) {
    val radius: Dp = min(h * scale / 2f, 999f).dp // FG: lib/ui/kit/live_background/live_mock_chat.dart:26
    Box(Modifier.width((w * scale).dp).height((h * scale).dp).background(color, RoundedCornerShape(radius)))
}
