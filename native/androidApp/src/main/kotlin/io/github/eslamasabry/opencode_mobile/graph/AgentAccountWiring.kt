package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.security.*
import io.github.eslamasabry.oc.engine.agents.account.*
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.LoadedProfile
import io.github.eslamasabry.oc.feature.servers.account.presentation.*
import io.github.eslamasabry.oc.protocol.codex.account.AccountEventKind as WireEventKind
import io.github.eslamasabry.oc.protocol.codex.account.AccountRateWindow as WireWindow
import io.github.eslamasabry.oc.protocol.codex.account.CodexAccountSession
import io.github.eslamasabry.oc.protocol.codex.gateway.CodexGateway
import java.text.DateFormat
import java.text.NumberFormat
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The account facade at one live Codex graph, never a second account reader or credential store.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<AgentAccountPort>())
class AndroidAgentAccountPort(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val dispatchers: AppDispatchers,
    private val appScope: AppCoroutineScope,
    private val clock: Clock,
    private val application: Application,
    private val policy: ExternalLinkPolicy,
    private val opener: ExternalLinkOpener,
) : AgentAccountPort {
    private data class Source(
        val graph: ConnectionGraph,
        val profile: LoadedProfile,
        val location: ServerLocation,
    )

    private fun current(source: Source): Boolean =
        registry.live.value[source.profile.id] === source.graph &&
            stores.profiles.profile(source.profile.id) == source.profile &&
            source.graph.session.server.location.value == source.location

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun watch(profile: ProfileId): Flow<AgentAccountSource> = flow {
        if (stores.warm() !is Outcome.Ok) {
            emit(AgentAccountSource(null, "", false, false))
            return@flow
        }
        emitAll(
            registry.live.flatMapLatest { graphs ->
                val graph = graphs[profile]
                if (graph == null)
                    flowOf(
                        AgentAccountSource(
                            null,
                            stores.profiles.profile(profile)?.profile?.name.orEmpty(),
                            false,
                            false,
                        )
                    )
                else
                    combine(
                        graph.session.server.events.status,
                        graph.session.server.location,
                        graph.session.capabilities,
                        stores.profiles.profiles,
                    ) { status, location, capabilities, profiles ->
                        val saved = profiles.firstOrNull { it.id == profile }
                        AgentAccountSource(
                            saved?.let { Source(graph, it, location) },
                            saved?.profile?.name.orEmpty(),
                            status == StreamStatus.Connected,
                            capabilities.agentAccount,
                        )
                    }
            }
        )
    }

    override suspend fun open(profile: ProfileId, scope: Any): Outcome<AgentAccountHandle> {
        val source = scope as? Source ?: return stale()
        if (source.profile.id != profile || !current(source)) return stale()
        val gateway =
            source.graph.session.server as? CodexGateway
                ?: return Failure.Unavailable(FeatureId("agentAccount")).failed()
        if (
            !gateway.capabilities.value.agentAccount ||
                gateway.events.status.value != StreamStatus.Connected
        )
            return Failure.Network().failed()
        val lane = dispatchers.serial("agent-account")
        return withContext(lane) {
            if (!current(source)) return@withContext stale()
            val raw = gateway.openAccountSession()
            val job = SupervisorJob(appScope.coroutineContext[Job])
            val ownerScope = CoroutineScope(appScope.coroutineContext + job + lane)
            val bridge = AccountSessionBridge(raw) { current(source) }
            val controller = AgentAccountController(bridge, ownerScope, clock)
            object : AgentAccountHandle {
                    private var closed = false
                    private val closing = Mutex()
                    override val active: Boolean
                        get() = !closed && bridge.active

                    override val state: StateFlow<AccountPanelState> = controller.state

                    override suspend fun refresh() {
                        withContext(lane) { controller.refresh() }
                    }

                    override suspend fun signIn() {
                        withContext(lane) { if (active) controller.signIn() }
                    }

                    override suspend fun cancel() {
                        withContext(lane) { if (active) controller.cancel() }
                    }

                    override suspend fun close() {
                        closing.withLock {
                            if (closed) return@withLock
                            closed = true
                            withContext(lane) {
                                controller.close()
                                bridge.close()
                            }
                            job.cancelAndJoin()
                        }
                    }
                }
                .ok()
        }
    }

    override fun openLink(
        profile: ProfileId,
        scope: Any,
        url: String,
        confirmed: Boolean,
    ): Outcome<AgentAccountLink> {
        val source = scope as? Source ?: return stale()
        if (source.profile.id != profile || !current(source)) return stale()
        return when (val decision = policy.check(url, LinkOrigin.ServerContent)) {
            is LinkDecision.Refuse -> Failure.Refused(RefusalReason.InvalidAddress).failed()
            is LinkDecision.Open ->
                if (current(source)) opener.open(decision.link).map { AgentAccountLink.Opened }
                else stale()
            is LinkDecision.AskFirst ->
                if (confirmed && !decision.insecure) {
                    if (current(source)) opener.open(decision.link).map { AgentAccountLink.Opened }
                    else stale()
                } else AgentAccountLink.Ask(decision.shownHost).ok()
        }
    }

    override fun copyCode(profile: ProfileId, scope: Any, code: String): Outcome<Unit> {
        val source = scope as? Source ?: return stale()
        if (
            source.profile.id != profile ||
                !current(source) ||
                code.length !in 1..64 ||
                code.any { it.code < 32 || it.code == 127 }
        )
            return stale()
        return try {
            val clipboard = application.getSystemService(ClipboardManager::class.java)
            val clip = ClipData.newPlainText(null, code)
            clip.description.extras =
                PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            clipboard.setPrimaryClip(clip)
            Unit.ok()
        } catch (_: SecurityException) {
            Failure.Device().failed()
        } catch (_: IllegalStateException) {
            Failure.Device().failed()
        }
    }

    private fun locale() = application.resources.configuration.locales[0]

    override fun moment(epochMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale())
            .format(java.util.Date(epochMs))

    override fun number(value: Long): String =
        NumberFormat.getIntegerInstance(locale()).format(value)

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()
}

/**
 * Exact model adaptation only; all reading, login ownership and cancellation stay in the protocol
 * session.
 */
private class AccountSessionBridge(
    private val raw: CodexAccountSession,
    private val valid: () -> Boolean,
) : AgentAccountSession {
    private var closed = false
    private val closeLock = Mutex()
    // B-AGENT-051: account changes and owned-login completion are never dropped while the panel
    // observes them.
    private val messages = Channel<AccountEvent>(Channel.UNLIMITED)
    private val remove = raw.addListener { event ->
        messages.trySend(
            AccountEvent(
                when (event.kind) {
                    WireEventKind.Changed -> AccountEventKind.Changed
                    WireEventKind.LimitsChanged -> AccountEventKind.LimitsChanged
                    WireEventKind.LoginCompleted -> AccountEventKind.LoginCompleted
                    WireEventKind.LoginCancelled -> AccountEventKind.LoginCancelled
                    WireEventKind.Disconnected -> AccountEventKind.Disconnected
                },
                event.success,
            )
        )
    }
    override val active: Boolean
        get() = !closed && valid() && raw.active

    override val supported: Boolean
        get() = raw.supported

    override val events: Flow<AccountEvent> = messages.receiveAsFlow()

    private suspend fun <T> guarded(action: suspend () -> Outcome<T>): Outcome<T> {
        if (!active) return Failure.Network().failed()
        val result = action()
        return if (active) result else Failure.Network().failed()
    }

    override suspend fun read(): Outcome<AgentRuntimeAccount> = guarded {
        raw.read().map { AgentRuntimeAccount(it.type, it.email, it.plan, it.requiresSignIn) }
    }

    override suspend fun rateLimits():
        Outcome<kotlinx.collections.immutable.ImmutableList<AccountRateBucket>> = guarded {
        raw.rateLimits().flatMap { buckets ->
            if (
                buckets.any {
                    listOfNotNull(it.primary, it.secondary).any { w ->
                        w.usedPercent > Int.MAX_VALUE || (w.durationMinutes ?: 0) > Int.MAX_VALUE
                    }
                }
            )
                Failure.UnreadableReply().failed()
            else
                buckets
                    .map {
                        AccountRateBucket(it.name, it.primary?.asEngine(), it.secondary?.asEngine())
                    }
                    .toImmutableList()
                    .ok()
        }
    }

    private fun WireWindow.asEngine(): AccountRateWindow =
        AccountRateWindow(usedPercent.toInt(), durationMinutes?.toInt(), resetsAtMs)

    override suspend fun usage(): Outcome<AccountTokenUsage> = guarded {
        raw.usage().map { AccountTokenUsage(it.lifetimeTokens, it.peakDailyTokens) }
    }

    override suspend fun startDeviceLogin(): Outcome<AccountDeviceCode> = guarded {
        raw.startDeviceLogin().map { AccountDeviceCode(it.verificationUrl, it.userCode) }
    }

    override suspend fun cancelLogin(): Outcome<Boolean> = guarded { raw.cancelLogin().ok() }

    override suspend fun close() {
        closeLock.withLock {
            if (closed) return@withLock
            closed = true
            remove()
            raw.close()
            messages.close()
        }
    }
}
