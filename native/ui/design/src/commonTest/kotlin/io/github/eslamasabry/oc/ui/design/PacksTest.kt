package io.github.eslamasabry.oc.ui.design

import io.github.eslamasabry.oc.ui.design.color.Accents
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.DynamicSource
import io.github.eslamasabry.oc.ui.design.color.DynamicTones
import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.Packs
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.color.argb
import io.github.eslamasabry.oc.ui.design.color.deltaE2000
import io.github.eslamasabry.oc.ui.design.color.deltaE2000Lab
import io.github.eslamasabry.oc.ui.design.color.hueDistance
import io.github.eslamasabry.oc.ui.design.color.isRedHue
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.color.withAccent
import io.github.eslamasabry.oc.ui.design.color.withPartner
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Ports `test/theme_roles_test.dart` and `test/theme_packs_test.dart` (the logic halves). */
class PacksTest {
    /**
     * Every floor a role set promises (Dart `_floors`), as failure lines. Dart measures its float colours; the native
     * roles are those colours rounded to 8 bits (what Flutter paints too), which can land up to [QUANTISATION] under a
     * floor the float colour meets. The derivation itself is not loosened.
     */
    private fun floors(tag: String, r: Roles): List<String> {
        val out = mutableListOf<String>()
        fun floor(what: String, fg: Argb, bg: Argb, min: Double) {
            val ratio = wcagContrast(fg, bg.over(r.ground))
            if (ratio < min - QUANTISATION) out += "$tag $what ${(ratio * 100).toInt() / 100.0} < $min"
        }
        val surfaces = mapOf("ground" to r.ground, "surface1" to r.surface1, "surface2" to r.surface2, "surface3" to r.surface3)
        for ((k, v) in surfaces) {
            floor("text1 on $k", r.text1, v, 7.0)
            floor("text2 on $k", r.text2, v, 4.5)
            floor("text3 on $k", r.text3, v, 4.5)
        }
        floor("accent on ground", r.accent, r.ground, 4.5)
        floor("accent on surface1", r.accent, r.surface1, 4.5)
        floor("onAccent on accent", r.onAccent, r.accent, 4.5)
        floor("attention on ground", r.attention, r.ground, 4.5)
        floor("attention on surface1", r.attention, r.surface1, 4.5)
        val card = r.attentionSurface.over(r.surface1)
        floor("attention on its card", r.attention, card, 4.5)
        floor("text1 on the needs-you card", r.text1, card, 7.0)
        floor("badge text", r.onAttentionFill, r.attentionFill, 4.5)
        floor("success on ground", r.success, r.ground, 4.5)
        for (c in listOf(r.codeKeyword, r.codeString, r.codeType)) floor("code on surface1", c, r.surface1, 4.5)
        for (t in listOf(r.text1, r.text2, r.text3, r.accent, r.attention)) if (t.alpha != 255) out += "$tag translucent text"
        return out
    }

    private fun apart(tag: String, accent: Argb, r: Roles): List<String> =
        buildList {
            if (hueDistance(accent, r.attention) < 30) add("$tag hue")
            if (deltaE2000(accent, r.attention) < 20) add("$tag deltaE")
        }

    private val staticPacks = Packs.static

    @Test
    fun `B-KIT-056 the red test is Flutter's and orange is not red`() {
        assertTrue(isRedHue(argb(0xFFE5322D)))
        assertFalse(isRedHue(argb(0xFFBA540C)))
        assertFalse(isRedHue(argb(0xFFFFB547)))
        assertFalse(isRedHue(argb(0xFF3A1010)))
    }

    @Test
    fun `B-KIT-061 the default pack is Graphite and an unknown stored name reads Graphite`() {
        assertEquals(PackId.Opencode, PackId.fromStorage(null))
        assertEquals(PackId.Opencode, PackId.fromStorage("crystal"))
        assertEquals(PackId.Opencode, PackId.fromStorage(""))
        val pack = Packs.of(PackId.Opencode)
        assertEquals("Graphite", pack.label)
        assertEquals(Graphite.dark, pack.dark.roles)
        assertEquals(Graphite.light, pack.light.roles)
        assertEquals(argb(0xFF3DDC8A), pack.dark.roles.accent)
        assertEquals(argb(0xFFFAFAF8), pack.light.roles.surface1)
    }

    @Test
    fun `B-KIT-061 there are 31 packs stored by their Dart names, round-tripping`() {
        assertEquals(31, PackId.entries.size)
        assertEquals(PackId.Dynamic, PackId.entries.last())
        val dartNames =
            listOf(
                "opencode", "catppuccin", "gruvbox", "solarized", "dracula", "nord", "tokyoNight", "oneDark", "monokai",
                "rosePine", "everforest", "kanagawa", "ayu", "nightOwl", "github", "palenight", "synthwave", "cobalt",
                "midnight", "paper", "forest", "ocean", "sunset", "sakura", "lavender", "mint", "coffee", "slate",
                "amber", "highContrast", "dynamic",
            )
        assertEquals(dartNames.toSet(), PackId.entries.map { it.storageName }.toSet())
        for (id in PackId.entries) assertEquals(id, PackId.fromStorage(id.storageName))
        assertEquals(PackId.Solarized, PackId.fromStorage("solarized"))
    }

    @Test
    fun `B-KIT-061 every static pack has complete distinct dark and light palettes`() {
        for (id in staticPacks) {
            val pack = Packs.of(id)
            assertEquals(id, pack.id)
            assertTrue(pack.dark.roles.dark, "$id dark")
            assertFalse(pack.light.roles.dark, "$id light")
            assertNotEquals(pack.dark.background, pack.light.background, "$id")
            assertTrue(pack.label.isNotBlank())
        }
    }

    @Test
    fun `B-KIT-061 every pack keeps text and controls readable in both modes`() {
        val failures = staticPacks.flatMap { id ->
            listOf(true, false).flatMap { d -> floors("${id.storageName}/${if (d) "dark" else "light"}", Packs.of(id).palette(d).roles) }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `B-KIT-061 a disabled button reads in every pack text3 on surface3`() {
        assertEquals(argb(0xFF8C8F96), Graphite.dark.text3)
        for (id in staticPacks) for (d in listOf(true, false)) {
            val r = Packs.of(id).palette(d).roles
            assertTrue(wcagContrast(r.text3, r.surface3) >= 4.5 - QUANTISATION, "$id $d")
        }
    }

    @Test
    fun `B-KIT-061 Material You resolves from the platform tones and falls back to Graphite`() {
        assertEquals(Packs.graphite, Packs.of(PackId.Dynamic, dynamic = null))
        val tones = { dark: Boolean ->
            if (dark) DynamicTones(argb(0xFFA8C7FA), argb(0xFFBFC6DC), argb(0xFFDDBCE0), argb(0xFFE3E2E6), argb(0xFF0E0E11), argb(0xFF1B1B1F))
            else DynamicTones(argb(0xFF3F5F90), argb(0xFF565F71), argb(0xFF705575), argb(0xFF1B1B1F), argb(0xFFFFFFFF), argb(0xFFF3F3F7))
        }
        val pack = Packs.of(PackId.Dynamic, DynamicSource(tones(true), tones(false)))
        assertEquals(PackId.Dynamic, pack.id)
        assertEquals(argb(0xFF86D8A5), pack.dark.success)
        assertEquals(argb(0xFF1E7A44), pack.light.success)
        assertEquals(argb(0xFF0E0E11), pack.dark.roles.ground)
        assertTrue(floors("dynamic dark", pack.dark.roles).isEmpty(), floors("dynamic dark", pack.dark.roles).joinToString())
        assertTrue(floors("dynamic light", pack.light.roles).isEmpty(), floors("dynamic light", pack.light.roles).joinToString())
    }

    @Test
    fun `B-KIT-062 a custom accent and ground derive a readable role set`() {
        val accents = listOf(0xFF3DDC8A, 0xFF5AB0FF, 0xFFFF8A4C, 0xFFC7A6FF, 0xFFFFEB3B, 0xFF1A237E, 0xFF808080, 0xFFE91E63)
        val darkGrounds = listOf(0xFF000000, 0xFF0B0C0E, 0xFF1E1E2E, 0xFF002B36, 0xFF2D2A2E)
        val lightGrounds = listOf(0xFFFFFFFF, 0xFFF3F3F1, 0xFFFDF6E3, 0xFFE1E2E7, 0xFFEFF1F5)
        val failures = mutableListOf<String>()
        for (a in accents) for ((dark, grounds) in listOf(true to darkGrounds, false to lightGrounds)) for (g in grounds) {
            failures += floors("${argb(a)} on ${argb(g)}", RoleDerivation.deriveRoles(argb(a), argb(g), dark))
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `B-KIT-062 withAccent swaps only the accent and keeps it readable`() {
        for (o in Accents.graphite) {
            val dark = Graphite.dark.withAccent(o.dark)
            val light = Graphite.light.withAccent(o.light)
            assertEquals(Graphite.dark.surface1, dark.surface1)
            assertEquals(Graphite.dark.attention, dark.attention)
            assertTrue(floors("dark ${o.id}", dark).isEmpty())
            assertTrue(floors("light ${o.id}", light).isEmpty())
        }
    }

    @Test
    fun `B-KIT-062 Graphite offers green blue teal and violet and teal passes unguarded`() {
        assertEquals(listOf("green", "blue", "teal", "violet"), Accents.graphite.map { it.id })
        val teal = Accents.graphite.first { it.id == "teal" }
        assertEquals(argb(0xFF3CCFCF), teal.dark)
        assertEquals(argb(0xFF0D7377), teal.light)
        assertEquals(teal.dark, Graphite.dark.withAccent(teal.dark).accent)
        assertEquals(teal.light, Graphite.light.withAccent(teal.light).accent)
    }

    @Test
    fun `B-KIT-062 the deltaE2000 matches the Sharma Wu Dalal reference pairs`() {
        assertEquals(2.0425, deltaE2000Lab(doubleArrayOf(50.0, 2.6772, -79.7751), doubleArrayOf(50.0, 0.0, -82.7485)), 1e-4)
        assertEquals(2.3669, deltaE2000Lab(doubleArrayOf(50.0, 0.0, 0.0), doubleArrayOf(50.0, -1.0, 2.0)), 1e-4)
        assertEquals(27.1492, deltaE2000Lab(doubleArrayOf(50.0, 2.5, 0.0), doubleArrayOf(73.0, 25.0, -18.0)), 1e-4)
        assertEquals(100.0, deltaE2000(argb(0xFF000000), argb(0xFFFFFFFF)), .01)
        assertEquals(180.0, hueDistance(argb(0xFFFF0000), argb(0xFF00FFFF)))
        assertEquals(60.0, hueDistance(argb(0xFFFF0000), argb(0xFFFF00FF)))
    }

    @Test
    fun `B-KIT-062 accentKeepsMeaning is the one LOOK-39 check`() {
        for (o in Accents.graphite) for ((roles, raw) in listOf(Graphite.dark to o.dark, Graphite.light to o.light)) {
            assertTrue(RoleDerivation.accentKeepsMeaning(raw, roles), o.id)
            assertTrue(RoleDerivation.accentKeepsMeaning(roles.withAccent(raw).accent, roles), "${o.id} guarded")
            assertTrue(apart(o.id, raw, roles).isEmpty())
        }
        for (r in listOf(Graphite.dark, Graphite.light)) assertFalse(RoleDerivation.accentKeepsMeaning(r.attention, r))
        assertFalse(RoleDerivation.accentKeepsMeaning(argb(0xFFFF8A4C), Graphite.dark))
        assertFalse(RoleDerivation.accentKeepsMeaning(argb(0xFFC2410C), Graphite.light))
    }

    @Test
    fun `B-KIT-009 every swatch in every theme is guarded to AA and keeps away from amber`() {
        assertEquals(listOf("mint", "teal", "blue", "violet", "pink", "mono", "silver"), Accents.swatches.map { it.id })
        assertEquals(null, Accents.byId(null))
        assertEquals(null, Accents.byId("orange"))
        for (id in staticPacks) for (d in listOf(true, false)) {
            val base = Packs.of(id).palette(d).roles
            for (a in Accents.swatches) {
                val r = base.withPartner(a.of(d))
                val where = "${id.storageName} $d ${a.id}"
                assertTrue(wcagContrast(r.partner, r.ground) >= 4.5 - QUANTISATION, where)
                assertTrue(wcagContrast(r.partner, r.surface1) >= 4.5 - QUANTISATION, where)
                assertTrue(wcagContrast(r.onPartner, r.partner) >= 4.5 - QUANTISATION, where)
                assertTrue(RoleDerivation.accentKeepsMeaning(a.of(d), base), where)
                // The swatch drives partner; accent stays the pack's own.
                assertEquals(base.accent, r.accent, where)
            }
        }
    }

    @Test
    fun `B-KIT-009 the theme resolver applies the swatch then Saver and Match theme clears it`() {
        val plain = ThemeResolver.roles(PackId.Nord, null, dark = true, saver = false, dynamic = null)
        assertEquals(Packs.of(PackId.Nord).dark.roles, plain)
        val blue = ThemeResolver.roles(PackId.Nord, "blue", dark = false, saver = false, dynamic = null)
        assertEquals(Packs.of(PackId.Nord).light.roles.withPartner(argb(0xFF1F6FEB)), blue)
        val saver = ThemeResolver.roles(PackId.Nord, "blue", dark = false, saver = true, dynamic = null)
        assertTrue(saver.dark)
        assertEquals(argb(0xFF000000), saver.ground)
        assertEquals(Packs.of(PackId.Nord).dark.roles.withPartner(argb(0xFF5AB0FF)).partner, saver.partner)
    }

    @Test
    fun `B-KIT-056 no pack and no swatch paints a red role`() {
        for (id in staticPacks) for (d in listOf(true, false)) {
            val r = Packs.of(id).palette(d).roles
            for (c in listOf(r.ground, r.surface1, r.surface2, r.surface3, r.text1, r.text2, r.text3, r.accent, r.partner,
                r.attention, r.success)) {
                assertFalse(isRedHue(c), "${id.storageName} $d $c")
            }
        }
    }

    @Test
    fun `B-KIT-061 the curated packs keep their own hues where they already read`() {
        // Catppuccin Mocha keeps its mauve (it reads on its ground already) and its lavender partner.
        val mocha = Packs.of(PackId.Catppuccin).dark.roles
        assertEquals(argb(0xFFCBA6F7), mocha.accent)
        assertEquals(argb(0xFF181825), mocha.ground)
        // Gruvbox's yellow secondary sits near amber, so the partner falls through to another hue.
        val gruv = Packs.of(PackId.Gruvbox).dark.roles
        assertTrue(hueDistance(gruv.partner, gruv.attention) >= 30)
        assertTrue(abs(wcagContrast(gruv.partner, gruv.ground)) >= 4.5)
    }

    private companion object {
        const val QUANTISATION = 0.035
    }
}
