package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.engine.runtime.history.HistoryCopyScripts
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.engine.runtime.switching.ManagedLocalServer
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveProblem
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveUi
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveWorld
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.OnboardingRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Root-owned upgrade: no session gateway exists until both local provenance and OC2 completion
 * agree.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ManagedMoveWorld>())
class RootManagedUpgrade(
    private val app: Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val setup: Lazy<NativePhoneSetup>,
    private val runtime: BuiltinRuntime,
    private val nav: AppNavigator,
    private val activities: ResumedActivities,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) : ManagedMoveWorld {
    private val scope =
        CoroutineScope(appScope.coroutineContext + dispatchers.serial("managed-upgrade"))
    private val shown = MutableStateFlow(ManagedMoveUi())
    override val state: StateFlow<ManagedMoveUi> = shown
    private var selected: ProfileId? = null
    private var activeAtStart: ProfileId? = null
    private var installing: Job? = null
    var reconnect: (suspend (ProfileId) -> Unit)? = null

    fun needsMove(profile: StoredProfile): Boolean =
        !profile.usesAgentSocket &&
            (profile.requiresOpenCode2Migration || profile.openCode2HistoryPending) &&
            (ManagedLocalServer.isBuiltin(profile.baseUrl) ||
                ManagedLocalServer.isTermux(profile.baseUrl))

    suspend fun prepare(id: ProfileId): Outcome<Unit> {
        val profile = stores.profiles.profile(id)?.profile ?: return refused()
        if (!needsMove(profile)) return Outcome.Ok(Unit)
        selected = id
        activeAtStart = stores.profiles.activeId.value
        withContext(dispatchers.main) { nav.open(OnboardingRoute.ManagedMove) }
        if (!ManagedLocalServer.isBuiltin(profile.baseUrl) || !current(profile)) {
            return fail(ManagedMoveProblem.NotReady)
        }
        if (profile.requiresOpenCode2Migration) {
            if (installing?.isActive != true) {
                shown.value = ManagedMoveUi()
                installing = scope.launch { install(profile) }
            }
            return refused()
        }
        shown.value = ManagedMoveUi()
        when (admission(profile, checkWork = false)) {
            Admission.Separate -> return fail(ManagedMoveProblem.SeparateHistory)
            Admission.Unknown -> return fail(ManagedMoveProblem.NotReady)
            Admission.Safe -> Unit
        }
        if (!current(profile)) return fail(ManagedMoveProblem.NotReady)
        val running = runtime.runtimeStatus()
        if ((running as? Outcome.Ok)?.value?.serverRunning != true) {
            val started = runtime.serverSource { Outcome.Ok(Unit) }.start()
            if (started is Outcome.Failed) return fail(ManagedMoveProblem.NotReady)
        }
        val confirmed = withTimeoutOrNull(180_000) { confirm(profile) } ?: false
        if (!confirmed) return fail(ManagedMoveProblem.NotConfirmed)
        val latest =
            stores.profiles.profile(id)?.profile ?: return fail(ManagedMoveProblem.NotConfirmed)
        if (!current(profile) || latest.requiresOpenCode2Migration)
            return fail(ManagedMoveProblem.NotConfirmed)
        val saved = stores.profiles.upsert(latest.copy(openCode2HistoryPending = false))
        if (saved is Outcome.Failed) return fail(ManagedMoveProblem.NotConfirmed)
        withContext(dispatchers.main) {
            if (nav.top.value == OnboardingRoute.ManagedMove) nav.land()
        }
        return Outcome.Ok(Unit)
    }

    private suspend fun install(profile: StoredProfile) {
        try {
            // App startup may reach this before the first Activity resume. Never start guest work
            // in the background.
            val visible =
                withTimeoutOrNull(10_000) {
                    while (withContext(dispatchers.main) { activities.current == null }) delay(100)
                    true
                } == true
            if (!visible || !current(profile) || registry.live.value[profile.id] != null) {
                fail(ManagedMoveProblem.NotReady)
                return
            }
            when (admission(profile)) {
                Admission.Separate -> {
                    fail(ManagedMoveProblem.SeparateHistory)
                    return
                }
                Admission.Unknown -> {
                    fail(ManagedMoveProblem.NotReady)
                    return
                }
                Admission.Safe -> Unit
            }
            if (!current(profile)) {
                fail(ManagedMoveProblem.NotReady)
                return
            }
            val owner = setup.value
            val started = owner.lifecycle.repair()
            if (started is Outcome.Failed) {
                fail(ManagedMoveProblem.Stopped)
                return
            }
            val end =
                withTimeoutOrNull(1_800_000) {
                    owner.progress.first {
                        it.state != SetupState.Running && it.state != SetupState.Idle
                    }
                }
            if (end?.state != SetupState.Done) {
                if (shown.value.problem == null) fail(ManagedMoveProblem.Stopped)
                return
            }
            // The real setup finisher calls PreviewRuntime.connect, which confirms pending history
            // before opening a graph.
            val saved = stores.profiles.profile(profile.id)?.profile
            if (
                saved == null || saved.requiresOpenCode2Migration || saved.openCode2HistoryPending
            ) {
                fail(ManagedMoveProblem.NotConfirmed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            fail(ManagedMoveProblem.Stopped)
        }
    }

    private enum class Admission {
        Safe,
        Separate,
        Unknown,
    }

    /**
     * Read-only before setup or any server stop; refuses separate DBs and unverified copy receipts.
     */
    private suspend fun admission(profile: StoredProfile, checkWork: Boolean = true): Admission =
        withContext(dispatchers.io) {
            if (!current(profile)) return@withContext Admission.Unknown
            try {
                val linux = BuiltinLinux.get(app)
                if (checkWork && linux.performance()["workHeld"] == true)
                    return@withContext Admission.Unknown
                val result = linux.run(HistoryCopyScripts.SWITCH_CHECK, 30)
                when {
                    result.exitCode == 0 -> Admission.Safe
                    result.output.contains(HistoryCopyScripts.SWITCH_BLOCKED_TEXT) ->
                        Admission.Separate
                    else -> Admission.Unknown
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Admission.Unknown
            }
        }

    private suspend fun confirm(profile: StoredProfile): Boolean {
        if (admission(profile, checkWork = false) != Admission.Safe) return false
        val secret = (stores.profiles.secret(profile.id) as? Outcome.Ok)?.value ?: return false
        if (secret.isEmpty()) return false
        val username = profile.username.ifEmpty { "opencode" }
        val authorization =
            "Basic " + Base64.getEncoder().encodeToString("$username:$secret".toByteArray())
        while (current(profile)) {
            val status = withContext(dispatchers.io) { migrationStatus(authorization) }
            if (!current(profile)) return false
            when (status) {
                "completed" -> return admission(profile, checkWork = false) == Admission.Safe
                "required",
                "running" -> delay(1_000)
                else -> return false
            }
        }
        return false
    }

    /**
     * Exact app-authored loopback target; no redirect can carry the private credential elsewhere.
     */
    private fun migrationStatus(authorization: String): String? {
        for (path in listOf("/api/experimental/migration/v1", "/experimental/migration/v1")) {
            val connection =
                URL("http", "127.0.0.1", ManagedLocalServer.BUILTIN_PORT, path).openConnection()
                    as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 2_000
                connection.readTimeout = 4_000
                connection.setRequestProperty("Authorization", authorization)
                if (connection.responseCode == 404) continue
                if (connection.responseCode !in 200..299) return null
                val buffer = ByteArray(65_537)
                val count =
                    connection.inputStream.use { input ->
                        var used = 0
                        while (used < buffer.size) {
                            val read = input.read(buffer, used, buffer.size - used)
                            if (read < 0) break
                            used += read
                        }
                        used
                    }
                if (count > 65_536) return null
                val json = JSONObject(String(buffer, 0, count, Charsets.UTF_8))
                return (json.optJSONObject("data") ?: json).optString("status").takeIf {
                    it.isNotEmpty()
                }
            } catch (_: Exception) {
                return null
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private fun current(expected: StoredProfile): Boolean {
        val now = stores.profiles.profile(expected.id)?.profile ?: return false
        val owner = (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value
        return selected == expected.id &&
            stores.profiles.activeId.value == activeAtStart &&
            owner == expected.id.raw &&
            now == expected &&
            ManagedLocalServer.isBuiltin(now.baseUrl)
    }

    private fun refused(): Outcome.Failed =
        Outcome.Failed(Failure.Refused(RefusalReason.MigrationPending))

    private fun fail(problem: ManagedMoveProblem): Outcome.Failed {
        shown.value = ManagedMoveUi(running = false, problem = problem)
        return refused()
    }

    override fun retry() {
        val id = selected ?: return
        if (installing?.isActive == true) return
        scope.launch { reconnect?.invoke(id) }
    }

    override fun changeServer() {
        nav.open(ServersRoute.Home)
    }
}
