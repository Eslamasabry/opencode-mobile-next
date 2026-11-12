package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.diagnostics.job.FailedJobKind
import io.github.eslamasabry.oc.engine.diagnostics.job.FailedJobReport
import io.github.eslamasabry.oc.feature.team.presentation.NoTeamFailureReports
import io.github.eslamasabry.oc.feature.team.presentation.TeamFailureKind
import io.github.eslamasabry.oc.feature.team.presentation.TeamFailureReports
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.SettingsRoute

/**
 * "Report this failure" for a failed AI Team set-up or task: the job is redacted and bounded into the same
 * attachment the other failed jobs use, then Report a problem opens with it. Nothing is sent until the person submits.
 */
@ContributesBinding(AppScope::class, replaces = [NoTeamFailureReports::class])
@Inject
class AppTeamFailureReports(
    private val redactor: Redactor,
    private val reports: GitHubProblemReporter,
    private val nav: AppNavigator,
) : TeamFailureReports {
    override fun report(kind: TeamFailureKind, jobId: String, title: String, detail: String, log: String) {
        val report =
            FailedJobReport.job(
                redactor,
                when (kind) {
                    TeamFailureKind.Setup -> FailedJobKind.TeamSetup
                    TeamFailureKind.Work -> FailedJobKind.TeamWork
                },
                jobId,
                title,
                detail,
                log,
            )
        val attachment = report.toAttachment()
        reports.attachFailure(attachment.title, attachment.details, attachment.source, attachment.log)
        nav.open(SettingsRoute.ReportProblem)
    }
}
