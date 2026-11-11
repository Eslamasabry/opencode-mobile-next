package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.check.PhoneCheckFailure
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentChecker
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallIssue
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

/**
 * "Check {agent}" on the Agents page (Flutter `runAgentPhoneCheck`): installs or refreshes what the agent needs through
 * the page's own install, starts the helper, asks it to answer, and stores `oc.agentPhoneGate.<owner>`. One check runs
 * at a time; the install it starts shows on the agent's tile like any install.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<PhoneAgentChecker>())
class AppPhoneAgentChecker(
    private val phones: AppPhoneAgents,
    private val helper: AppLocalAgentHostStarter,
) : PhoneAgentChecker {
    private val one = Mutex()

    override suspend fun check(id: String): Outcome<PhoneCheckFailure?> {
        if (!one.tryLock()) return Outcome.Ok(PhoneCheckFailure.Busy)
        try {
            val site = phones.checkSite(id) ?: return Outcome.Ok(PhoneCheckFailure.WrongArchitecture)
            val steps =
                object : PhoneCheckSteps {
                    override suspend fun clearProof() = phones.clearGate(site) is Outcome.Ok

                    override suspend fun install(): InstallEnd {
                        when (val started = phones.install(id)) {
                            is Outcome.Failed -> return InstallEnd.Failed
                            is Outcome.Ok ->
                                when (val answer = started.value) {
                                    is PhoneAgentInstallResult.Refused ->
                                        return when (answer.issue) {
                                            PhoneAgentInstallIssue.Busy -> InstallEnd.Busy
                                            PhoneAgentInstallIssue.Space -> InstallEnd.Space
                                            PhoneAgentInstallIssue.Unavailable -> InstallEnd.Failed
                                        }
                                    PhoneAgentInstallResult.Started -> Unit
                                }
                        }
                        // The page follows the install itself; this waits for it to end (at most a quarter hour).
                        repeat(WAIT_STEPS) {
                            delay(WAIT_MS)
                            phones.refresh()
                            val installation = phones.state.value.installation?.takeIf { it.id == id }
                            when (installation?.phase) {
                                PhoneAgentInstallPhase.Installing -> Unit
                                PhoneAgentInstallPhase.Failed,
                                PhoneAgentInstallPhase.Interrupted -> return InstallEnd.Failed
                                PhoneAgentInstallPhase.Done,
                                null -> return InstallEnd.Done
                            }
                        }
                        return InstallEnd.Failed
                    }

                    override suspend fun pinnedRuns() = phones.pinnedRuns(site)

                    override suspend fun helperAnswer() = helper.checkHelper()

                    override suspend fun saveProof() = phones.writeGate(site) is Outcome.Ok
                }
            val failure =
                PhoneAgentCheckRun(
                    steps,
                    AgentPins.builtIn.paseo.version,
                    helperTooOld = { (it.failure as? Failure.Runtime)?.problem == RuntimeProblem.HelperUpdateRequired },
                ).run()
            phones.refresh()
            return Outcome.Ok(failure)
        } finally {
            one.unlock()
        }
    }

    private companion object {
        const val WAIT_MS = 2_000L
        const val WAIT_STEPS = 450
    }
}
