package io.github.eslamasabry.oc.feature.team.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** The AI Team jobs that can fail and be reported (the team rows of the Flutter `FailedJobKind`). */
public enum class TeamFailureKind { Setup, Work }

/**
 * Opens Report a problem with a failed AI Team job attached (Flutter P8.4 "Report this failure"). [log] is whatever
 * technical text the job kept (empty when none); the app side redacts and bounds everything before it is shown.
 */
public fun interface TeamFailureReports {
    public fun report(kind: TeamFailureKind, jobId: String, title: String, detail: String, log: String)
}

/** Desktop and tests: nothing to open. Android replaces it (graph/TeamFailureWiring.kt). */
@ContributesBinding(AppScope::class)
@Inject
public class NoTeamFailureReports : TeamFailureReports {
    override fun report(kind: TeamFailureKind, jobId: String, title: String, detail: String, log: String): Unit = Unit
}
