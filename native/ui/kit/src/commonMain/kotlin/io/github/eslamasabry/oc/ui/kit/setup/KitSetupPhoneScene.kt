package io.github.eslamasabry.oc.ui.kit.setup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * SetupPhoneScene and SetupReadyScene: one entrance, active-only waiting, and a finite celebration.
 */
@Composable
public fun KitSetupPhone(
    mood: KitSetupPhoneMood = KitSetupPhoneMood.Fresh,
    modifier: Modifier = Modifier,
) {
    val c = LocalKitColors.current
    val celebration = mood == KitSetupPhoneMood.Celebration
    val f = setupSceneFrame(mood == KitSetupPhoneMood.Starting, celebration = celebration)
    val stopped = mood == KitSetupPhoneMood.Stopped
    Canvas(
        modifier
            .widthIn(max = 248.dp) // F: lib/ui/screens/phone_setup/phone_setup_hero.dart:65
            .fillMaxWidth()
            .aspectRatio(160f / 120f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:35
            .clearAndSetSemantics {}
    ) { // F: lib/ui/screens/phone_setup/phone_setup_hero.dart:65
        // lib/ui/kit/scenes/setup_phone_scene.dart:35
        withTransform({
            scale(size.width / 160f, size.height / 120f, Offset.Zero) // F: lib/ui/kit/scenes/setup_phone_scene.dart:35
        }) { // F: lib/ui/kit/scenes/setup_phone_scene.dart:35
            clipRect(0f, 0f, 160f, 120f) { // F: lib/ui/kit/scenes/setup_phone_scene.dart:35
                val wash = f.interval(0f, .4f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:73
                val soft = c.accent.copy(alpha = FlutterKitTokens.SCENE_WASH_ALPHA)
                drawCircle(
                    (if (stopped) c.text3.faded(FlutterKitTokens.SCENE_WASH_ALPHA) else soft).faded(
                        wash
                    ),
                    (50 + if (celebration) 0f else 3 * f.breath) * (.7f + .3f * wash), // F: lib/ui/kit/scenes/setup_phone_scene.dart:41,77
                    Offset(80f, 60f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:41,76
                ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:41,76
                if (mood == KitSetupPhoneMood.Starting && f.looping) {
                    drawCircle(
                        c.accent,
                        2.6f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:41,88,89
                        phoneOrbit(Offset(80f, 60f), 56f, f.loop), // F: lib/ui/kit/scenes/setup_phone_scene.dart:41,88,89
                    ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:41,88,89
                    drawCircle(
                        c.accent.faded(.45f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:41,91-94
                        1.8f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:41,91-94
                        phoneOrbit(Offset(80f, 60f), 56f, f.loop - .04f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:41,91-94
                    ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:41,91-94
                }
                val lit =
                    if (celebration) f.interval(.1f, .35f) // F: lib/ui/kit/scenes/setup_ready_scene.dart:58
                    else
                        f.interval(
                            .25f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:98
                            .55f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:98
                        ) // F: lib/ui/kit/scenes/setup_ready_scene.dart:58
                          // lib/ui/kit/scenes/setup_phone_scene.dart:99
                val screen =
                    Rect(61f, 17f, 99f, 101f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:43
                drawRoundRect(
                    c.surface3.faded(lit),
                    screen.topLeft,
                    screen.size,
                    CornerRadius(5f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:47
                ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:47
                if (!stopped)
                    drawRoundRect(
                        soft.faded(lit * if (celebration) 1.4f else .9f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:47,107 lib/ui/kit/scenes/setup_ready_scene.dart:58,63,69,74
                        screen.topLeft,
                        screen.size,
                        CornerRadius(5f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:47
                    ) // F: lib/ui/kit/scenes/setup_ready_scene.dart:63
                      // lib/ui/kit/scenes/setup_phone_scene.dart:47,108
                scenePath(
                    Path().apply {
                        addRoundRect(RoundRect(Rect(54f, 8f, 106f, 112f), CornerRadius(12f))) // F: lib/ui/kit/scenes/setup_phone_scene.dart:42,44,116
                    },
                    if (celebration) f.interval(0f, .35f) else f.interval(.05f, .5f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:114 lib/ui/kit/scenes/setup_ready_scene.dart:58,63,69,74
                    if (stopped) c.text2 else c.text1,
                ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:42,44,116
                  // lib/ui/kit/scenes/setup_ready_scene.dart:69
                val home =
                    if (celebration) f.interval(.3f, .4f) // F: lib/ui/kit/scenes/setup_ready_scene.dart:74
                    else
                        f.interval(
                            .45f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:118 lib/ui/kit/scenes/setup_ready_scene.dart:58,63,69,74
                            .6f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:118 lib/ui/kit/scenes/setup_ready_scene.dart:58,63,69,74
                        ) // F: lib/ui/kit/scenes/setup_ready_scene.dart:74
                          // lib/ui/kit/scenes/setup_phone_scene.dart:120
                drawLine(
                    c.text2.faded(.8f * if (celebration) lit else 1f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:123-125
                    Offset(74f, 106.5f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:121
                    Offset(74f + 12 * home, 106.5f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:122
                    2f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:123-125
                    StrokeCap.Round,
                ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:123-125
                if (celebration) {
                    val drawn =
                        f.interval(.25f, .5f) // F: lib/ui/kit/scenes/setup_ready_scene.dart:79
                    scenePortal(
                        Offset(80f, 58f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:20,80-90
                        34f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:20,80-90
                        c.accent,
                        drawn,
                        drawn,
                        -3f + 5.5f * f.interval(.45f, .8f, land = true), // F: lib/ui/kit/scenes/setup_ready_scene.dart:20,80-90
                        4.5f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:20,80-90
                    ) // F: lib/ui/kit/scenes/setup_ready_scene.dart:20,80-90
                    scenePath(
                        Path().apply {
                            moveTo(
                                80f - 15f * .36f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:20,29 lib/ui/kit/scenes/setup_cast.dart:109
                                58f + 15f * .02f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:20,29 lib/ui/kit/scenes/setup_cast.dart:109
                            ) // F: lib/ui/kit/scenes/setup_ready_scene.dart:20,29
                              // lib/ui/kit/scenes/setup_cast.dart:109
                            lineTo(
                                80f - 15f * .1f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:20,29 lib/ui/kit/scenes/setup_cast.dart:110
                                58f + 15f * .28f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:20,29 lib/ui/kit/scenes/setup_cast.dart:110
                            ) // F: lib/ui/kit/scenes/setup_ready_scene.dart:20,29
                              // lib/ui/kit/scenes/setup_cast.dart:110
                            lineTo(
                                80f + 15f * .38f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:20,29 lib/ui/kit/scenes/setup_cast.dart:111
                                58f - 15f * .24f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:20,29 lib/ui/kit/scenes/setup_cast.dart:111
                            ) // F: lib/ui/kit/scenes/setup_ready_scene.dart:20,29
                              // lib/ui/kit/scenes/setup_cast.dart:111
                        },
                        f.interval(.6f, .85f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:94,95
                        c.accent,
                        3.5f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:95,96
                    ) // F: lib/ui/kit/scenes/setup_ready_scene.dart:95,96
                    val burst =
                        f.interval(.55f, 1f) // F: lib/ui/kit/scenes/setup_ready_scene.dart:99
                    if (burst > 0 && burst < 1)
                        repeat(8) { i -> // F: lib/ui/kit/scenes/setup_ready_scene.dart:33
                            val angle =
                                i * PI / 4 + .3 // F: lib/ui/kit/scenes/setup_ready_scene.dart:34
                            val ray = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                            val inner =
                                34 + 26 * burst // F: lib/ui/kit/scenes/setup_ready_scene.dart:106
                            val outer =
                                inner +
                                    8 * // F: lib/ui/kit/scenes/setup_ready_scene.dart:107
                                        (1 -
                                            burst *
                                                .6f) // F: lib/ui/kit/scenes/setup_ready_scene.dart:107
                                                     // lib/ui/kit/scenes/setup_ready_scene.dart:108
                            drawLine(
                                c.accent.faded(1 - burst),
                                Offset(80f + ray.x * inner * 1.2f, 60f + ray.y * inner), // F: lib/ui/kit/scenes/setup_ready_scene.dart:19,110,111
                                Offset(80f + ray.x * outer * 1.2f, 60f + ray.y * outer), // F: lib/ui/kit/scenes/setup_ready_scene.dart:19,110,111
                                2.5f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:19,109,110 lib/ui/kit/kit_illustration.dart:167
                                StrokeCap.Round,
                            ) // F: lib/ui/kit/scenes/setup_ready_scene.dart:19,110,111
                              // lib/ui/kit/kit_illustration.dart:167
                        }
                    phoneSparkle(
                        Offset(126f, 30f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:119-121
                        10 * f.interval(.6f, .9f, land = true), // F: lib/ui/kit/scenes/setup_ready_scene.dart:117,119
                        c.accent,
                    ) // F: lib/ui/kit/scenes/setup_ready_scene.dart:119-121
                    phoneSparkle(
                        Offset(34f, 50f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:122
                        6 * f.interval(.7f, 1f, land = true), // F: lib/ui/kit/scenes/setup_ready_scene.dart:118,123
                        c.accent.faded(.8f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:120,124-126
                    ) // F: lib/ui/kit/scenes/setup_ready_scene.dart:120,124-126
                    phoneSparkle(
                        Offset(118f, 88f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:129
                        4.5f * f.interval(.7f, 1f, land = true), // F: lib/ui/kit/scenes/setup_ready_scene.dart:118,130
                        c.accent.faded(.6f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:120,131-133
                    ) // F: lib/ui/kit/scenes/setup_ready_scene.dart:120,131-133
                    val confetti =
                        listOf(
                            Triple(Offset(28f, 30f), .6f, false), // F: lib/ui/kit/scenes/setup_ready_scene.dart:39-43
                            Triple(Offset(136f, 72f), -.5f, false), // F: lib/ui/kit/scenes/setup_ready_scene.dart:39-43
                            Triple(Offset(124f, 100f), .9f, true), // F: lib/ui/kit/scenes/setup_ready_scene.dart:39-43
                            Triple(Offset(24f, 76f), -.8f, true), // F: lib/ui/kit/scenes/setup_ready_scene.dart:39-43
                            Triple(Offset(140f, 20f), .2f, true), // F: lib/ui/kit/scenes/setup_ready_scene.dart:39-43
                        ) // F: lib/ui/kit/scenes/setup_ready_scene.dart:39-43
                    confetti.forEachIndexed { i, (at, angle, muted) ->
                        val pop =
                            f.interval(
                                .6f + i * .05f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:137,138
                                .85f + i * .03f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:137,138
                                land = true,
                            ) // F: lib/ui/kit/scenes/setup_ready_scene.dart:139,140
                        val delta =
                            Offset(cos(angle), sin(angle)) *
                                3.5f * // F: lib/ui/kit/scenes/setup_ready_scene.dart:142
                                pop // F: lib/ui/kit/scenes/setup_ready_scene.dart:144
                        if (pop > 0)
                            drawLine(
                                if (muted) c.text2.faded(.6f) else c.accent.faded(.9f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:147
                                at - delta,
                                at + delta,
                                2.5f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:147 lib/ui/kit/kit_illustration.dart:167
                                StrokeCap.Round,
                            ) // F: lib/ui/kit/scenes/setup_ready_scene.dart:149
                              // lib/ui/kit/kit_illustration.dart:167
                    }
                } else {
                    if (!stopped && mood != KitSetupPhoneMood.Ready) {
                        scenePath(
                            Path().apply {
                                moveTo(66f, 26f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:49,174
                                lineTo(66f + 8f / 2, 26f + 8f / 2) // F: lib/ui/kit/scenes/setup_phone_scene.dart:49 lib/ui/kit/kit_illustration.dart:167 lib/ui/kit/scenes/setup_cast.dart:104
                                lineTo(66f, 26f + 8f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:49,174
                            },
                            f.interval(.4f, .55f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:168 lib/ui/kit/kit_illustration.dart:167
                            c.accent,
                            2.5f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:49,174 lib/ui/kit/kit_illustration.dart:167
                        ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:49,174
                          // lib/ui/kit/scenes/setup_cast.dart:102-105
                          // lib/ui/kit/kit_illustration.dart:167
                        if (
                            mood == KitSetupPhoneMood.Termux ||
                                !f.looping ||
                                (f.loop * 4).toInt() % 2 == 0 // NV: SetupCast.cursorOn at setup_cast.dart:115 uses even quarters of the loop
                        )
                            drawLine(
                                c.accent,
                                Offset(73f, 34f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:174
                                Offset(73 + 7 * f.interval(.5f, .62f), 34f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:171,175
                                2.5f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:176 lib/ui/kit/kit_illustration.dart:167
                                StrokeCap.Round,
                            ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:177-181
                              // lib/ui/kit/kit_illustration.dart:167
                    }
                    when (mood) {
                        KitSetupPhoneMood.Termux -> {
                            val termLines = listOf(44f to 22f, 53f to 14f, 62f to 26f, 71f to 18f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:57-60
                            termLines.forEachIndexed {
                                i,
                                (y, length) -> // F: lib/ui/kit/scenes/setup_phone_scene.dart:57-60
                                val drawn =
                                    f.interval(
                                        .5f + i * .1f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:186
                                        .7f + i * .1f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:186
                                    ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:193
                                if (drawn > 0)
                                    drawLine(
                                        if (i == termLines.lastIndex) c.accent else c.text2.faded(.7f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:57-60,192
                                        Offset(66f, y), // F: lib/ui/kit/scenes/setup_phone_scene.dart:57-60,200
                                        Offset(66 + length * drawn, y), // F: lib/ui/kit/scenes/setup_phone_scene.dart:57-60,200
                                        2.5f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:57-60,193 lib/ui/kit/kit_illustration.dart:167
                                        StrokeCap.Round,
                                    ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:57-60,200
                                      // lib/ui/kit/kit_illustration.dart:167
                                if (i == termLines.lastIndex && drawn >= 1) // F: lib/ui/kit/scenes/setup_phone_scene.dart:57-60,196
                                    drawCircle(
                                        c.accent,
                                        2.4f * f.interval(.88f, 1f, land = true), // F: lib/ui/kit/scenes/setup_phone_scene.dart:197,200
                                        Offset(66 + length + 6, y), // F: lib/ui/kit/scenes/setup_phone_scene.dart:57-60,199
                                    ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:57-60,206-209
                            }
                        }
                        KitSetupPhoneMood.Stopped -> {
                            scenePortal(
                                Offset(80f, 60f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:140-146
                                30f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:140-146
                                c.text2.faded(.7f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:140-146
                                f.interval(.45f, .8f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:140-146
                                f.interval(.55f, .9f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:140-146
                                -2f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:140-146
                                4f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:140-146
                            ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:140-146
                            val moon =
                                Path.combine(
                                    PathOperation.Difference,
                                    Path().apply {
                                        addOval(
                                            Rect(Offset(128f - 9f, 26f - 9f), Size(9f * 2, 9f * 2)) // NV: circle bounding rect from setup_phone_scene.dart:52
                                        )
                                    }, // NV: circle bounding rect from setup_phone_scene.dart:52
                                       // centre/radius
                                    Path().apply {
                                        addOval(
                                            Rect(Offset(133f - 8f, 21f - 8f), Size(8f * 2, 8f * 2)) // NV: circle bounding rect from setup_phone_scene.dart:53
                                        )
                                    },
                                ) // NV: circle bounding rect from setup_phone_scene.dart:53
                                  // centre/radius
                            drawPath(
                                moon,
                                c.text2.faded(.8f * f.interval(.7f, 1f)), // F: lib/ui/kit/scenes/setup_phone_scene.dart:148,152
                            ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:148,152
                        }
                        else -> {
                            val ready = mood == KitSetupPhoneMood.Ready
                            val center =
                                if (ready) Offset(80f, 58f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:133,219
                                else
                                    Offset(
                                        80f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:133,219
                                        66f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:133,212
                                    ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:133,219
                            val begin =
                                if (ready) .4f // F: lib/ui/kit/scenes/setup_phone_scene.dart:131,133
                                else .5f // F: lib/ui/kit/scenes/setup_phone_scene.dart:131,133
                            scenePortal(
                                center,
                                if (ready) 34f else 30f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:219
                                c.accent,
                                f.interval(begin, begin + .3f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:226,228-231
                                f.interval(begin + .1f, begin + .4f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:222
                                1.6f * f.breath, // F: lib/ui/kit/scenes/setup_phone_scene.dart:223
                                4.5f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:224
                            ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:226,228-231
                            drawCircle(
                                c.accent,
                                3.2f * f.interval(begin + .3f, 1f, land = true), // F: lib/ui/kit/scenes/setup_phone_scene.dart:226,231
                                if (f.looping) phoneOrbit(center, 2.4f, f.loop) else center, // F: lib/ui/kit/scenes/setup_phone_scene.dart:229
                            ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:233,236,238
                        }
                    }
                    if (!stopped) {
                        phoneSparkle(
                            Offset(126f, 26f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:242
                            (9 + 1.2f * f.breath) * f.interval(.7f, .95f, land = true), // F: lib/ui/kit/scenes/setup_phone_scene.dart:238,243
                            c.accent,
                        ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:245,249,250
                        phoneSparkle(
                            Offset(138f, 46f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:249
                            4.5f * f.interval(.8f, 1f, land = true), // F: lib/ui/kit/scenes/setup_phone_scene.dart:239,250
                            c.accent.faded(.8f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:251
                        ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:246,256-258
                        phoneSparkle(
                            Offset(32f, 90f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:256
                            5 * f.interval(.8f, 1f, land = true), // F: lib/ui/kit/scenes/setup_phone_scene.dart:239,257
                            c.accent.faded(.55f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:258
                        ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:246,263-265
                    }
                    val dots =
                        c.text2.faded(
                            (if (stopped) .35f else .5f) * f.interval(.6f, 1f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:265,268
                        ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:272,276
                    drawCircle(
                        dots,
                        2.2f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:270
                        Offset(34f, 34f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:270
                    ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:278
                    drawCircle(
                        dots,
                        1.6f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:271
                        Offset(22f, 62f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:271
                    ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:279
                    drawCircle(
                        dots,
                        2.4f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:272
                        Offset(132f, 94f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:272
                    ) // F: lib/ui/kit/scenes/setup_phone_scene.dart:280
                }
            }
        }
    }
}

private fun phoneOrbit(at: Offset, radius: Float, turns: Float): Offset {
    val angle = turns * 2 * PI - PI / 2 // F: lib/ui/kit/scenes/setup_cast.dart:120
    return at + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius
}

private fun DrawScope.phoneSparkle(at: Offset, radius: Float, color: Color) {
    if (radius <= 0) return
    val p =
        Path().apply {
            moveTo(0f, -1f)
            quadraticTo(.14f, -.14f, 1f, 0f) // F: lib/ui/kit/scenes/setup_cast.dart:74-77
            quadraticTo(.14f, .14f, 0f, 1f) // F: lib/ui/kit/scenes/setup_cast.dart:74-77
            quadraticTo(-.14f, .14f, -1f, 0f) // F: lib/ui/kit/scenes/setup_cast.dart:74-77
            quadraticTo(-.14f, -.14f, 0f, -1f) // F: lib/ui/kit/scenes/setup_cast.dart:74-77
            close() // F: lib/ui/kit/scenes/setup_cast.dart:74-77
        }
    withTransform({
        translate(at.x, at.y)
        scale(radius, radius, Offset.Zero)
    }) {
        drawPath(p, color)
    }
}
