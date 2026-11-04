package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.cards.CardAgent
import io.github.eslamasabry.oc.engine.cards.CardsInstallOutcome
import io.github.eslamasabry.oc.engine.cards.CardsInstallScripts
import io.github.eslamasabry.oc.engine.cards.CardsProblem
import io.github.eslamasabry.oc.engine.cards.CardsQualifier
import io.github.eslamasabry.oc.engine.cards.CardsStatus
import io.github.eslamasabry.oc.engine.cards.CardsVerdict
import io.github.eslamasabry.oc.engine.cards.ManagedCardsInstaller
import io.github.eslamasabry.oc.engine.runtime.switching.ManagedLocalServer
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * "Cards from agents" on the phone's own server (B-CHAT-040..042, 047). The phone manages the built-in Linux, so the
 * Cards switch runs the app-authored install, removal and verification scripts there (Claude Code under the agent
 * user, OpenCode 2 as root) and reports a tool ready only when the verification script's exit status says so. A
 * connection that is not the phone's own server never reaches this class: it is verified over the server alone.
 */
internal class PhoneCardsQualifier(
    private val app: Application,
    private val dispatchers: AppDispatchers,
    private val stores: PreviewStores,
) : CardsQualifier {
    private val linux by lazy { BuiltinLinux.get(app) }

    private val installer =
        ManagedCardsInstaller(
            runner = { profileId, agent, enabled ->
                exit(CardsInstallScripts.install(profileId, agent, enabled), agent)
                    .let { CardsInstallOutcome.ofExitCode(it) }
            },
            verifier = { profileId, agent -> exit(CardsInstallScripts.verification(profileId, agent), agent) == 0 },
        )

    private suspend fun exit(script: String, agent: CardAgent): Int =
        withContext(dispatchers.io) {
            try {
                linux.run(script, TIMEOUT_SECONDS, agentUser = CardsInstallScripts.runsAsAgentUser(agent)).exitCode
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                -1 // a refused launch is a failure, never a success
            }
        }

    /**
     * Flutter `_genUiManaged`: the profile's address is the built-in Linux server (`http://127.0.0.1:4097`), whichever
     * profile currently owns the runtime. A Termux or remote address is never set up from here.
     */
    override fun managed(profileId: String): Boolean =
        try {
            ManagedLocalServer.isBuiltin(stores.profiles.profile(ProfileId(profileId))?.profile?.baseUrl)
        } catch (e: RuntimeException) {
            false
        }

    override fun agentsHere(profileId: String): List<CardAgent> = listOf(CardAgent.Claude, CardAgent.OpenCode2)

    override suspend fun verify(profileId: String): Outcome<CardsVerdict> {
        val ready = ArrayList<CardAgent>()
        for (agent in agentsHere(profileId)) {
            // Only the evidence-backed agent is verified at all; OpenCode 2 stays unqualified.
            if (agent == CardAgent.Claude && exit(CardsInstallScripts.verification(profileId, agent), agent) == 0) ready += agent
        }
        if (ready.isEmpty()) return Outcome.Ok(CardsVerdict(emptyList(), CardsProblem.VerificationFailed, agentsHere(profileId)))
        val missing = agentsHere(profileId) - ready.toSet()
        return Outcome.Ok(CardsVerdict(ready, if (missing.isEmpty()) null else CardsProblem.NotQualified, missing))
    }

    override suspend fun install(profileId: String): Outcome<CardsVerdict> = Outcome.Ok(verdict(installer.setEnabled(profileId, agentsHere(profileId), true)))

    override suspend fun remove(profileId: String): Outcome<Unit> {
        installer.setEnabled(profileId, agentsHere(profileId), false)
        return Outcome.Ok(Unit)
    }

    private fun verdict(status: CardsStatus): CardsVerdict =
        when (status) {
            is CardsStatus.On -> CardsVerdict(status.agents)
            is CardsStatus.Partial -> CardsVerdict(status.agents, status.reason, status.affected)
            is CardsStatus.Unavailable -> CardsVerdict(emptyList(), status.reason, status.affected)
            is CardsStatus.Failed -> CardsVerdict(emptyList(), status.reason, status.affected)
            is CardsStatus.RestartRequired, CardsStatus.Off, CardsStatus.Checking ->
                CardsVerdict(emptyList(), CardsProblem.VerificationFailed)
        }

    private companion object {
        const val TIMEOUT_SECONDS = 120L
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
object PhoneCardsAndroidBindings {
    @Provides
    @IntoSet
    @SingleIn(AppScope::class)
    fun phoneCards(app: Application, dispatchers: AppDispatchers, stores: PreviewStores): CardsQualifier =
        PhoneCardsQualifier(app, dispatchers, stores)
}
