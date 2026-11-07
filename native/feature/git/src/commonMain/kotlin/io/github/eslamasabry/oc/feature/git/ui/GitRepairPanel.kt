package io.github.eslamasabry.oc.feature.git.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.git.GitLinkRepairResult
import io.github.eslamasabry.oc.feature.git.presentation.GitActions
import io.github.eslamasabry.oc.feature.git.presentation.GitUi
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairAction
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairDetailLocal
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairFailed
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairHealthy
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairMissing
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairNotProject
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairRestored
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairWorking
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A visible repair path on the real Git page, including its failed/NotRepository states. */
@Composable
internal fun GitRepairPanel(state: GitUi, actions: GitActions) {
    val repair = state.repair
    if (!repair.available || state.directory?.startsWith("/root/projects/") != true) return
    KitRowGroup {
        KitRow(
            title = stringResource(Res.string.linuxGitRepairAction),
            supporting = stringResource(Res.string.linuxGitRepairDetailLocal),
            supportingMaxLines = 3,
            enabled = !state.working,
            disabledReason = stringResource(Res.string.linuxGitRepairWorking),
            onClick = actions::repairObjects,
        )
    }
    if (repair.failed) KitNotice(message = stringResource(Res.string.linuxGitRepairFailed))
    repair.report?.let { report ->
        val text =
            when (report.result) {
                GitLinkRepairResult.Healthy -> stringResource(Res.string.linuxGitRepairHealthy)
                GitLinkRepairResult.Repaired ->
                    pluralStringResource(Res.plurals.linuxGitRepairRestored, report.restored, report.restored)
                GitLinkRepairResult.MissingObjects ->
                    stringResource(Res.string.linuxGitRepairMissing)
                GitLinkRepairResult.NotRepository ->
                    stringResource(Res.string.linuxGitRepairNotProject)
            }
        KitNotice(message = text, notes = report.missing.take(20))
    }
}
