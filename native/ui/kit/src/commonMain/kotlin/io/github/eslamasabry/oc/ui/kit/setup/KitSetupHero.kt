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
    Canvas(modifier.widthIn(max = 248.dp).fillMaxWidth().aspectRatio(160f / 120f).clearAndSetSemantics {}) { // F: lib/ui/screens/phone_setup/phone_setup_hero.dart:75 lib/ui/kit/scenes/setup_phone_scene.dart:39
        withTransform({ scale(size.width / 160f, size.height / 120f, Offset.Zero) }) { // F: lib/ui/kit/scenes/setup_phone_scene.dart:39
            val wash = c.accent.copy(alpha = FlutterKitTokens.SCENE_WASH_ALPHA)
            drawCircle(if (stopped) c.text3.copy(alpha = FlutterKitTokens.SCENE_WASH_ALPHA) else wash, 50f, Offset(80f, 60f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:46,81
            drawRoundRect(c.surface3, Offset(61f, 17f), Size(38f, 84f), CornerRadius(5f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:48,52
            if (!stopped) drawRoundRect(wash, Offset(61f, 17f), Size(38f, 84f), CornerRadius(5f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:48,52
            drawRoundRect(if (stopped) c.text2 else c.text1, Offset(54f, 8f), Size(52f, 104f), CornerRadius(12f), style = Stroke(5f, cap = StrokeCap.Round)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:47 lib/ui/kit/kit_illustration.dart:165
            drawLine(c.text2.copy(alpha = .8f), Offset(74f, 106.5f), Offset(86f, 106.5f), 2f, StrokeCap.Round) // F: lib/ui/kit/scenes/setup_phone_scene.dart:133-135
            if (!ready && !stopped) {
                val prompt = Path().apply { moveTo(66f, 26f); lineTo(70f, 30f); lineTo(66f, 34f) } // F: lib/ui/kit/scenes/setup_phone_scene.dart:54 lib/ui/kit/scenes/setup_cast.dart:127-130
                drawPath(prompt, c.accent, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)) // F: lib/ui/kit/kit_illustration.dart:168
                drawLine(c.accent, Offset(73f, 34f), Offset(80f, 34f), 2.5f, StrokeCap.Round) // F: lib/ui/kit/scenes/setup_phone_scene.dart:183-185 lib/ui/kit/kit_illustration.dart:168
            }
            if (mood == KitSetupPhoneMood.Termux) {
                listOf(44f to 22f, 53f to 14f, 62f to 26f, 71f to 18f).forEachIndexed { index, (y, length) -> // F: lib/ui/kit/scenes/setup_phone_scene.dart:63-66
                    drawLine(if (index == 3) c.accent else c.text2.copy(alpha = .7f), Offset(66f, y), Offset(66f + length, y), 2.5f, StrokeCap.Round) // F: lib/ui/kit/scenes/setup_phone_scene.dart:209 lib/ui/kit/kit_illustration.dart:168
                }
            } else {
                val cy = if (ready) 58f else 66f // F: lib/ui/kit/scenes/setup_phone_scene.dart:148,234
                val span = if (ready) 34f else 30f // F: lib/ui/kit/scenes/setup_phone_scene.dart:244
                portal(Offset(80f, cy), span, if (stopped) c.text2 else c.accent, if (mood == KitSetupPhoneMood.Celebration) 2.5f else 0f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:154 lib/ui/kit/scenes/setup_ready_scene.dart:120
                if (mood == KitSetupPhoneMood.Celebration) {
                    val check = Path().apply {
                        moveTo(74.6f, 58.3f); lineTo(78.5f, 62.2f); lineTo(85.7f, 54.4f) // NV: SetupCast.check at (80,58), size 15, using .36/.02/.1/.28/.38/.24
                    }
                    drawPath(check, c.accent, style = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)) // F: lib/ui/kit/scenes/setup_ready_scene.dart:125
                } else if (!stopped) drawCircle(c.accent, 3.2f, Offset(80f, cy)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:259
            }
            if (!stopped) {
                sparkle(Offset(126f, 26f), 9f, c.accent) // F: lib/ui/kit/scenes/setup_phone_scene.dart:271-272
                sparkle(Offset(138f, 46f), 4.5f, c.accent.copy(alpha = .8f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:279-281
                sparkle(Offset(32f, 90f), 5f, c.accent.copy(alpha = .55f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:287-289
            }
            drawCircle(c.text2.copy(alpha = .5f), 2.2f, Offset(34f, 34f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:302,304
            drawCircle(c.text2.copy(alpha = .5f), 1.6f, Offset(22f, 62f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:302,305
            drawCircle(c.text2.copy(alpha = .5f), 2.4f, Offset(132f, 94f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:302,306
        }
    }
}

private fun DrawScope.portal(center: Offset, span: Float, color: Color, open: Float) {
    fun x(v: Float) = center.x + (v - 54f) / 60f * span // F: lib/ui/kit/scenes/setup_cast.dart:14
    fun y(v: Float) = center.y + (v - 54f) / 60f * span // F: lib/ui/kit/scenes/setup_cast.dart:14
    val upper = Path().apply {
        moveTo(x(61f) - open, y(31f) - open); lineTo(x(43f) - open, y(31f) - open) // F: lib/ui/kit/scenes/setup_cast.dart:17-18
        quadraticTo(x(31f) - open, y(31f) - open, x(31f) - open, y(43f) - open) // F: lib/ui/kit/scenes/setup_cast.dart:19-24
        lineTo(x(31f) - open, y(64f) - open) // F: lib/ui/kit/scenes/setup_cast.dart:24
    }
    val lower = Path().apply {
        moveTo(x(77f) + open, y(44f) + open); lineTo(x(77f) + open, y(65f) + open) // F: lib/ui/kit/scenes/setup_cast.dart:27-28
        quadraticTo(x(77f) + open, y(77f) + open, x(65f) + open, y(77f) + open) // F: lib/ui/kit/scenes/setup_cast.dart:29-34
        lineTo(x(44f) + open, y(77f) + open) // F: lib/ui/kit/scenes/setup_cast.dart:34
    }
    val pen = Stroke(4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round) // F: lib/ui/kit/scenes/setup_phone_scene.dart:249
    drawPath(upper, color, style = pen); drawPath(lower, color, style = pen)
}

private fun DrawScope.sparkle(at: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(0f, -1f)
        quadraticTo(.14f, -.14f, 1f, 0f); quadraticTo(.14f, .14f, 0f, 1f) // F: lib/ui/kit/scenes/setup_cast.dart:78-79
        quadraticTo(-.14f, .14f, -1f, 0f); quadraticTo(-.14f, -.14f, 0f, -1f) // F: lib/ui/kit/scenes/setup_cast.dart:80-81
        close()
    }
    withTransform({ translate(at.x, at.y); scale(r, r, Offset.Zero) }) { drawPath(p, color) }
}
