package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.certification.AgentCertification
import io.github.eslamasabry.oc.engine.agents.tools.AgentToolAdapter
import io.github.eslamasabry.oc.engine.cards.CardAgent
import io.github.eslamasabry.oc.engine.cards.CardsInstallOutcome
import io.github.eslamasabry.oc.engine.cards.CardsInstallScripts
import io.github.eslamasabry.oc.engine.cards.CardsPins
import io.github.eslamasabry.oc.engine.cards.CardsProblem
import io.github.eslamasabry.oc.engine.cards.CardsQualifier
import io.github.eslamasabry.oc.engine.cards.CardsVerdict
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgents
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGateway
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Native scripts prove the registered tool only after the carried Claude transport qualification
 * matches.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<CardsQualifier>())
class PhoneAgentCardsQualifier(
    private val app: Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val dispatchers: AppDispatchers,
    private val phone: PhoneAgents,
) : CardsQualifier {
    private val linux
        get() = BuiltinLinux.get(app)

    private fun owner(profileId: String): String? {
        val id = ProfileId(profileId)
        val saved = stores.profiles.profile(id)?.profile ?: return null
        if (
            saved.backend != BackendKind.Paseo ||
                saved.baseUrl != "ws://127.0.0.1:4099" ||
                saved.codexDirectory != "/root/projects"
        )
            return null
        val mapped =
            (stores.prefs.get("oc.phoneAgentOwner.$profileId") as? PrefValue.Str)?.value?.takeIf {
                ID.matches(it)
            } ?: return null
        if (profileId != "$mapped-agents") return null
        val runtime =
            (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value ?: return null
        val runtimeOwner =
            (stores.prefs.get("oc.phoneAgentOwner.$runtime") as? PrefValue.Str)?.value ?: runtime
        return mapped.takeIf {
            it == runtimeOwner && stores.profiles.profile(ProfileId(runtime)) != null
        }
    }

    private fun gateway(profileId: String): PaseoGateway? {
        val graph = registry.live.value[ProfileId(profileId)] ?: return null
        if (stores.profiles.profile(graph.profile.id)?.toServerProfile() != graph.profile)
            return null
        return graph.session.server as? PaseoGateway
    }

    override fun managed(profileId: String): Boolean = owner(profileId) != null

    override fun agentsHere(profileId: String): List<CardAgent> =
        if (managed(profileId)) listOf(CardAgent.Claude) else emptyList()

    override suspend fun verify(profileId: String): Outcome<CardsVerdict> =
        apply(profileId, install = false)

    override suspend fun install(profileId: String): Outcome<CardsVerdict> =
        apply(profileId, install = true)

    private suspend fun apply(profileId: String, install: Boolean): Outcome<CardsVerdict> {
        val owner = owner(profileId) ?: return unavailable()
        val gateway = gateway(profileId) ?: return unavailable()
        gateway.setDisplayCardPermissionAllowance(null)
        val proof = native {
            if (phone.state.value.needsRestart || !linux.installed) return@native false
            val status = linux.agentHost.status(owner)
            val architecture =
                when (status["abi"]) {
                    "arm64-v8a" ->
                        AgentArchitecture.Arm64.takeIf { "arm64-v8a" in Build.SUPPORTED_ABIS }
                    "x86_64" -> AgentArchitecture.X64.takeIf { "x86_64" in Build.SUPPORTED_ABIS }
                    else -> null
                } ?: return@native false
            val helper = status["helperVersion"] as? String
            val agent = AgentCatalog.builtIn.byId(AgentId.CLAUDE) ?: return@native false
            if (
                status["running"] != true ||
                    helper != AgentPins.builtIn.paseo.version ||
                    !AgentToolAdapter.CLAUDE.cardsQualified ||
                    !AgentCertification.bundled
                        .capabilitiesFor(agent, architecture, helper)
                        .permissions
            )
                return@native false
            val pins =
                CardsPins(agent.recipe!!.version, AgentPins.builtIn.node.version, qualified = true)
            if (install) {
                val result =
                    linux.run(
                        CardsInstallScripts.install(owner, CardAgent.Claude, true, pins),
                        120,
                        agentUser = true,
                    )
                if (
                    CardsInstallOutcome.ofExitCode(result.exitCode) !=
                        CardsInstallOutcome.Registered
                )
                    return@native false
            }
            linux
                .run(
                    CardsInstallScripts.verification(owner, CardAgent.Claude, pins),
                    90,
                    agentUser = true,
                )
                .exitCode == 0
        }
        if (proof is Outcome.Failed) return proof
        if (
            (proof as Outcome.Ok).value != true ||
                this.gateway(profileId) !== gateway ||
                this.owner(profileId) != owner ||
                phone.state.value.needsRestart
        )
            return unavailable()
        gateway.setDisplayCardPermissionAllowance {
            !phone.state.value.needsRestart &&
                this.gateway(profileId) === gateway &&
                this.owner(profileId) == owner &&
                (stores.prefs.get("oc.genui.enabled.$profileId") as? PrefValue.Bool)?.value == true
        }
        return Outcome.Ok(CardsVerdict(listOf(CardAgent.Claude)))
    }

    override suspend fun remove(profileId: String): Outcome<Unit> {
        gateway(profileId)?.setDisplayCardPermissionAllowance(null)
        val owner =
            owner(profileId) ?: return Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
        return when (
            val removed = native {
                CardsInstallOutcome.ofExitCode(
                    linux
                        .run(
                            CardsInstallScripts.install(owner, CardAgent.Claude, false),
                            90,
                            agentUser = true,
                        )
                        .exitCode
                )
            }
        ) {
            is Outcome.Failed -> removed
            is Outcome.Ok ->
                when (removed.value) {
                    CardsInstallOutcome.Registered,
                    CardsInstallOutcome.RemovalPending,
                    CardsInstallOutcome.NotInstalled -> Outcome.Ok(Unit)
                    else -> Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
                }
        }
    }

    private fun unavailable(): Outcome<CardsVerdict> =
        Outcome.Ok(CardsVerdict(emptyList(), CardsProblem.NotQualified, listOf(CardAgent.Claude)))

    private suspend fun <T> native(block: () -> T): Outcome<T> =
        withContext(dispatchers.io) {
            try {
                Outcome.Ok(block())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
            }
        }

    private companion object {
        val ID = Regex("[A-Za-z0-9_-]{1,80}")
    }
}
