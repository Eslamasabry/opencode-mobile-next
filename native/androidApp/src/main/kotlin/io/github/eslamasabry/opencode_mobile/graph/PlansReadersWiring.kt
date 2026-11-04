package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.scope.*
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.quota.*
import io.github.eslamasabry.oc.engine.integrations.usage.*
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.feature.plans.presentation.*
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGateway
import io.github.eslamasabry.oc.protocol.paseo.usage.PaseoProviderUsageReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

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
    private val collectors = HashMap<String, InAppQuotaCollector>()

    init {
        scope.launch {
            if (stores.warm() !is Outcome.Ok) return@launch
            stores.profiles.profiles.collect { profiles ->
                val alive = profiles.mapTo(HashSet()) { it.id.raw }
                state.update { current -> current.filterKeys { it in alive }.toImmutableMap() }
                for (p in profiles) if (
                    p.profile.backend == BackendKind.OpenCode2 && p.id.raw !in closed
                )
                    publish(p)
            }
        }
    }

    fun connections(): List<UsageConnection> =
        stores.profiles.profiles.value.mapNotNull { profile ->
            if (profile.id.raw in closed) null
            else
                when (profile.profile.backend) {
                    BackendKind.OpenCode2 ->
                        UsageConnection(
                            profile.id.raw,
                            profile.profile.name,
                            if (inApp(profile)) UsageConnectionKind.InAppUbuntu
                            else UsageConnectionKind.OpenCodeServer,
                        )
                    BackendKind.Paseo ->
                        UsageConnection(
                            profile.id.raw,
                            profile.profile.name,
                            UsageConnectionKind.PaseoServer,
                        )
                    else -> null
                }
        }

    private fun profile(id: String): LoadedProfile? =
        stores.profiles.profile(ProfileId(id))?.takeIf { id !in closed }

    private fun inApp(p: LoadedProfile): Boolean =
        InAppQuotaCollector.looksLikeInAppServer(quotaProfile(p))

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

    override suspend fun setUp(id: String): Outcome<Unit> = lane.withLock {
        if (stores.warm() !is Outcome.Ok) return@withLock Failure.StorageRefused().failed()
        val p =
            profile(id) ?: return@withLock Failure.Refused(RefusalReason.ProfileDeleted).failed()
        if (p.profile.backend != BackendKind.OpenCode2 || p.reentry.password || !p.hasSecret)
            return@withLock problem(p, PlanReaderProblem.Unavailable)
        publish(p) { it.copy(busy = true, phase = PlanReaderPhase.Checking, problem = null) }
        if (inApp(p)) {
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
            if (!current(p)) return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
            val error = (result as? Outcome.Ok)?.value
            if (result is Outcome.Failed || error != null)
                return@withLock problem(
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
            if (saved is Outcome.Failed) return@withLock problem(p, PlanReaderProblem.SaveFailed)
            readComputer(p).also { answer -> if (current(p)) pending[id] = answer }.outcome(p)
        }
    }

    override suspend fun read(id: String): Outcome<Unit> = lane.withLock {
        val p =
            profile(id) ?: return@withLock Failure.Refused(RefusalReason.ProfileDeleted).failed()
        if (inApp(p)) {
            if (!enabled(p)) return@withLock problem(p, PlanReaderProblem.Unavailable)
            if (!collector(p).ensureRunning(quotaProfile(p)))
                return@withLock problem(p, PlanReaderProblem.Start)
            touchCollector()
            publish(p) { it.copy(busy = false, phase = PlanReaderPhase.Ready, problem = null) }
            Unit.ok()
        } else {
            readComputer(p).also { answer -> if (current(p)) pending[id] = answer }.outcome(p)
        }
    }

    override suspend fun turnOff(id: String): Outcome<Unit> = lane.withLock {
        val p =
            profile(id) ?: return@withLock Failure.Refused(RefusalReason.ProfileDeleted).failed()
        publish(p) { it.copy(busy = true, problem = null) }
        if (inApp(p)) {
            idleStop?.cancel()
            val answer = attempt({ Failure.Device() }) { collector(p).turnOff(p.id) }
            if (answer is Outcome.Failed || (answer as? Outcome.Ok)?.value != null)
                return@withLock problem(p, PlanReaderProblem.Remove)
        } else if (
            stores.prefs.commit(stores.lanes.forProfile(p.id)) { remove(computerKey(id)) }
                is Outcome.Failed
        )
            return@withLock problem(p, PlanReaderProblem.SaveFailed)
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
                val p = profile(connection.id) ?: continue
                if (p.profile.backend == BackendKind.Paseo) {
                    val gateway =
                        registry.live.value[p.id]?.session?.server as? PaseoGateway ?: continue
                    val answer =
                        PaseoProviderUsageReader(gateway.transport, clock, owns = false).read()
                    if (!current(p)) continue
                    if (answer is Outcome.Failed) {
                        unreached += p.id.raw
                        continue
                    }
                    for (reading in (answer as Outcome.Ok).value) {
                        val provider =
                            canonicalUsageProviderId(reading.providerId) ?: reading.providerId
                        signIns +=
                            UsageSignIn(
                                provider,
                                p.id.raw,
                                if (reading.needsSignIn) SignInState.NotSignedIn
                                else SignInState.SignedIn,
                                SignInMethod.Plan,
                                reading.displayName,
                            )
                        if (!reading.needsSignIn)
                            readings +=
                                UsageReading(
                                    provider,
                                    reading.displayName,
                                    p.id.raw,
                                    UsageReadingStatus.Ok,
                                    kotlin.time.Instant.fromEpochMilliseconds(reading.fetchedAtMs),
                                    reading.windows.mapIndexed { i, w ->
                                        UsageWindow(
                                            "paseo.$i",
                                            (100 - w.remainingPercent).toDouble(),
                                            w.durationSeconds?.toLong(),
                                            w.resetsAtMs?.let(
                                                kotlin.time.Instant::fromEpochMilliseconds
                                            ),
                                            w.label,
                                        )
                                    },
                                    method = SignInMethod.Plan,
                                )
                    }
                    continue
                }
                if (p.profile.backend != BackendKind.OpenCode2 || !enabled(p)) continue
                if (inApp(p)) {
                    if (!collector(p).ensureRunning(quotaProfile(p))) {
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
                                inAppToken = { InAppQuotaCollector.readToken(stores.secrets, p.id) },
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

    private fun collector(p: LoadedProfile): InAppQuotaCollector =
        collectors.getOrPut(p.id.raw) {
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
                            require(current(p))
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
                            require(current(p))
                            check(runtime.host.startService(name, script, "") is Outcome.Ok)
                        }

                        override suspend fun stopService(name: String) {
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
                                                .readBytes()
                                        }
                            }

                        override suspend fun write(path: String, bytes: ByteArray) =
                            withContext(dispatchers.io) {
                                require(current(p))
                                val file = checkNotNull(safeQuotaFile(path, create = true))
                                Files.newByteChannel(
                                        file.toPath(),
                                        setOf(
                                            StandardOpenOption.WRITE,
                                            StandardOpenOption.CREATE,
                                            StandardOpenOption.TRUNCATE_EXISTING,
                                            NOFOLLOW_LINKS,
                                        ),
                                    )
                                    .use { channel ->
                                        java.nio.channels.Channels.newOutputStream(channel).use {
                                            it.write(bytes)
                                        }
                                    }
                                android.system.Os.chmod(file.absolutePath, 0b110_000_000)
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
        idleStop?.cancel()
        idleStop = scope.launch {
            delay(120_000)
            lane.withLock { runtime.host.stopService(InAppQuotaCollector.SERVICE_NAME) }
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
        val answer =
            when (val result = attempt({ Failure.Device() }) { runComputer(p) }) {
                is Outcome.Failed -> ComputerAnswer(problem = PlanReaderProblem.Unreachable)
                is Outcome.Ok -> result.value
            }
        if (current(p)) {
            if (answer.problem == PlanReaderProblem.ServerTooOld)
                stores.prefs.commit(stores.lanes.forProfile(p.id)) { remove(computerKey(p.id.raw)) }
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
                        if (answer.problem == null) PlanReaderPhase.Ready else PlanReaderPhase.Idle,
                    problem = answer.problem,
                    foundProviderIds = found,
                )
            }
        }
        return answer
    }

    private suspend fun runComputer(p: LoadedProfile): ComputerAnswer {
        if (!current(p) || p.reentry.password || !p.hasSecret)
            return ComputerAnswer(problem = PlanReaderProblem.Unavailable)
        val model =
            p.toServerProfile() ?: return ComputerAnswer(problem = PlanReaderProblem.Unavailable)
        val graph =
            registry.open(model).getOrNull()
                ?: return ComputerAnswer(problem = PlanReaderProblem.Unreachable)
        if (!graph.session.capabilities.value.terminal || !current(p))
            return ComputerAnswer(problem = PlanReaderProblem.ServerTooOld)
        val source =
            withContext(dispatchers.io) {
                application.assets.open("quota/read_once.mjs").bufferedReader().use {
                    it.readText()
                }
            }
        val operations =
            graph.session.server.facet<ServerOperationsGateway>()
                ?: return ComputerAnswer(problem = PlanReaderProblem.ServerTooOld)
        return withTimeoutOrNull(25_000) {
            var last = ComputerAnswer(problem = PlanReaderProblem.NoAnswer)
            val attempts =
                listOf(
                    "node" to listOf("--input-type=module", "-e", source),
                    "bash" to
                        listOf(
                            "-lc",
                            "command -v node >/dev/null 2>&1 || { echo 'node: command not found'; sleep 1; exit 127; }; node --input-type=module -e \"\$1\"; s=\$?; sleep 1; exit \$s",
                            "_",
                            source,
                        ),
                )
            for ((command, args) in attempts) {
                if (!current(p) || registry.live.value[p.id] !== graph)
                    return@withTimeoutOrNull ComputerAnswer(problem = PlanReaderProblem.Unreachable)
                val answer = attemptComputer(operations, command, args)
                if (
                    answer.problem == null ||
                        answer.problem == PlanReaderProblem.ServerTooOld ||
                        answer.problem == PlanReaderProblem.Unreachable
                )
                    return@withTimeoutOrNull answer
                if (
                    answer.problem != PlanReaderProblem.NoAnswer ||
                        last.problem == PlanReaderProblem.NoAnswer
                )
                    last = answer
            }
            last
        } ?: ComputerAnswer(problem = PlanReaderProblem.NoAnswer)
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
                        "Read plan usage",
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
        return lane.withLock {
            val p = stores.profiles.profile(id)
            if (p != null && inApp(p) && enabled(p)) {
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
                        val path = InAppQuotaCollector.DIR + "/" + name
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
            stores.secrets.delete(InAppQuotaCollector.tokenKey(id))
            collectors.remove(id.raw)
            pending.remove(id.raw)
            attempted.remove(id.raw)
            state.update { (it - id.raw).toImmutableMap() }
            1.ok()
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
    @Volatile private var connection: HttpURLConnection? = null

    override suspend fun fetch(request: QuotaRequest): QuotaResponse =
        withContext(dispatchers.io) {
            val url = URL(request.url)
            require(
                url.protocol == "http" &&
                    url.host == "127.0.0.1" &&
                    url.port == IN_APP_QUOTA_PORT &&
                    url.userInfo == null
            )
            val c = (url.openConnection() as HttpURLConnection).also { connection = it }
            c.instanceFollowRedirects = false
            c.connectTimeout = request.connectTimeoutMs.toInt()
            c.readTimeout = request.receiveTimeoutMs.toInt()
            c.requestMethod = "GET"
            for ((name, value) in request.headers) c.setRequestProperty(name, value)
            val status = c.responseCode
            QuotaResponse(
                status,
                flow {
                        if (status == 200)
                            c.inputStream.use { input ->
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
                    .flowOn(dispatchers.io),
                isRedirect = status in 300..399,
            )
        }

    override fun close(force: Boolean) {
        connection?.disconnect()
        connection = null
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
