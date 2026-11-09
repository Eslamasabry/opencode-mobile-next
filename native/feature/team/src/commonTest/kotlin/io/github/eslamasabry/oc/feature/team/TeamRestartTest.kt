package io.github.eslamasabry.oc.feature.team

import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamPlanningState
import io.github.eslamasabry.oc.engine.team.TeamProject
import io.github.eslamasabry.oc.engine.team.TeamState
import io.github.eslamasabry.oc.feature.team.presentation.Reason
import io.github.eslamasabry.oc.feature.team.presentation.TeamPresent
import io.github.eslamasabry.oc.feature.team.presentation.TeamReasons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeamRestartTest {
    @Test
    fun `B-186 the engine's restart codes have plain words`() {
        assertEquals(Reason.ServerRestarted, TeamReasons.reason("serverRestarted"))
        assertEquals(Reason.RestartResend, TeamReasons.reason("restartResend"))
    }

    @Test
    fun `B-186 a plan cut off by a restart offers Resume, not a new plan`() {
        val p =
            TeamProject(
                id = "a",
                status = "failed",
                simulated = false,
                planningState = TeamPlanningState(stage = "interrupted", reason = "serverRestarted"),
            )
        val view = TeamPresent.project(TeamState(TeamPhase.Ready, workspace = team(p), health = READY_HEALTH), "a", NOW).project!!
        assertTrue(view.planFailed!!.resumable)
    }
}
