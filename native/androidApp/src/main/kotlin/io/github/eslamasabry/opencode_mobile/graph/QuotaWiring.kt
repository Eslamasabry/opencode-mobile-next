package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.system.Os
import android.system.OsConstants
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.quota.*
import io.github.eslamasabry.oc.engine.integrations.usage.*
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.feature.linux.host.LinuxHost
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.plans.presentation.CollectorControls
import io.github.eslamasabry.oc.feature.plans.presentation.CollectorFailure
import io.github.eslamasabry.oc.feature.plans.presentation.CollectorUi
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.linuxQuotaTitle
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.getString

/** Optional local reader: no startup hook, no network sign-in, no credential leaves Ubuntu. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CollectorControls>())
@Inject
class AndroidQuotaCollector(
    private val app: Application,
    private val stores: PreviewStores,
    private val linux: LinuxHost,
    private val platform: PlatformCapabilities,
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
    appScope: AppCoroutineScope,
) : CollectorControls {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.io)
    private val lane = Mutex()
    private val mutable = MutableStateFlow(CollectorUi())
    override val state: StateFlow<CollectorUi> = mutable.asStateFlow()
    private var idle: Job? = null
    private val random = SecureRandom()
    private val hosts = java.util.concurrent.ConcurrentHashMap<ProfileId, InAppQuotaCollector>()

    init {
        scope.launch {
            stores.warm()
            stores.profiles.profiles.collect { publish() }
        }
    }

    private fun profile(): QuotaServerProfile? =
        stores.profiles.profiles.value
            .firstOrNull {
                platform.builtinLinux &&
                    stores.lanes.forProfile(it.id).isAdmitting &&
                    it.profile.backend == BackendKind.OpenCode2 &&
                    it.profile.baseUrl.trimEnd('/') == "http://127.0.0.1:4097"
            }
            ?.let { QuotaServerProfile(it.id, it.profile.baseUrl, isOpenCode = true) }

    private fun publish() {
        val p = profile()
        mutable.update {
            it.copy(
                available = p != null,
                enabled =
                    p?.let { stores.prefs.bool(InAppQuotaCollector.enabledKey(it.id)) } == true,
            )
        }
    }

    private fun collector(profile: ProfileId): InAppQuotaCollector =
        hosts[profile]
            ?: InAppQuotaCollector(
                    prefs = stores.prefs,
                    lane = stores.lanes.forProfile(profile),
                    secrets = stores.secrets,
                    linux =
                        object : QuotaLinuxHost {
                            override suspend fun status(): QuotaLinuxHost.Status {
                                val ready = linux.presence() == LinuxPresence.Ready
                                return QuotaLinuxHost.Status(
                                    ready,
                                    ready,
                                    if (ready && linux.serviceRunning("quota")) listOf("quota")
                                    else emptyList(),
                                )
                            }

                            override suspend fun run(
                                script: String,
                                timeoutMs: Long,
                            ): QuotaLinuxHost.RunResult =
                                when (
                                    val result =
                                        linux.run(script, (timeoutMs / 1_000).coerceAtLeast(1))
                                ) {
                                    is Outcome.Ok ->
                                        QuotaLinuxHost.RunResult(
                                            result.value.exitCode,
                                            result.value.output,
                                        )
                                    is Outcome.Failed -> QuotaLinuxHost.RunResult(-1, "")
                                }

                            override suspend fun startService(
                                name: String,
                                script: String,
                                port: Int?,
                            ) {
                                check(
                                    linux.startService(
                                        name,
                                        script,
                                        getString(Res.string.linuxQuotaTitle),
                                    ) is Outcome.Ok
                                )
                            }

                            override suspend fun stopService(name: String) {
                                check(linux.stopService(name) is Outcome.Ok)
                            }

                            override suspend fun serviceLog(name: String): String =
                                linux.serviceLog(name, 16_384)
                        },
                    rootfs =
                        object : QuotaRootfs {
                            private fun file(path: String): File {
                                require(path.startsWith(InAppQuotaCollector.DIR + "/"))
                                val root = BuiltinDiscovery.rootfs(app.filesDir).canonicalFile
                                val target = File(root, path.removePrefix("/"))
                                require(
                                    target.canonicalPath == target.absolutePath &&
                                        target.canonicalPath.startsWith(root.path + "/")
                                )
                                return target
                            }

                            override suspend fun read(path: String): ByteArray? =
                                withContext(dispatchers.io) {
                                    val f = file(path)
                                    if (!f.isFile) null else f.readBytes()
                                }

                            override suspend fun write(path: String, bytes: ByteArray) =
                                withContext(dispatchers.io) {
                                    val f = file(path)
                                    f.parentFile!!.mkdirs()
                                    Os.chmod(f.parentFile!!.path, 448)
                                    val fd =
                                        Os.open(
                                            f.path,
                                            OsConstants.O_WRONLY or
                                                OsConstants.O_CREAT or
                                                OsConstants.O_TRUNC or
                                                OsConstants.O_NOFOLLOW,
                                            384,
                                        )
                                    java.io.FileOutputStream(fd).use {
                                        it.write(bytes)
                                        it.fd.sync()
                                    }
                                    Os.chmod(f.path, 384)
                                }
                        },
                    randomBytes = { count -> ByteArray(count).also(random::nextBytes) },
                    loadCollector = {
                        withContext(dispatchers.io) {
                            app.assets.open("quota/collector.mjs").bufferedReader().use {
                                it.readText()
                            }
                        }
                    },
                    loadKeysHelper = {
                        withContext(dispatchers.io) {
                            app.assets.open("quota/keys.mjs").bufferedReader().use { it.readText() }
                        }
                    },
                    probe = ::probe,
                    supported = platform.builtinLinux,
                )
                .also { hosts[profile] = it }

    override suspend fun setUp(): Unit = lane.withLock {
        val p = profile() ?: return@withLock
        mutable.update { it.copy(busy = true, failure = null, details = null) }
        try {
            finish(collector(p.id).setUp(p))
            touch()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            finish(QuotaSetupError(QuotaSetupFailure.Prepare))
        } finally {
            mutable.update { it.copy(busy = false) }
            publish()
        }
    }

    override suspend fun turnOff(): Unit = lane.withLock {
        val p = profile() ?: return@withLock
        mutable.update { it.copy(busy = true, failure = null, details = null) }
        idle?.cancel()
        idle = null
        try {
            finish(collector(p.id).turnOff(p.id))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            finish(QuotaSetupError(QuotaSetupFailure.Remove))
        } finally {
            mutable.update { it.copy(busy = false) }
            publish()
        }
    }

    private fun finish(error: QuotaSetupError?) {
        mutable.update {
            it.copy(
                failure = error?.failure?.let { f -> CollectorFailure.valueOf(f.name) },
                details =
                    error?.detail?.let { detail ->
                        Failure.Runtime(
                            RuntimeProblem.OwnershipUnknown,
                            redactor.details(redactor.excerpt(detail)),
                        )
                    },
            )
        }
    }

    private fun touch() {
        idle?.cancel()
        idle = scope.launch {
            delay(120_000)
            lane.withLock { linux.stopService(InAppQuotaCollector.SERVICE_NAME) }
        }
    }

    /** Only the quota gateway invokes this; ordinary phone server startup never does. */
    suspend fun token(profile: QuotaServerProfile): String? = lane.withLock {
        if (
            !stores.lanes.forProfile(profile.id).isAdmitting ||
                stores.profiles.profile(profile.id) == null
        )
            return@withLock null
        val c = collector(profile.id)
        if (!c.appliesTo(profile) || !c.isEnabled(profile.id)) return@withLock null
        val token =
            InAppQuotaCollector.readToken(stores.secrets, profile.id) ?: return@withLock null
        redactor.registerSecret(token)
        if (!c.ensureRunning(profile)) return@withLock null
        touch()
        repeat(20) {
            if (probe(token)) return@withLock token
            delay(500)
        }
        null
    }

    private suspend fun probe(token: String): Boolean =
        withContext(dispatchers.io) {
            try {
                val connection =
                    URL(IN_APP_QUOTA_ORIGIN + PROVIDER_QUOTA_PATH).openConnection()
                        as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 3_000
                    connection.readTimeout = 12_000
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    // Any answer proves it listens; the readings say the rest.
                    connection.responseCode > 0
                } finally {
                    connection.disconnect()
                }
            } catch (_: java.io.IOException) {
                false
            }
        }

    suspend fun removeProfile(id: ProfileId, options: DeletionOptions): Outcome<Int> =
        lane.withLock {
            val wasOn = stores.prefs.bool(InAppQuotaCollector.enabledKey(id)) == true
            if (wasOn && !options.linuxGone) {
                idle?.cancel()
                idle = null
                // Best effort, as in Flutter: a leftover folder is guarded by the token removed below.
                if (linux.stopService(InAppQuotaCollector.SERVICE_NAME) is Outcome.Ok)
                    linux.run("rm -rf ${InAppQuotaCollector.DIR} && echo 'qc removed'", 60)
            }
            if (stores.secrets.delete(InAppQuotaCollector.tokenKey(id)) is Outcome.Failed)
                return@withLock Failure.StorageRefused().failed()
            hosts.remove(id)
            // The deletion transaction already closed the profile lane; its generic scope sweep
            // removes the switch.
            (if (wasOn) 1 else 0).ok()
        }

    fun usageSource(): UsageSource = UsageSource { context ->
        val readings = ArrayList<UsageReading>()
        val failures = ArrayList<UsageFailure>()
        for (connection in context.connections) {
            val saved = stores.profiles.profile(ProfileId(connection.id)) ?: continue
            val p =
                QuotaServerProfile(
                    saved.id,
                    saved.profile.baseUrl,
                    isOpenCode = saved.profile.backend == BackendKind.OpenCode2,
                )
            if (!collector(p.id).appliesTo(p) || !collector(p.id).isEnabled(saved.id)) continue
            for (provider in
                listOf(QuotaProvider.Codex, QuotaProvider.Glm, QuotaProvider.Minimax)) {
                val gateway =
                    HttpProviderQuotaGateway(
                        p.baseUrl,
                        "",
                        "",
                        transport = AndroidQuotaTransport(dispatchers),
                        provider = provider,
                        inAppToken = { token(p) },
                    )
                try {
                    when (val read = gateway.readSnapshot()) {
                        is QuotaRead.Failed ->
                            failures +=
                                UsageFailure(connection.id, provider.wire, UsageProblem.Broken)
                        is QuotaRead.Read -> {
                            val s = read.snapshot
                            if (
                                s.status == ProviderQuotaStatus.Unconfigured ||
                                    s.status == ProviderQuotaStatus.Unsupported
                            )
                                continue
                            if (
                                s.isStale(context.now) ||
                                    (s.status == ProviderQuotaStatus.Ok && !s.canShowWindows)
                            ) {
                                failures +=
                                    UsageFailure(connection.id, provider.wire, UsageProblem.Broken)
                                continue
                            }
                            val status =
                                when (s.status) {
                                    ProviderQuotaStatus.Ok ->
                                        if (s.windows.isEmpty()) UsageReadingStatus.NoLimits
                                        else UsageReadingStatus.Ok
                                    ProviderQuotaStatus.AuthRequired ->
                                        UsageReadingStatus.AuthRequired
                                    else -> {
                                        failures +=
                                            UsageFailure(
                                                connection.id,
                                                provider.wire,
                                                UsageProblem.Broken,
                                            )
                                        continue
                                    }
                                }
                            readings +=
                                UsageReading(
                                    provider.wire,
                                    when (provider) {
                                        QuotaProvider.Codex -> "Codex"
                                        QuotaProvider.Glm -> "GLM"
                                        else -> "MiniMax"
                                    },
                                    connection.id,
                                    status,
                                    s.fetchedAt,
                                    windows =
                                        if (s.canShowWindows)
                                            s.windows.mapNotNull { w ->
                                                w.usedPercent?.let {
                                                    UsageWindow(
                                                        w.id,
                                                        it,
                                                        w.durationSeconds,
                                                        w.resetsAt,
                                                    )
                                                }
                                            }
                                        else emptyList(),
                                    accountRef = s.account.ref,
                                    plan = s.account.plan,
                                    method = SignInMethod.Plan,
                                )
                        }
                    }
                } finally {
                    gateway.close()
                }
            }
        }
        UsageSourceResult(readings = readings, failures = failures).ok()
    }
}

/**
 * Redirect-free, cookie-free transport with response lifetime owned by its bounded body collector.
 */
private class AndroidQuotaTransport(private val dispatchers: AppDispatchers) : QuotaTransport {
    private val connections =
        java.util.Collections.synchronizedSet(mutableSetOf<HttpURLConnection>())

    override suspend fun fetch(request: QuotaRequest): QuotaResponse =
        withContext(dispatchers.io) {
            val connection = URL(request.url).openConnection() as HttpURLConnection
            connections += connection
            connection.instanceFollowRedirects = false
            connection.requestMethod = request.method
            connection.connectTimeout = request.connectTimeoutMs.toInt()
            connection.readTimeout = request.receiveTimeoutMs.toInt()
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            try {
                val code = connection.responseCode
                if (code != 200) {
                    connection.disconnect()
                    connections -= connection
                }
                QuotaResponse(
                    code,
                    flow {
                            try {
                                connection.inputStream.use { input ->
                                    val bytes = ByteArray(8_192)
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val read = input.read(bytes)
                                        if (read < 0) break
                                        emit(bytes.copyOf(read))
                                    }
                                }
                            } finally {
                                connection.disconnect()
                                connections -= connection
                            }
                        }
                        .flowOn(dispatchers.io),
                    isRedirect = code in 300..399,
                )
            } catch (e: Exception) {
                connection.disconnect()
                connections -= connection
                throw e
            }
        }

    override fun close(force: Boolean) {
        val copy = synchronized(connections) { connections.toList().also { connections.clear() } }
        copy.forEach { it.disconnect() }
    }
}

@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
@Inject
class QuotaCollectorDeletionCleaner(private val collector: AndroidQuotaCollector) :
    ProfileResourceCleaner {
    override val owner: String = ":engine:integrations-quota"
    override val order: Int = DeletionStep.DEVICE_SURFACES

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        collector.removeProfile(profileId, options)
}
