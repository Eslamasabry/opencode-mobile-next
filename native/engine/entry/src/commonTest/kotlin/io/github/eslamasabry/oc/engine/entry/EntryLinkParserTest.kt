package io.github.eslamasabry.oc.engine.entry

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from test/session_handoff_domain_test.dart and the team-link cases (07 §7.6). */
class EntryLinkParserTest {
    @Test
    fun `B-NAV-063 a session link carries only the two ids and round-trips exactly`() {
        val link = EntryLinkParser.sessionLink(ProfileId("1730000000000"), SessionId("ses_abc.1-x"))
        assertEquals("opencode-mobile://session?profile=1730000000000&session=ses_abc.1-x", link)
        assertEquals(ParsedLink.Session(ProfileId("1730000000000"), SessionId("ses_abc.1-x")), EntryLinkParser.parse(link!!))
        assertEquals(
            ParsedLink.Session(ProfileId("p"), SessionId("s")),
            EntryLinkParser.parse("OPENCODE-MOBILE://SESSION/?session=s&profile=p"),
        )
    }

    @Test
    fun `B-NAV-063 unsafe ids are refused when building a link`() {
        assertNull(EntryLinkParser.sessionLink(ProfileId("-flag"), SessionId("s")))
        assertNull(EntryLinkParser.sessionLink(ProfileId("p"), SessionId("a b")))
    }

    @Test
    fun `B-NAV-063 anything but the exact shape is ignored safely`() {
        val rejected =
            listOf(
                "",
                "   ",
                "https://session?profile=p&session=s",
                "opencode-mobile://other?profile=p&session=s",
                "opencode-mobile://session",
                "opencode-mobile://session?profile=p",
                "opencode-mobile://session?profile=p&profile=q&session=s",
                "opencode-mobile://session?profile=p&session=s#frag",
                "opencode-mobile://session/extra?profile=p&session=s",
                "opencode-mobile://user@session?profile=p&session=s",
                "opencode-mobile://session:80?profile=p&session=s",
                "opencode-mobile://session?profile=-p&session=s",
                "opencode-mobile://session?profile=p&session=s%20x",
                "opencode-mobile://session?profile=p&session=" + "a".repeat(129),
                "opencode-mobile://session?profile=p&session=" + "a".repeat(1_100),
                "not a uri at all",
            )
        for (raw in rejected) assertNull(EntryLinkParser.parse(raw), raw)
    }

    @Test
    fun `B-NAV-063 team links parse gate and run ids and nothing else`() {
        assertEquals(ParsedLink.TeamGate(ProfileId("p1"), "bead:w-1"), EntryLinkParser.parse("opencode-mobile://team/gate/p1/bead:w-1"))
        assertEquals(ParsedLink.TeamRun(ProfileId("p1"), "run_1"), EntryLinkParser.parse("opencode-mobile://team/run/p1/run_1"))
        for (raw in listOf(
            "opencode-mobile://team/gate/p1/bead:",
            "opencode-mobile://team/gate/p1",
            "opencode-mobile://team/other/p1/x",
            "opencode-mobile://team/run/p1/x?y=1",
            "opencode-mobile://team/run/p1/x/extra",
        )) {
            assertNull(EntryLinkParser.parse(raw), raw)
        }
    }

    @Test
    fun `B-ANDR-071 the intake enforces the ASCII byte ceiling before anything is parked`() {
        assertTrue(EntryValidators.ingressAccepts("opencode-mobile://session?profile=p&session=s"))
        assertTrue(EntryValidators.ingressAccepts("a".repeat(1_024)))
        assertFalse(EntryValidators.ingressAccepts("a".repeat(1_025)))
        assertFalse(EntryValidators.ingressAccepts(""))
        assertFalse(EntryValidators.ingressAccepts("opencode-mobile://session?profile=p&session=s t"))
        assertFalse(EntryValidators.ingressAccepts("opencode-mobile://session?profile=é"))
        assertFalse(EntryValidators.ingressAccepts("opencode-mobile://session\n"))
    }
}
