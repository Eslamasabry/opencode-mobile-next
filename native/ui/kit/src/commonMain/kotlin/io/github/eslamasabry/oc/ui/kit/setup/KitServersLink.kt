package io.github.eslamasabry.oc.ui.kit.setup

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitMotion
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.motion.CurveId
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.motion.KitEasing
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

public enum class KitServersLinkState { Idle, Linking, Linked, Failed }

/** ServersLinkScene + servers_cast.dart. The design box mirrors as a whole; every color follows KitPalette. */
@Composable
public fun KitServersLink(state: KitServersLinkState, modifier: Modifier = Modifier, width: Dp = FlutterKitTokens.ILLUSTRATION_PAGE.dp) {
    val colors = LocalKitColors.current
    val motion = KitMotion.state
    val celebrations = LocalKitAppearance.current.celebrations
    val moved = remember { booleanArrayOf(false) }
    if (state != KitServersLinkState.Idle) moved[0] = true
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val entrance = remember(state) { Animatable(if (motion.reduced || motion.stillFrame) 1f else 0f) }
    val loop = remember(state) { Animatable(0f) }
    LaunchedEffect(state, motion, celebrations) {
        if (motion.reduced || motion.stillFrame || (state == KitServersLinkState.Linked && !celebrations)) entrance.snapTo(1f)
        else entrance.animateTo(1f, tween(if (state == KitServersLinkState.Linked) FlutterKitMotion.CELEBRATION_MS else FlutterKitMotion.ENTRANCE_MS, easing = LinearEasing))
        if (state == KitServersLinkState.Linking && motion.ambientLoops) {
            while (true) {
                loop.snapTo(0f)
                loop.animateTo(1f, tween(FlutterKitMotion.BREATH_MS, easing = LinearEasing))
            }
        }
    }
    Canvas(modifier.width(width).aspectRatio(200f / 84f).clearAndSetSemantics {}) { // F: lib/ui/kit/scenes/servers_link_scene.dart:47
        val t = entrance.value
        fun interval(from: Float, to: Float, land: Boolean = false): Float = when {
            t <= from -> 0f
            t >= to -> 1f
            else -> (if (land) CubicBezierEasing(CurveId.Land.x1, CurveId.Land.y1, CurveId.Land.x2, CurveId.Land.y2) else KitEasing.Enter).transform((t - from) / (to - from))
        }
        val wash = colors.accent.copy(alpha = FlutterKitTokens.SCENE_WASH_ALPHA)
        withTransform({
            scale(size.width / 200f, size.height / 84f, Offset.Zero) // F: lib/ui/kit/scenes/servers_link_scene.dart:47
            if (rtl) { translate(200f, 0f); scale(-1f, 1f, Offset.Zero) } // F: lib/ui/kit/scenes/servers_link_scene.dart:47
        }) {
            val phone = Rect(26f, 18f, 54f, 66f) // F: lib/ui/kit/scenes/servers_link_scene.dart:58
            val laptop = Rect(112f, 16f, 168f, 56f) // F: lib/ui/kit/scenes/servers_link_scene.dart:59
            val first = state == KitServersLinkState.Idle && !moved[0]
            val phoneT = if (first) interval(0f, .55f) else 1f // F: lib/ui/kit/scenes/servers_link_scene.dart:101
            val laptopT = if (first) interval(.12f, .65f) else 1f // F: lib/ui/kit/scenes/servers_link_scene.dart:104
            fun staged(value: Float, from: Float, to: Float): Float = if (value <= from) 0f else if (value >= to) 1f else KitEasing.Enter.transform((value - from) / (to - from))
            val phoneRadius = phone.width * .25f // F: lib/ui/kit/scenes/servers_cast.dart:16
            drawRoundRect(colors.surface3.copy(alpha = staged(phoneT, .3f, 1f)), phone.topLeft, phone.size, CornerRadius(phoneRadius)) // F: lib/ui/kit/scenes/servers_cast.dart:43-46
            segment(Path().apply { addRoundRect(RoundRect(phone, CornerRadius(phoneRadius))) }, staged(phoneT, 0f, .8f), colors.text2, 4f) // F: lib/ui/kit/scenes/servers_cast.dart:48-54
            segment(Path().apply { moveTo(phone.center.x - phone.width * .14f, phone.top + phone.height * .1f); lineTo(phone.center.x + phone.width * .14f, phone.top + phone.height * .1f) }, staged(phoneT, .55f, 1f), colors.text3) // F: lib/ui/kit/scenes/servers_cast.dart:18-22,54-59
            listOf(.34f to .5f, .48f to .34f, .62f to .44f).forEachIndexed { i, (y, width) -> // F: lib/ui/kit/scenes/servers_cast.dart:25
                segment(Path().apply { moveTo(phone.left + phone.width * .24f, phone.top + phone.height * y); lineTo(phone.left + phone.width * (.24f + width), phone.top + phone.height * y) }, staged(phoneT, .6f + i * .1f, .9f + i * .03f), if (state == KitServersLinkState.Linked) colors.accent else colors.text3, 3f) // F: lib/ui/kit/scenes/servers_cast.dart:25-30,61-66 lib/ui/kit/kit_illustration.dart:167
            }
            val laptopRadius = laptop.height * .14f // F: lib/ui/kit/scenes/servers_cast.dart:77
            drawRoundRect(colors.surface3.copy(alpha = staged(laptopT, .3f, 1f)), laptop.topLeft, laptop.size, CornerRadius(laptopRadius)) // F: lib/ui/kit/scenes/servers_cast.dart:95-97
            segment(Path().apply { addRoundRect(RoundRect(laptop, CornerRadius(laptopRadius))) }, staged(laptopT, 0f, .7f), colors.text2, 4f) // F: lib/ui/kit/scenes/servers_cast.dart:99-103
            segment(Path().apply { moveTo(laptop.left - laptop.width * .12f, laptop.bottom + 9f); lineTo(laptop.right + laptop.width * .12f, laptop.bottom + 9f) }, staged(laptopT, .35f, .9f), colors.text2, 4f) // F: lib/ui/kit/scenes/servers_cast.dart:80-81,105-109
            segment(Path().apply { moveTo(laptop.center.x - laptop.width * .12f, laptop.bottom + 9f); lineTo(laptop.center.x - laptop.width * .08f, laptop.bottom + 12.5f); lineTo(laptop.center.x + laptop.width * .08f, laptop.bottom + 12.5f); lineTo(laptop.center.x + laptop.width * .12f, laptop.bottom + 9f) }, staged(laptopT, .6f, 1f), colors.text3) // F: lib/ui/kit/scenes/servers_cast.dart:83-86,114
            if (state == KitServersLinkState.Linked) drawRoundRect(wash.copy(alpha = wash.alpha * interval(.3f, .8f)), Offset(laptop.left + 4f, laptop.top + 4f), Size(laptop.width - 8f, laptop.height - 8f), CornerRadius(3f)) // F: lib/ui/kit/scenes/servers_link_scene.dart:107-113
            val portalT = if (first) interval(.45f, .9f) else 1f // F: lib/ui/kit/scenes/servers_link_scene.dart:122
            withTransform({ translate(140f, 36f); scale(22f / 60f, 22f / 60f, Offset.Zero); translate(-54f, -54f) }) { // F: lib/ui/kit/scenes/servers_link_scene.dart:60,120 lib/ui/kit/scenes/servers_cast.dart:150-154
                val ink = if (state == KitServersLinkState.Failed) colors.text2 else colors.accent
                val upper = Path().apply { moveTo(61f,31f); lineTo(43f,31f); arcTo(Rect(31f,31f,55f,55f), -90f, -90f, false); lineTo(31f,64f) } // F: lib/ui/kit/scenes/servers_cast.dart:123-131
                val lower = Path().apply { moveTo(77f,44f); lineTo(77f,65f); arcTo(Rect(53f,53f,77f,77f), 0f, 90f, false); lineTo(44f,77f) } // F: lib/ui/kit/scenes/servers_cast.dart:133-137
                segment(upper, staged(portalT, 0f, .7f), ink, 10f) // F: lib/ui/kit/scenes/servers_cast.dart:156-157
                segment(lower, staged(portalT, .25f, 1f), ink, 10f) // F: lib/ui/kit/scenes/servers_cast.dart:158
            }
            val link = Path().apply { moveTo(62f,42f); quadraticTo(83f,20f,104f,38f) } // F: lib/ui/kit/scenes/servers_link_scene.dart:62-66
            val metric = PathMeasure().apply { setPath(link, false) }
            val middle = metric.getPosition(metric.length / 2) // F: lib/ui/kit/scenes/servers_link_scene.dart:75-77
            when (state) {
                KitServersLinkState.Idle -> {
                    var distance = 0f
                    while (distance <= metric.length) {
                        drawCircle(colors.text3.copy(alpha = interval(if (first) .7f else 0f,1f)), 1.6f, metric.getPosition(distance)) // F: lib/ui/kit/scenes/servers_link_scene.dart:127-130
                        distance += 7f // F: lib/ui/kit/scenes/servers_link_scene.dart:87
                    }
                }
                KitServersLinkState.Linking -> {
                    segment(link, interval(0f,.8f), wash, 9f) // F: lib/ui/kit/scenes/servers_link_scene.dart:134-139
                    segment(link, interval(0f,.8f), colors.accent)
                    val along = if (motion.ambientLoops) loop.value else .5f // NV: a reduced-motion still dot rests midway, as Flutter
                    val fade = if (along < .15f) along / .15f else if (along > .85f) (1 - along) / .15f else 1f // F: lib/ui/kit/scenes/servers_link_scene.dart:151-155
                    drawCircle(colors.accent.copy(alpha = fade), 4f * interval(.6f,1f), metric.getPosition(metric.length * along)) // F: lib/ui/kit/scenes/servers_link_scene.dart:158,163-165
                }
                KitServersLinkState.Linked -> {
                    segment(link, interval(0f,.35f), colors.accent, 4f) // F: lib/ui/kit/scenes/servers_link_scene.dart:172-173
                    val land = interval(.25f,.7f, land = true) // F: lib/ui/kit/scenes/servers_link_scene.dart:175
                    drawCircle(colors.accent, 6.5f * land, middle) // F: lib/ui/kit/scenes/servers_link_scene.dart:177
                    drawCircle(wash, 11f * land, middle, style = Stroke(3f)) // F: lib/ui/kit/scenes/servers_link_scene.dart:180-181
                    val rays = interval(.35f,1f, land = true) // F: lib/ui/kit/scenes/servers_link_scene.dart:186
                    repeat(5) { i -> // F: lib/ui/kit/scenes/servers_link_scene.dart:192
                        val angle = -PI * (5.0 / 6) + i * PI / 6 // F: lib/ui/kit/scenes/servers_link_scene.dart:194
                        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                        val start = 13f + 3f * rays // F: lib/ui/kit/scenes/servers_link_scene.dart:196
                        drawLine(colors.accent.copy(alpha = .85f), middle + direction * start, middle + direction * (start + 5f * rays), 2.5f, StrokeCap.Round) // F: lib/ui/kit/scenes/servers_link_scene.dart:189,196-199 lib/ui/kit/kit_illustration.dart:167
                    }
                }
                KitServersLinkState.Failed -> {
                    val apart = interval(.35f,1f) * 2.5f // F: lib/ui/kit/scenes/servers_link_scene.dart:206
                    listOf(0f to .4f, .6f to 1f).forEachIndexed { index, (from, to) -> // F: lib/ui/kit/scenes/servers_link_scene.dart:70-73
                        val dx = if (index == 0) -apart else apart
                        val piece = Path().apply { metric.getSegment(metric.length * from, metric.length * to, this) }
                        withTransform({ translate(dx, apart * .4f) }) { segment(piece, interval(0f,.5f), colors.text2, 3f) } // F: lib/ui/kit/scenes/servers_link_scene.dart:205-214 lib/ui/kit/kit_illustration.dart:167
                        val end = metric.getPosition(metric.length * if (index == 0) to else from)
                        drawCircle(colors.text1.copy(alpha = interval(.45f,1f)), 3.2f, end + Offset(dx, apart * .4f)) // F: lib/ui/kit/scenes/servers_link_scene.dart:216-220
                    }
                }
            }
        }
    }
}

private fun DrawScope.segment(path: Path, amount: Float, color: Color, width: Float = 2.5f) { // F: lib/ui/kit/kit_illustration.dart:167
    if (amount <= 0) return
    val measure = PathMeasure().apply { setPath(path, false) }
    val shown = if (amount >= 1) path else Path().apply { measure.getSegment(0f, measure.length * amount, this) }
    drawPath(shown, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
