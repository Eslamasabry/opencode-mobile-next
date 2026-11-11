package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
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
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.host.PaseoConfig
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.engine.profiles.SecretEdit
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentHostStarter
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgents
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoProbeResult
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentsPhoneHelperProfile
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.PhoneAgentHost
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/**
 * Starts the phone agent helper and connects its conversations. The Agents page calls it on its own once an agent is
 * installed; creating the binding never launches a process or changes a profile. Each failure names its real reason
 * (no built-in Linux, a helper that did not start, a key that could not be read, a helper that did not answer).
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<PhoneAgentHostStarter>())
class AppLocalAgentHostStarter(
    private val app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
    private val phone: PhoneAgents,
    private val connections: ServerConnections,
    private val factory: AppPaseoGatewayFactory,
) : PhoneAgentHostStarter {
    private val lane = Mutex()

    override suspend fun connect(): Outcome<Unit> {
        // The lock covers the helper and the saved profile only: connecting the profile asks [ensureHelper] again
        // (it is the same path any later connection of this server takes), so it must not run under the lock.
        val id =
            when (val prepared = prepare(save = true, budgetMs = HelperAnswerWait.BUDGET_MS)) {
                is Outcome.Failed -> return prepared
                is Outcome.Ok -> prepared.value
            }
        return connections.connect(id)
    }

    /**
     * Makes sure the helper behind the saved "Phone agents" server [id] runs and answers, starting it when it does
     * not (Flutter started it whenever that server was needed, not only from the Agents page). Nothing is saved.
     * Ok for any other server: only this one is the helper's own.
     */
    suspend fun ensureHelper(id: ProfileId): Outcome<Unit> {
        val owned = (stores.prefs.get("oc.phoneAgentOwner.${id.raw}") as? PrefValue.Str)?.value
        if (!PhoneAgentRules.isHelperProfile(id.raw, owned)) return Outcome.Ok(Unit)
        return when (val prepared = prepare(save = false, budgetMs = GATE_BUDGET_MS)) {
            is Outcome.Failed -> prepared
            is Outcome.Ok -> if (prepared.value == id) Outcome.Ok(Unit) else unavailable()
        }
    }

    private suspend fun prepare(save: Boolean, budgetMs: Long): Outcome<ProfileId> = lane.withLock {
        if (phone.state.value.needsRestart) return@withLock unavailable()
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return@withLock warm
            is Outcome.Ok -> Unit
        }
        val profile =
            (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)
                ?.value
                ?.takeIf { ID.matches(it) }
                ?.let(::ProfileId) ?: return@withLock unavailable()
        if (stores.profiles.profile(profile) == null) return@withLock unavailable()
        val owner =
            (stores.prefs.get("oc.phoneAgentOwner.${profile.raw}") as? PrefValue.Str)
                ?.value
                ?.takeIf { ID.matches(it) }
                ?.let(::ProfileId) ?: profile
        val key = "oc.agentHostSecret.${owner.raw}"
        val read =
            when (val value = stores.secrets.read(key)) {
                is Outcome.Failed -> return@withLock value
                is Outcome.Ok -> value.value
            }
        if (read != null && !PASSWORD.matches(read)) return@withLock unavailable()
        val password =
            read
                ?: ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") {
                    "%02x".format(it)
                }
        redactor.registerSecret(password)
        if (read == null)
            when (val saved = stores.secrets.write(key, password)) {
                is Outcome.Failed -> return@withLock saved
                is Outcome.Ok -> Unit
            }
        val started =
            withContext(dispatchers.io) {
                try {
                    val linux = BuiltinLinux.get(app)
                    if (!linux.installed) Outcome.Failed(Failure.Runtime(RuntimeProblem.NotInstalled))
                    else if (phone.state.value.needsRestart) unavailable()
                    else {
                        linux.startPhoneAgentHelper(
                            owner.raw,
                            password,
                            PaseoConfig.render().json,
                            expectedHelperVersion = AgentPins.builtIn.paseo.version,
                            // As the Flutter app's ordinary start: an older tested helper runs as it is.
                            testedHelperVersions = AgentPins.builtIn.paseoTestedVersions.toSet(),
                        )
                        Outcome.Ok(Unit)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A helper that is missing or too old to run is its own reason, with its own way forward.
                    Outcome.Failed(
                        Failure.Runtime(
                            if (PhoneAgentHost.failureCode(e) == "helper_update_required")
                                RuntimeProblem.HelperUpdateRequired
                            else RuntimeProblem.ServerStartUnavailable
                        )
                    )
                }
            }
        if (started is Outcome.Failed) return@withLock started
        // A live authenticated wire check, rather than a process handle, admits the connection.
        // The helper may still be starting (20 s or more on a slow phone): ask again as the Flutter app did.
        val answer =
            HelperAnswerWait.until(
                now = { android.os.SystemClock.elapsedRealtime() },
                pause = { kotlinx.coroutines.delay(it) },
                done = { it !is PaseoProbeResult.Refused || !HelperAnswerWait.stillStarting(it.failure) },
                budgetMs = budgetMs,
            ) {
                factory.paseo.checkConnection(ADDRESS, password, FOLDER)
            }
        when (val checked = answer) {
            is PaseoProbeResult.Refused -> return@withLock Outcome.Failed(checked.failure)
            is PaseoProbeResult.InvalidInput -> return@withLock unavailable()
            is PaseoProbeResult.Verified -> Unit
        }
        if (phone.state.value.needsRestart || stores.profiles.profile(profile) == null)
            return@withLock unavailable()
        val id = ProfileId("${owner.raw}$SUFFIX")
        if (!save) return@withLock Outcome.Ok(id)
        val existing = stores.profiles.profile(id)?.profile
        if (
            existing != null &&
                (existing.backend != BackendKind.Paseo ||
                    existing.baseUrl != ADDRESS ||
                    existing.codexDirectory != FOLDER)
        )
            return@withLock unavailable()
        when (
            val mapped =
                stores.prefs.commit(stores.lanes.forProfile(id)) {
                    putString("oc.phoneAgentOwner.${id.raw}", owner.raw)
                }
        ) {
            is Outcome.Failed -> return@withLock mapped
            is Outcome.Ok -> Unit
        }
        when (
            val saved =
                stores.profiles.upsert(
                    existing
                        ?: StoredProfile(
                            id,
                            getString(Res.string.agentsPhoneHelperProfile),
                            ADDRESS,
                            backend = BackendKind.Paseo,
                            codexDirectory = FOLDER,
                        ),
                    SecretEdit.Set(password),
                )
        ) {
            is Outcome.Failed -> return@withLock saved
            is Outcome.Ok -> Unit
        }
        Outcome.Ok(id)
    }

    private fun unavailable(): Outcome.Failed =
        Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))

    private companion object {
        const val SUFFIX = "-agents"
        const val GATE_BUDGET_MS = 30_000L
        const val ADDRESS = "ws://127.0.0.1:4099"
        const val FOLDER = "/root/projects"
        val ID = Regex("[A-Za-z0-9_-]{1,80}")
        val PASSWORD = Regex("[a-f0-9]{64}")
    }
}
