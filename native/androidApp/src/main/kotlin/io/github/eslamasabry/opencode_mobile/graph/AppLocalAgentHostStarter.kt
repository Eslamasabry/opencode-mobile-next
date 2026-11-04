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
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/** An explicit helper start; creating the binding never launches a process or changes a profile. */
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

    override suspend fun connect(): Outcome<Unit> = lane.withLock {
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
                    if (!linux.installed || phone.state.value.needsRestart) unavailable()
                    else {
                        linux.startPhoneAgentHelper(
                            owner.raw,
                            password,
                            PaseoConfig.render().json,
                            expectedHelperVersion = AgentPins.builtIn.paseo.version,
                        )
                        Outcome.Ok(Unit)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    unavailable()
                }
            }
        if (started is Outcome.Failed) return@withLock started
        // A live authenticated wire check, rather than a process handle, admits the connection.
        when (val checked = factory.paseo.checkConnection(ADDRESS, password, FOLDER)) {
            is PaseoProbeResult.Refused -> return@withLock Outcome.Failed(checked.failure)
            is PaseoProbeResult.InvalidInput -> return@withLock unavailable()
            is PaseoProbeResult.Verified -> Unit
        }
        if (phone.state.value.needsRestart || stores.profiles.profile(profile) == null)
            return@withLock unavailable()
        val id = ProfileId("${owner.raw}-agents")
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
        connections.connect(id)
    }

    private fun unavailable(): Outcome.Failed =
        Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))

    private companion object {
        const val ADDRESS = "ws://127.0.0.1:4099"
        const val FOLDER = "/root/projects"
        val ID = Regex("[A-Za-z0-9_-]{1,80}")
        val PASSWORD = Regex("[a-f0-9]{64}")
    }
}
