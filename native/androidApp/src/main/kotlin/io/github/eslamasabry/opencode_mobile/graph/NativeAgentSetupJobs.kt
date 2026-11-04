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
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.guard.AgentInstallGuard
import io.github.eslamasabry.oc.engine.agents.guard.requiredSetupFreeBytes
import io.github.eslamasabry.oc.engine.agents.install.AgentSetupComponent
import io.github.eslamasabry.oc.engine.agents.install.AgentSetupFailure
import io.github.eslamasabry.oc.engine.agents.install.AgentSetupJob
import io.github.eslamasabry.oc.engine.agents.install.AgentSetupJobState
import io.github.eslamasabry.oc.engine.agents.install.AgentSetupJobs
import io.github.eslamasabry.oc.engine.agents.scripts.AgentPhoneScripts
import io.github.eslamasabry.oc.engine.agents.scripts.PaseoLockCodec
import io.github.eslamasabry.oc.engine.agents.scripts.PaseoPhoneScripts
import io.github.eslamasabry.oc.engine.agents.scripts.SetupPrelude
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.opencode_mobile.BuildConfig
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.SetupRunner
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import org.json.JSONObject

/**
 * Durable setup in the existing native job runner. It shares the global installer slot with phone
 * setup.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class NativeAgentSetupJobs(
    private val app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val payloads: PhonePayloadGuard,
    private val payloadAdmission: NativePaseoAdmission,
) : AgentSetupJobs, ProfileCloser {
    override val owner: String = ":engine:agents"
    private val denied = ConcurrentHashMap.newKeySet<ProfileId>()
    private val admission = Any()
    private val linux by lazy { BuiltinLinux.get(app) }
    private val runner by lazy { SetupRunner.get(app) }
    val guard = AgentInstallGuard(BuildConfig.AGENT_INSTALL_MIN_FREE_BYTES)

    override suspend fun anySetupRunning(): Boolean? =
        (native {
                payloadAdmission.payloadsBusy() ||
                    runner.running ||
                    status()?.optString("state") == "running"
            }
                as? Outcome.Ok)
            ?.value

    override suspend fun freeBytes(): Long? =
        (native { app.filesDir.usableSpace.coerceAtLeast(0L) } as? Outcome.Ok)?.value

    override suspend fun start(
        owner: OwnerId,
        agent: AgentId,
        components: List<AgentSetupComponent>,
    ): Outcome<Unit> {
        val texts =
            SetupRunner.Texts(
                getString(Res.string.phoneSetupNotificationChannel),
                getString(Res.string.phoneSetupNotificationTitle),
                getString(Res.string.phoneSetupNotificationProgress, "{percent}"),
                getString(Res.string.phoneSetupNotificationDone),
                getString(Res.string.phoneSetupNotificationStopped),
            )
        val started = native {
            check(admitted(owner)) { "unavailable" }
            val descriptor = AgentCatalog.builtIn.byId(agent) ?: error("unavailable")
            val estimate = guard.downloadBytesFor(descriptor) ?: error("unavailable")
            val installed =
                descriptor.recipe?.artifacts?.values?.mapNotNull { it.installedBytes }?.maxOrNull()
            val required = requiredSetupFreeBytes(estimate, installed)
            check(app.filesDir.usableSpace >= required) { "storage" }
            val lock =
                app.assets.open("agents/paseo-package-lock.json").bufferedReader().use {
                    it.readText()
                }
            val paseo =
                PaseoPhoneScripts.install(
                    lock,
                    object : PaseoLockCodec {
                        override fun sha256Hex(bytes: ByteArray): String =
                            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                                "%02x".format(it)
                            }

                        override fun gzip(bytes: ByteArray): ByteArray =
                            ByteArrayOutputStream().use { out ->
                                GZIPOutputStream(out).use { it.write(bytes) }
                                out.toByteArray()
                            }
                    },
                )
            val specs = components.map { component ->
                val script =
                    when (component.id) {
                        "linux" -> null
                        "essentials" -> NativePhoneSetup.ESSENTIALS_INSTALL
                        "python" -> NativePhoneSetup.PYTHON_INSTALL
                        "agent-user" -> AgentPhoneScripts.bootstrapUser
                        "agent-node" -> PaseoPhoneScripts.nodeInstall
                        "agent-paseo" -> paseo
                        "agent-${agent.raw}" -> AgentPhoneScripts.install(descriptor)
                        else -> error("unavailable")
                    }
                SetupRunner.Spec(
                    id = component.id,
                    script = script?.let(SetupPrelude::with),
                    native = component.id == "linux",
                    step = false,
                    weight = 1.0,
                    skipped = component.id == "linux" && linux.installed,
                    version = null,
                    stage = null,
                    labels = emptyMap(),
                    data = mapOf("requiredFreeBytes" to required.toString()),
                    agentUser = component.agentUser,
                )
            }
            // Deletion revokes and drains the same admission lock before the profile sweep.
            val mutation =
                when (val claim = payloads.beginMutation(owner)) {
                    is Outcome.Failed -> error("busy")
                    is Outcome.Ok -> claim.value
                }
            try {
                synchronized(admission) {
                    check(admitted(owner)) { "unavailable" }
                    payloadAdmission.startSetup {
                        runner.start(
                            "agent-${UUID.randomUUID()}",
                            specs,
                            JSONObject().put("agentOwner", owner.raw).put("agentId", agent.raw),
                            texts,
                        )
                    }
                }
            } finally {
                mutation.release()
            }
        }
        return when (started) {
            is Outcome.Failed -> started
            is Outcome.Ok ->
                if (started.value) Outcome.Ok(Unit)
                else Outcome.Failed(Failure.Runtime(RuntimeProblem.SlotBusy))
        }
    }

    override suspend fun job(owner: OwnerId): Outcome<AgentSetupJob?> = native {
        val snapshot = status() ?: return@native null
        val params = snapshot.optJSONObject("params") ?: return@native null
        if (params.optString("agentOwner") != owner.raw) return@native null
        val agent = AgentId(params.optString("agentId"))
        check(AgentCatalog.builtIn.byId(agent) != null) { "unavailable" }
        val current = snapshot.optString("current").takeIf { it.isNotEmpty() && it != "null" }
        val component = current?.let { snapshot.optJSONObject("components")?.optJSONObject(it) }
        AgentSetupJob(
            agent,
            when (snapshot.optString("state")) {
                "running" -> AgentSetupJobState.Running
                "done" -> AgentSetupJobState.Done
                "failed" -> AgentSetupJobState.Failed
                else -> AgentSetupJobState.Interrupted
            },
            current,
            component?.optLong("done", -1L)?.takeIf { it >= 0 },
            component?.optLong("total", -1L)?.takeIf { it > 0 },
            if (snapshot.optString("state") == "failed") failure(snapshot) else null,
        )
    }

    override suspend fun cancel(owner: OwnerId, agent: AgentId): Outcome<Unit> = stop(owner, agent)

    private suspend fun stop(profile: ProfileId, agent: AgentId?): Outcome<Unit> {
        val stopped = native {
            val snapshot = status() ?: return@native false
            val params = snapshot.optJSONObject("params") ?: return@native false
            if (
                params.optString("agentOwner") != profile.raw ||
                    (agent != null && params.optString("agentId") != agent.raw)
            )
                return@native false
            runner.cancel()
            true
        }
        if (stopped is Outcome.Failed) return stopped
        if ((stopped as Outcome.Ok).value) {
            val ended =
                withTimeoutOrNull(15_000) {
                    while (withContext(dispatchers.io) { runner.running }) delay(100)
                    true
                } ?: false
            if (!ended) return Outcome.Failed(Failure.Runtime(RuntimeProblem.SlotBusy))
        }
        return Outcome.Ok(Unit)
    }

    private fun status(): JSONObject? = runner.status()?.let(::JSONObject)

    private fun admitted(profile: ProfileId): Boolean =
        profile !in denied &&
            stores.profiles.profile(profile) != null &&
            (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value == profile.raw

    private fun failure(snapshot: JSONObject): AgentSetupFailure {
        val text = snapshot.optString("error") + " " + snapshot.optString("logTail")
        return when {
            "checksum" in text.lowercase() -> AgentSetupFailure.Checksum
            "space" in text.lowercase() || "storage" in text.lowercase() ->
                AgentSetupFailure.Storage
            "version" in text.lowercase() -> AgentSetupFailure.Version
            "download" in text.lowercase() -> AgentSetupFailure.Download
            else -> AgentSetupFailure.Unknown
        }
    }

    override fun stopAdmission(profile: ProfileId) {
        synchronized(admission) { denied.add(profile) }
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = stop(profile, null)

    override fun reopen(profile: ProfileId) {
        synchronized(admission) { denied.remove(profile) }
    }

    private suspend fun <T> native(block: () -> T): Outcome<T> =
        withContext(dispatchers.io) {
            try {
                Outcome.Ok(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
            }
        }
}
