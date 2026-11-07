package io.github.eslamasabry.oc.ui.kit.rb.o20

import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.modal.KitServerTrustRules
import io.github.eslamasabry.oc.ui.kit.team.KitFindingSeverity
import io.github.eslamasabry.oc.ui.kit.team.KitFindingsRules
import io.github.eslamasabry.oc.ui.kit.team.KitPlanPhase
import io.github.eslamasabry.oc.ui.kit.team.KitPlanRules
import io.github.eslamasabry.oc.ui.kit.team.KitTeamFinding
import io.github.eslamasabry.oc.ui.kit.team.KitTeamRules
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.addServerCleartextWarning
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateAccept
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateBody
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateChangedTitle
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateReplace
import io.github.eslamasabry.oc.ui.words.res.serverTrustCertificateTitle
import io.github.eslamasabry.oc.ui.words.res.serverTrustHttpAccept
import io.github.eslamasabry.oc.ui.words.res.serverTrustHttpPublicBody
import io.github.eslamasabry.oc.ui.words.res.serverTrustHttpTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The non-drawing rules of order 20's parts, each against its Dart line. */
class TeamRulesTest {
    @Test fun `B-KIT-2001 team tone is attention for needs-you, primary for failed, secondary otherwise`() {
        // team_parts.dart:9-13
        KitTeamState.entries.forEach {
            val want = when (it) {
                KitTeamState.NeedsYou -> TextTone.Attention
                KitTeamState.Failed -> TextTone.Primary
                else -> TextTone.Secondary
            }
            assertEquals(want, KitTeamRules.tone(it), it.name)
        }
    }

    @Test fun `B-KIT-2002 team states keep the Dart order`() {
        // kit_team_data.dart:4-13
        assertEquals(listOf("Empty", "Loading", "Running", "NeedsYou", "Stalled", "Failed", "Done", "Stale"), KitTeamState.entries.map { it.name })
    }

    @Test fun `B-KIT-2003 actions hide while stale or loading`() {
        // team_parts.dart:142-143
        assertFalse(KitTeamRules.allowsActions(KitTeamState.Stale))
        assertFalse(KitTeamRules.allowsActions(KitTeamState.Loading))
        listOf(KitTeamState.Empty, KitTeamState.Running, KitTeamState.NeedsYou, KitTeamState.Stalled, KitTeamState.Failed, KitTeamState.Done)
            .forEach { assertTrue(KitTeamRules.allowsActions(it), it.name) }
    }

    @Test fun `B-KIT-2004 the counted bar shows only for an honest count`() {
        // team_parts.dart:85-89
        assertEquals(0.4f, KitTeamRules.progress(2, 5))
        assertEquals(0f, KitTeamRules.progress(0, 5))
        assertEquals(1f, KitTeamRules.progress(5, 5))
        assertNull(KitTeamRules.progress(6, 5))
        assertNull(KitTeamRules.progress(-1, 5))
        assertNull(KitTeamRules.progress(0, 0))
        assertNull(KitTeamRules.progress(null, 5))
        assertNull(KitTeamRules.progress(2, null))
    }

    @Test fun `B-KIT-2005 the card heading glyph shows only for failed and needs-you`() {
        // team_parts.dart:152
        assertEquals(setOf(KitTeamState.Failed, KitTeamState.NeedsYou), KitTeamState.entries.filter { KitTeamRules.cardMarked(it) }.toSet())
    }

    @Test fun `B-KIT-2006 findings severity chip and toggling`() {
        // kit_findings_card.dart:58-67,71-76
        assertEquals(KitChipTone.Attention, KitFindingsRules.chipTone(KitFindingSeverity.Critical))
        assertEquals(KitChipTone.Attention, KitFindingsRules.chipTone(KitFindingSeverity.Major))
        assertEquals(KitChipTone.Neutral, KitFindingsRules.chipTone(KitFindingSeverity.Minor))
        val live = KitTeamFinding("1", "t", "Major", onChanged = {})
        val readOnly = KitTeamFinding("2", "t", "Minor")
        assertTrue(KitFindingsRules.canToggle(KitTeamState.Done, live))
        assertFalse(KitFindingsRules.canToggle(KitTeamState.Done, readOnly))
        assertFalse(KitFindingsRules.canToggle(KitTeamState.Stale, live))
        assertFalse(KitFindingsRules.canToggle(KitTeamState.Loading, live))
    }

    @Test fun `B-KIT-2007 plan phase heading, tone and the large-text switch`() {
        // kit_plan_card.dart:67-74,92
        assertEquals("Build", KitPlanRules.phaseHeading(KitPlanPhase("Build", emptyList())))
        assertEquals("Build · Check scope", KitPlanRules.phaseHeading(KitPlanPhase("Build", emptyList(), "Check scope")))
        assertEquals(TextTone.Secondary, KitPlanRules.phaseTone(KitPlanPhase("Build", emptyList())))
        assertEquals(TextTone.Attention, KitPlanRules.phaseTone(KitPlanPhase("Build", emptyList(), "x")))
        assertEquals(1.3f, KitPlanRules.LARGE_TEXT)
    }

    @Test fun `B-KIT-2008 server trust words follow certificate, change and network`() {
        // kit_server_trust.dart:27-31,46-50,104-108
        assertEquals(Res.string.serverTrustCertificateChangedTitle, KitServerTrustRules.title(certificate = true, changed = true))
        assertEquals(Res.string.serverTrustCertificateTitle, KitServerTrustRules.title(certificate = true, changed = false))
        assertEquals(Res.string.serverTrustHttpTitle, KitServerTrustRules.title(certificate = false, changed = true))
        assertEquals(Res.string.serverTrustCertificateReplace, KitServerTrustRules.accept(certificate = true, changed = true))
        assertEquals(Res.string.serverTrustCertificateAccept, KitServerTrustRules.accept(certificate = true, changed = false))
        assertEquals(Res.string.serverTrustHttpAccept, KitServerTrustRules.accept(certificate = false, changed = false))
        assertEquals(Res.string.serverTrustCertificateBody, KitServerTrustRules.body(certificate = true, privateNetwork = true))
        assertEquals(Res.string.addServerCleartextWarning, KitServerTrustRules.body(certificate = false, privateNetwork = true))
        assertEquals(Res.string.serverTrustHttpPublicBody, KitServerTrustRules.body(certificate = false, privateNetwork = false))
    }

    @Test fun `B-KIT-2009 fingerprint is capitals with a colon between pairs`() {
        // kit_server_trust.dart:126-128
        assertEquals("01:23:AB:EF", KitServerTrustRules.fingerprint("0123abef"))
        assertEquals("01:2", KitServerTrustRules.fingerprint("012"))
        assertEquals("", KitServerTrustRules.fingerprint(""))
        assertEquals(95, KitServerTrustRules.fingerprint("0123456789abcdef".repeat(4)).length)
    }
}
