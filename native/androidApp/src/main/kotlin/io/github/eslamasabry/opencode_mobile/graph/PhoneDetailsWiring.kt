package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ReplyTiming
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ReplyTimingEvent
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ReplyTimingState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.on
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneDetailsSource
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneDetailsUi
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneHistoryInfo
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneHistoryPhase
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneProotMode
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneReplyReading
import io.github.eslamasabry.oc.protocol.oc2.live.HistoryMigrationPhase
import io.github.eslamasabry.oc.protocol.oc2.live.readHistoryMigrationStatus
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.net.URI
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

@ContributesTo(AppScope::class)
interface PhoneDetailsAccessors {
    val nativePhoneDetails: NativePhoneDetails
}

/**
 * This phone's actual runtime readings. The application owns reply timing; opening the page starts
 * bounded native reads, and only the visible Details fold refreshes the log. No extra stream or
 * service.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NativePhoneDetails(
    private val app: Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val connections: ServerConnections,
    private val setup: NativePhoneSetup,
    private val platform: PlatformCapabilities,
    private val redactor: Redactor,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) : PhoneDetailsSource {
    private val scope =
        CoroutineScope(appScope.coroutineContext + dispatchers.serial("phone-details"))
    private val lane = Mutex()
    private val logLane = Mutex()
    private val mutable = MutableStateFlow(PhoneDetailsUi())
    override val state: StateFlow<PhoneDetailsUi> = mutable.asStateFlow()
    private val linux by lazy { BuiltinLinux.get(app) }
    private var started = false
    @Volatile private var visible = false
    @Volatile private var viewRevision = 0L
    private var last: ReplyTiming? = null

    /**
     * Called once at app startup, after the graph exists; the adopted stores warm asynchronously.
     */
    fun start() {
        if (started || !platform.builtinLinux) return
        started = true
        scope.launch {
            if (stores.warm() is Outcome.Failed) return@launch
            last = restoreTiming((stores.prefs.get(REPLY_KEY) as? PrefValue.Str)?.value)
            publishReply()
            combine(registry.live, stores.profiles.activeId, stores.profiles.profiles) { live, _, _
                    ->
                    val owner = owner()
                    mutable.update {
                        it.copy(
                            canConnect = owner != null && stores.profiles.activeId.value != owner
                        )
                    }
                    if (owner == null) {
                        last = null
                        publishReply()
                    }
                    owner?.let(live::get)?.takeIf(::localOwner)
                }
                .distinctUntilChanged()
                .collectLatest { graph ->
                    graph ?: return@collectLatest
                    val owner = graph.profile.id
                    var timing = ReplyTimingState()
                    graph.session.server.events.all.collect { envelope ->
                        if (registry.live.value[owner] !== graph || owner() != owner) return@collect
                        val event = envelope.event
                        if (event == GatewayEvent.Connected) {
                            timing = ReplyTimingState()
                            return@collect
                        }
                        val input = timingEvent(event) ?: return@collect
                        val before = timing.lastInApp
                        timing = timing.on(input, envelope.receivedAtMs)
                        val finished = timing.lastInApp
                        if (finished != null && finished !== before) {
                            last = finished
                            publishReply()
                            // The profile lane closes before deletion. A late reply cannot recreate
                            // the shared runtime timing after the phone owner's deletion/removal.
                            if (owner() == owner && registry.live.value[owner] === graph)
                                stores.prefs.commit(stores.lanes.forProfile(owner)) {
                                    putString(REPLY_KEY, encode(finished))
                                }
                        }
                    }
                }
        }
        scope.launch {
            setup.toolsChanged.drop(1).collect {
                if (visible) refresh()
            }
        }
    }

    private fun owner(): ProfileId? =
        (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)
            ?.value
            ?.takeIf(String::isNotBlank)
            ?.let(::ProfileId)
            ?.takeIf { stores.profiles.profile(it) != null }

    private fun localOwner(graph: ConnectionGraph): Boolean =
        try {
            val uri = URI(graph.profile.origin.toString())
            graph.profile.id == owner() &&
                uri.host in listOf("127.0.0.1", "localhost", "::1") &&
                uri.port == PhoneServerScripts.PORT
        } catch (_: java.net.URISyntaxException) {
            false
        }

    private fun publishReply() {
        val reply = last?.let {
            PhoneReplyReading(
                it.firstTokenMs,
                it.serverFirstTokenMs,
                it.totalMs,
                it.model?.let(redactor::details),
            )
        }
        mutable.update { it.copy(reply = reply) }
    }

    override fun shown(visible: Boolean) {
        this.visible = visible
        viewRevision++
    }

    private suspend fun <T> native(block: () -> T): Outcome<T> =
        withContext(dispatchers.io) {
            attempt({ Failure.Runtime(RuntimeProblem.Generic) }) { block() }
        }

    override suspend fun refresh() = lane.withLock {
        val revision = viewRevision
        val isShown = visible
        if (!isShown || !platform.builtinLinux || stores.warm() is Outcome.Failed) return@withLock
        mutable.update { it.copy(loading = true) }
        val observed = native {
            val installed = linux.installed
            val performance = if (installed) linux.performance() else null
            val memory =
                if (performance?.get("serverRunning") == true) {
                    val rows =
                        (linux.processTable()["processes"] as? List<*>)
                            .orEmpty()
                            .mapNotNull { it as? Map<*, *> }
                            .filter { it["owner"] != "app" }
                    val values = rows.map {
                        (it["rssBytes"] as? Number)?.toLong()?.takeIf { n -> n >= 0 }
                    }
                    if (values.any { it == null }) null
                    else
                        values
                            .filterNotNull()
                            .fold(0L) { sum, n ->
                                if (sum > Long.MAX_VALUE - n) Long.MAX_VALUE else sum + n
                            }
                            .takeIf { it > 0 }
                } else null
            NativeReading(
                installed,
                memory,
                performance?.get("prootMode") as? String,
                performance?.get("workHeld") as? Boolean,
                linux.message,
            )
        }
        if (!visible || revision != viewRevision) return@withLock
        if (observed is Outcome.Failed) {
            mutable.update {
                it.copy(loading = false, memoryBytes = null, logFailure = observed.failure)
            }
            return@withLock
        }
        val reading = (observed as Outcome.Ok).value
        val id = owner()
        refreshHistory()
        if (!visible || revision != viewRevision) return@withLock
        mutable.update {
            it.copy(
                installed = reading.installed,
                memoryBytes = reading.memory,
                canConnect = id != null && stores.profiles.activeId.value != id,
                prootMode =
                    reading.mode?.let { mode ->
                        when (mode) {
                            "seccomp" -> PhoneProotMode.Fast
                            "ptrace" -> PhoneProotMode.Slow
                            else -> PhoneProotMode.Unknown
                        }
                    },
                workHeld = reading.held,
                nativeFailure =
                    reading.failure?.let { text -> redactor.details(redactor.diagnostics(text)) },
            )
        }
        if (!reading.installed) {
            last = null
            mutable.update {
                it.copy(
                    loading = false,
                    reply = null,
                    installedNames = null,
                    inventoryFailure = null,
                    log = null,
                )
            }
            return@withLock
        }
        var inventory = setup.installedInventory()
        if (inventory is Outcome.Failed) {
            delay(1_500)
            if (!visible || revision != viewRevision) return@withLock
            inventory = setup.installedInventory()
        }
        if (!visible || revision != viewRevision) return@withLock
        when (val result = inventory) {
            is Outcome.Ok ->
                mutable.update {
                    it.copy(
                        loading = false,
                        installedNames = result.value.toImmutableList(),
                        inventoryFailure = null,
                    )
                }
            is Outcome.Failed ->
                mutable.update { it.copy(loading = false, inventoryFailure = result.failure) }
        }
        refreshLog()
    }

    /**
     * At most 150 foreground reads are scheduled by the shown page, and only while import is
     * pending.
     */
    override suspend fun refreshHistory(): Boolean {
        val revision = viewRevision
        if (!visible) return false
        val id = owner()
        val graph = id?.let { registry.live.value[it] }?.takeIf(::localOwner)
        val migration = graph?.session?.server?.let { readHistoryMigrationStatus(it) }
        if (
            !visible ||
                revision != viewRevision ||
                (id != null && (owner() != id || registry.live.value[id] !== graph))
        )
            return false
        val history = (migration as? Outcome.Ok)?.value
        val info =
            when (history?.phase) {
                HistoryMigrationPhase.Required -> PhoneHistoryInfo(PhoneHistoryPhase.Moving)
                HistoryMigrationPhase.Running ->
                    PhoneHistoryInfo(PhoneHistoryPhase.Moving, history.done, history.total)
                HistoryMigrationPhase.Error -> PhoneHistoryInfo(PhoneHistoryPhase.Failed)
                else -> null
            }
        mutable.update {
            it.copy(
                history = info,
                historyFailure =
                    history?.error?.let { text -> redactor.details(redactor.diagnostics(text)) },
            )
        }
        return info?.phase == PhoneHistoryPhase.Moving
    }

    override suspend fun refreshLog() = logLane.withLock {
        val revision = viewRevision
        if (!visible || !platform.builtinLinux) return@withLock
        when (val log = native { linux.serverLogTail(64 * 1024) to linux.performance() }) {
            is Outcome.Ok ->
                if (visible && revision == viewRevision)
                    mutable.update {
                        it.copy(
                            log = redactor.details(redactor.excerpt(log.value.first)),
                            prootMode =
                                when (log.value.second["prootMode"]) {
                                    "seccomp" -> PhoneProotMode.Fast
                                    "ptrace" -> PhoneProotMode.Slow
                                    else -> PhoneProotMode.Unknown
                                },
                            workHeld = log.value.second["workHeld"] as? Boolean,
                            logFailure = null,
                        )
                    }
            is Outcome.Failed ->
                if (visible && revision == viewRevision)
                    mutable.update { it.copy(logFailure = log.failure) }
        }
    }

    override suspend fun connect(): Outcome<Unit> {
        val id = owner() ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        if (!platform.builtinLinux || !visible)
            return Failure.Refused(RefusalReason.StaleScope).failed()
        val result = connections.connect(id)
        if (owner() != id) return Failure.Refused(RefusalReason.StaleScope).failed()
        mutable.update { it.copy(canConnect = stores.profiles.activeId.value != id) }
        return result
    }

    private data class NativeReading(
        val installed: Boolean,
        val memory: Long?,
        val mode: String?,
        val held: Boolean?,
        val failure: String?,
    )

    private fun timingEvent(event: GatewayEvent): ReplyTimingEvent? =
        when (event) {
            is GatewayEvent.MessageUpserted ->
                if (event.message.role == Role.User)
                    ReplyTimingEvent.UserMessageSeen(
                        event.message.sessionId,
                        event.message.id,
                        event.message.time?.createdMs,
                        true,
                    )
                else
                    ReplyTimingEvent.AssistantMessageSeen(
                        event.message.sessionId,
                        event.message.id,
                        event.message.providerId,
                        event.message.modelId,
                    )
            is GatewayEvent.PartUpserted ->
                ReplyTimingEvent.PartSeen(
                    event.part.sessionId,
                    event.part.messageId,
                    event.part.type,
                    event.part.tool?.startedAtMs,
                )
            is GatewayEvent.PartDelta ->
                ReplyTimingEvent.DeltaSeen(event.sessionId.raw, event.messageId.raw)
            is GatewayEvent.RunStarted -> ReplyTimingEvent.Busy(event.sessionId.raw)
            is GatewayEvent.RunRetrying -> ReplyTimingEvent.Busy(event.sessionId.raw)
            is GatewayEvent.RunEnded ->
                ReplyTimingEvent.Ended(event.sessionId.raw, event.outcome is RunOutcome.Failed)
            else -> null
        }

    private fun encode(timing: ReplyTiming): String =
        JSONObject()
            .apply {
                put("inApp", true)
                put("totalMs", timing.totalMs)
                put("firstMs", timing.firstTokenMs)
                put("serverFirstMs", timing.serverFirstTokenMs)
                put("model", timing.model)
                put("failed", timing.failed)
                put("finishedAt", timing.finishedAtMs)
            }
            .toString()

    private fun restoreTiming(raw: String?): ReplyTiming? =
        try {
            val value = raw?.let(::JSONObject)
            val total = (value?.opt("totalMs") as? Number)?.toLong()?.takeIf { it >= 0 }
            val finished = (value?.opt("finishedAt") as? Number)?.toLong()?.takeIf { it >= 0 }
            if (value == null || value.opt("inApp") != true || total == null || finished == null)
                null
            else
                ReplyTiming(
                    true,
                    (value.opt("firstMs") as? Number)?.toLong()?.takeIf { it in 0..total },
                    (value.opt("serverFirstMs") as? Number)?.toLong()?.takeIf { it >= 0 },
                    total,
                    value.opt("model") as? String,
                    value.opt("failed") == true,
                    finished,
                )
        } catch (_: org.json.JSONException) {
            null
        }

    private companion object {
        const val REPLY_KEY = "oc.replySpeed.inApp"
    }
}
