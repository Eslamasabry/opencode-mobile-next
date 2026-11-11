package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.entry.EntryTarget
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import io.github.eslamasabry.opencode_mobile.release.RepoFiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Where an AI Team alert's tap lands (team-rest: the computer team's alerts carry `gate:<id>` / `run:<id>`). */
class TeamAlertTapsTest {
    private val p = ProfileId("p1")

    @Test
    fun `a computer team's question opens its Gate page and its run opens the task page`() {
        assertEquals(TeamRoute.Gate(p, "g1"), teamAlertRoute(AlertKind.TeamDecision, "gate:g1", "p1"))
        assertEquals(TeamRoute.Gate(p, "g2"), teamAlertRoute(AlertKind.TeamReview, "gate:g2", "p1"))
        assertEquals(TeamRoute.FleetRun(p, "r1"), teamAlertRoute(AlertKind.TeamRunFailed, "run:r1", "p1"))
        assertEquals(TeamRoute.FleetRun(p, "r2"), teamAlertRoute(AlertKind.TeamCompleted, "run:r2", "p1"))
    }

    @Test
    fun `the entry router's TeamPage maps to the same routes`() {
        assertEquals(TeamRoute.Gate(p, "g1"), EntryTarget.TeamPage(p, true, "g1").route())
        assertEquals(TeamRoute.FleetRun(p, "r1"), EntryTarget.TeamPage(p, false, "r1").route())
        assertEquals(EntryTarget.TeamPage(p, true, "g1"), teamPageOf(p, "gate:g1"))
        assertNull(teamPageOf(p, "gate:"))
        assertNull(teamPageOf(p, "request-9"))
    }

    @Test
    fun `a phone team's bare id opens the team page, never a conversation`() {
        assertEquals(TeamRoute.Home(p), teamAlertRoute(AlertKind.TeamDecision, "request-9", "p1"))
        assertEquals(TeamRoute.Home(p), teamAlertRoute(AlertKind.TeamCompleted, "project-9", "p1"))
        assertEquals(TeamRoute.Home(p), teamAlertRoute(AlertKind.TeamRunFailed, "run:", "p1"))
    }

    @Test
    fun `a conversation's alert, or one with no server, is not a team tap`() {
        assertNull(teamAlertRoute(AlertKind.Complete, "ses_1", "p1"))
        assertNull(teamAlertRoute(AlertKind.Permission, "gate:g1", "p1"))
        assertNull(teamAlertRoute(null, "gate:g1", "p1"))
        assertNull(teamAlertRoute(AlertKind.TeamDecision, "gate:g1", ""))
    }

    @Test
    fun `the tap path asks for the team route before the conversation route`() {
        val hub = RepoFiles.text("androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlatformWiring.kt")
        val team = hub.indexOf("teamAlertRoute(")
        val chat = hub.indexOf("alertTap(intent)?.takeIf { trusted }")
        assertTrue(team in 0 until chat, "team alerts must be routed before the generic chat tap")
    }
}
