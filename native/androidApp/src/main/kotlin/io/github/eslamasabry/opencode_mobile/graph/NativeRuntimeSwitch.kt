package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.engine.runtime.history.HistoryCopyScripts
import io.github.eslamasabry.oc.engine.runtime.history.HistorySwitchAdmission
import io.github.eslamasabry.oc.engine.runtime.switching.LocalProfile
import io.github.eslamasabry.oc.engine.runtime.switching.ManagedLocalServer
import io.github.eslamasabry.oc.engine.runtime.switching.ServerBackendKind
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Explicit history migration admission. Does not read or rewrite the saved offline queue. */
@SingleIn(AppScope::class)
@Inject
class NativeRuntimeSwitch(
    private val app: Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val connections: PreviewRuntime,
    private val dispatchers: AppDispatchers,
) {
    private val lane = Mutex()

    private fun managed(profile: StoredProfile): Boolean =
        ManagedLocalServer.isManagedLocal(
            LocalProfile(
                profile.id.raw,
                profile.name,
                profile.baseUrl,
                if (profile.backend == BackendKind.OpenCode2) ServerBackendKind.OpenCode
                else ServerBackendKind.Other,
            )
        )

    private fun managedIds(): Set<ProfileId> =
        stores.profiles.profiles.value.filter { managed(it.profile) }.map { it.id }.toSet()

    /**
     * Check all live local aliases, including ones visible only in the background conversation
     * list.
     */
    private suspend fun checkWork(): Outcome<Unit> {
        for (id in managedIds()) {
            val graph = registry.live.value[id] ?: continue
            val local = graph as? ChatGraphAccessors ?: return unavailable()
            if (
                local.chatDelivery.engine?.pending?.value?.isNotEmpty() == true ||
                    local.chatDelivery.queue?.state?.value?.sending?.isNotEmpty() == true ||
                    local.chatRequests.engine.pending.value.isNotEmpty()
            )
                return busy()
            val gateway = graph.session.server
            val statuses =
                when (val read = gateway.sessionStatuses()) {
                    is Outcome.Failed -> return read
                    is Outcome.Ok -> read.value
                }
            if (statuses.values.any { it != SessionStatus.Idle }) return busy()
            val caps = gateway.capabilities.value
            if (caps.permissionRequests)
                when (val read = gateway.pendingPermissions()) {
                    is Outcome.Failed -> return read
                    is Outcome.Ok -> if (read.value.isNotEmpty()) return busy()
                }
            if (caps.legacyQuestionRequests)
                when (val read = gateway.pendingQuestions()) {
                    is Outcome.Failed -> return read
                    is Outcome.Ok -> if (read.value.isNotEmpty()) return busy()
                }
            if (caps.forms)
                when (val read = gateway.pendingForms()) {
                    is Outcome.Failed -> return read
                    is Outcome.Ok -> if (read.value.isNotEmpty()) return busy()
                }
            if (caps.inbox) {
                val page =
                    when (val read = readSessionPages(gateway)) {
                        is Outcome.Failed -> return read
                        is Outcome.Ok -> read.value
                    }
                if (!page.complete) return unavailable()
                for (session in page.sessions) when (val read = gateway.inbox(session.id)) {
                    is Outcome.Failed -> return read
                    is Outcome.Ok -> if (read.value.isNotEmpty()) return busy()
                }
            }
            if (registry.live.value[id] !== graph) return unavailable()
        }
        return Outcome.Ok(Unit)
    }

    suspend fun prepare(): Outcome<Unit> = lane.withLock {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return@withLock warm
            is Outcome.Ok -> Unit
        }
        // Read-only admission precedes recovery/selection writes and every installer action.
        val history =
            withContext(dispatchers.io) {
                attempt(::classifyRuntime) {
                    BuiltinLinux.get(app).run(HistoryCopyScripts.admissionScript, 20)
                }
            }
        when (history) {
            is Outcome.Failed -> return@withLock history
            is Outcome.Ok ->
                when (
                    HistorySwitchAdmission.fromResult(history.value.exitCode, history.value.output)
                ) {
                    HistorySwitchAdmission.Allowed -> Unit
                    // B-LINUX-108: callers still run setup and a normal OpenCode 2 start; only the
                    // move (fenced again by the start path) stays refused.
                    HistorySwitchAdmission.SeparateHistory ->
                        return@withLock Outcome.Failed(
                            Failure.Refused(RefusalReason.MigrationPending)
                        )
                    HistorySwitchAdmission.Unavailable -> return@withLock unavailable()
                }
        }
        when (val work = checkWork()) {
            is Outcome.Failed -> return@withLock work
            is Outcome.Ok -> Unit
        }
        val ids = managedIds()
        for (id in ids) {
            val key = "oc.managedServerRecovery.${id.raw}"
            val saved = stores.prefs.get(key)
            if (saved != null && saved !is PrefValue.Str) return@withLock unavailable()
            val record =
                try {
                    (saved as? PrefValue.Str)?.value?.let(::JSONObject) ?: JSONObject()
                } catch (_: Exception) {
                    return@withLock unavailable()
                }
            record.put("manuallySuspended", true)
            val persisted =
                stores.prefs.commit(stores.lanes.forProfile(id)) {
                    putString(key, record.toString())
                }
            if (persisted is Outcome.Failed) return@withLock persisted
            val revoked =
                withContext(dispatchers.io) {
                    attempt(::classifyRuntime) {
                        BuiltinLinux.get(app).unbindServerRecovery(id.raw)
                    }
                }
            if (revoked is Outcome.Failed) return@withLock revoked
        }
        when (val work = checkWork()) {
            is Outcome.Failed -> return@withLock work
            is Outcome.Ok -> Unit
        }
        val selected = stores.profiles.activeId.value
        if (selected !in ids) return@withLock Outcome.Ok(Unit)
        val cleared = stores.profiles.setActive(null)
        if (cleared is Outcome.Failed) return@withLock cleared
        when (val work = checkWork()) {
            is Outcome.Failed -> work
            is Outcome.Ok -> connections.disconnectForRuntimeSwitch(setOfNotNull(selected))
        }
    }

    private fun busy(): Outcome.Failed = Outcome.Failed(Failure.Runtime(RuntimeProblem.SlotBusy))

    private fun unavailable(): Outcome.Failed =
        Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
}
