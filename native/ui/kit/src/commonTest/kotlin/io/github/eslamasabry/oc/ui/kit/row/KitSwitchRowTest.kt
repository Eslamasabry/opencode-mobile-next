package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.ui.graphics.Color
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** KitSwitchRow's pure rules against kit_row_parts.dart and app_theme.dart at 8302de537. */
class KitSwitchRowTest {
    private val label: (KitUntil) -> String = { it.name }

    private fun colors(): KitColors {
        var n = 0
        fun c() = Color(0xFF000000.toInt() or ++n)
        return KitColors(
            dark = false, ground = c(), surface1 = c(), surface2 = c(), surface3 = c(), hairline = c(), text1 = c(),
            text2 = c(), text3 = c(), accent = c(), onAccent = c(), partner = c(), onPartner = c(), attention = c(),
            attentionFill = c(), onAttentionFill = c(), attentionSurface = c(), attentionLine = c(), success = c(),
            scrim = c(), codeKeyword = c(), codeString = c(), codeType = c(), elevationShadow = c(),
        )
    }

    @Test fun `B-KIT-R10-001 a disabled row's reason replaces its supporting line`() {
        assertEquals(
            "Turn on notifications first",
            SwitchRules.supporting("Play a sound", false, "Turn on notifications first", false, null, null, listOf(KitUntil.Off), label),
        )
    }

    @Test fun `B-KIT-R10-002 a risky switch that is on leads with its scope and the duration in force`() {
        assertEquals(
            "Every conversation on this server · Hour",
            SwitchRules.supporting(null, true, null, true, "Every conversation on this server", KitUntil.Hour, KitUntil.entries, label),
        )
        // One duration offered: that one is in force; the row's own line comes last.
        assertEquals(
            "Scope · Off · Tools run without asking",
            SwitchRules.supporting("Tools run without asking", true, null, true, "Scope", null, listOf(KitUntil.Off), label),
        )
        // Several offered and none chosen: no duration.
        assertEquals("Scope", SwitchRules.supporting(null, true, null, true, "Scope", null, KitUntil.entries, label))
    }

    @Test fun `B-KIT-R10-003 an off risky switch keeps its own supporting line`() {
        assertEquals("Tools run without asking", SwitchRules.supporting("Tools run without asking", true, null, false, "Scope", null, KitUntil.entries, label))
        assertNull(SwitchRules.supporting(null, true, null, false, null, null, KitUntil.entries, label))
    }

    @Test fun `B-KIT-R10-004 the risk step folds when the switch turns on elsewhere or stops being risky`() {
        assertTrue(SwitchRules.keepStep(step = true, checked = false, risky = true))
        assertFalse(SwitchRules.keepStep(step = true, checked = true, risky = true))
        assertFalse(SwitchRules.keepStep(step = true, checked = false, risky = false))
        assertFalse(SwitchRules.keepStep(step = false, checked = false, risky = true))
    }

    @Test fun `B-KIT-R10-005 a risk offers one to three durations in Flutter's order`() {
        assertEquals(listOf(KitUntil.Off, KitUntil.Conversation, KitUntil.Hour), KitUntil.entries)
        assertEquals(listOf(KitUntil.Off), KitRisk("s", "on").until)
        assertFailsWith<IllegalArgumentException> { KitRisk("s", "on", until = emptyList()) }
        assertFailsWith<IllegalArgumentException> { KitRisk("s", "on", until = KitUntil.entries + KitUntil.Off) }
    }

    @Test fun `B-KIT-R10-006 a tap on an off risky switch asks first, never flips`() {
        assertEquals(SwitchTap.AskRisk, SwitchRules.onTap(checked = false, enabled = true, locked = false, risky = true))
        assertEquals(SwitchTap.Flip, SwitchRules.onTap(checked = true, enabled = true, locked = false, risky = true))
        assertEquals(SwitchTap.Ignore, SwitchRules.onTap(checked = false, enabled = false, locked = false, risky = true))
    }

    @Test fun `B-KIT-R10-007 the switch colours follow the platform switch theme`() {
        val c = colors()
        // app_theme.dart:457-478: off = surface3 track with a hairline outline and a text2 thumb.
        assertEquals(KitSwitchColors(c.surface3, c.text2, c.hairline), kitSwitchColors(c, on = false, enabled = true))
        // on = accent track, onAccent thumb, no outline.
        assertEquals(KitSwitchColors(c.accent, c.onAccent, Color.Transparent), kitSwitchColors(c, on = true, enabled = true))
        // disabled: off keeps surface3 with a text3 thumb; on rests on text3 at .35 with a text2 thumb.
        assertEquals(KitSwitchColors(c.surface3, c.text3, c.hairline), kitSwitchColors(c, on = false, enabled = false))
        assertEquals(KitSwitchColors(c.text3.copy(alpha = .35f), c.text2, Color.Transparent), kitSwitchColors(c, on = true, enabled = false))
        // quiet: accent thumb, faint accent track and a half-accent outline.
        val quiet = kitSwitchColors(c, on = true, enabled = true, quiet = true)
        assertEquals(c.accent, quiet.thumb)
        assertEquals(c.accent.copy(alpha = .5f), quiet.outline)
    }

    @Test fun `B-KIT-R10-008 the Solid thumb is 16 dp off and 24 dp on, centred half a track height from the ends`() {
        assertEquals(16, KitSwitchGeometry.thumb(on = false, own = true))
        assertEquals(24, KitSwitchGeometry.thumb(on = true, own = true))
        // The style-drawn switch keeps its 24 dp thumb both ways (kit_row_parts.dart:728).
        assertEquals(24, KitSwitchGeometry.thumb(on = false, own = false))
        assertEquals(16, KitSwitchGeometry.thumbCentre(on = false))
        assertEquals(36, KitSwitchGeometry.thumbCentre(on = true))
        assertEquals(60 to 48, KitSwitchGeometry.BOX_W to KitSwitchGeometry.BOX_H)
        assertEquals(52 to 32, KitSwitchGeometry.TRACK_W to KitSwitchGeometry.TRACK_H)
    }
}
