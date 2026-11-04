package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.scope.*
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.quota.*
import io.github.eslamasabry.oc.engine.integrations.usage.*
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.feature.plans.presentation.*
import io.github.eslamasabry.oc.protocol.codex.gateway.CodexGateway
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGateway
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransport
import io.github.eslamasabry.oc.protocol.paseo.usage.PaseoProviderUsageReader
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.usageReadUsage
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jetbrains.compose.resources.getString

/**
 * User-consented readers over the exact saved profile; no provider credential enters presentation.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PlanReaders>())
@Inject
class AndroidPlansReaders(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val runtime: BuiltinRuntime,
    private val application: Application,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val factories: Set<GatewayFactory>,
    private val redactor: Redactor,
    appScope: AppCoroutineScope,
) : PlanReaders, UsageSource {
    private val scope = CoroutineScope(appScope.coroutineContext)
    private val lane = Mutex()
    private val state = MutableStateFlow<ImmutableMap<String, PlanReaderStatus>>(persistentMapOf())
    override val states: StateFlow<ImmutableMap<String, PlanReaderStatus>> = state.asStateFlow()
    private val closed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val attempted = HashMap<String, Long>()
    private val pending = HashMap<String, ComputerAnswer>()
    private var idleStop: Job? = null
    private val collectors = HashMap<String, Pair<LoadedProfile, InAppQuotaCollector>>()

    init {
        scope.launch {
            if (stores.warm() !is Outcome.Ok) return@launch
            var observed = emptyMap<ProfileId, LoadedProfile>()
            stores.profiles.profiles.collect { profiles ->
                val alive = profiles.mapTo(HashSet()) { it.id.raw }
                state.update { current -> current.filterKeys { it in alive }.toImmutableMap() }
                for (p in profiles) if (
                    p.profile.backend == BackendKind.OpenCode2 && p.id.raw !in closed
                ) {
                    if (observed[p.id] != p) state.update { (it - p.id.raw).toImmutableMap() }
                    publish(p)
                }
                observed = profiles.associateBy { it.id }
            }
        }
    }

    val signInSource: UsageSource =
        OpenCodeSignInSource(
            servers = {
                stores.profiles.profiles.value
                    .filter { it.profile.backend == BackendKind.OpenCode2 && it.id.raw !in closed }
                    .map { SignInServer(it.id.raw, true, it.reentry.password) }
            },
            isReadable = { id -> profile(id) != null },
            providers = { server ->
                val p = profile(server.id)
                if (p == null) Failure.Refused(RefusalReason.ProfileDeleted).failed()
                else
                    withSavedPair(p) { pair ->
                        pair.live.providers().map { response ->
                            response.providers.map { ConnectedProvider(it.id, it.name) }
                        }
                    }
            },
        )

    fun connections(): List<UsageConnection> = buildList {
        for (profile in stores.profiles.profiles.value) {
            if (profile.id.raw in closed) continue
            val kind =
                when (profile.profile.backend) {
                    BackendKind.OpenCode2 ->
                        if (inApp(profile)) UsageConnectionKind.InAppUbuntu
                        else UsageConnectionKind.OpenCodeServer
                    BackendKind.Paseo -> UsageConnectionKind.PaseoServer
                    BackendKind.Codex -> UsageConnectionKind.CodexServer
                }
            add(UsageConnection(profile.id.raw, profile.profile.name, kind))
        }
        if (phoneOwner() != null)
            add(UsageConnection(UsageConnection.PHONE_ID, "", UsageConnectionKind.PhoneAgents))
    }

    private fun profile(id: String): LoadedProfile? =
        stores.profiles.profile(ProfileId(id))?.takeIf { id !in closed }

    private fun inApp(p: LoadedProfile): Boolean =
        InAppQuotaCollector.looksLikeInAppServer(quotaProfile(p))

    private fun ownsCollector(p: LoadedProfile): Boolean =
        inApp(p) && stores.prefs.string("oc.builtinServerOwner") == p.id.raw

    private fun quotaProfile(p: LoadedProfile): QuotaServerProfile =
        QuotaServerProfile(p.id, p.profile.baseUrl, p.profile.backend == BackendKind.OpenCode2)

    private fun enabled(p: LoadedProfile): Boolean =
        stores.prefs.bool(
            if (inApp(p)) InAppQuotaCollector.enabledKey(p.id) else computerKey(p.id.raw)
        ) == true

    private fun current(p: LoadedProfile): Boolean =
        profile(p.id.raw) == p && stores.lanes.forProfile(p.id).isAdmitting

    private fun publish(p: LoadedProfile, change: (PlanReaderStatus) -> PlanReaderStatus = { it }) {
        if (!current(p)) return
        state.update { old ->
            (old +
                    (p.id.raw to
                        change(
                                old[p.id.raw]
                                    ?: PlanReaderStatus(
                                        p.id.raw,
                                        if (inApp(p)) PlanReaderKind.InAppUbuntu
                                        else PlanReaderKind.Computer,
                                    )
                            )
                            .copy(enabled = enabled(p))))
                .toImmutableMap()
        }
    }

    private fun problem(p: LoadedProfile, why: PlanReaderProblem): Outcome<Nothing> {
        publish(p) { it.copy(busy = false, problem = why) }
        return Failure.Device().failed()
    }

    private suspend fun readerCommand(
        id: String,
        work: suspend () -> Outcome<Unit>,
    ): Outcome<Unit> = lane.withLock {
        try {
            when (val result = attempt({ Failure.Device() }) { work() }) {
                is Outcome.Ok -> result.value
                is Outcome.Failed -> {
                    profile(id)?.let { p ->
                        publish(p) { it.copy(problem = PlanReaderProblem.Unavailable) }
                    }
                    result
                }
            }
        } finally {
            profile(id)?.let { p -> publish(p) { it.copy(busy = false) } }
        }
    }

    override suspend fun setUp(id: String): Outcome<Unit> =
        readerCommand(id) {
            if (stores.warm() !is Outcome.Ok) return@readerCommand Failure.StorageRefused().failed()
            val p =
                profile(id)
                    ?: return@readerCommand Failure.Refused(RefusalReason.ProfileDeleted).failed()
            if (p.profile.backend != BackendKind.OpenCode2 || p.reentry.password || !p.hasSecret)
                return@readerCommand problem(p, PlanReaderProblem.Unavailable)
            publish(p) { it.copy(busy = true, phase = PlanReaderPhase.Checking, problem = null) }
            if (inApp(p)) {
                if (!ownsCollector(p))
                    return@readerCommand problem(p, PlanReaderProblem.Unavailable)
                val result =
                    attempt({ Failure.Device() }) {
                        collector(p).setUp(quotaProfile(p)) { step ->
                            publish(p) {
                                it.copy(
                                    phase =
                                        when (step) {
                                            QuotaSetupStep.Checking -> PlanReaderPhase.Checking
                                            QuotaSetupStep.Copying -> PlanReaderPhase.Copying
                                            QuotaSetupStep.SignIns -> PlanReaderPhase.SignIns
                                            QuotaSetupStep.Starting -> PlanReaderPhase.Starting
                                            QuotaSetupStep.Reading -> PlanReaderPhase.Reading
                                        }
                                )
                            }
                        }
                    }
                if (!current(p))
                    return@readerCommand Failure.Refused(RefusalReason.StaleScope).failed()
                val error = (result as? Outcome.Ok)?.value
                if (result is Outcome.Failed || error != null)
                    return@readerCommand problem(
                        p,
                        when (error?.failure) {
                            QuotaSetupFailure.LinuxNotReady -> PlanReaderProblem.LinuxNotReady
                            QuotaSetupFailure.NoNode -> PlanReaderProblem.NodeMissing
                            QuotaSetupFailure.NodeTooOld -> PlanReaderProblem.NodeTooOld
                            QuotaSetupFailure.Start -> PlanReaderProblem.Start
                            QuotaSetupFailure.Remove -> PlanReaderProblem.Remove
                            else -> PlanReaderProblem.Prepare
                        },
                    )
                touchCollector()
                publish(p) { it.copy(busy = false, phase = PlanReaderPhase.Ready, problem = null) }
                Unit.ok()
            } else {
                val saved =
                    stores.prefs.commit(stores.lanes.forProfile(p.id)) {
                        putBool(computerKey(id), true)
                    }
                if (saved is Outcome.Failed)
                    return@readerCommand problem(p, PlanReaderProblem.SaveFailed)
                readComputer(p).also { answer -> if (current(p)) pending[id] = answer }.outcome(p)
            }
        }

    override suspend fun read(id: String): Outcome<Unit> =
        readerCommand(id) {
            val p =
                profile(id)
                    ?: return@readerCommand Failure.Refused(RefusalReason.ProfileDeleted).failed()
            if (inApp(p)) {
                if (!enabled(p)) return@readerCommand problem(p, PlanReaderProblem.Unavailable)
                if (ownsCollector(p)) {
                    if (!collector(p).ensureRunning(quotaProfile(p)))
                        return@readerCommand problem(p, PlanReaderProblem.Start)
                } else {
                    val owner = phoneOwner()
                    if (
                        owner == null ||
                            !enabled(owner) ||
                            !runtime.host.serviceRunning(InAppQuotaCollector.SERVICE_NAME)
                    )
                        return@readerCommand problem(p, PlanReaderProblem.Unavailable)
                }
                touchCollector()
                publish(p) { it.copy(busy = false, phase = PlanReaderPhase.Ready, problem = null) }
                Unit.ok()
            } else {
                readComputer(p).also { answer -> if (current(p)) pending[id] = answer }.outcome(p)
            }
        }

    override suspend fun turnOff(id: String): Outcome<Unit> =
        readerCommand(id) {
            val p =
                profile(id)
                    ?: return@readerCommand Failure.Refused(RefusalReason.ProfileDeleted).failed()
            publish(p) { it.copy(busy = true, problem = null) }
            if (inApp(p)) {
                if (!ownsCollector(p)) {
                    if (
                        stores.prefs.commit(stores.lanes.forProfile(p.id)) {
                            remove(InAppQuotaCollector.enabledKey(p.id))
                        } is Outcome.Failed
                    )
                        return@readerCommand problem(p, PlanReaderProblem.SaveFailed)
                    if (stores.secrets.delete(InAppQuotaCollector.tokenKey(p.id)) is Outcome.Failed)
                        return@readerCommand problem(p, PlanReaderProblem.Remove)
                    publish(p) {
                        it.copy(
                            busy = false,
                            phase = PlanReaderPhase.Idle,
                            problem = null,
                            foundProviderIds = persistentListOf(),
                        )
                    }
                    return@readerCommand Unit.ok()
                }
                idleStop?.cancel()
                val answer = attempt({ Failure.Device() }) { collector(p).turnOff(p.id) }
                if (answer is Outcome.Failed || (answer as? Outcome.Ok)?.value != null)
                    return@readerCommand problem(p, PlanReaderProblem.Remove)
                if (stores.secrets.delete(InAppQuotaCollector.tokenKey(p.id)) is Outcome.Failed)
                    return@readerCommand problem(p, PlanReaderProblem.Remove)
                if (
                    stores.prefs.commit(stores.lanes.forProfile(p.id)) {
                        remove(InAppQuotaCollector.enabledKey(p.id))
                    } is Outcome.Failed
                )
                    return@readerCommand problem(p, PlanReaderProblem.SaveFailed)
            } else if (
                stores.prefs.commit(stores.lanes.forProfile(p.id)) { remove(computerKey(id)) }
                    is Outcome.Failed
            )
                return@readerCommand problem(p, PlanReaderProblem.SaveFailed)
            pending.remove(id)
            attempted.remove(id)
            publish(p) {
                it.copy(
                    enabled = false,
                    busy = false,
                    phase = PlanReaderPhase.Idle,
                    problem = null,
                    foundProviderIds = persistentListOf(),
                )
            }
            Unit.ok()
        }

    override suspend fun read(context: UsageReadContext): Outcome<UsageSourceResult> =
        lane.withLock {
            val readings = ArrayList<UsageReading>()
            val signIns = ArrayList<UsageSignIn>()
            val failures = ArrayList<UsageFailure>()
            val unreached = HashSet<String>()
            for (connection in context.connections) {
                if (connection.id == UsageConnection.PHONE_ID) {
                    val phone = readPhone()
                    if (phone == null || phone is Outcome.Failed)
                        unreached += UsageConnection.PHONE_ID
                    else
                        foldPaseo(
                            UsageConnection.PHONE_ID,
                            (phone as Outcome.Ok).value,
                            readings,
                            signIns,
                        )
                    continue
                }
                val p = profile(connection.id) ?: continue
                if (p.profile.backend == BackendKind.Codex) {
                    if (stores.profiles.activeId.value != p.id) continue
                    readCodex(p, context, readings, signIns, failures, unreached)
                    continue
                }
                if (p.profile.backend == BackendKind.Paseo) {
                    val answer =
                        withTimeoutOrNull(12_000) {
                            withSavedPair(p) { pair ->
                                val gateway = pair.live as? PaseoGateway
                                if (gateway == null)
                                    Failure.Unavailable(FeatureId("providerUsage")).failed()
                                else
                                    PaseoProviderUsageReader(gateway.transport, clock, owns = false)
                                        .read()
                            }
                        }
                    if (!current(p)) continue
                    if (answer == null || answer is Outcome.Failed) unreached += p.id.raw
                    else foldPaseo(p.id.raw, (answer as Outcome.Ok).value, readings, signIns)
                    continue
                }
                if (p.profile.backend != BackendKind.OpenCode2 || !enabled(p)) continue
                if (inApp(p)) {
                    val owner = if (ownsCollector(p)) p else phoneOwner()
                    val running =
                        owner != null &&
                            enabled(owner) &&
                            if (ownsCollector(p)) collector(p).ensureRunning(quotaProfile(p))
                            else runtime.host.serviceRunning(InAppQuotaCollector.SERVICE_NAME)
                    if (!running || owner == null) {
                        for (provider in readableProviders) failures +=
                            UsageFailure(p.id.raw, provider.wire, UsageProblem.Broken)
                        continue
                    }
                    touchCollector()
                    for (provider in readableProviders) {
                        val gateway =
                            HttpProviderQuotaGateway(
                                p.profile.baseUrl,
                                p.profile.username,
                                "",
                                transport = LoopbackQuotaTransport(dispatchers),
                                provider = provider,
                                inAppToken = {
                                    if (current(owner) && ownsCollector(owner))
                                        InAppQuotaCollector.readToken(stores.secrets, owner.id)
                                    else null
                                },
                            )
                        val answer =
                            try {
                                gateway.readSnapshot()
                            } finally {
                                gateway.close()
                            }
                        if (!current(p)) break
                        when (answer) {
                            is QuotaRead.Read ->
                                foldSnapshot(
                                    p.id.raw,
                                    provider,
                                    answer.snapshot,
                                    context,
                                    readings,
                                    failures,
                                )
                            is QuotaRead.Failed ->
                                if (answer.failure.kind == QuotaFailureKind.CollectorAuth)
                                    readings +=
                                        bare(p.id.raw, provider, UsageReadingStatus.AuthRequired)
                                else
                                    failures +=
                                        UsageFailure(p.id.raw, provider.wire, UsageProblem.Broken)
                        }
                    }
                } else {
                    val before = attempted[p.id.raw]
                    val every = if (context.background) 900_000L else 60_000L
                    val answer =
                        pending.remove(p.id.raw)
                            ?: if (
                                context.force ||
                                    before == null ||
                                    clock.now().toEpochMilliseconds() - before >= every
                            )
                                readComputer(p)
                            else continue
                    if (!current(p)) continue
                    if (answer.problem == PlanReaderProblem.Unreachable) {
                        unreached += p.id.raw
                        continue
                    }
                    if (answer.problem != null) {
                        for (provider in readableProviders) if (
                            context.lastReading(p.id.raw, provider.wire) != null ||
                                provider.wire in context.signedIn(p.id.raw)
                        )
                            failures += UsageFailure(p.id.raw, provider.wire, UsageProblem.Broken)
                        continue
                    }
                    for (provider in readableProviders) {
                        val auth = answer.auth[provider]
                        val snapshot = answer.snapshots[provider]
                        if (auth == null) {
                            if (context.lastReading(p.id.raw, provider.wire) != null)
                                readings += bare(p.id.raw, provider, UsageReadingStatus.NotSignedIn)
                            continue
                        }
                        val api =
                            auth == "api" &&
                                (provider == QuotaProvider.Codex ||
                                    provider == QuotaProvider.Minimax &&
                                        snapshot?.status == ProviderQuotaStatus.Unsupported)
                        signIns +=
                            UsageSignIn(
                                provider.wire,
                                p.id.raw,
                                method = if (api) SignInMethod.ApiKey else SignInMethod.Plan,
                                displayName = providerName(provider),
                            )
                        if (!api && snapshot != null)
                            foldSnapshot(p.id.raw, provider, snapshot, context, readings, failures)
                    }
                }
            }
            UsageSourceResult(readings, signIns, failures, unreached).ok()
        }

    private fun phoneOwner(): LoadedProfile? =
        stores.prefs.string("oc.builtinServerOwner")?.let(::profile)?.takeIf {
            inApp(it) && current(it)
        }

    /** A usage read never starts the helper, even when its last cached reading exists. */
    private suspend fun readPhone():
        Outcome<List<io.github.eslamasabry.oc.protocol.paseo.usage.ProviderUsageReading>>? {
        val owner = phoneOwner() ?: return null
        val status =
            attempt({ Failure.Device() }) {
                withContext(dispatchers.io) {
                    BuiltinLinux.get(application).agentHost.status(owner.id.raw)
                }
            }
        if ((status as? Outcome.Ok)?.value?.get("running") != true || !current(owner)) return null
        val secret =
            stores.secrets.read("oc.agentHostSecret.${owner.id.raw}").getOrNull()?.takeIf {
                Regex("^[a-f0-9]{64}$").matches(it)
            } ?: return Failure.SecretUnavailable().failed()
        redactor.registerSecret(secret)
        val transport =
            PaseoTransport(
                "ws://127.0.0.1:4099",
                secret,
                OkHttpPaseoSockets(),
                scope,
                dispatchers.serial("usage-phone"),
                dispatchers.default,
                requestTimeoutMs = 10_000,
            )
        return try {
            withTimeoutOrNull(12_000) {
                val answer = attempt({ Failure.Device() }) { transport.connect() }
                if (answer is Outcome.Failed) return@withTimeoutOrNull answer
                if (!current(owner))
                    return@withTimeoutOrNull Failure.Refused(RefusalReason.StaleScope).failed()
                val result = PaseoProviderUsageReader(transport, clock, owns = false).read()
                if (current(owner)) result else Failure.Refused(RefusalReason.StaleScope).failed()
            } ?: Failure.Network().failed()
        } finally {
            withContext(NonCancellable) {
                withTimeoutOrNull(3000) { attempt({ Failure.Device() }) { transport.close() } }
            }
        }
    }

    private fun foldPaseo(
        id: String,
        list: List<io.github.eslamasabry.oc.protocol.paseo.usage.ProviderUsageReading>,
        readings: MutableList<UsageReading>,
        signIns: MutableList<UsageSignIn>,
    ) {
        for (reading in list) {
            val provider = canonicalUsageProviderId(reading.providerId) ?: reading.providerId
            if (reading.needsSignIn && reading.windows.isEmpty()) {
                readings +=
                    UsageReading(
                        provider,
                        reading.displayName,
                        id,
                        UsageReadingStatus.NotSignedIn,
                        kotlin.time.Instant.fromEpochMilliseconds(reading.fetchedAtMs),
                        method = SignInMethod.Plan,
                    )
                continue
            }
            val windows =
                reading.windows.mapIndexed { index, w ->
                    UsageWindow(
                        when (w.durationSeconds) {
                            18_000 -> "5h"
                            604_800 -> "weekly"
                            else -> w.label.ifBlank { "window.$index" }
                        },
                        (100 - w.remainingPercent).toDouble(),
                        w.durationSeconds?.toLong(),
                        w.resetsAtMs?.let(kotlin.time.Instant::fromEpochMilliseconds),
                        w.label,
                    )
                }
            readings +=
                UsageReading(
                    provider,
                    reading.displayName,
                    id,
                    if (windows.isEmpty()) UsageReadingStatus.NoLimits else UsageReadingStatus.Ok,
                    kotlin.time.Instant.fromEpochMilliseconds(reading.fetchedAtMs),
                    windows,
                    method = SignInMethod.Plan,
                )
            signIns +=
                UsageSignIn(
                    provider,
                    id,
                    method = SignInMethod.Plan,
                    displayName = reading.displayName,
                )
        }
    }

    private suspend fun readCodex(
        p: LoadedProfile,
        context: UsageReadContext,
        readings: MutableList<UsageReading>,
        signIns: MutableList<UsageSignIn>,
        failures: MutableList<UsageFailure>,
        unreached: MutableSet<String>,
    ) {
        val graph = registry.live.value[p.id] ?: return
        if (
            !graph.session.capabilities.value.agentAccount ||
                graph.profile != p.toServerProfile(managedOnPhone = graph.profile.managedOnPhone)
        )
            return
        val gateway = graph.session.server as? CodexGateway ?: return
        val session = gateway.openAccountSession()
        try {
            val account = session.read().getOrNull()
            if (!current(p) || registry.live.value[p.id] !== graph) return
            if (account == null) {
                if (!session.active) unreached += p.id.raw
                else failures += UsageFailure(p.id.raw, "codex", UsageProblem.Broken)
                return
            }
            if (!account.signedIn) {
                if (context.lastReading(p.id.raw, "codex") != null || account.requiresSignIn)
                    readings += bare(p.id.raw, QuotaProvider.Codex, UsageReadingStatus.NotSignedIn)
                return
            }
            if (account.type?.lowercase()?.contains("key") == true) {
                signIns +=
                    UsageSignIn(
                        "codex",
                        p.id.raw,
                        method = SignInMethod.ApiKey,
                        displayName = "OpenAI",
                    )
                return
            }
            val buckets = session.rateLimits().getOrNull()
            if (!current(p) || registry.live.value[p.id] !== graph) return
            if (buckets == null) {
                failures += UsageFailure(p.id.raw, "codex", UsageProblem.Broken)
                return
            }
            val bucket = buckets.firstOrNull()
            val windows =
                listOf(bucket?.primary, bucket?.secondary).mapIndexedNotNull { index, w ->
                    w?.let {
                        UsageWindow(
                            if (index == 0) "primary" else "secondary",
                            it.usedPercent.toDouble(),
                            it.durationMinutes?.let { m ->
                                if (m <= Long.MAX_VALUE / 60) m * 60 else null
                            },
                            it.resetsAtMs?.let(kotlin.time.Instant::fromEpochMilliseconds),
                        )
                    }
                }
            val accountRef =
                account.email
                    ?.takeIf { it.isNotBlank() }
                    ?.let { DartJson.sha256Hex("codex-account:$it") }
            val plan =
                account.plan?.lowercase()?.takeIf {
                    it in
                        setOf("free", "go", "plus", "pro", "team", "business", "enterprise", "edu")
                }
            readings +=
                UsageReading(
                    "codex",
                    "Codex",
                    p.id.raw,
                    if (windows.isEmpty()) UsageReadingStatus.NoLimits else UsageReadingStatus.Ok,
                    context.now,
                    windows,
                    accountRef,
                    plan,
                    SignInMethod.Plan,
                )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (current(p)) failures += UsageFailure(p.id.raw, "codex", UsageProblem.Broken)
        } finally {
            withContext(NonCancellable) {
                withTimeoutOrNull(2000) { attempt({ Failure.Device() }) { session.close() } }
            }
        }
    }

    private fun bare(
        id: String,
        provider: QuotaProvider,
        status: UsageReadingStatus,
    ): UsageReading =
        UsageReading(
            provider.wire,
            providerName(provider),
            id,
            status,
            clock.now(),
            method = SignInMethod.Plan,
        )

    private fun foldSnapshot(
        id: String,
        provider: QuotaProvider,
        snapshot: ProviderQuotaSnapshot,
        context: UsageReadContext,
        readings: MutableList<UsageReading>,
        failures: MutableList<UsageFailure>,
    ) {
        when {
            snapshot.status == ProviderQuotaStatus.Ok && snapshot.canShowWindows -> {
                val windows =
                    snapshot.windows.mapNotNull { w ->
                        w.usedPercent?.let { UsageWindow(w.id, it, w.durationSeconds, w.resetsAt) }
                    }
                readings +=
                    UsageReading(
                        provider.wire,
                        providerName(provider),
                        id,
                        if (windows.isEmpty()) UsageReadingStatus.NoLimits
                        else UsageReadingStatus.Ok,
                        snapshot.fetchedAt,
                        windows,
                        snapshot.account.ref,
                        snapshot.account.plan,
                        SignInMethod.Plan,
                    )
            }
            snapshot.status == ProviderQuotaStatus.Unconfigured ->
                if (context.lastReading(id, provider.wire) != null)
                    readings += bare(id, provider, UsageReadingStatus.NotSignedIn)
            snapshot.status == ProviderQuotaStatus.AuthRequired ->
                readings += bare(id, provider, UsageReadingStatus.AuthRequired)
            snapshot.status == ProviderQuotaStatus.RateLimited ->
                failures += UsageFailure(id, provider.wire, UsageProblem.RateLimited)
            else -> failures += UsageFailure(id, provider.wire, UsageProblem.Broken)
        }
    }

    private fun collector(p: LoadedProfile): InAppQuotaCollector {
        check(ownsCollector(p))
        collectors[p.id.raw]
            ?.takeIf { it.first == p }
            ?.let {
                return it.second
            }
        val created =
            InAppQuotaCollector(
                prefs = stores.prefs,
                lane = stores.lanes.forProfile(p.id),
                secrets = stores.secrets,
                linux =
                    object : QuotaLinuxHost {
                        override suspend fun status(): QuotaLinuxHost.Status {
                            val status =
                                runtime.runtimeStatus().getOrNull()
                                    ?: return QuotaLinuxHost.Status(false, false, emptyList())
                            return QuotaLinuxHost.Status(
                                status.installed,
                                status.phase == RuntimePhase.Ready,
                                status.services,
                            )
                        }

                        override suspend fun run(
                            script: String,
                            timeoutMs: Long,
                        ): QuotaLinuxHost.RunResult {
                            require(current(p) && ownsCollector(p))
                            val result =
                                runtime.host.run(script, (timeoutMs + 999) / 1000).getOrNull()
                                    ?: return QuotaLinuxHost.RunResult(-1, "")
                            return QuotaLinuxHost.RunResult(result.exitCode, result.output)
                        }

                        override suspend fun startService(
                            name: String,
                            script: String,
                            port: Int?,
                        ) {
                            require(current(p) && ownsCollector(p))
                            check(runtime.host.startService(name, script, "") is Outcome.Ok)
                        }

                        override suspend fun stopService(name: String) {
                            require(current(p) && ownsCollector(p))
                            check(runtime.host.stopService(name) is Outcome.Ok)
                        }

                        override suspend fun serviceLog(name: String): String =
                            runtime.host.serviceLog(name, 4096)
                    },
                rootfs =
                    object : QuotaRootfs {
                        override suspend fun read(path: String): ByteArray? =
                            withContext(dispatchers.io) {
                                val file =
                                    safeQuotaFile(path, create = false) ?: return@withContext null
                                if (
                                    !Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) ||
                                        file.length() > 512 * 1024
                                )
                                    null
                                else
                                    Files.newByteChannel(
                                            file.toPath(),
                                            setOf(StandardOpenOption.READ, NOFOLLOW_LINKS),
                                        )
                                        .use { channel ->
                                            java.nio.channels.Channels.newInputStream(channel)
                                                .use { input ->
                                                    val out = java.io.ByteArrayOutputStream()
                                                    val buffer = ByteArray(8192)
                                                    while (true) {
                                                        val count = input.read(buffer)
                                                        if (count < 0) break
                                                        check(out.size() + count <= 512 * 1024)
                                                        out.write(buffer, 0, count)
                                                    }
                                                    out.toByteArray()
                                                }
                                        }
                            }

                        override suspend fun write(path: String, bytes: ByteArray) =
                            withContext(dispatchers.io) {
                                require(current(p) && ownsCollector(p))
                                val file = checkNotNull(safeQuotaFile(path, create = true))
                                val descriptor =
                                    android.system.Os.open(
                                        file.absolutePath,
                                        android.system.OsConstants.O_WRONLY or
                                            android.system.OsConstants.O_CREAT or
                                            android.system.OsConstants.O_TRUNC or
                                            android.system.OsConstants.O_NOFOLLOW or
                                            android.system.OsConstants.O_CLOEXEC,
                                        0b110_000_000,
                                    )
                                try {
                                    android.system.Os.fchmod(descriptor, 0b110_000_000)
                                    var at = 0
                                    while (at < bytes.size) {
                                        val count =
                                            android.system.Os.write(
                                                descriptor,
                                                bytes,
                                                at,
                                                bytes.size - at,
                                            )
                                        check(count > 0)
                                        at += count
                                    }
                                    android.system.Os.fsync(descriptor)
                                } finally {
                                    android.system.Os.close(descriptor)
                                }
                            }
                    },
                randomBytes = { size -> ByteArray(size).also(SecureRandom()::nextBytes) },
                loadCollector = {
                    withContext(dispatchers.io) {
                        application.assets.open("quota/collector.mjs").bufferedReader().use {
                            it.readText()
                        }
                    }
                },
                loadKeysHelper = {
                    withContext(dispatchers.io) {
                        application.assets.open("quota/keys.mjs").bufferedReader().use {
                            it.readText()
                        }
                    }
                },
                probe = { token -> probeCollector(token) },
                supported = true,
            )
        collectors[p.id.raw] = p to created
        return created
    }

    private fun safeQuotaFile(path: String, create: Boolean): File? {
        if (
            path !in
                setOf(
                    InAppQuotaCollector.COLLECTOR_PATH,
                    InAppQuotaCollector.KEYS_PATH,
                    InAppQuotaCollector.TOKEN_PATH,
                )
        )
            return null
        var current = BuiltinDiscovery.rootfs(application.filesDir)
        if (!Files.isDirectory(current.toPath(), NOFOLLOW_LINKS)) return null
        for (part in path.trimStart('/').split('/').dropLast(1)) {
            current = File(current, part)
            if (!Files.exists(current.toPath(), NOFOLLOW_LINKS) && create)
                Files.createDirectory(current.toPath())
            if (!Files.isDirectory(current.toPath(), NOFOLLOW_LINKS)) return null
        }
        if (create) android.system.Os.chmod(current.absolutePath, 0b111_000_000)
        val file = File(current, path.substringAfterLast('/'))
        if (Files.isSymbolicLink(file.toPath())) return null
        return file
    }

    private suspend fun probeCollector(token: String): Boolean =
        withContext(dispatchers.io) {
            val transport = LoopbackQuotaTransport(dispatchers)
            try {
                transport
                    .fetch(
                        QuotaRequest(
                            "GET",
                            "$IN_APP_QUOTA_ORIGIN$PROVIDER_QUOTA_PATH",
                            persistentMapOf("Authorization" to "Bearer $token"),
                            false,
                            0,
                            3000,
                            3000,
                            12000,
                        )
                    )
                    .body
                    .collect {}
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            } finally {
                transport.close(true)
            }
        }

    private fun touchCollector() {
        val owner = phoneOwner() ?: return
        idleStop?.cancel()
        idleStop = scope.launch {
            delay(120_000)
            lane.withLock {
                if (phoneOwner() == owner && current(owner) && ownsCollector(owner))
                    runtime.host.stopService(InAppQuotaCollector.SERVICE_NAME)
            }
        }
    }

    private data class ComputerAnswer(
        val snapshots: Map<QuotaProvider, ProviderQuotaSnapshot> = emptyMap(),
        val auth: Map<QuotaProvider, String> = emptyMap(),
        val problem: PlanReaderProblem? = null,
    ) {
        override fun toString(): String = "ComputerAnswer(${snapshots.size},${auth.size},$problem)"
    }

    private fun ComputerAnswer.outcome(p: LoadedProfile): Outcome<Unit> =
        if (problem == null) Unit.ok() else this@AndroidPlansReaders.problem(p, problem)

    private suspend fun readComputer(p: LoadedProfile): ComputerAnswer {
        attempted[p.id.raw] = clock.now().toEpochMilliseconds()
        publish(p) { it.copy(busy = true, phase = PlanReaderPhase.Reading, problem = null) }
        return try {
            val answer =
                when (val result = attempt({ Failure.Device() }) { runComputer(p) }) {
                    is Outcome.Failed -> ComputerAnswer(problem = PlanReaderProblem.Unreachable)
                    is Outcome.Ok -> result.value
                }
            if (current(p)) {
                if (answer.problem == PlanReaderProblem.ServerTooOld)
                    stores.prefs.commit(stores.lanes.forProfile(p.id)) {
                        remove(computerKey(p.id.raw))
                    }
                val found =
                    readableProviders
                        .filter { provider ->
                            answer.auth[provider]?.let { auth ->
                                auth != "api" ||
                                    provider != QuotaProvider.Codex &&
                                        !(provider == QuotaProvider.Minimax &&
                                            answer.snapshots[provider]?.status ==
                                                ProviderQuotaStatus.Unsupported)
                            } == true
                        }
                        .map { it.wire }
                        .toImmutableList()
                publish(p) {
                    it.copy(
                        busy = false,
                        phase =
                            if (answer.problem == null) PlanReaderPhase.Ready
                            else PlanReaderPhase.Idle,
                        problem = answer.problem,
                        foundProviderIds = found,
                    )
                }
            }
            answer
        } finally {
            if (current(p)) publish(p) { it.copy(busy = false) }
        }
    }

    /** A read owns a temporary gateway only when it cannot reuse the exact current graph. */
    private suspend fun <T> withSavedPair(
        p: LoadedProfile,
        reuseLive: Boolean = true,
        read: suspend (GatewayPair) -> Outcome<T>,
    ): Outcome<T> {
        if (!current(p) || p.reentry.password || p.reentry.agentToken)
            return Failure.SecretUnavailable().failed()
        val model =
            p.toServerProfile() ?: return Failure.Refused(RefusalReason.InvalidAddress).failed()
        val existing = registry.live.value[p.id].takeIf { reuseLive }
        if (existing != null) {
            if (
                existing.profile !=
                    p.toServerProfile(managedOnPhone = existing.profile.managedOnPhone)
            )
                return Failure.Refused(RefusalReason.StaleScope).failed()
            val operations = existing.session.server.facet<ServerOperationsGateway>()
            if (operations != null) {
                val result = read(GatewayPair(existing.session.server, operations))
                return if (current(p) && registry.live.value[p.id] === existing) result
                else Failure.Refused(RefusalReason.StaleScope).failed()
            }
        }
        val secret =
            when (val answer = stores.profiles.secret(p.id)) {
                is Outcome.Failed -> return answer
                is Outcome.Ok -> answer.value
            }
        secret?.let(redactor::registerSecret)
        if (!current(p)) return Failure.Refused(RefusalReason.StaleScope).failed()
        val factory =
            factories.singleOrNull { it.backend == model.backend }
                ?: return Failure.Unavailable(FeatureId("backend")).failed()
        val own =
            ConnectionCoroutineScope(
                supervisedContext(scope.coroutineContext[Job], dispatchers.default, "usage-read")
            )
        var pair: GatewayPair? = null
        try {
            val connected =
                factory.connect(
                    GatewaySpec(
                        model,
                        ConnectionCredentials(model.username, secret),
                        own,
                        reconnectAllowed = MutableStateFlow(false),
                    )
                )
            when (connected) {
                is Outcome.Failed -> return connected
                is Outcome.Ok -> pair = connected.value
            }
            if (!current(p)) return Failure.Refused(RefusalReason.StaleScope).failed()
            val result = read(pair)
            return if (current(p)) result else Failure.Refused(RefusalReason.StaleScope).failed()
        } finally {
            withContext(NonCancellable) {
                own.coroutineContext[Job]?.cancel()
                withTimeoutOrNull(3000) { attempt({ Failure.Device() }) { pair?.live?.close() } }
                withTimeoutOrNull(3000) { own.coroutineContext[Job]?.cancelAndJoin() }
            }
        }
    }

    private suspend fun runComputer(p: LoadedProfile): ComputerAnswer {
        if (!current(p) || p.reentry.password || !p.hasSecret)
            return ComputerAnswer(problem = PlanReaderProblem.Unavailable)
        val source =
            withContext(dispatchers.io) {
                application.assets.open("quota/read_once.mjs").bufferedReader().use {
                    it.readText()
                }
            }
        val result =
            withTimeoutOrNull(25_000) {
                withSavedPair(p, reuseLive = false) { pair ->
                    if (!pair.live.capabilities.value.terminal)
                        return@withSavedPair ComputerAnswer(
                                problem = PlanReaderProblem.ServerTooOld
                            )
                            .ok()
                    var last = ComputerAnswer(problem = PlanReaderProblem.NoAnswer)
                    val attempts =
                        listOf(
                            "node" to listOf("--input-type=module", "-e", source),
                            "bash" to
                                listOf(
                                    "-lc",
                                    """command -v node >/dev/null 2>&1 || { echo 'node: command not found'; sleep 1; exit 127; }; node --input-type=module -e "${'$'}1"; s=${'$'}?; sleep 1; exit ${'$'}s""",
                                    "_",
                                    source,
                                ),
                        )
                    for ((command, args) in attempts) {
                        if (!current(p))
                            return@withSavedPair ComputerAnswer(
                                    problem = PlanReaderProblem.Unreachable
                                )
                                .ok()
                        val answer = attemptComputer(pair.operations, command, args)
                        if (
                            answer.problem == null ||
                                answer.problem == PlanReaderProblem.ServerTooOld ||
                                answer.problem == PlanReaderProblem.Unreachable
                        )
                            return@withSavedPair answer.ok()
                        if (
                            answer.problem != PlanReaderProblem.NoAnswer ||
                                last.problem == PlanReaderProblem.NoAnswer
                        )
                            last = answer
                    }
                    last.ok()
                }
            }
        return when (result) {
            null -> ComputerAnswer(problem = PlanReaderProblem.NoAnswer)
            is Outcome.Failed ->
                ComputerAnswer(
                    problem =
                        if (result.failure is Failure.SecretUnavailable)
                            PlanReaderProblem.Unavailable
                        else PlanReaderProblem.Unreachable
                )
            is Outcome.Ok -> result.value
        }
    }

    private suspend fun attemptComputer(
        operations: ServerOperationsGateway,
        command: String,
        args: List<String>,
    ): ComputerAnswer {
        var id: String? = null
        var channel: TerminalChannel? = null
        val seen = StringBuilder()
        try {
            when (
                val started =
                    operations.createCommandTerminal(
                        command,
                        args.toImmutableList(),
                        getString(Res.string.usageReadUsage),
                    )
            ) {
                is Outcome.Failed ->
                    return ComputerAnswer(
                        problem =
                            when (val f = started.failure) {
                                is Failure.Unavailable -> PlanReaderProblem.ServerTooOld
                                is Failure.Rejected ->
                                    if (f.status in listOf(404, 405, 501))
                                        PlanReaderProblem.ServerTooOld
                                    else PlanReaderProblem.NodeMissing
                                else -> PlanReaderProblem.Unreachable
                            }
                    )
                is Outcome.Ok -> id = started.value.id
            }
            channel =
                operations.connectTerminal(id).getOrNull()
                    ?: return ComputerAnswer(problem = PlanReaderProblem.NodeMissing)
            var found: ComputerAnswer? = null
            try {
                channel.output
                    .takeWhile { chunk ->
                        if (seen.length + chunk.length > 256 * 1024) false
                        else {
                            seen.append(chunk)
                            found = parseComputer(seen.toString())
                            found == null
                        }
                    }
                    .collect {}
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
            return found ?: ComputerAnswer(problem = classifyComputer(seen.toString()))
        } finally {
            seen.clear()
            withContext(NonCancellable) {
                withTimeoutOrNull(2000) { attempt({ Failure.Device() }) { channel?.close() } }
                if (id != null) withTimeoutOrNull(5000) { operations.removeTerminal(id) }
            }
        }
    }

    private fun parseComputer(output: String): ComputerAnswer? {
        var answer: ComputerAnswer? = null
        for (line in strip(output).split(Regex("[\\r\\n]+"))) {
            val encoded =
                Regex("^OCMN_USAGE_V1 ([A-Za-z0-9+/]+={0,2})$")
                    .matchEntire(line.trim())
                    ?.groupValues
                    ?.get(1) ?: continue
            if (encoded.length > 96 * 1024) continue
            try {
                val json =
                    Json.parseToJsonElement(
                        Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8)
                    ) as? JsonObject ?: continue
                if (json["v"]?.jsonPrimitive?.intOrNull != 1) continue
                if (json["error"] != null) {
                    answer =
                        ComputerAnswer(
                            problem =
                                if (json["error"]?.jsonPrimitive?.contentOrNull == "nodeTooOld")
                                    PlanReaderProblem.NodeTooOld
                                else PlanReaderProblem.NoAnswer
                        )
                    continue
                }
                val snapshots =
                    readableProviders
                        .mapNotNull { provider ->
                            ProviderQuotaSnapshot.fromJson(
                                    (json["providers"] as? JsonObject)?.get(provider.wire)
                                )
                                ?.takeIf { it.provider == provider }
                                ?.let { provider to it }
                        }
                        .toMap()
                val auth =
                    readableProviders
                        .mapNotNull { provider ->
                            (json["auth"] as? JsonObject)
                                ?.get(provider.wire)
                                ?.jsonPrimitive
                                ?.contentOrNull
                                ?.takeIf { it in setOf("api", "oauth") }
                                ?.let { provider to it }
                        }
                        .toMap()
                answer = ComputerAnswer(snapshots, auth)
            } catch (_: Exception) {}
        }
        return answer
    }

    private fun strip(output: String): String =
        output.replace(
            Regex(
                "\\x1B\\][^\\x07\\x1B]*(?:\\x07|\\x1B\\\\)|\\x1B\\[[0-9;?<=>]*[ -/]*[@-~]|\\x1B[@-Z\\\\-_]"
            ),
            "",
        )

    private fun classifyComputer(output: String): PlanReaderProblem {
        val text = strip(output).lowercase()
        return when {
            listOf("not found", "not recognized", "enoent", "no such file").any(text::contains) ->
                PlanReaderProblem.NodeMissing
            listOf("syntaxerror", "err_unknown_builtin", "unexpected token", "requires node")
                .any(text::contains) -> PlanReaderProblem.NodeTooOld
            else -> PlanReaderProblem.NoAnswer
        }
    }

    suspend fun removeProfile(id: ProfileId): Outcome<Int> {
        closed += id.raw
        val deletion: Outcome<Outcome<Int>> =
            attempt({ Failure.StorageRefused() }) {
                lane.withLock {
                    val p = stores.profiles.profile(id)
                    if (p != null && ownsCollector(p) && enabled(p)) {
                        idleStop?.cancel()
                        val stopped = runtime.host.stopService(InAppQuotaCollector.SERVICE_NAME)
                        if (stopped is Outcome.Failed) {
                            closed -= id.raw
                            return@withLock stopped
                        }
                        // Delete only this collector's fixed files, never follow runtime links.
                        withContext(dispatchers.io) {
                            for (name in
                                listOf(
                                    "collector.mjs",
                                    "keys.mjs",
                                    "read.token",
                                    "auth.env",
                                    "glm.key",
                                    "minimax.key",
                                )) {
                                val parent =
                                    BuiltinDiscovery.rootfs(application.filesDir)
                                        .resolve(InAppQuotaCollector.DIR.trimStart('/'))
                                if (
                                    parent.canonicalFile == parent.absoluteFile &&
                                        Files.isDirectory(parent.toPath(), NOFOLLOW_LINKS)
                                )
                                    Files.deleteIfExists(File(parent, name).toPath())
                            }
                        }
                    }
                    when (val erased = stores.secrets.delete(InAppQuotaCollector.tokenKey(id))) {
                        is Outcome.Failed -> {
                            closed -= id.raw
                            return@withLock erased
                        }
                        is Outcome.Ok -> Unit
                    }
                    collectors.remove(id.raw)
                    pending.remove(id.raw)
                    attempted.remove(id.raw)
                    state.update { (it - id.raw).toImmutableMap() }
                    1.ok()
                }
            }
        return when (deletion) {
            is Outcome.Ok -> deletion.value
            is Outcome.Failed -> {
                closed -= id.raw
                deletion
            }
        }
    }

    companion object {
        private val readableProviders = QuotaProvider.entries.filter(::quotaCollectionAvailable)

        fun computerKey(id: String): String = "oc.usageComputerReader.$id"

        private fun providerName(p: QuotaProvider): String =
            when (p) {
                QuotaProvider.Codex -> "Codex"
                QuotaProvider.Claude -> "Claude"
                QuotaProvider.Glm -> "GLM"
                QuotaProvider.Minimax -> "MiniMax"
            }

        fun register(scope: ProfileScope) {
            InAppQuotaCollector.register(scope)
            scope.register(
                KeyShape(
                    "oc.usageComputerReader.",
                    KeyScope.Profile,
                    PrefType.Bool,
                    ":engine:integrations",
                    "consent to one-shot plan usage reads",
                )
            )
        }
    }
}

/** A dedicated no-redirect loopback transport. Tokens can never travel to a remote URL. */
private class LoopbackQuotaTransport(private val dispatchers: AppDispatchers) : QuotaTransport {
    private val client =
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .proxy(Proxy.NO_PROXY)
            .cache(null)
            .build()
    @Volatile private var response: Response? = null
    @Volatile private var call: Call? = null

    override suspend fun fetch(request: QuotaRequest): QuotaResponse {
        val url = request.url.toHttpUrl()
        require(
            url.scheme == "http" &&
                url.host == "127.0.0.1" &&
                url.port == IN_APP_QUOTA_PORT &&
                url.username.isEmpty() &&
                url.password.isEmpty()
        )
        require(request.method == "GET" && !request.followRedirects && request.maxRedirects == 0)
        val req =
            Request.Builder()
                .url(url)
                .get()
                .apply { for ((name, value) in request.headers) header(name, value) }
                .build()
        val c =
            client
                .newBuilder()
                .connectTimeout(request.connectTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(request.sendTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(request.receiveTimeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(request.receiveTimeoutMs, TimeUnit.MILLISECONDS)
                .build()
                .newCall(req)
        call = c
        val answer =
            suspendCancellableCoroutine<Response> { waiting ->
                waiting.invokeOnCancellation { c.cancel() }
                c.enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, error: IOException) {
                            waiting.resumeWithException(error)
                        }

                        override fun onResponse(call: Call, value: Response) {
                            response = value
                            waiting.resume(value) { _, received, _ -> received.close() }
                        }
                    }
                )
            }
        return QuotaResponse(
            answer.code,
            flow {
                    answer.use { received ->
                        if (received.code == 200)
                            received.body.byteStream().use { input ->
                                val buffer = ByteArray(8192)
                                var total = 0
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= HttpProviderQuotaGateway.MAX_RESPONSE_BYTES)
                                    emit(buffer.copyOf(count))
                                }
                            }
                    }
                }
                .flowOn(dispatchers.io),
            isRedirect = answer.isRedirect,
        )
    }

    override fun close(force: Boolean) {
        if (force) call?.cancel()
        response?.close()
        response = null
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
}

@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
@Inject
class PlansReaderDeletionCleaner(private val readers: AndroidPlansReaders) :
    ProfileResourceCleaner {
    override val owner: String = ":engine:integrations-usage (readers)"
    override val order: Int = DeletionStep.DEVICE_SURFACES - 1
    override val guardsSweep: Boolean = true

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        readers.removeProfile(profileId)
}
