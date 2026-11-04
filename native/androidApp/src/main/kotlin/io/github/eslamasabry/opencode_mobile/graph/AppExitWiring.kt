package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitHistory
import io.github.eslamasabry.oc.engine.runtime.recovery.AppExitNotice
import io.github.eslamasabry.oc.engine.runtime.recovery.PreviousExitKind
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The same saved Flutter policy used by recovery; a bad or unreadable record never grants a
 * restart.
 */
internal fun restartPhoneServerAllowed(stores: PreviewStores, profile: ProfileId): Boolean =
    try {
        val saved = stores.prefs.get("oc.automation.${profile.raw}")
        if (saved == null) true
        else {
            val raw = (saved as? PrefValue.Str)?.value
            val policy = raw?.let(::JSONObject)
            policy != null &&
                policy.opt("version") == 1 &&
                policy.optString("supervision") in setOf("high", "balanced", "autonomous") &&
                policy.optJSONObject("behaviors")?.opt("restartPhoneServer") == true
        }
    } catch (_: Exception) {
        false
    }

@ContributesTo(AppScope::class)
interface AppExitAccessors {
    val appExit: AppExitHub
}

/**
 * B-ANDR-008: the app-wide account of the previous exit, from the same launch report diagnostics
 * consumed. This owner only observes recovery: a permission to restart is never presented as a
 * start that has actually begun.
 */
@SingleIn(AppScope::class)
@Inject
class AppExitHub(
    private val application: Application,
    private val stores: PreviewStores,
    private val exits: ExitHistory,
    private val phoneServer: PhoneServerSource,
    private val registry: ConnectionRegistry,
    appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
) {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private val mutableNotice = MutableStateFlow<AppExitNotice?>(null)
    val notice: StateFlow<AppExitNotice?> = mutableNotice.asStateFlow()
    private val mutableBack = MutableStateFlow(false)
    val serverBack: StateFlow<Boolean> = mutableBack.asStateFlow()
    private var started = false
    private var dismissed = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            val report = withContext(dispatchers.io) { exits.launchReport() }
            if (stores.warm() !is Outcome.Ok) return@launch
            val exit = report.exit?.takeIf { it.kind.notable } ?: return@launch
            val previous = report.previousServices
            if (previous.none { it == "server" || it == "aiteam" }) return@launch
            val stopped =
                withContext(dispatchers.io) {
                    val prefs =
                        application.getSharedPreferences(
                            "builtin_server_recovery",
                            Context.MODE_PRIVATE,
                        )
                    prefs.getBoolean("userStopped", !prefs.getBoolean("wanted", false))
                }
            if (stopped) return@launch
            val profile =
                (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)
                    ?.value
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::ProfileId)
            val original =
                AppExitNotice(
                    kind = PreviousExitKind.valueOf(exit.kind.name),
                    at = exit.at,
                    teamStopped = "aiteam" in previous,
                    recoveryAllowed = false,
                    phoneProfile = profile,
                )
            val connected =
                registry.live.flatMapLatest { live ->
                    profile?.let { live[it] }?.session?.server?.events?.status
                        ?: flowOf(StreamStatus.Connecting)
                }
            // A setting can be revoked while the start is pending; the next acknowledged preference
            // change rewords it.
            val policy =
                MutableStateFlow(profile?.let { restartPhoneServerAllowed(stores, it) } == true)
            launch {
                stores.prefs.changes.collect { changed ->
                    if (profile != null && "oc.automation.${profile.raw}" in changed) {
                        policy.value = restartPhoneServerAllowed(stores, profile)
                    }
                }
            }
            combine(phoneServer.facts, connected, policy) { facts, stream, permits ->
                    val back =
                        facts.probe == ProbeAnswer.Answered &&
                            facts.status?.serverRunning == true || stream == StreamStatus.Connected
                    back to original.copy(recoveryAllowed = permits && facts.starting && !back)
                }
                .collect { (back, next) ->
                    mutableBack.value = back
                    if (!dismissed)
                        mutableNotice.value = if (back && !next.offersKeepAlive) null else next
                }
        }
    }

    fun dismiss() {
        dismissed = true
        mutableNotice.value = null
    }
}
