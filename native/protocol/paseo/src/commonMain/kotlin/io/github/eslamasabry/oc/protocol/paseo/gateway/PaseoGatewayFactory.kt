package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ProbeResult
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.protocol.paseo.PaseoCapabilities
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoProfileRules
import io.github.eslamasabry.oc.protocol.paseo.PaseoUrlProblem
import io.github.eslamasabry.oc.protocol.paseo.paseoAttempt
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoSocketFactory
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransport
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock

/**
 * Backend plug-in for Paseo (plan 03 S3, plan 04 §8): `probe` reads health only; `open` builds the transport and the
 * gateway pair with no I/O until first use. The socket factory comes from `:core:net` (P-N1) — see
 * `PaseoSocketFactory`. The phone helper profile (plan 04 §8 "Phone agents helper") passes
 * [defaultProviderModes] `claude → default` and a 15 s request timeout.
 */
public class PaseoGatewayFactory(
    private val sockets: PaseoSocketFactory,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val requestTimeoutMs: Long = 20_000,
    private val defaultProviderModes: Map<String, String> = emptyMap(),
) : GatewayFactory {
    override val kind: BackendKind = BackendKind.Paseo

    /** Validate → hello. Never sends the password to an address the rules refuse (B-SEC-124, B-PROTO-070). */
    override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> {
        val url = target.origin.paseoUrl()
        if (PaseoProfileRules.validateUrl(url) != null) {
            return Outcome.Failed(Failure.Refused(io.github.eslamasabry.oc.core.common.RefusalReason.InvalidAddress))
        }
        return paseoAttempt {
            kotlinx.coroutines.coroutineScope { probeHello(url, target) }
        }
    }

    private suspend fun CoroutineScope.probeHello(url: String, target: ProbeTarget): ProbeResult {
            val transport = PaseoTransport(
                endpoint = url,
                socketFactory = sockets,
                scope = this,
                lane = dispatchers.serial("paseo-probe"),
                decodeDispatcher = dispatchers.default,
                passwordSource = { target.credentials.password().getOrNull() },
            )
            try {
                transport.connect()
                return ProbeResult(BackendKind.Paseo, transport.serverVersion, PaseoCapabilities.Base)
            } finally {
                transport.close()
            }
    }

    override fun open(spec: GatewaySpec, scope: ConnectionCoroutineScope): GatewayPair {
        val lane = dispatchers.serial("paseo:${spec.profile.id.raw}")
        val transport = PaseoTransport(
            endpoint = spec.profile.origin.paseoUrl(),
            socketFactory = sockets,
            scope = scope,
            lane = lane,
            decodeDispatcher = dispatchers.default,
            requestTimeoutMs = requestTimeoutMs,
            passwordSource = { spec.credentials.password().getOrNull() },
        )
        val gateway = PaseoGateway(
            transport = transport,
            scope = scope,
            lane = lane,
            mapDispatcher = dispatchers.default,
            clock = clock,
            directory = spec.location.directory,
            defaultProviderModes = defaultProviderModes,
        )
        return GatewayPair(gateway, object : ServerOperationsGateway {})
    }
}

/** A profile origin as a daemon address (`ws://host:port`). */
internal fun ServerOrigin.paseoUrl(): String = toString()

/** What the pairing editor's probe proved (Dart `probePaseoConnection` → CodexConnectionProbeResult). */
public sealed interface PaseoProbeResult {
    /** [runtimes] are the ready runtimes' display names; empty does not prove the folder exists. */
    public data class Verified(val version: String?, val runtimes: ImmutableList<String>) : PaseoProbeResult

    /** The typed address or password was refused before any socket opened. */
    public data class InvalidInput(val url: PaseoUrlProblem?, val directory: Boolean, val password: Boolean) : PaseoProbeResult

    public data class Refused(val failure: Failure) : PaseoProbeResult
}

/**
 * Checks a daemon and a project folder without starting an agent (B-PROTO-070): validation runs before [open], so
 * malformed input cannot open a socket; hello proves address, host allowlist and password; a one-item agent page
 * proves a scoped read; the runtime list says which agent CLIs the daemon drives. The gateway is always closed.
 */
public suspend fun probePaseoConnection(
    baseUrl: String,
    password: String,
    directory: String,
    open: (url: String, password: String, directory: String) -> PaseoGateway,
): PaseoProbeResult {
    val url = PaseoProfileRules.normalizeUrl(baseUrl)
    val dir = directory.trim()
    val urlProblem = PaseoProfileRules.validateUrl(url)
    val dirBad = dir.isEmpty() || dir.length > 4096 || dir.any { it.code <= 0x1f || it.code == 0x7f } || !dir.startsWith("/")
    val passwordBad = !PaseoProfileRules.passwordAcceptable(password)
    if (urlProblem != null || dirBad || passwordBad) return PaseoProbeResult.InvalidInput(urlProblem, dirBad, passwordBad)
    var gateway: PaseoGateway? = null
    try {
        val opened = open(url, password, dir)
        gateway = opened
        val health = opened.health()
        if (health is Outcome.Failed) return PaseoProbeResult.Refused(health.failure)
        val page = opened.sessionPage(null, 1)
        if (page is Outcome.Failed) return PaseoProbeResult.Refused(page.failure)
        return when (val providers = opened.providers()) {
            is Outcome.Failed -> PaseoProbeResult.Refused(providers.failure)
            is Outcome.Ok ->
                PaseoProbeResult.Verified(
                    // The daemon's own number, not a display line.
                    version = opened.transport.serverVersion ?: (health as Outcome.Ok).value.version,
                    runtimes = providers.value.providers.map { it.name }.toImmutableList(),
                )
        }
    } catch (e: PaseoException) {
        return PaseoProbeResult.Refused(e.toFailure())
    } finally {
        gateway?.close()
    }
}
