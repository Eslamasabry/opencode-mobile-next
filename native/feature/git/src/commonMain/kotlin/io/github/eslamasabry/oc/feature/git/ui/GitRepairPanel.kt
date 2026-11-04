package io.github.eslamasabry.oc.feature.git.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.git.GitLinkRepairResult
import io.github.eslamasabry.oc.feature.git.presentation.GitActions
import io.github.eslamasabry.oc.feature.git.presentation.GitUi
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairAction
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairAppSettings
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairDetail
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairFailed
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairHealthy
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairMissing
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairNoAnswer
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairNotProject
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairPermission
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairRestored
import io.github.eslamasabry.oc.ui.words.res.linuxGitRepairTermuxMissing
import org.jetbrains.compose.resources.stringResource

/** A visible repair path on the real Git page, including its failed/NotRepository states. */
@Composable
internal fun GitRepairPanel(state: GitUi, actions: GitActions) {
    val repair = state.repair
    if (!repair.available || state.directory?.startsWith("/root/projects/") != true) return
    KitRowGroup {
        KitRow(
            title = stringResource(Res.string.linuxGitRepairAction),
            supporting = stringResource(Res.string.linuxGitRepairDetail),
            supportingMaxLines = 3,
            enabled = !state.working,
            onClick = actions::repairObjects,
        )
    }
    if (repair.failed) KitNotice(message = stringResource(Res.string.linuxGitRepairFailed))
    repair.report?.let { report ->
        val text =
            when (report.result) {
                GitLinkRepairResult.Healthy -> stringResource(Res.string.linuxGitRepairHealthy)
                GitLinkRepairResult.Repaired ->
                    stringResource(Res.string.linuxGitRepairRestored, report.restored)
                GitLinkRepairResult.MissingObjects ->
                    stringResource(Res.string.linuxGitRepairMissing)
                GitLinkRepairResult.TermuxMissing ->
                    stringResource(Res.string.linuxGitRepairTermuxMissing)
                GitLinkRepairResult.TermuxPermission ->
                    stringResource(Res.string.linuxGitRepairPermission)
                GitLinkRepairResult.TermuxUnavailable ->
                    stringResource(Res.string.linuxGitRepairNoAnswer)
                GitLinkRepairResult.NotRepository ->
                    stringResource(Res.string.linuxGitRepairNotProject)
            }
        KitNotice(
            message = text,
            notes = report.missing.take(20),
            actions =
                if (report.result == GitLinkRepairResult.TermuxPermission)
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.linuxGitRepairAppSettings),
                            actions::openRepairSettings,
                        )
                    )
                else emptyList(),
        )
    }
}
