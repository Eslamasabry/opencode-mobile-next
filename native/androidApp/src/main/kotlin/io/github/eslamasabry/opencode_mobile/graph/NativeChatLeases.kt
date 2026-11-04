package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.SessionDispatchAdmissionGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.live.PhoneChatDispatchTracker
import io.github.eslamasabry.oc.engine.profiles.LoadedProfile
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.net.URI
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Local chat runs own named CPU leases. A reconnect reconciles current statuses instead of
 * replaying work.
 */
@SingleIn(AppScope::class)
@Inject
class NativeChatLeases(
    private val app: Application,
    private val registry: ConnectionRegistry,
    private val profiles: ProfileStore,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val activities: ResumedActivities,
    appScope: AppCoroutineScope,
) {
    private val scope =
        CoroutineScope(appScope.coroutineContext + dispatchers.serial("phone-chat-leases"))
    private val linux by lazy { BuiltinLinux.get(app) }

    private data class Watch(val graph: ConnectionGraph, val job: Job)

    private val admission = Mutex()

    private data class Lease(val name: String, val agentOwner: String?, var closed: Boolean = false)

    private val watches = mutableMapOf<ProfileId, Watch>()
    private val readings = MutableStateFlow<Map<String, Boolean?>>(emptyMap())
    val busy: StateFlow<Map<String, Boolean?>> = readings
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            if (stores.warm() is Outcome.Failed) return@launch
            registry.live.collect { live ->
                val removed = watches.filter { (id, watch) -> live[id] !== watch.graph }
                for ((id, watch) in removed) {
                    watches.remove(id)
                    watch.job.cancelAndJoin()
                    readings.update { it - id.raw }
                }
                for ((id, graph) in live) {
                    if (id !in watches && local(graph)) {
                        val dispatches = PhoneChatDispatchTracker()
                        graph.session.server
                            .facet<SessionDispatchAdmissionGateway>()
                            ?.setSessionDispatchAdmission(
                                admit = { session -> admit(graph, session, dispatches) },
                                settled = { session ->
                                    synchronized(dispatches) { dispatches.settled(session) }
                                },
                            )
                        watches[id] = Watch(graph, scope.launch { follow(graph, dispatches) })
                    }
                }
            }
        }
    }

    // The same rule as the gateway's requireDispatchAdmission, so the hook exists wherever it is
    // required (bracketed IPv6 and any letter case included).
    private fun local(graph: ConnectionGraph): Boolean =
        AddressRules.isLoopback(graph.profile.origin.host) &&
            graph.profile.origin.port in listOf(PhoneServerScripts.PORT, 4099)

    /**
     * The native fallback of Flutter `beforePersonDispatch`: where the heartbeat admission owner is
     * unavailable, pause the exact configured phone engine and verify it stopped before a human
     * request leaves. The OpenCode daemon stays running. Nothing is resumed implicitly. A plain
     * local server and remote servers never stop an engine through this path.
     */
    private suspend fun admit(
        graph: ConnectionGraph,
        session: SessionId,
        dispatches: PhoneChatDispatchTracker,
    ): Outcome<Unit> = admission.withLock {
        val profile = graph.profile.id
        val saved = profiles.profile(profile) ?: return@withLock staleAdmission()
        if (registry.live.value[profile] !== graph || !local(graph))
            return@withLock staleAdmission()
        // Like Flutter's preparePhoneAliasDispatch: another saved alias of this phone's OpenCode
        // daemon can still own an AI Team process, whether or not this profile uses AI Team.
        val aliases = withContext(dispatchers.io) { pauseOwners(graph, saved) { it != profile } }
        if (aliases is Outcome.Failed) return@withLock aliases
        if (registry.live.value[profile] !== graph || profiles.profile(profile) != saved)
            return@withLock staleAdmission()
        if (!phoneEngineConfigured(saved.profile.orchestration?.encoded)) return@withLock Unit.ok()
        if (!PROFILE.matches(profile.raw)) return@withLock staleAdmission()
        val directory = graph.session.server.location.value.directory
        synchronized(dispatches) { dispatches.begin(session, directory) }
        val paused =
            try {
                withContext(dispatchers.io) { pauseOwners(graph, saved) { it == profile } }
            } catch (cancelled: CancellationException) {
                synchronized(dispatches) { dispatches.settled(session, removeUnsent = true) }
                throw cancelled
            }
        if (
            paused is Outcome.Failed ||
                registry.live.value[profile] !== graph ||
                profiles.profile(profile) != saved
        ) {
            synchronized(dispatches) { dispatches.settled(session, removeUnsent = true) }
            return@withLock if (paused is Outcome.Failed) paused else staleAdmission()
        }
        readings.update { it + (profile.raw to true) }
        Unit.ok()
    }

    /**
     * Stops, by exact id, each saved and configured loopback AI Team owner of this daemon that
     * [which] selects, and reads back that it stopped. Unknown readbacks fail the admission.
     */
    private suspend fun pauseOwners(
        graph: ConnectionGraph,
        saved: LoadedProfile,
        which: (ProfileId) -> Boolean,
    ): Outcome<Unit> =
        attempt(::classifyRuntime) {
            val profile = graph.profile.id
            val owners =
                profiles.profiles.value.filter { row ->
                    which(row.id) &&
                        PROFILE.matches(row.id.raw) &&
                        phoneEngineConfigured(row.profile.orchestration?.encoded) &&
                        row.toServerProfile()?.origin?.let { origin ->
                            AddressRules.isLoopback(origin.host) &&
                                origin.port == graph.profile.origin.port
                        } == true
                }
            for (owner in owners) {
                check(registry.live.value[profile] === graph && profiles.profile(profile) == saved)
                check(profiles.profile(owner.id) == owner)
                val before = linux.phoneEngineStatus(owner.id.raw)
                check(before["profileId"] == owner.id.raw && before["running"] is Boolean)
                if (before["running"] == true) {
                    val stopped = linux.stopPhoneEngine(owner.id.raw)
                    check(stopped["profileId"] == owner.id.raw && stopped["running"] == false)
                    val confirmed = linux.phoneEngineStatus(owner.id.raw)
                    check(confirmed["profileId"] == owner.id.raw && confirmed["running"] == false)
                }
            }
        }

    private fun phoneEngineConfigured(encoded: String?): Boolean =
        if (encoded == null) false
        else
            try {
                JSONObject(encoded).optString("provider") == "phoneEngine"
            } catch (_: Exception) {
                false
            }

    private fun staleAdmission(): Outcome<Nothing> =
        Failure.Refused(RefusalReason.StaleScope).failed()

    private suspend fun follow(graph: ConnectionGraph, dispatches: PhoneChatDispatchTracker): Unit =
        coroutineScope {
            val profile = graph.profile.id
            if (!PROFILE.matches(profile.raw)) return@coroutineScope
            val lane = Mutex()
            val busySessions = mutableSetOf<SessionId>()
            var known = false
            var lease: Lease? = null
            val agent = URI(graph.profile.origin.toString()).port == 4099
            val owner =
                if (agent)
                    (stores.prefs.get("oc.phoneAgentOwner.${profile.raw}") as? PrefValue.Str)?.value
                        ?: profile.raw
                else null
            if (owner != null && !PROFILE.matches(owner)) return@coroutineScope

            suspend fun hold(current: Lease, on: Boolean, duration: Long): Pair<Boolean, Boolean>? =
                withContext(dispatchers.io) {
                    if (
                        on &&
                            (profiles.profile(profile) == null ||
                                registry.live.value[profile] !== graph)
                    )
                        return@withContext null
                    if (
                        !NAME.matches(current.name) ||
                            (current.agentOwner != null && !PROFILE.matches(current.agentOwner))
                    )
                        return@withContext null
                    when (
                        val reply =
                            attempt(::classifyRuntime) {
                                if (current.agentOwner == null)
                                    linux.setChatWorkLease(current.name, on, duration)
                                else
                                    linux.setPhoneAgentChatWorkLease(
                                        current.agentOwner,
                                        current.name,
                                        on,
                                        duration,
                                    )
                            }
                    ) {
                        is Outcome.Failed -> null
                        is Outcome.Ok -> {
                            val held = reply.value["held"]
                            val capped = reply.value["capped"]
                            if (held is Boolean && capped is Boolean && !(held && capped))
                                held to capped
                            else null
                        }
                    }
                }

            suspend fun heartbeat(value: Boolean?) {
                if (!agent || owner == null) return
                val alias =
                    (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value ?: return
                if (!PROFILE.matches(alias)) return
                val canonical =
                    (stores.prefs.get("oc.phoneAgentOwner.$alias") as? PrefValue.Str)?.value
                        ?: alias
                if (canonical != owner) return
                withContext(dispatchers.io) {
                    attempt(::classifyRuntime) { linux.observePhoneAgentWork(alias, value) }
                }
            }

            suspend fun observe() {
                val pending = synchronized(dispatches) { dispatches.isNotEmpty }
                val busy = busySessions.isNotEmpty() || pending
                if (agent) heartbeat(if (busy) true else if (known) false else null)
                readings.update {
                    it + (profile.raw to if (busy) true else if (known) false else null)
                }
                if (!known && !pending) return
                if (!busy) {
                    lease?.let { hold(it, false, if (agent) AGENT_TTL else CHAT_TTL) }
                    lease = null
                    return
                }
                var current = lease
                if (current == null) {
                    current =
                        Lease(
                            (if (agent) "agent." else "chat.") + UUID.randomUUID().toString(),
                            owner,
                        )
                    lease = current
                }
                if (current.closed) return
                val reply = hold(current, true, if (agent) AGENT_TTL else CHAT_TTL)
                if (reply == null || !reply.first) {
                    current.closed = true
                    if (reply?.second != true) hold(current, true, 0)
                }
            }

            suspend fun reconcile() {
                val readEpoch = synchronized(dispatches) { dispatches.epoch }
                val directory = graph.session.server.location.value.directory
                val read = graph.session.server.sessionStatuses()
                if (
                    read is Outcome.Ok &&
                        read.value.values.all {
                            it in
                                listOf(SessionStatus.Busy, SessionStatus.Retry, SessionStatus.Idle)
                        }
                ) {
                    busySessions.clear()
                    busySessions.addAll(
                        read.value
                            .filterValues { it == SessionStatus.Busy || it == SessionStatus.Retry }
                            .keys
                    )
                    known = true
                    synchronized(dispatches) {
                        dispatches.reconcile(read.value, readEpoch, directory)
                    }
                } else known = false
                observe()
            }

            val renewal = launch {
                while (true) {
                    delay(5.minutes)
                    lane.withLock { if (lease != null && known) observe() }
                }
            }
            val heartbeats = launch {
                if (agent)
                    while (true) {
                        delay(15.seconds)
                        if (activities.current == null)
                            lane.withLock {
                                val pending = synchronized(dispatches) { dispatches.isNotEmpty }
                                heartbeat(
                                    if (pending || busySessions.isNotEmpty()) true
                                    else if (known) false else null
                                )
                            }
                    }
            }
            val status = launch {
                graph.session.server.events.status.collect { stream ->
                    lane.withLock {
                        if (stream == StreamStatus.Connected) reconcile()
                        else {
                            known = false
                            heartbeat(null)
                            readings.update { it + (profile.raw to null) }
                        }
                    }
                }
            }
            try {
                graph.session.server.events.all.collect { envelope ->
                    lane.withLock {
                        when (val event = envelope.event) {
                            GatewayEvent.Connected -> reconcile()
                            is GatewayEvent.RunStarted -> {
                                busySessions.add(event.sessionId)
                                synchronized(dispatches) { dispatches.observeBusy(event.sessionId) }
                                known = true
                                observe()
                            }
                            is GatewayEvent.RunRetrying -> {
                                busySessions.add(event.sessionId)
                                synchronized(dispatches) { dispatches.observeBusy(event.sessionId) }
                                known = true
                                observe()
                            }
                            is GatewayEvent.RunEnded,
                            is GatewayEvent.SessionDeleted -> reconcile()
                            else -> Unit
                        }
                    }
                }
            } finally {
                renewal.cancel()
                status.cancel()
                heartbeats.cancel()
                withContext(NonCancellable) {
                    lane.withLock {
                        lease?.let { hold(it, false, if (agent) AGENT_TTL else CHAT_TTL) }
                    }
                }
            }
        }

    private companion object {
        val PROFILE = Regex("[A-Za-z0-9_-]{1,80}")
        val NAME = Regex("[A-Za-z0-9_.-]{1,80}")
        const val CHAT_TTL = 600_000L
        const val AGENT_TTL = 900_000L
    }
}
