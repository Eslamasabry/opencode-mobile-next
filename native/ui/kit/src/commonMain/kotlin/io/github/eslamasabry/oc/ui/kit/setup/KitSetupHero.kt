package io.github.eslamasabry.oc.ui.kit.setup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.geometry.Rect
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

public enum class KitSetupPhoneMood { Fresh, Ready, Stopped, Termux, Starting, Celebration }

/** Top-anchored PhoneSetupHero, following phone_setup_hero.dart's slot order and state measure. */
@Composable
public fun KitSetupHero(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    mood: KitSetupPhoneMood = KitSetupPhoneMood.Fresh,
    primary: KitAction? = null,
    secondary: KitAction? = null,
    tertiary: List<KitAction> = emptyList(),
    content: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalKitTokens.current
    Box(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(
        start = tokens.gutter.dp, end = tokens.gutter.dp, top = Space.S2.dp, bottom = Space.S6.dp,
    ), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.kitMaxWidth(CapRole.State).fillMaxWidth()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                KitSetupPhone(mood)
            }
            Spacer(Modifier.height(Space.S5.dp))
            KitText(title, role = TypeRole.Title)
            if (body != null) {
                Spacer(Modifier.height(Space.S2.dp))
                KitText(body, tone = TextTone.Secondary)
            }
            content?.let {
                Spacer(Modifier.height(Space.S4.dp))
                it()
            }
            if (primary != null || secondary != null || tertiary.isNotEmpty()) {
                Spacer(Modifier.height(Space.S6.dp))
                KitActionBlock(primary = primary, secondary = secondary, tertiary = tertiary)
            }
            footer?.let {
                Spacer(Modifier.height(Space.S4.dp))
                it()
            }
        }
    }
}

/** The finished scene frame from setup_phone_scene.dart and setup_ready_scene.dart; decorative. */
@Composable
public fun KitSetupPhone(mood: KitSetupPhoneMood = KitSetupPhoneMood.Fresh, modifier: Modifier = Modifier) {
    val c = LocalKitColors.current
    val stopped = mood == KitSetupPhoneMood.Stopped
    val ready = mood == KitSetupPhoneMood.Ready || mood == KitSetupPhoneMood.Celebration
    Canvas(modifier.widthIn(max = 248.dp).fillMaxWidth().aspectRatio(160f / 120f).clearAndSetSemantics {}) { // F: lib/ui/kit/scenes/setup_phone_scene.dart:35 lib/ui/screens/phone_setup/phone_setup_hero.dart:65
        withTransform({ scale(size.width / 160f, size.height / 120f, Offset.Zero) }) { // F: lib/ui/kit/scenes/setup_phone_scene.dart:35
            val wash = c.accent.copy(alpha = FlutterKitTokens.SCENE_WASH_ALPHA)
            drawCircle(if (stopped) c.text3.copy(alpha = FlutterKitTokens.SCENE_WASH_ALPHA) else wash, 50f, Offset(80f, 60f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:41,77
            drawRoundRect(c.surface3, Offset(61f, 17f), Size(99f - 61f, 101f - 17f), CornerRadius(5f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:43,47
            if (!stopped) drawRoundRect(wash, Offset(61f, 17f), Size(99f - 61f, 101f - 17f), CornerRadius(5f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:43,47
            drawRoundRect(if (stopped) c.text2 else c.text1, Offset(54f, 8f), Size(106f - 54f, 112f - 8f), CornerRadius(12f), style = Stroke(5f, cap = StrokeCap.Round)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:42,44,47
            drawLine(c.text2.copy(alpha = .8f), Offset(74f, 106.5f), Offset(74f + 12f, 106.5f), 2f, StrokeCap.Round) // F: lib/ui/kit/scenes/setup_phone_scene.dart:122,123
            if (!ready && !stopped) {
                val prompt = Path().apply { moveTo(66f, 26f); lineTo(66f + 8f / 2, 26f + 8f / 2); lineTo(66f, 34f) } // F: lib/ui/kit/scenes/setup_phone_scene.dart:49,123,174
                drawPath(prompt, c.accent, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)) // F: lib/ui/kit/kit_illustration.dart:167
                drawLine(c.accent, Offset(73f, 34f), Offset(80f, 34f), 2.5f, StrokeCap.Round) // F: lib/ui/kit/kit_illustration.dart:167 lib/ui/kit/scenes/setup_phone_scene.dart:140,174
            }
            if (mood == KitSetupPhoneMood.Termux) {
                listOf(44f to 22f, 53f to 14f, 62f to 26f, 71f to 18f).forEachIndexed { index, (y, length) -> // F: lib/ui/kit/scenes/setup_phone_scene.dart:57,58,59,60
                    drawLine(if (index == listOf(44f, 53f, 62f, 71f).lastIndex) c.accent else c.text2.copy(alpha = .7f), Offset(66f, y), Offset(66f + length, y), 2.5f, StrokeCap.Round) // F: lib/ui/kit/scenes/setup_phone_scene.dart:57,58,59,60,77 lib/ui/kit/kit_illustration.dart:167
                }
            } else {
                val cy = if (ready) 58f else if (stopped) 60f else 66f // F: lib/ui/kit/scenes/setup_phone_scene.dart:41,49,133
                val span = if (ready) 34f else 30f // F: lib/ui/kit/scenes/setup_phone_scene.dart:219
                portal(Offset(80f, cy), span, if (stopped) c.text2.copy(alpha = .7f) else c.accent, // F: lib/ui/kit/scenes/setup_phone_scene.dart:140,142
                    if (mood == KitSetupPhoneMood.Celebration) 2.5f else if (stopped) -2f else 0f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:145 lib/ui/kit/kit_illustration.dart:167
                    penWidth = if (stopped) 4f else 4.5f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:146,224
                if (mood == KitSetupPhoneMood.Celebration) {
                    val check = Path().apply {
                        moveTo(74.6f, 58.3f); lineTo(78.5f, 62.2f); lineTo(85.7f, 54.4f) // NV: SetupCast.check at (80,58), size 15, using .36/.02/.1/.28/.38/.24
                    }
                    drawPath(check, c.accent, style = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)) // F: lib/ui/kit/scenes/setup_ready_scene.dart:142
                } else if (!stopped) drawCircle(c.accent, 3.2f, Offset(80f, cy)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:212,231
            }
            if (mood == KitSetupPhoneMood.Celebration) {
                sparkle(Offset(126f, 30f), 10f, c.accent) // F: lib/ui/kit/scenes/setup_ready_scene.dart:119
                sparkle(Offset(34f, 50f), 6f, c.accent.copy(alpha = .8f)) // F: lib/ui/kit/scenes/setup_ready_scene.dart:122,123,124
                sparkle(Offset(118f, 88f), 4.5f, c.accent.copy(alpha = .6f)) // F: lib/ui/kit/scenes/setup_ready_scene.dart:129,130,131
                val confetti = listOf(
                    Triple(Offset(28f, 30f), .6f, false), Triple(Offset(136f, 72f), -.5f, false), // F: lib/ui/kit/scenes/setup_ready_scene.dart:39,40
                    Triple(Offset(124f, 100f), .9f, true), Triple(Offset(24f, 76f), -.8f, true), // F: lib/ui/kit/scenes/setup_ready_scene.dart:41,42
                    Triple(Offset(140f, 20f), .2f, true), // F: lib/ui/kit/scenes/setup_ready_scene.dart:43
                )
                confetti.forEach { (at, angle, muted) ->
                    val d = Offset(cos(angle), sin(angle)) * 3.5f // F: lib/ui/kit/scenes/setup_ready_scene.dart:142
                    drawLine(if (muted) c.text2.copy(alpha = .6f) else c.accent.copy(alpha = .9f), at - d, at + d, 2.5f, StrokeCap.Round) // F: lib/ui/kit/scenes/setup_ready_scene.dart:147 lib/ui/kit/kit_illustration.dart:167
                }
            } else {
                if (!stopped) {
                    sparkle(Offset(126f, 26f), 9f, c.accent) // F: lib/ui/kit/scenes/setup_phone_scene.dart:242,243
                    sparkle(Offset(138f, 46f), 4.5f, c.accent.copy(alpha = .8f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:249,250,251
                    sparkle(Offset(32f, 90f), 5f, c.accent.copy(alpha = .55f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:256,257,258
                } else {
                    val moon = Path.combine(PathOperation.Difference,
                        Path().apply { addOval(Rect(Offset(128f - 9f, 26f - 9f), Size(9f * 2, 9f * 2))) }, // F: lib/ui/kit/scenes/setup_phone_scene.dart:52,123
                        Path().apply { addOval(Rect(Offset(133f - 8f, 21f - 8f), Size(8f * 2, 8f * 2))) }) // F: lib/ui/kit/scenes/setup_phone_scene.dart:53,123
                    drawPath(moon, c.text2.copy(alpha = .8f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:152
                }
                val dots = c.text2.copy(alpha = if (stopped) .35f else .5f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:268
                drawCircle(dots, 2.2f, Offset(34f, 34f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:270
                drawCircle(dots, 1.6f, Offset(22f, 62f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:271
                drawCircle(dots, 2.4f, Offset(132f, 94f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:272
            }
        }
    }
}

private fun DrawScope.portal(center: Offset, span: Float, color: Color, open: Float, penWidth: Float) {
    fun x(v: Float) = center.x + (v - 54f) / 60f * span // F: lib/ui/kit/scenes/setup_cast.dart:15
    fun y(v: Float) = center.y + (v - 54f) / 60f * span // F: lib/ui/kit/scenes/setup_cast.dart:15
    val upper = Path().apply {
        moveTo(x(61f) - open, y(31f) - open); lineTo(x(43f) - open, y(31f) - open) // F: lib/ui/kit/scenes/setup_cast.dart:18,19
        quadraticTo(x(31f) - open, y(31f) - open, x(31f) - open, y(43f) - open) // F: lib/ui/kit/scenes/setup_cast.dart:19
        lineTo(x(31f) - open, y(64f) - open) // F: lib/ui/kit/scenes/setup_cast.dart:25
    }
    val lower = Path().apply {
        moveTo(x(77f) + open, y(44f) + open); lineTo(x(77f) + open, y(65f) + open) // F: lib/ui/kit/scenes/setup_cast.dart:28,29
        quadraticTo(x(77f) + open, y(77f) + open, x(65f) + open, y(77f) + open) // F: lib/ui/kit/scenes/setup_cast.dart:29
        lineTo(x(44f) + open, y(77f) + open) // F: lib/ui/kit/scenes/setup_cast.dart:35
    }
    val pen = Stroke(penWidth, cap = StrokeCap.Round, join = StrokeJoin.Round) // F: lib/ui/kit/scenes/setup_phone_scene.dart:250
    drawPath(upper, color, style = pen); drawPath(lower, color, style = pen)
}

private fun DrawScope.sparkle(at: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(0f, -1f)
        quadraticTo(.14f, -.14f, 1f, 0f); quadraticTo(.14f, .14f, 0f, 1f) // F: lib/ui/kit/scenes/setup_cast.dart:75
        quadraticTo(-.14f, .14f, -1f, 0f); quadraticTo(-.14f, -.14f, 0f, -1f) // F: lib/ui/kit/scenes/setup_cast.dart:75
        close()
    }
    withTransform({ translate(at.x, at.y); scale(r, r, Offset.Zero) }) { drawPath(p, color) }
}
