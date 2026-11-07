package io.github.eslamasabry.oc.ui.kit.rb.o28

import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.team.KitTeamGlyphs
import io.github.eslamasabry.oc.ui.kit.team.KitTeamRules
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Order 28, part-team_parts: every constant and branch of lib/ui/kit/team/team_parts.dart at 8302de537. */
class TeamPartsParityTest {
    private val all = KitTeamState.entries

    @Test
    fun `RB28-P01 teamTone needs-you attention, failed primary, the rest secondary`() {
        // team_parts.dart:9-13 (and the row glyph colour, :39-43)
        for (s in all) {
            val expected =
                when (s) {
                    KitTeamState.NeedsYou -> TextTone.Attention
                    KitTeamState.Failed -> TextTone.Primary
                    else -> TextTone.Secondary
                }
            assertEquals(expected, KitTeamRules.tone(s), s.name)
        }
    }

    @Test
    fun `RB28-P02 teamIcon draws Flutter's Material glyph per state`() {
        // team_parts.dart:15-24
        val names =
            mapOf(
                KitTeamState.Empty to "Icons.inbox_outlined",
                KitTeamState.Loading to "Icons.hourglass_empty",
                KitTeamState.Running to "Icons.pending_outlined",
                KitTeamState.NeedsYou to "Icons.notifications_none",
                KitTeamState.Stalled to "Icons.pause_circle_outline",
                KitTeamState.Failed to "Icons.error_outline",
                KitTeamState.Done to "Icons.check_circle_outline",
                KitTeamState.Stale to "Icons.history",
            )
        assertEquals(all.toSet(), names.keys)
        for ((s, name) in names) assertEquals(name, KitTeamRules.glyph(s).name, s.name)
        assertEquals(all.size, all.map { KitTeamRules.glyph(it) }.toSet().size)
    }

    @Test
    fun `RB28-P03 glyphs are drawn on the Material 24 grid and only the chevron mirrors`() {
        // team_parts.dart:114 Icons.chevron_right (matchTextDirection)
        assertEquals("Icons.chevron_right", KitTeamGlyphs.chevronRight.name)
        assertTrue(KitTeamGlyphs.chevronRight.autoMirror)
        for (s in all) {
            val g = KitTeamRules.glyph(s)
            assertFalse(g.autoMirror, g.name)
            assertEquals(24f, g.viewportWidth)
            assertEquals(24f, g.viewportHeight)
        }
    }

    @Test
    fun `RB28-P04 progress shows only for a positive total and completed within 0 to total`() {
        // team_parts.dart:83-87, value completed / total at :91
        assertEquals(0.5f, KitTeamRules.progress(2, 4))
        assertEquals(0f, KitTeamRules.progress(0, 3))
        assertEquals(1f, KitTeamRules.progress(3, 3))
        assertNull(KitTeamRules.progress(null, 3))
        assertNull(KitTeamRules.progress(1, null))
        assertNull(KitTeamRules.progress(0, 0))
        assertNull(KitTeamRules.progress(1, -2))
        assertNull(KitTeamRules.progress(-1, 3))
        assertNull(KitTeamRules.progress(4, 3))
    }

    @Test
    fun `RB28-P05 the row end appears for a trailing value or a tap`() {
        // team_parts.dart:100
        assertTrue(KitTeamRules.hasEnd("See changes", opens = false))
        assertTrue(KitTeamRules.hasEnd(null, opens = true))
        assertFalse(KitTeamRules.hasEnd(null, opens = false))
    }

    @Test
    fun `RB28-P06 stale and loading cards offer no actions`() {
        // team_parts.dart:144-145, 196-197
        for (s in all) {
            val allowed = s != KitTeamState.Stale && s != KitTeamState.Loading
            assertEquals(allowed, KitTeamRules.allowsActions(s), s.name)
            assertEquals(allowed, KitTeamRules.showsActions(s, hasPrimary = true, hasSecondary = false, others = 0), s.name)
            assertFalse(KitTeamRules.showsActions(s, hasPrimary = false, hasSecondary = false, others = 0), s.name)
        }
        assertTrue(KitTeamRules.showsActions(KitTeamState.Done, hasPrimary = false, hasSecondary = true, others = 0))
        assertTrue(KitTeamRules.showsActions(KitTeamState.Running, hasPrimary = false, hasSecondary = false, others = 1))
    }

    @Test
    fun `RB28-P07 card head - glyph only when failed or needs you, tones and status role`() {
        // team_parts.dart:153-179
        for (s in all) {
            assertEquals(s == KitTeamState.Failed || s == KitTeamState.NeedsYou, KitTeamRules.cardMarked(s), s.name)
            val attention = s == KitTeamState.NeedsYou
            assertEquals(if (attention) TextTone.Attention else TextTone.Primary, KitTeamRules.cardTitleTone(s), s.name)
            assertEquals(if (attention) TextTone.Attention else TextTone.Primary, KitTeamRules.cardGlyphTone(s), s.name)
            assertEquals(if (s == KitTeamState.Failed) TypeRole.RowTitle else TypeRole.Secondary, KitTeamRules.cardStatusRole(s), s.name)
            assertEquals(TextTone.Secondary, KitTeamRules.cardStatusTone(s, neutral = true), s.name)
            assertEquals(KitTeamRules.tone(s), KitTeamRules.cardStatusTone(s, neutral = false), s.name)
        }
    }

    @Test
    fun `RB28-P08 the attention card's edge and Solid fill alphas`() {
        // team_parts.dart:217 (.30 edge) and :227 (.09 fill over surface1)
        assertEquals(.30f, KitTeamRules.ATTENTION_EDGE_ALPHA)
        assertEquals(.09f, KitTeamRules.ATTENTION_FILL_ALPHA)
    }
}
