package io.github.eslamasabry.oc.ui.kit.rb.o28

import io.github.eslamasabry.oc.ui.kit.team.KitFindingSeverity
import io.github.eslamasabry.oc.ui.kit.team.KitPlanPhase
import io.github.eslamasabry.oc.ui.kit.team.KitPlanTask
import io.github.eslamasabry.oc.ui.kit.team.KitTeamFinding
import io.github.eslamasabry.oc.ui.kit.team.KitTeamItem
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Order 28, part-kit_team_data: every enum order and default of lib/ui/kit/team/kit_team_data.dart at 8302de537. */
class KitTeamDataParityTest {
    @Test
    fun `RB28-D01 team states keep Flutter's order`() {
        // kit_team_data.dart:4-13
        assertEquals(
            listOf("Empty", "Loading", "Running", "NeedsYou", "Stalled", "Failed", "Done", "Stale"),
            KitTeamState.entries.map { it.name },
        )
    }

    @Test
    fun `RB28-D02 finding severities keep Flutter's order`() {
        // kit_team_data.dart:36
        assertEquals(listOf("Critical", "Major", "Minor"), KitFindingSeverity.entries.map { it.name })
    }

    @Test
    fun `RB28-D03 a team item defaults to done with no detail, meta, trailing or tap`() {
        // kit_team_data.dart:17-24
        val item = KitTeamItem(title = "Review")
        assertEquals(KitTeamState.Done, item.state)
        assertNull(item.detail)
        assertNull(item.meta)
        assertNull(item.trailing)
        assertNull(item.onPressed)
    }

    @Test
    fun `RB28-D04 a finding defaults to minor, unselected and read only`() {
        // kit_team_data.dart:40-49
        val finding = KitTeamFinding(id = "f1", title = "Null check", severityLabel = "Minor")
        assertEquals(KitFindingSeverity.Minor, finding.severity)
        assertFalse(finding.selected)
        assertNull(finding.detail)
        assertNull(finding.location)
        assertNull(finding.onChanged)
    }

    @Test
    fun `RB28-D05 a plan task defaults to plain who with no detail`() {
        // kit_team_data.dart:63-69
        val task = KitPlanTask(number = 3, title = "Write tests")
        assertEquals(3, task.number)
        assertNull(task.detail)
        assertNull(task.who)
        assertNull(task.onChangeWho)
    }

    @Test
    fun `RB28-D06 a plan phase holds its tasks in order and no flag by default`() {
        // kit_team_data.dart:83-87
        val tasks = listOf(KitPlanTask(1, "One"), KitPlanTask(2, "Two"))
        val phase = KitPlanPhase(title = "Build", tasks = tasks)
        assertEquals(listOf(1, 2), phase.tasks.map { it.number })
        assertNull(phase.flagLabel)
        assertEquals("Check the schema", KitPlanPhase("Build", tasks, flagLabel = "Check the schema").flagLabel)
    }
}
