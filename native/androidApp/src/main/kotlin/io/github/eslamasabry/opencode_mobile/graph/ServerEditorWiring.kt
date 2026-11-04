@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.android.platform.AndroidTailscaleApp
import io.github.eslamasabry.oc.android.platform.CurrentActivity
import io.github.eslamasabry.oc.android.platform.TailscaleAppState
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.core.security.ExternalLinkOpener
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.protocol.codex.CodexProfileRules
import io.github.eslamasabry.oc.protocol.codex.probe.CodexProbeResult
import io.github.eslamasabry.oc.protocol.oc2.live.Oc2GatewayFactory
import io.github.eslamasabry.oc.protocol.paseo.PaseoProfileRules
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoProbeResult
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The editor uses the same trust-enforced clients and disposable socket probes as a real
 * connection.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ServerEditorPort>())
@Inject
class NativeServerEditor(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val oc2: Oc2GatewayFactory,
    private val paseoFactory: AppPaseoGatewayFactory,
    private val codexFactory: AppCodexGatewayFactory,
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
    application: Application,
    activities: ResumedActivities,
    private val links: ExternalLinkPolicy,
    private val opener: ExternalLinkOpener,
) : ServerEditorPort {
    private val lane = dispatchers.serial("server-editor")
    private val writes = Mutex()
    private val tailscale = AndroidTailscaleApp(application, CurrentActivity { activities.current })

    override fun tailscaleState(): EditorTailscaleState =
        when (tailscale.state()) {
            TailscaleAppState.Installed -> EditorTailscaleState.Installed
            TailscaleAppState.Missing -> EditorTailscaleState.Missing
            TailscaleAppState.Unavailable -> EditorTailscaleState.Unavailable
        }

    override fun openTailscale(): Outcome<Unit> =
        if (tailscale.open()) Unit.ok() else Failure.Device().failed()

    override fun installTailscale(): Outcome<Unit> =
        when (
            val checked =
                links.check(
                    "https://play.google.com/store/apps/details?id=com.tailscale.ipn",
                    LinkOrigin.AppAuthored,
                )
        ) {
            is LinkDecision.Open -> opener.open(checked.link)
            else -> Failure.Device().failed()
        }

    override suspend fun read(id: ProfileId): Outcome<ServerEditorRecord> {
        val profile = stores.profiles.profile(id) ?: return gone()
        val p = profile.profile
        return ServerEditorRecord(
                id,
                p.name,
                p.baseUrl,
                p.username,
                p.backend,
                p.codexDirectory,
                profile.hasSecret,
                profile.reentry.password || profile.reentry.agentToken,
                profile.tailscale,
                profile.trust.cleartext?.origin,
                profile.trust.pin?.leafSha256,
                profile.trust.pin?.origin,
                profile,
            )
            .ok()
    }

    private fun normalized(input: ServerEditorInput): Outcome<ServerOrigin> {
        val address =
            when (input.backend) {
                BackendKind.OpenCode2 ->
                    return AddressRules.normalize(input.address).let { answer ->
                        if (
                            answer is Outcome.Ok &&
                                input.tailscale &&
                                !AddressRules.isTailnet(answer.value.host)
                        )
                            invalid()
                        else answer
                    }
                BackendKind.Paseo -> PaseoProfileRules.normalizeUrl(input.address)
                BackendKind.Codex -> CodexProfileRules.normalizeUrl(input.address)
            }
        val invalidUrl =
            when (input.backend) {
                BackendKind.Paseo -> PaseoProfileRules.validateUrl(address) != null
                BackendKind.Codex -> CodexProfileRules.validateUrl(address) != null
                else -> false
            }
        if (invalidUrl || !CodexProfileRules.directoryAcceptable(input.directory.trim()))
            return invalid()
        return ProfileOrigins.serverOrigin(address)?.ok() ?: invalid()
    }

    private suspend fun secret(input: ServerEditorInput): Outcome<String> {
        if (!input.keepSecret) {
            val raw = input.secret?.reveal().orEmpty()
            val value = if (input.backend == BackendKind.OpenCode2) raw else raw.trim()
            redactor.registerSecret(raw)
            redactor.registerSecret(value)
            return value.ok()
        }
        val id = input.id ?: return invalid()
        val record = stores.profiles.profile(id) ?: return gone()
        if (input.editToken !== record) return stale()
        if (
            record.profile.backend != input.backend ||
                record.reentry.password ||
                record.reentry.agentToken
        )
            return Failure.SecretUnavailable().failed()
        return stores.profiles.secret(id, expected = record).map { it.orEmpty() }
    }

    private fun trust(input: ServerEditorInput, origin: ServerOrigin): Outcome<TrustInput> {
        if (input.backend != BackendKind.OpenCode2) return TrustInput().ok()
        val consent =
            input.cleartextOrigin?.let { AddressRules.normalize(it).getOrNull()?.toString() }
        if (AddressRules.needsCleartextConsent(origin) && consent != origin.toString())
            return Failure.Refused(RefusalReason.CleartextNotConfirmed).failed()
        val pin = input.certificatePin?.takeIf { input.certificateOrigin == origin.toString() }
        val checked = pin?.let { AddressRules.checkPin(origin, it) }
        if (checked is Outcome.Failed) return checked
        return TrustInput(
                (checked as? Outcome.Ok)?.value?.let {
                    CertificatePinRecord(origin.toString(), it)
                },
                consent,
            )
            .ok()
    }

    override suspend fun check(input: ServerEditorInput): Outcome<ServerEditorProbe> {
        val normalized = normalized(input)
        if (normalized is Outcome.Failed) return normalized
        val origin = (normalized as Outcome.Ok).value
        val trusted = trust(input, origin)
        if (trusted is Outcome.Failed) return trusted
        val secret = secret(input)
        if (secret is Outcome.Failed) return secret
        val value = (secret as Outcome.Ok).value
        return withTimeoutOrNull(25_000) {
            when (input.backend) {
                BackendKind.Codex ->
                    when (
                        val answer =
                            codexFactory.codex.checkConnection(
                                origin.toString(),
                                value.trim(),
                                input.directory.trim(),
                            )
                    ) {
                        is CodexProbeResult.Verified -> ServerEditorProbe(answer.version).ok()
                        is CodexProbeResult.NotVerified -> answer.failure.failed()
                        is CodexProbeResult.InvalidInput -> invalid()
                    }
                BackendKind.Paseo ->
                    when (
                        val answer =
                            paseoFactory.paseo.checkConnection(
                                origin.toString(),
                                value.trim(),
                                input.directory.trim(),
                            )
                    ) {
                        is PaseoProbeResult.Verified ->
                            ServerEditorProbe(answer.version, answer.runtimes).ok()
                        is PaseoProbeResult.Refused -> answer.failure.failed()
                        is PaseoProbeResult.InvalidInput -> invalid()
                    }
                BackendKind.OpenCode2 -> {
                    val choice = (trusted as Outcome.Ok).value
                    oc2.checkConnection(
                            origin,
                            ConnectionCredentials(
                                input.username.trim().ifEmpty { "opencode" },
                                value.ifEmpty { null },
                            ),
                            ProfileTrust(
                                choice.pin?.leafSha256,
                                if (choice.cleartextOrigin == origin.toString()) 0L else null,
                            ),
                        )
                        .map { ServerEditorProbe(it.version) }
                }
            }
        } ?: Failure.TimedOut().failed()
    }

    override suspend fun save(input: ServerEditorInput, version: String?): Outcome<ProfileId> =
        withContext(lane) {
            writes.withLock {
                val normalized = normalized(input)
                if (normalized is Outcome.Failed) return@withLock normalized
                val origin = (normalized as Outcome.Ok).value
                val trusted = trust(input, origin)
                if (trusted is Outcome.Failed) return@withLock trusted
                val snapshot =
                    input.id?.let { stores.profiles.profile(it) ?: return@withLock gone() }
                if (snapshot != null && input.editToken !== snapshot) return@withLock stale()
                val previous = snapshot?.profile
                val id = previous?.id ?: ProfileId(UUID.randomUUID().toString())
                val token = secret(input)
                if (token is Outcome.Failed) return@withLock token
                val value = (token as Outcome.Ok).value
                if (
                    input.backend == BackendKind.Codex &&
                        !CodexProfileRules.tokenAcceptable(value.trim())
                )
                    return@withLock invalid()
                if (
                    input.backend == BackendKind.Paseo &&
                        !PaseoProfileRules.passwordAcceptable(value.trim())
                )
                    return@withLock invalid()
                val next =
                    (previous ?: StoredProfile(id, "", origin.toString())).copy(
                        name = input.name.trim().ifEmpty { origin.host },
                        baseUrl = origin.toString(),
                        backend = input.backend,
                        username = input.username.trim().ifEmpty { "opencode" },
                        codexDirectory =
                            if (input.backend == BackendKind.OpenCode2) ""
                            else input.directory.trim(),
                        serverVersion =
                            version
                                ?: previous?.serverVersion?.takeIf {
                                    previous.baseUrl == origin.toString() &&
                                        previous.backend == input.backend
                                },
                    )
                // Stop and drain the old credential owner before storage can publish its
                // replacement.
                // A refused save stays disconnected; no stale-credential graph is reopened
                // automatically.
                if (previous != null) registry.close(id)
                when (
                    val stored =
                        stores.profiles.upsert(
                            next,
                            if (input.keepSecret) SecretEdit.Keep
                            else if (value.isEmpty()) SecretEdit.Clear else SecretEdit.Set(value),
                            (trusted as Outcome.Ok).value,
                            expected = snapshot,
                        )
                ) {
                    is Outcome.Failed -> stored
                    is Outcome.Ok -> {
                        val guide = stores.profiles.setTailscaleGuidance(id, input.tailscale)
                        if (guide is Outcome.Failed) guide else id.ok()
                    }
                }
            }
        }

    override suspend fun inspect(address: String): Outcome<ServerCertificate> {
        val parsed = AddressRules.normalize(address)
        if (parsed is Outcome.Failed) return parsed
        val origin = (parsed as Outcome.Ok).value
        if (origin.scheme != "https") return invalid()
        return withContext(dispatchers.io) {
            attempt({ Failure.Certificate(CertificateWhy.Untrusted) }) {
                // Inspection sends only a TLS handshake. This client never sends HTTP or
                // credentials and is never reused.
                val inspector =
                    object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

                        override fun checkClientTrusted(
                            chain: Array<X509Certificate>,
                            authType: String,
                        ) = Unit

                        override fun checkServerTrusted(
                            chain: Array<X509Certificate>,
                            authType: String,
                        ) = Unit
                    }
                val context =
                    SSLContext.getInstance("TLS").apply {
                        init(null, arrayOf<TrustManager>(inspector), null)
                    }
                Socket().use { raw ->
                    raw.connect(InetSocketAddress(origin.host, origin.port), 8_000)
                    (context.socketFactory.createSocket(raw, origin.host, origin.port, true)
                            as SSLSocket)
                        .use { tls ->
                            tls.soTimeout = 8_000
                            tls.startHandshake()
                            val leaf = tls.session.peerCertificates.first() as X509Certificate
                            val digest =
                                MessageDigest.getInstance("SHA-256")
                                    .digest(leaf.encoded)
                                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                            val dates =
                                SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
                                    timeZone = TimeZone.getTimeZone("UTC")
                                }
                            ServerCertificate(
                                origin.toString(),
                                digest,
                                dates.format(leaf.notBefore),
                                dates.format(leaf.notAfter),
                            )
                        }
                }
            }
        }
    }

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()

    private fun gone(): Outcome<Nothing> = Failure.Refused(RefusalReason.ProfileDeleted).failed()

    private fun invalid(): Outcome<Nothing> = Failure.Refused(RefusalReason.InvalidAddress).failed()
}
