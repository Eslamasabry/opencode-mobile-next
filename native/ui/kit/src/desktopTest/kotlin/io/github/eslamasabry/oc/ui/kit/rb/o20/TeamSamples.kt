package io.github.eslamasabry.oc.ui.kit.rb.o20

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.team.KitDigest
import io.github.eslamasabry.oc.ui.kit.team.KitFindingSeverity
import io.github.eslamasabry.oc.ui.kit.team.KitFindingsCard
import io.github.eslamasabry.oc.ui.kit.team.KitMergeQueue
import io.github.eslamasabry.oc.ui.kit.team.KitMilestoneRow
import io.github.eslamasabry.oc.ui.kit.team.KitPhaseCard
import io.github.eslamasabry.oc.ui.kit.team.KitPlanCard
import io.github.eslamasabry.oc.ui.kit.team.KitProjectRow
import io.github.eslamasabry.oc.ui.kit.team.KitPromoteCard
import io.github.eslamasabry.oc.ui.kit.team.KitTeamFinding
import io.github.eslamasabry.oc.ui.kit.team.KitTeamItem
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState

/** test/kit/team_kit_test_support.dart `teamSample`, inside the golden tests' 16 dp padding. */
@Composable
internal fun TeamSample(part: String, state: KitTeamState = KitTeamState.NeedsYou) {
    val items =
        if (state == KitTeamState.Loading || state == KitTeamState.Empty) emptyList()
        else listOf(
            KitTeamItem("Data model", detail = "Backend · Home PC", meta = "App repo · 3 acceptance checks"),
            KitTeamItem("Settings screen", detail = "Frontend · after Data model", state = KitTeamState.Running),
        )
    val primary = KitAction("Approve and start", onClick = {})
    val title = "Milestone 2"
    val status = when (state) {
        KitTeamState.Empty -> "No work planned yet"
        KitTeamState.Loading -> "Loading the latest plan"
        KitTeamState.Running -> "Checking acceptance criteria · 4 min"
        KitTeamState.NeedsYou -> "Waiting for your review · 2 min"
        KitTeamState.Stalled -> "No answer from Home PC yet · 3 min"
        KitTeamState.Failed -> "The check could not finish. Retry the check."
        KitTeamState.Done -> "Accepted · 5 min ago"
        KitTeamState.Stale -> "Last known · Home PC offline · 20 min ago"
    }
    Box(Modifier.padding(16.dp)) {
        when (part) {
            "kit_plan_card" -> KitPlanCard(title, status, state = state, items = items, primary = primary)
            "kit_phase_card" -> KitPhaseCard(title, status, state = state, items = items, primary = primary)
            "kit_merge_queue" -> KitMergeQueue(title, status, state = state, items = items, primary = primary)
            "kit_promote_card" -> KitPromoteCard(title, status, state = state, items = items, primary = primary)
            "kit_digest" -> KitDigest(title, status, state = state, items = items, primary = primary)
            "kit_project_row" -> KitProjectRow(title, status, state = state, onPressed = {}, completed = 2, total = 5)
            "kit_milestone_row" -> KitMilestoneRow(title, status, state = state, onPressed = {}, completed = 2, total = 5)
            "kit_findings_card" -> KitFindingsCard(
                title, status,
                findings =
                    if (state == KitTeamState.Loading || state == KitTeamState.Empty) emptyList()
                    else listOf(
                        KitTeamFinding("critical", "Preserve the saved draft", "Critical", KitFindingSeverity.Critical, detail = "Criterion 1 · survives restart"),
                        KitTeamFinding("1", "Keep the saved choice after restart", "Major", KitFindingSeverity.Major, selected = true, onChanged = {}),
                        KitTeamFinding("2", "Explain the recovery action", "Minor"),
                    ),
                state = state, primary = primary,
            )
            else -> error(part)
        }
    }
}
