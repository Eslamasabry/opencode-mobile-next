package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.engine.diagnostics.exit.LifecycleSource
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.lifecycle.AppStorageFacts
import io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecyclePorts
import io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecycleReportController
import io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecycleTarget
import io.github.eslamasabry.oc.engine.runtime.lifecycle.ProjectStorageFacts
import io.github.eslamasabry.oc.engine.runtime.recovery.LaunchFacts
import io.github.eslamasabry.oc.engine.runtime.recovery.PreviousExit
import io.github.eslamasabry.oc.engine.runtime.recovery.PreviousExitKind
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneStateDetails
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** B-STATE-025/027/028: real read-only observations, reachable on Settings › This phone. */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<PhoneStateDetails>())
@Inject
class AndroidPhoneStateDetails(
    private val application: Application,
    private val stores: PreviewStores,
    private val source: LifecycleSource,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) : PhoneStateDetails {
    private val linux by lazy { BuiltinLinux.get(application) }

    private fun selected() = stores.profiles.activeId.value?.let(stores.profiles::profile)

    private fun eligible(): Boolean {
        val loaded = selected() ?: return false
        if (stores.prefs.string("oc.builtinServerOwner") != loaded.id.raw) return false
        val address =
            try {
                URI(loaded.profile.baseUrl)
            } catch (_: Exception) {
                return false
            }
        return address.scheme == "http" &&
            address.host == "127.0.0.1" &&
            address.port == PhoneServerScripts.PORT
    }

    private fun target(): LifecycleTarget? {
        if (!eligible()) return null
        val loaded = selected() ?: return null
        val config =
            loaded.profile.orchestration?.encoded?.let { DartJson.decodeOrNull(it) as? Map<*, *> }
        val team = config?.get("provider") == "gascity" && config["url"] == "http://127.0.0.1:8472"
        return LifecycleTarget(
            DartJson.sha256Hex(
                loaded.id.raw +
                    ":" +
                    loaded.profile.baseUrl +
                    ":" +
                    loaded.profile.username +
                    ":" +
                    loaded.profile.orchestration?.encoded.orEmpty()
            ),
            team,
        )
    }

    private val controller =
        LifecycleReportController(
            object : LifecyclePorts {
                override fun target(): LifecycleTarget? = this@AndroidPhoneStateDetails.target()

                override suspend fun history(): Outcome<LaunchFacts> =
                    withContext(dispatchers.io) {
                        when (val report = source.launchReport()) {
                            is Outcome.Failed -> report
                            is Outcome.Ok ->
                                Outcome.Ok(
                                    LaunchFacts(
                                        exit =
                                            report.value.exit
                                                ?.takeIf { it.hasTime }
                                                ?.let {
                                                    PreviousExit(
                                                        PreviousExitKind.valueOf(it.kind.name),
                                                        it.at,
                                                    )
                                                },
                                        previousServices =
                                            report.value.previousServices.filterNotNull().toList(),
                                    )
                                )
                        }
                    }

                override suspend fun status(): Outcome<RuntimeStatus> =
                    withContext(dispatchers.io) {
                        attempt({ Failure.Device() }) {
                            val installed =
                                BuiltinDiscovery.presence(application.filesDir) ==
                                    io.github.eslamasabry.oc.feature.linux.host.LinuxPresence.Ready
                            RuntimeStatus(
                                installed = installed,
                                phase = if (installed) RuntimePhase.Ready else RuntimePhase.Idle,
                                serverRunning = installed && linux.serverRunning,
                                services = if (installed) linux.runningServices() else emptyList(),
                            )
                        }
                    }

                override suspend fun authenticatedHealth(
                    target: LifecycleTarget
                ): Outcome<Boolean> {
                    val profile = selected() ?: return Outcome.Ok(false)
                    if (
                        this@AndroidPhoneStateDetails.target() != target || profile.reentry.password
                    )
                        return Outcome.Ok(false)
                    val secret =
                        (stores.profiles.secret(profile.id) as? Outcome.Ok)?.value?.takeIf {
                            it.isNotEmpty()
                        } ?: return Outcome.Ok(false)
                    val username = profile.profile.username.ifEmpty { PhoneServerScripts.USERNAME }
                    return withContext(dispatchers.io) {
                        attempt({ Failure.Device() }) {
                            if (this@AndroidPhoneStateDetails.target() != target) false
                            else {
                                val authorization =
                                    "Basic " +
                                        Base64.getEncoder()
                                            .encodeToString(
                                                "$username:$secret".toByteArray(Charsets.UTF_8)
                                            )
                                var healthy = false
                                for (path in PhoneServerScripts.PROBE_PATHS) {
                                    val status =
                                        request(PhoneServerScripts.PORT, path, authorization)?.first
                                    if (status == 404) continue
                                    healthy = status != null && status in 200..299
                                    break
                                }
                                healthy
                            }
                        }
                    }
                }

                override suspend fun teamSupervisorHealth(): Outcome<Boolean> =
                    teamHealth("/health")

                override suspend fun teamCityHealth(): Outcome<Boolean> =
                    teamHealth("/v0/city/phone/health")
            }
        )
    override val recovery = controller.value

    init {
        // The controller is confined to the main thread, like the page that refreshes it: its
        // epoch fence is unsynchronized, so a profile-change invalidation must not race a refresh.
        appScope.launch(dispatchers.main) {
            combine(stores.profiles.activeId, stores.profiles.profiles) { _, _ -> target() }
                .distinctUntilChanged()
                .collect { controller.invalidate() }
        }
    }

    override fun invalidate() = controller.invalidate()

    override fun dismissRecovery() = controller.dismiss()

    override suspend fun refresh() {
        if (stores.warm() !is Outcome.Ok) {
            controller.invalidate()
            return
        }
        controller.refresh()
    }

    /**
     * The same tolerant walk as Export projects (port of Flutter's project_export.dart scanner): an
     * entry that vanishes or cannot be read while OpenCode works counts as nothing, so a running
     * server never turns the whole measurement into "could not be measured".
     */
    override suspend fun measureStorage(): Outcome<AppStorageFacts> =
        withContext(dispatchers.io) {
            attempt({ Failure.StorageRefused() }) {
                appStorageFacts(projectFiles.measure(stores.profiles.profiles.value.size))
            }
        }

    private val projectFiles by lazy { NativeProjectFiles(application) }

    private suspend fun teamHealth(path: String): Outcome<Boolean> =
        withContext(dispatchers.io) {
            attempt({ Failure.Device() }) {
                val reply = request(8472, path) ?: return@attempt false
                reply.first in 200..299 &&
                    (DartJson.decodeOrNull(reply.second) as? Map<*, *>)?.get("status") == "ok"
            }
        }

    private fun request(
        port: Int,
        path: String,
        authorization: String? = null,
    ): Pair<Int, String>? {
        val connection = URL("http", "127.0.0.1", port, path).openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 4_000
        connection.instanceFollowRedirects = false
        authorization?.let { connection.setRequestProperty("Authorization", it) }
        return try {
            val status = connection.responseCode
            val body =
                if (authorization == null && status in 200..299)
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(16_385)
                        var count = 0
                        while (count < buffer.size) {
                            val read = input.read(buffer, count, buffer.size - count)
                            if (read < 0) break
                            count += read
                        }
                        if (count > 16_384) "" else String(buffer, 0, count, Charsets.UTF_8)
                    }
                else ""
            status to body
        } finally {
            connection.disconnect()
        }
    }
}

/** Export projects' measurement as This phone's App storage reads it (one walk, one set of numbers). */
internal fun appStorageFacts(
    facts: io.github.eslamasabry.oc.feature.phone.storage.ProjectStorageFacts
): AppStorageFacts =
    AppStorageFacts(
        projects =
            facts.projects.map { ProjectStorageFacts(it.name, it.bytes, it.files, it.privateFiles) },
        serverInstalled = facts.serverInstalled,
        serverBytes = facts.serverBytes,
        privateDataBytes = facts.privateDataBytes,
        savedServers = facts.savedServers,
    )
