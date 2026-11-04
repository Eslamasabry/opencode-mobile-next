package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.app.NotificationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.android.notifications.AlertRoute
import io.github.eslamasabry.oc.android.notifications.AlertShown
import io.github.eslamasabry.oc.android.notifications.AndroidAlertShade
import io.github.eslamasabry.oc.android.notifications.ChannelRegistry
import io.github.eslamasabry.oc.android.notifications.CodingAlert
import io.github.eslamasabry.oc.android.notifications.NotifyChannel
import io.github.eslamasabry.oc.android.notifications.NotifyWords
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.engine.integrations.quota.*
import io.github.eslamasabry.oc.engine.integrations.usage.*
import io.github.eslamasabry.oc.engine.profiles.LoadedProfile
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.feature.linux.host.LinuxHost
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.plans.presentation.PlanCollectorUi
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.quotaCollectorService
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/** Real collector readings and explicit consent, fed to Usage > Plans alongside daemon readings. */
@SingleIn(AppScope::class)
@Inject
class CollectorUsageSource(
    private val app: Application,
    private val stores: PreviewStores,
    private val linux: LinuxHost,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    private val appScope: AppCoroutineScope,
    private val alerts: QuotaAlertPublisher,
    private val http: HttpClientFactory,
) : UsageSource, UsageAlertBridge {
    private val random = SecureRandom()

    init {
        appScope.launch {
            while (true) {
                delay(5.minutes)
                if (stores.warm() is Outcome.Ok && alertsOn()) {
                    read(
                        UsageReadContext(
                            connections(),
                            clock.now(),
                            force = true,
                            background = true,
                        )
                    )
                }
            }
        }
    }

    private val lock = Mutex()
    private val snapshots = mutableMapOf<String, Pair<ProviderQuotaSnapshot, Instant>>()
    private val credentialSalt = ByteArray(32).also(random::nextBytes)
    private val credentialIdentities = mutableMapOf<ProfileId, String>()
    private val changedCredentials = mutableSetOf<ProfileId>()
    private val retired = java.util.concurrent.ConcurrentHashMap.newKeySet<ProfileId>()
    private val rules by lazy {
        CollectorRules(
            stores.prefs,
            stores.lanes::forProfile,
            { stores.profiles.profile(it) != null },
            ::newToken,
        )
    }

    private fun all(): List<LoadedProfile> =
        stores.profiles.profiles.value.filter { it.profile.backend == BackendKind.OpenCode2 }

    private fun local(p: LoadedProfile): Boolean =
        InAppQuotaCollector.looksLikeInAppServer(quotaProfile(p))

    private fun quotaProfile(p: LoadedProfile) =
        QuotaServerProfile(p.id, p.profile.baseUrl, p.profile.backend == BackendKind.OpenCode2)

    private fun source(p: LoadedProfile): String =
        DartJson.sha256Hex(DartJson.encode(listOf(p.profile.baseUrl, p.profile.username)))

    private fun current(p: LoadedProfile): Boolean =
        p.id !in retired && stores.profiles.profile(p.id) == p

    private fun enabled(p: LoadedProfile): Boolean =
        stores.prefs.bool(InAppQuotaCollector.enabledKey(p.id)) == true

    private fun key(id: ProfileId, provider: QuotaProvider): String = "${id.raw}|${provider.wire}"

    private fun rule(p: LoadedProfile, provider: QuotaProvider): CollectorRule? =
        rules.rule(p.id, provider)?.takeIf { it.source == source(p) }

    private fun applies(p: LoadedProfile, provider: QuotaProvider): Boolean =
        quotaCollectionAvailable(provider) &&
            if (local(p)) enabled(p) else rule(p, provider) != null

    fun alertsOn(): Boolean = stores.prefs.bool(ALERTS) == true

    suspend fun setAlertsOn(enabled: Boolean): Outcome<Unit> =
        stores.prefs.commit(stores.lanes.app) { putBool(ALERTS, enabled) }

    fun collectorOptions(): List<PlanCollectorUi> =
        all().map { p ->
            PlanCollectorUi(
                p.id,
                p.profile.name,
                local(p),
                if (local(p)) enabled(p) else QuotaProvider.entries.any { rule(p, it) != null },
            )
        }

    fun connections(): List<UsageConnection> =
        all()
            .filter { p ->
                local(p) && enabled(p) || QuotaProvider.entries.any { rule(p, it) != null }
            }
            .map { p ->
                UsageConnection(
                    p.id.raw,
                    p.profile.name,
                    if (local(p)) UsageConnectionKind.InAppUbuntu
                    else UsageConnectionKind.OpenCodeServer,
                )
            }

    override suspend fun read(context: UsageReadContext): Outcome<UsageSourceResult> =
        lock.withLock {
            val readings = mutableListOf<UsageReading>()
            val failures = mutableListOf<UsageFailure>()
            val unreached = mutableSetOf<String>()
            for (p in all()) {
                if (p.id.raw !in context.connections.map { it.id }) continue
                for (provider in QuotaProvider.entries) {
                    if (p.id.raw in unreached || !applies(p, provider) || !current(p)) continue
                    val old = snapshots[key(p.id, provider)]
                    val read =
                        if (
                            !context.force &&
                                old != null &&
                                context.now - old.second < 4.minutes &&
                                !old.first.isStale(context.now)
                        )
                            QuotaRead.Read(old.first)
                        else readSnapshot(p, provider)
                    if (!current(p) || !applies(p, provider)) continue
                    when (read) {
                        is QuotaRead.Failed ->
                            when (read.failure.kind) {
                                QuotaFailureKind.CollectorAuth ->
                                    readings +=
                                        bare(
                                            p,
                                            provider,
                                            UsageReadingStatus.AuthRequired,
                                            context.now,
                                        )
                                QuotaFailureKind.Unavailable ->
                                    if (local(p))
                                        failures +=
                                            UsageFailure(
                                                p.id.raw,
                                                provider.wire,
                                                UsageProblem.Broken,
                                            )
                                    else unreached += p.id.raw
                                else ->
                                    failures +=
                                        UsageFailure(p.id.raw, provider.wire, UsageProblem.Broken)
                            }
                        is QuotaRead.Read -> {
                            val s = read.snapshot
                            snapshots[key(p.id, provider)] = s to context.now
                            maybeAlert(p, s)
                            when {
                                s.status == ProviderQuotaStatus.Ok && s.canShowWindows -> {
                                    // Consent is bound to the account as well as the saved source.
                                    val consent = rule(p, provider)
                                    if (!local(p) && consent?.account != s.account.ref) {
                                        readings +=
                                            bare(
                                                p,
                                                provider,
                                                UsageReadingStatus.AuthRequired,
                                                context.now,
                                            )
                                        continue
                                    }
                                    val windows =
                                        s.windows.mapNotNull { w ->
                                            w.usedPercent?.let {
                                                UsageWindow(w.id, it, w.durationSeconds, w.resetsAt)
                                            }
                                        }
                                    readings +=
                                        UsageReading(
                                            provider.wire,
                                            name(provider),
                                            p.id.raw,
                                            if (windows.isEmpty()) UsageReadingStatus.NoLimits
                                            else UsageReadingStatus.Ok,
                                            s.fetchedAt,
                                            windows,
                                            s.account.ref,
                                            s.account.plan,
                                            SignInMethod.Plan,
                                        )
                                }
                                s.status == ProviderQuotaStatus.Unconfigured ->
                                    if (context.lastReading(p.id.raw, provider.wire) != null)
                                        readings +=
                                            bare(
                                                p,
                                                provider,
                                                UsageReadingStatus.NotSignedIn,
                                                context.now,
                                            )
                                s.status == ProviderQuotaStatus.AuthRequired ->
                                    readings +=
                                        bare(
                                            p,
                                            provider,
                                            UsageReadingStatus.AuthRequired,
                                            context.now,
                                        )
                                s.status == ProviderQuotaStatus.RateLimited ->
                                    failures +=
                                        UsageFailure(
                                            p.id.raw,
                                            provider.wire,
                                            UsageProblem.RateLimited,
                                        )
                                else ->
                                    failures +=
                                        UsageFailure(p.id.raw, provider.wire, UsageProblem.Broken)
                            }
                        }
                    }
                }
            }
            Outcome.Ok(
                UsageSourceResult(readings = readings, failures = failures, unreached = unreached)
            )
        }

    override fun legacyRules(): List<LegacyQuotaRule> =
        all().flatMap { p ->
            QuotaProvider.entries.mapNotNull { provider ->
                rule(p, provider)?.let {
                    LegacyQuotaRule(p.id.raw, provider.wire, it.account, it.threshold)
                }
            }
        }

    override suspend fun reconcile(wanted: List<QuotaWatch>, alertsOn: Boolean): Unit =
        lock.withLock {
            for (watch in wanted) {
                val p = stores.profiles.profile(ProfileId(watch.connectionId)) ?: continue
                val provider =
                    QuotaProvider.entries.firstOrNull { it.wire == watch.providerId } ?: continue
                val prior = rule(p, provider)
                if (prior == null) {
                    // A remote collector is NEVER automatically enrolled by an alert switch.
                    if (!alertsOn || !local(p) || !enabled(p)) continue
                    val s = snapshots[key(p.id, provider)]?.first ?: continue
                    rules.enroll(
                        p.id,
                        source(p),
                        s,
                        clock.now(),
                        notifications = this.alertsOn(),
                        threshold = watch.threshold,
                    )
                } else if (prior.threshold != watch.threshold || prior.notifications != alertsOn) {
                    rules.save(
                        p.id,
                        provider,
                        prior.copy(threshold = watch.threshold, notifications = alertsOn),
                    )
                }
            }
        }

    suspend fun consentRemote(id: ProfileId, enabled: Boolean): Outcome<Unit> = lock.withLock {
        val p = stores.profiles.profile(id) ?: return@withLock stale()
        if (local(p)) return@withLock Outcome.Failed(Failure.UnreadableReply())
        if (!enabled) {
            for (provider in QuotaProvider.entries) {
                val result = rules.save(id, provider, null)
                if (result is Outcome.Failed) return@withLock result
                snapshots.remove(key(id, provider))
            }
            return@withLock Outcome.Ok(Unit)
        }
        changedCredentials.remove(id)
        credentialIdentities.remove(id)
        var enrolled = false
        for (provider in QuotaProvider.entries.filter(::quotaCollectionAvailable)) {
            val read = readSnapshot(p, provider)
            if (!current(p)) return@withLock stale()
            if (read !is QuotaRead.Read || !read.snapshot.canShowWindows) continue
            val result =
                rules.enroll(
                    id,
                    source(p),
                    read.snapshot,
                    clock.now(),
                    notifications = alertsOn(),
                    threshold = 80.0,
                )
            if (result is Outcome.Failed) return@withLock result
            snapshots[key(id, provider)] = read.snapshot to clock.now()
            enrolled = true
        }
        if (enrolled) Outcome.Ok(Unit) else Outcome.Failed(Failure.UnreadableReply())
    }

    suspend fun setUpInApp(id: ProfileId): Outcome<Unit> = lock.withLock {
        val p = stores.profiles.profile(id) ?: return@withLock stale()
        if (!current(p)) return@withLock stale()
        if (!local(p)) return@withLock Outcome.Failed(Failure.UnreadableReply())
        if (all().any { it.id != p.id && local(it) && enabled(it) })
            return@withLock Outcome.Failed(Failure.Conflict())
        when (val done = attempt({ Failure.Device() }) { collector(p).setUp(quotaProfile(p)) }) {
            is Outcome.Failed -> done
            is Outcome.Ok ->
                if (!current(p)) stale()
                else if (done.value == null) Outcome.Ok(Unit) else Outcome.Failed(Failure.Device())
        }
    }

    suspend fun turnOff(id: ProfileId): Outcome<Unit> = lock.withLock {
        val p = stores.profiles.profile(id) ?: return@withLock stale()
        if (!local(p)) return@withLock Outcome.Failed(Failure.UnreadableReply())
        when (val done = attempt({ Failure.Device() }) { collector(p).turnOff(id) }) {
            is Outcome.Failed -> done
            is Outcome.Ok -> {
                if (done.value != null) return@withLock Outcome.Failed(Failure.Device())
                for (provider in QuotaProvider.entries) {
                    rules.save(id, provider, null)
                    snapshots.remove(key(id, provider))
                }
                Outcome.Ok(Unit)
            }
        }
    }

    fun stopAdmission(id: ProfileId) {
        retired += id
    }

    fun reopen(id: ProfileId) {
        retired -= id
    }

    suspend fun drain(id: ProfileId): Outcome<Unit> = lock.withLock {
        val p = stores.profiles.profile(id)
        if (p != null && local(p) && enabled(p)) {
            when (
                val stopped =
                    attempt({ Failure.Device() }) { collector(p, retiring = true).turnOff(id) }
            ) {
                is Outcome.Failed -> return@withLock stopped
                is Outcome.Ok ->
                    if (stopped.value != null) return@withLock Outcome.Failed(Failure.Device())
            }
        }
        snapshots.keys.removeAll { it.startsWith(id.raw + "|") }
        for (provider in QuotaProvider.entries) alerts.dismiss("quota:${id.raw}:${provider.wire}")
        Outcome.Ok(Unit)
    }

    private suspend fun maybeAlert(p: LoadedProfile, snapshot: ProviderQuotaSnapshot) {
        if (
            !alertsOn() ||
                !current(p) ||
                !snapshot.canShowWindows ||
                snapshot.freshness != QuotaFreshness.Fresh ||
                snapshot.fetchedAt > clock.now() ||
                snapshot.isStale(clock.now())
        )
            return
        val rule = rule(p, snapshot.provider) ?: return
        if (!rule.notifications || rule.account != snapshot.account.ref) return
        if (rule.wifiOnly) {
            val manager = app.getSystemService(ConnectivityManager::class.java)
            if (
                manager
                    .getNetworkCapabilities(manager.activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true
            )
                return
        }
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = rule.quietStart
        val end = rule.quietEnd
        if (
            start != null &&
                end != null &&
                (start == end ||
                    if (start < end) minute >= start && minute < end
                    else minute >= start || minute < end)
        )
            return
        val window =
            snapshot.windows.firstOrNull {
                (it.usedPercent ?: -1.0) >= rule.threshold &&
                    (it.resetsAt == null || it.resetsAt!! > clock.now())
            } ?: return
        val key =
            DartJson.sha256Hex(
                DartJson.encode(
                    listOf(
                        snapshot.provider.wire,
                        rule.account,
                        window.id,
                        window.resetsAt?.toEpochMilliseconds(),
                    )
                )
            )
        val marker = DartJson.sha256Hex(rule.threshold.toString())
        if (rule.alerted[key] == marker) return
        if (!current(p)) return
        val alert =
            CodingAlert(
                AlertKind.Quota,
                "quota",
                "quota:${p.id.raw}:${snapshot.provider.wire}",
                profileId = p.id.raw,
                monitorToken = rule.token,
            )
        if (alerts.show(alert) == AlertShown.Shown) {
            val marked =
                (rule.alerted + (key to marker)).entries.toList().takeLast(64).associate {
                    it.toPair()
                }
            rules.save(p.id, snapshot.provider, rule.copy(alerted = marked))
        }
    }

    private suspend fun readSnapshot(p: LoadedProfile, provider: QuotaProvider): QuotaRead {
        if (!current(p)) return QuotaRead.Failed(ProviderQuotaFailure(QuotaFailureKind.Unavailable))
        val password =
            when (val value = stores.profiles.secret(p.id)) {
                is Outcome.Ok -> value.value.orEmpty()
                is Outcome.Failed -> ""
            }
        if (!current(p)) return QuotaRead.Failed(ProviderQuotaFailure(QuotaFailureKind.Unavailable))
        if (!local(p)) {
            val mac =
                Mac.getInstance("HmacSHA256").apply {
                    init(SecretKeySpec(credentialSalt, "HmacSHA256"))
                }
            val identity =
                mac.doFinal(
                        DartJson.encode(
                                listOf(
                                    p.profile.baseUrl,
                                    p.profile.username,
                                    password,
                                    p.reentry.password,
                                )
                            )
                            .encodeToByteArray()
                    )
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
            val prior = credentialIdentities.put(p.id, identity)
            if (prior != null && prior != identity) changedCredentials += p.id
            if (p.id in changedCredentials)
                return QuotaRead.Failed(ProviderQuotaFailure(QuotaFailureKind.CollectorAuth))
        }
        val transport =
            transportFor(p)
                ?: return QuotaRead.Failed(ProviderQuotaFailure(QuotaFailureKind.Unsupported))
        val gateway =
            HttpProviderQuotaGateway(
                p.profile.baseUrl,
                p.profile.username,
                password,
                requiresPasswordReentry = p.reentry.password,
                transport = transport,
                provider = provider,
                inAppToken =
                    if (local(p))
                        ({
                            if (!current(p) || !enabled(p)) null
                            else {
                                collector(p).ensureRunning(quotaProfile(p))
                                InAppQuotaCollector.readToken(stores.secrets, p.id)
                            }
                        })
                    else null,
            )
        return try {
            gateway.readSnapshot()
        } finally {
            gateway.close()
        }
    }

    private fun transportFor(p: LoadedProfile): CollectorQuotaTransport? {
        val profile = p.toServerProfile() ?: return null
        val origin =
            if (local(p)) ServerOrigin("http", "127.0.0.1", IN_APP_QUOTA_PORT) else profile.origin
        val cleartext =
            AddressRules.isLoopback(origin.host) ||
                (!local(p) && profile.trust.cleartextConsentAtMs != null)
        if (origin.scheme != "https" && !cleartext) return null
        val pin =
            if (local(p)) null
            else
                profile.trust.pinnedLeafSha256?.let { value ->
                    val hex = value.replace(":", "")
                    if (hex.length != 64 || hex.any { it.digitToIntOrNull(16) == null }) return null
                    ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
                }
        if (origin.scheme != "https" && pin != null) return null
        return CollectorQuotaTransport(
            http,
            TransportSpec(
                origin,
                AddressRules.classify(origin.host),
                cleartext,
                pin,
                connectTimeoutMs = 10_000,
                readTimeoutMs = 12_000,
            ),
        )
    }

    private fun collector(p: LoadedProfile, retiring: Boolean = false): InAppQuotaCollector =
        InAppQuotaCollector(
            stores.prefs,
            stores.lanes.forProfile(p.id),
            stores.secrets,
            linux =
                object : QuotaLinuxHost {
                    override suspend fun status(): QuotaLinuxHost.Status {
                        if (!retiring && !current(p)) throw CollectorUnavailable()
                        val ready = linux.presence() == LinuxPresence.Ready
                        if (!retiring && !current(p)) throw CollectorUnavailable()
                        return QuotaLinuxHost.Status(
                            ready,
                            ready,
                            if (linux.serviceRunning(InAppQuotaCollector.SERVICE_NAME))
                                listOf(InAppQuotaCollector.SERVICE_NAME)
                            else emptyList(),
                        )
                    }

                    override suspend fun run(
                        script: String,
                        timeoutMs: Long,
                    ): QuotaLinuxHost.RunResult {
                        if (!retiring && !current(p)) throw CollectorUnavailable()
                        return when (val r = linux.run(script, (timeoutMs + 999) / 1000)) {
                            is Outcome.Ok ->
                                QuotaLinuxHost.RunResult(r.value.exitCode, r.value.output)
                            is Outcome.Failed -> throw CollectorUnavailable()
                        }
                    }

                    override suspend fun startService(name: String, script: String, port: Int?) {
                        if (!retiring && !current(p)) throw CollectorUnavailable()
                        if (
                            linux.startService(
                                name,
                                script,
                                getString(Res.string.quotaCollectorService),
                            ) is Outcome.Failed
                        )
                            throw CollectorUnavailable()
                    }

                    override suspend fun stopService(name: String) {
                        if (!retiring && !current(p)) throw CollectorUnavailable()
                        if (linux.stopService(name) is Outcome.Failed) throw CollectorUnavailable()
                    }

                    override suspend fun serviceLog(name: String): String {
                        if (!retiring && !current(p)) throw CollectorUnavailable()
                        return linux.serviceLog(name, 4096)
                    }
                },
            rootfs =
                object : QuotaRootfs {
                    private fun target(path: String): File {
                        if (!retiring && !current(p)) throw CollectorUnavailable()
                        require(path.startsWith(InAppQuotaCollector.DIR + "/"))
                        val guestRoot = BuiltinLinux.get(app).rootfs
                        val root = guestRoot.canonicalFile
                        require(root.path.startsWith(app.filesDir.canonicalPath + File.separator))
                        val candidate = File(root, path.removePrefix("/"))
                        // Every existing parent must resolve under this root, including the private
                        // collector directory.
                        var parent = candidate.parentFile
                        while (parent != null && parent != root) {
                            require(parent.canonicalPath.startsWith(root.path + File.separator))
                            parent = parent.parentFile
                        }
                        require(candidate.canonicalPath == candidate.absolutePath)
                        return candidate
                    }

                    override suspend fun read(path: String): ByteArray? =
                        withContext(dispatchers.io) {
                            target(path).let { if (it.isFile) it.readBytes() else null }
                        }

                    override suspend fun write(path: String, bytes: ByteArray) {
                        withContext(dispatchers.io) {
                            val file = target(path)
                            check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
                            file.writeBytes(bytes)
                            android.system.Os.chmod(file.path, 384)
                        }
                    }
                },
            randomBytes = { count -> ByteArray(count).also(random::nextBytes) },
            loadCollector = { asset("collector.mjs", COLLECTOR_HASH) },
            loadKeysHelper = { asset("keys.mjs", KEYS_HASH) },
            probe = probe@{ token ->
                    val transport = transportFor(p) ?: return@probe false
                    val gateway =
                        HttpProviderQuotaGateway(
                            p.profile.baseUrl,
                            "",
                            "",
                            transport = transport,
                            inAppToken = { token },
                        )
                    try {
                        gateway.readSnapshot() is QuotaRead.Read
                    } finally {
                        gateway.close()
                    }
                },
            supported = local(p),
        )

    private suspend fun asset(name: String, hash: String): String =
        withContext(dispatchers.io) {
            val bytes = app.assets.open("quota/$name").use { it.readBytes() }
            check(
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                    "%02x".format(it.toInt() and 255)
                } == hash
            )
            bytes.decodeToString(throwOnInvalidSequence = true)
        }

    private fun newToken(): String =
        ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun bare(
        p: LoadedProfile,
        provider: QuotaProvider,
        status: UsageReadingStatus,
        now: Instant,
    ) =
        UsageReading(
            provider.wire,
            name(provider),
            p.id.raw,
            status,
            now,
            method = SignInMethod.Plan,
        )

    private fun name(provider: QuotaProvider): String =
        when (provider) {
            QuotaProvider.Codex -> "Codex"
            QuotaProvider.Claude -> "Claude"
            QuotaProvider.Minimax -> "MiniMax"
            QuotaProvider.Glm -> "GLM"
        }

    private fun stale(): Outcome<Unit> = Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

    private companion object {
        const val ALERTS = "oc.notify.quotaAlerts"
        const val COLLECTOR_HASH =
            "35b951535ef614815c7be0f4d3de2e46dddb6b47d11c3690804d9b22323248b7"
        const val KEYS_HASH = "3485e45f7ec729f472aebf555c766caed3b0dc725482fc6d76e5c5e16a14a4e1"
    }
}

/**
 * Dedicated GET-only client: no shared cookies, redirects, automatic retries or query-bearing
 * diagnostics.
 */
/** The quota shade needs only Android notification services and the current app language. */
@SingleIn(AppScope::class)
@Inject
class QuotaAlertPublisher(application: Application, preferences: AppPreferences) {
    private val words = NotifyWords(application) { preferences.localeTag.value }
    private val channels =
        ChannelRegistry(application.getSystemService(NotificationManager::class.java), words)
    private val shade = AndroidAlertShade(application, words, channels)

    fun show(alert: CodingAlert): AlertShown =
        shade.show(alert, AlertRoute.Post(NotifyChannel.CodingStatus, null))

    fun dismiss(key: String): Unit = shade.dismiss(key)
}

private class CollectorUnavailable : Exception()

/** One client for the collector's exact approved origin, using the existing core trust gate. */
private class CollectorQuotaTransport(factory: HttpClientFactory, spec: TransportSpec) :
    QuotaTransport {
    private val client: NetClient = factory.create(spec)
    private var closed = false

    override suspend fun fetch(request: QuotaRequest): QuotaResponse {
        require(
            !closed && request.method == "GET" && request.body == null && !request.followRedirects
        )
        val target = URI(request.url)
        require(target.rawUserInfo == null && target.rawQuery == null && target.rawFragment == null)
        require(QuotaProvider.entries.any { target.rawPath == quotaPathFor(it) })
        // Keep the raw status: the quota contract distinguishes collector-auth and availability.
        // The response never outlives execute, and at most one byte beyond the parser cap is
        // buffered.
        return client.http
            .prepareGet(request.url) {
                request.headers.forEach { (name, value) -> headers.append(name, value) }
                timeout {
                    connectTimeoutMillis = request.connectTimeoutMs.coerceAtMost(12_000)
                    socketTimeoutMillis = request.receiveTimeoutMs.coerceAtMost(12_000)
                    requestTimeoutMillis = 12_000
                }
            }
            .execute { response ->
                val status = response.status.value
                if (status != 200) {
                    QuotaResponse(status, flowOf(), isRedirect = status in 300..399)
                } else {
                    val channel = response.bodyAsChannel()
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    val bound = HttpProviderQuotaGateway.MAX_RESPONSE_BYTES + 1
                    while (out.size() < bound) {
                        val count =
                            channel.readAvailable(buffer, 0, minOf(buffer.size, bound - out.size()))
                        if (count < 0) break
                        if (count > 0) out.write(buffer, 0, count)
                    }
                    QuotaResponse(status, flowOf(out.toByteArray()))
                }
            }
    }

    override fun close(force: Boolean) {
        if (closed) return
        closed = true
        client.close()
    }
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class QuotaCollectorProfileCloser(private val source: CollectorUsageSource) : ProfileCloser {
    override val owner: String = ":engine:integrations-quota"

    override fun stopAdmission(profile: ProfileId) = source.stopAdmission(profile)

    override fun reopen(profile: ProfileId) = source.reopen(profile)

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = source.drain(profile)
}
