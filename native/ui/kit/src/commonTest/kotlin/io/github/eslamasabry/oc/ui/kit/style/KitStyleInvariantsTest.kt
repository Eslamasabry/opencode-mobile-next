package io.github.eslamasabry.oc.ui.kit.style

import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The rules every style keeps across every brightness and knob combination (Dart `kit_style_test.dart` invariants). */
class KitStyleInvariantsTest {
    private val styled = KitSurfaceStyle.entries - KitSurfaceStyle.Solid

    @Test
    fun `B-KIT-041 every style keeps words at AA, shadows on even corners and amber only for needs-you`() {
        val fails = mutableListOf<String>()
        for (style in KitSurfaceStyle.entries) {
            for (roles in graphite) {
                for (knobs in allKnobs()) {
                    for (role in KitSurfaceRole.entries) {
                        for (down in listOf(false, true)) {
                            val d = KitStyles.resolve(roles, style, role, solidPanel, knobs, selected = down)
                            if (style == KitSurfaceStyle.Solid || d === solidPanel) continue
                            val why = "$style ${roles.dark} $role $knobs $down"
                            if (d.hardOffset > 0f || d.shadows.isNotEmpty() || d.distance > 0f) {
                                if (!d.radius.uniform) fails += "$why: a shadow on uneven corners"
                            }
                            if ((style == KitSurfaceStyle.Brutal || style == KitSurfaceStyle.Paper) &&
                                role != KitSurfaceRole.Divider && role != KitSurfaceRole.Toggle
                            ) {
                                val seen = d.fill.on(roles.ground)
                                val words = if (style == KitSurfaceStyle.Paper) {
                                    KitStyles.spec(style).onColor(KitSurfaceRequest(roles, role, solidPanel, knobs, selected = down))
                                } else {
                                    null
                                }
                                for (ink in words?.let { listOf(it) } ?: listOf(roles.text1, roles.text2, roles.text3)) {
                                    val c = ratio(ink, seen)
                                    if (c < 4.5) fails += "$why: words at $c"
                                }
                            }
                            if (d.fill == roles.attention || d.hardShadow == roles.attention) fails += "$why: amber"
                        }
                    }
                }
            }
        }
        assertTrue(fails.isEmpty(), fails.take(20).joinToString("\n"))
    }

    @Test
    fun `B-KIT-041 the page is its fill alone and never casts in every style, brightness and knob`() {
        for (style in KitSurfaceStyle.entries) {
            for (base in graphite) {
                val roles = KitStyles.spec(style).adjustRoles(base)
                for (knobs in allKnobs()) {
                    val page = KitSurfaceLook(fill = roles.ground)
                    val d = KitStyles.resolve(roles, style, KitSurfaceRole.Ground, page, knobs, level = SurfaceLevel.Ground)
                    val why = "$style ${roles.dark} $knobs"
                    assertFalse(d.casts, why)
                    assertEquals(0f, d.edgeWidth, why)
                    assertFalse(d.hosts, why)
                    assertEquals(roles.ground, d.fill, why)
                }
            }
        }
    }

    @Test
    fun `B-KIT-041 a field's and an off switch's boundary reads at 3 to 1 in every style but Solid, every theme and knob`() {
        val fails = mutableListOf<String>()
        for (style in styled) {
            for ((name, base) in everyTheme()) {
                val roles = KitStyles.spec(style).adjustRoles(base)
                for (knobs in allKnobs()) {
                    for (role in listOf(KitSurfaceRole.Field, KitSurfaceRole.Toggle)) {
                        val d = KitStyles.resolve(roles, style, role, solidPanel, knobs)
                        for (behind in listOf(roles.ground, roles.surface1, roles.surface2)) {
                            val under = behind.on(roles.ground.withAlpha(1f))
                            val face = d.fill.on(under)
                            for (edge in listOf(d.edgeStart, d.edgeEnd)) {
                                val seen = edge.on(face)
                                val outside = ratio(seen, under)
                                val inside = ratio(seen, face)
                                if (d.edgeWidth <= 0f || outside < 3 || inside < 3) {
                                    fails += "$style $name $role $knobs: ${d.edgeWidth} dp, $outside out, $inside in"
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(fails.isEmpty(), fails.take(20).joinToString("\n"))
    }

    @Test
    fun `B-KIT-041 code stays opaque in every style, brightness and knob`() {
        for (style in KitSurfaceStyle.entries) {
            for (roles in graphite) {
                for (knobs in allKnobs()) {
                    val d = KitStyles.resolve(roles, style, KitSurfaceRole.Code, solidPanel, knobs, color = roles.surface1.withAlpha(.4f))
                    if (style == KitSurfaceStyle.Solid) continue
                    assertEquals(255, d.fill.alpha, "$style $knobs")
                    assertEquals(255, d.fillStart.alpha, "$style $knobs")
                }
            }
        }
    }

    @Test
    fun `B-KIT-041 a highlight is never a part's own fill and never casts`() {
        for (style in styled) {
            for (roles in graphite) {
                for (pressed in listOf(false, true)) {
                    val d = KitStyles.resolve(roles, style, KitSurfaceRole.StateLayer, solidPanel, pressed = pressed)
                    val why = "$style ${roles.dark} $pressed"
                    assertFalse(d.casts, why)
                    assertTrue(d.fill.alpha > 0, why)
                    assertNotEquals(roles.attention, d.fill, why)
                }
            }
        }
    }

    @Test
    fun `B-KIT-041 Brutal's ink edge and Paper's ink boxes read at 3 to 1`() {
        for (roles in graphite) {
            val rest = KitStyles.resolve(roles, KitSurfaceStyle.Brutal, KitSurfaceRole.Content, solidPanel)
            assertTrue(ratio(rest.edgeStart, roles.ground) >= 3)
            for (role in KitSurfaceRole.entries) {
                val d = KitStyles.resolve(roles, KitSurfaceStyle.Paper, role, solidPanel)
                assertTrue(d.shadows.isEmpty() && d.hardOffset == 0f && d.distance == 0f, "$role")
                if (role in setOf(
                        KitSurfaceRole.Control, KitSurfaceRole.Track, KitSurfaceRole.Floating, KitSurfaceRole.Sheet,
                        KitSurfaceRole.Field, KitSurfaceRole.Toggle,
                    )
                ) {
                    assertTrue(ratio(d.edgeStart.on(d.fill), d.fill) >= 3, "$role")
                }
            }
        }
    }

    @Test
    fun `B-KIT-041 Soft keeps words at AA on its raised and pressed faces`() {
        for (roles in graphite) {
            for (selected in listOf(false, true)) {
                val d = KitStyles.resolve(roles, KitSurfaceStyle.Soft, KitSurfaceRole.Content, solidPanel, selected = selected)
                for (face in listOf(d.fill, d.fillStart)) {
                    for (text in listOf(roles.text1, roles.text2, roles.text3)) {
                        assertTrue(ratio(text, face) >= 4.5, "${roles.dark} $selected")
                    }
                }
            }
        }
    }

    @Test
    fun `B-KIT-041 Signal's day and night enamel keep every word at AA`() {
        for (roles in listOf(KitStyleSignal.adjustRoles(graphiteLight), KitStyleSignal.adjustRoles(graphiteDark))) {
            for (fill in listOf(roles.ground, roles.surface2, roles.surface3)) {
                for (ink in listOf(roles.text1, roles.text2, roles.text3)) assertTrue(ratio(ink, fill) >= 4.5)
            }
        }
    }
}
