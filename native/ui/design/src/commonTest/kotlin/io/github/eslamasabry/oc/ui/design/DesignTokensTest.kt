package io.github.eslamasabry.oc.ui.design

import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.design.color.argb
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.effects.FrostConditions
import io.github.eslamasabry.oc.ui.design.effects.FrostHold
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.effects.FrostRole
import io.github.eslamasabry.oc.ui.design.effects.FrostStrength
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionSignals
import io.github.eslamasabry.oc.ui.design.motion.resolveMotion
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.tokens.Corner
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Shapes
import io.github.eslamasabry.oc.ui.design.tokens.Tokens
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.type.TypeScale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesignTokensTest {
    @Test
    fun `B-KIT-003 Graphite keeps the contrast floors in both brightnesses`() {
        for (r in listOf(Graphite.dark, Graphite.light)) {
            val surfaces = listOf(r.ground, r.surface1, r.surface2, r.surface3)
            for (s in surfaces) assertTrue(wcagContrast(r.text1, s) >= 7.0, "text1 on $s (${r.dark})")
            for (s in surfaces) {
                assertTrue(wcagContrast(r.text2, s) >= 4.5, "text2 on $s (${r.dark})")
                assertTrue(wcagContrast(r.text3, s) >= 4.5, "text3 on $s (${r.dark})")
            }
            assertTrue(wcagContrast(r.accent, r.ground) >= 4.5)
            assertTrue(wcagContrast(r.accent, r.surface1) >= 4.5)
            assertTrue(wcagContrast(r.onAccent, r.accent) >= 4.5)
            assertTrue(wcagContrast(r.attention, r.ground) >= 4.5)
        }
    }

    @Test
    fun `B-KIT-056 no role is red`() {
        for (r in listOf(Graphite.dark, Graphite.light)) {
            val all = listOf(r.ground, r.surface1, r.surface2, r.surface3, r.text1, r.text2, r.text3, r.accent,
                r.onAccent, r.partner, r.onPartner, r.attention, r.attentionFill, r.success, r.codeKeyword,
                r.codeString, r.codeType)
            for (c in all) assertTrue(!(c.red > 180 && c.green < 90 && c.blue < 90), "red-looking role $c")
        }
    }

    @Test
    fun `B-KIT-008 Saver puts dark roles on true black and leaves light as is`() {
        val s = Graphite.dark.saver
        assertEquals(argb(0xFF000000), s.ground)
        assertEquals(lerp(Graphite.dark.surface1, argb(0xFF000000), 0.3f), s.surface1)
        assertEquals(Graphite.dark.text1, s.text1)
        assertEquals(Graphite.light, Graphite.light.saver)
    }

    @Test
    fun `alpha blend matches Flutter for the opaque outline`() {
        // hairline (white 7 %) over surface1 #141518 → about #252528.
        val flat = Graphite.dark.hairline.over(Graphite.dark.surface1)
        assertEquals(0xFF, flat.alpha)
        assertTrue(abs(flat.red - 0x25) <= 1 && abs(flat.blue - 0x28) <= 1, "got $flat")
    }

    @Test
    fun `B-KIT-052 the ten type roles have the code values`() {
        assertEquals(28, TypeScale.of(TypeRole.LargeTitle).sizeSp)
        assertEquals(34, TypeScale.of(TypeRole.LargeTitle).lineHeightSp)
        assertEquals(-0.28f, TypeScale.of(TypeRole.LargeTitle).trackingSp)
        assertEquals(400, TypeScale.of(TypeRole.Body).weight)
        assertEquals(24, TypeScale.of(TypeRole.Body).lineHeightSp)
        assertEquals(20, TypeScale.of(TypeRole.Button).lineHeightSp)
        assertEquals(19, TypeScale.of(TypeRole.Mono).lineHeightSp)
        assertEquals(0f, TypeScale.forLocale(TypeRole.Headline, arabic = true).trackingSp)
        assertEquals(10, TypeRole.entries.size)
    }

    @Test
    fun `B-KIT-038 compact steps stay on the 4 dp scale and targets never shrink`() {
        val c = Tokens.Compact
        assertEquals(48, c.buttonHeight)
        assertEquals(48, c.rowHeightOne)
        assertEquals(56, c.rowHeightTwo)
        assertEquals(16, c.sectionGap)
        assertEquals(4, c.labelGap)
        assertEquals(48, c.minTarget)
        assertEquals(Corner.Radius(30), Shapes.of(KitShapeId.Sheet))
        assertEquals(Corner.Full, Shapes.of(KitShapeId.Pill))
    }

    @Test
    fun `B-KIT-023 motion resolves Off, Calm and Full`() {
        val calm = MotionSignals(systemScale = 1f, powerSave = false, severeHeat = false)
        assertEquals(MotionLevel.Off, resolveMotion(calm.copy(systemScale = 0f), MotionLevel.Full, saver = false))
        assertEquals(MotionLevel.Off, resolveMotion(calm, MotionLevel.Full, saver = true))
        assertEquals(MotionLevel.Calm, resolveMotion(calm.copy(powerSave = true), MotionLevel.Full, saver = false))
        assertEquals(MotionLevel.Calm, resolveMotion(calm.copy(severeHeat = true), MotionLevel.Full, saver = false))
        assertEquals(MotionLevel.Full, resolveMotion(calm, MotionLevel.Full, saver = false))
    }

    @Test
    fun `B-KIT-018 frost strengths map to blur, saturation and tint, and holds keep it solid`() {
        val ok = FrostConditions()
        val composer = assertNotNull(FrostPolicy.spec(FrostRole.Composer, FrostStrength.Balanced, dark = false, ok))
        assertEquals(10f, composer.sigmaDp)
        assertEquals(1.4f, composer.saturation)
        assertEquals(0.70f, composer.tintAlpha)
        val header = assertNotNull(FrostPolicy.spec(FrostRole.ChatHeader, FrostStrength.Strong, dark = true, ok))
        assertEquals(0.75f, header.tintAlpha, 1e-6f)
        assertNull(FrostPolicy.spec(FrostRole.Composer, FrostStrength.Off, false, ok))
        assertNull(FrostPolicy.spec(FrostRole.Composer, FrostStrength.Weak, false, ok.copy(highContrast = true)))
        assertEquals(FrostHold.Saver, FrostPolicy.heldBy(FrostConditions(saver = true, reducedMotion = true)))
        assertEquals(FrostHold.Style, FrostPolicy.heldBy(FrostConditions(styleAllows = false)))
        assertEquals(FrostStrength.Balanced, FrostStrength.fromStorage("crystal"))
        // Android/Skia radius → sigma: 0.57735 × r + 0.5 = sigma px.
        val r = FrostPolicy.blurRadiusPx(10f, 2.625f)
        assertEquals(26.25f, 0.57735f * r + 0.5f, 1e-3f)
    }

    @Test
    fun `B-KIT-059 frame scale caps at 1_3, reading at 2_5, and the large-text test uses 1_29`() {
        assertEquals(20.8f, TextScalePolicy.frameDp(16f, platformDp = 28f), 1e-4f) // Android 16 sp at 200 %
        assertEquals(25.0f, TextScalePolicy.frameDp(22f, platformDp = 25f)) // Android 22 sp at 130 %: kept
        assertEquals(1.3f, TextScalePolicy.frameScale(2.0f))
        assertEquals(1.15f, TextScalePolicy.frameScale(1.15f))
        assertEquals(1.3f, TextScalePolicy.frameScale(1.2f, AppTextSize.Large))
        assertEquals(2.5f, TextScalePolicy.readingScale(2.4f, AppTextSize.Large))
        assertTrue(TextScalePolicy.isLargeText(1.2999999523f))
        assertEquals(2.5f, TextScalePolicy.ROOT_CAP)
    }
}
