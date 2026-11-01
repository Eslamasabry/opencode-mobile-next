package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Plan 08 §5.3 `GlowColoursTest`: the one-colour partner is Flutter's straight sRGB `Color.lerp(accent, text1, t)` with
 * t = 0.55 in dark and 0.30 in light — the hue the owner likes — and it is never amber.
 */
class GlowColoursTest {
    private val accent = Color(0xFF2FB36A) // a green accent, as the owner's
    private val darkText = Color(0xFFECEDEE)
    private val lightText = Color(0xFF15171A)

    private fun channel(a: Float, b: Float, t: Float) = a + (b - a) * t

    @Test
    fun `B-CHAT-006 the partner is the sRGB lerp toward text1 - 0_55 in dark, 0_30 in light`() {
        val dark = glowColours(accent, darkText, dark = true)
        assertEquals(accent, dark.primary)
        assertEquals(channel(accent.red, darkText.red, 0.55f), dark.partner.red, EIGHT_BIT)
        assertEquals(channel(accent.green, darkText.green, 0.55f), dark.partner.green, EIGHT_BIT)
        assertEquals(channel(accent.blue, darkText.blue, 0.55f), dark.partner.blue, EIGHT_BIT)
        val light = glowColours(accent, lightText, dark = false)
        assertEquals(channel(accent.red, lightText.red, 0.30f), light.partner.red, EIGHT_BIT)
        assertEquals(channel(accent.green, lightText.green, 0.30f), light.partner.green, EIGHT_BIT)
        assertEquals(channel(accent.blue, lightText.blue, 0.30f), light.partner.blue, EIGHT_BIT)
    }

    @Test
    fun `the Flutter maths is kept - Compose's Oklab lerp would shift the liked hue`() {
        val ours = glowColours(accent, darkText, dark = true).partner
        val oklab = lerp(accent, darkText, 0.55f)
        val diff = abs(ours.red - oklab.red) + abs(ours.green - oklab.green) + abs(ours.blue - oklab.blue)
        assertTrue(diff > 0.005f, "sRGB and Oklab mixes differ ($diff), so the choice matters")
    }

    @Test
    fun `the classic sweep is a dim base, one highlight, at Flutter's stops`() {
        val c = glowColours(accent, darkText, dark = true)
        assertEquals(listOf(0f, 0.10f, 0.18f, 0.26f, 0.38f, 1f), c.classicStops.map { it.first })
        assertEquals(listOf(c.base, c.primary, c.partner, c.primary, c.base, c.base), c.classicStops.map { it.second })
        assertEquals(0.28f, c.base.alpha, EIGHT_BIT)
        val soft = c.softStops(1f)
        assertEquals(listOf(0f, 0.30f, 0.55f, 0.80f, 1f), soft.map { it.first })
        assertEquals(0.75f, soft[0].second.alpha, EIGHT_BIT)
        assertEquals(0.12f, soft[2].second.alpha, EIGHT_BIT)
        assertEquals(0.22f, c.softStops(0.22f / 0.75f)[0].second.alpha, EIGHT_BIT, "the wash peaks at 0.22")
    }

    @Test
    fun `never amber - in every pack, light and dark, the glow's hues are the accent family`() {
        for (pack in PackId.entries.filter { it != PackId.Dynamic }) {
            for (dark in listOf(false, true)) {
                val roles = ThemeResolver.roles(pack, null, dark, false, null)
                val accent = Color(roles.accent.v)
                val attention = Color(roles.attention.v)
                val c = glowColours(accent, Color(roles.text1.v), dark)
                for (hue in listOf(c.primary, c.partner)) {
                    assertNotEquals(attention, hue, "$pack dark=$dark: the glow is never the attention colour")
                }
            }
        }
    }
}

/** Compose packs sRGB colours into 8-bit channels: one step of 1/255. */
private const val EIGHT_BIT = 1f / 255f + 1e-4f
