package io.github.eslamasabry.oc.engine.linuxsetup

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.security.Redactor

/**
 * What the Set up page needs from the engine beyond [SetupEngine] (unit P12-BUILD-RUNTIME-C, Flutter
 * phone_setup_start_screen.dart): the default selection, its download size, and Details as [Redacted].
 */
public object SetupJourney {
    /** Required and default-on components, plus what they depend on, in install order (the "Set up" button's job). */
    public fun defaultJob(registry: List<SetupComponent>): List<SetupComponent> =
        expandSelection(registry, registry.filter { it.required || it.defaultOn }.map { it.id }.toSet())

    /** The ids "Set up" asks for. */
    public fun defaultIds(registry: List<SetupComponent>): Set<String> = defaultJob(registry).map { it.id }.toSet()

    /** The download the start page promises (display and preflight only, never consent evidence). */
    public fun downloadBytes(job: List<SetupComponent>): Long = job.sumOf { it.downloadBytes ?: 0L }

    /** The installed size the preflight admits against, when every component knows it; else null. */
    public fun installedBytes(job: List<SetupComponent>): Long? =
        job.mapNotNull { it.installedBytes }.takeIf { it.isNotEmpty() }?.sum()

    /** The job's Details, already redacted by the engine, as the kit's [Redacted]; null when nothing was logged. */
    public fun details(progress: SetupProgress, redactor: Redactor = Redactor()): Redacted? {
        val start = (progress.error as? SetupProblem.StartFailed)?.details.orEmpty()
        val text = listOf(start, setupLogForPeople(progress.logTail)).filter { it.isNotBlank() }.joinToString("\n")
        return if (text.isBlank()) null else redactor.details(text)
    }

    /** The app-run step (starting OpenCode) is due: the runner waits for the app to report it. */
    public fun stepDue(progress: SetupProgress): Boolean =
        progress.state == SetupState.Running && progress.current == SetupComponentIds.START &&
            progress.components.any { it.id == SetupComponentIds.START && it.state == ComponentState.Running }

    /** OpenCode was (re)installed by this job, so the start step restarts a running server. */
    public fun openCodeChanged(progress: SetupProgress): Boolean =
        progress.components.any { it.id == SetupComponentIds.OPENCODE && it.state == ComponentState.Done }
}
