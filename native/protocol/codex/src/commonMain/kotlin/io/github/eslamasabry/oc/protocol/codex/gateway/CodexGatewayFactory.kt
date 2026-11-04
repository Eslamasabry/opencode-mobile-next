package io.github.eslamasabry.oc.protocol.codex.gateway

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ProbeResult
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.protocol.codex.CodexCapabilities
import io.github.eslamasabry.oc.protocol.codex.CodexEndpoint
import io.github.eslamasabry.oc.protocol.codex.CodexProfileRules
import io.github.eslamasabry.oc.protocol.codex.codexAttempt
import io.github.eslamasabry.oc.protocol.codex.probe.CodexProbeResult
import io.github.eslamasabry.oc.protocol.codex.probe.probeCodexConnection
import io.github.eslamasabry.oc.protocol.codex.transport.CodexSocketFactory
import io.github.eslamasabry.oc.protocol.codex.transport.CodexTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlin.time.Clock

/**
 * Backend plug-in for Codex app-server profiles (plan 03 S3, plan 04 §11.1). The socket comes from the app
 * ([CodexSocketFactory], OkHttp). The connection secret is the profile's agent socket token
 * (`GatewaySpec.credentials.secret`); the folder is `GatewaySpec.location.directory` (the profile's `codexDirectory`).
 */
public class CodexGatewayFactory(
    private val sockets: CodexSocketFactory,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val requestTimeoutMs: Long = 15_000,
) : GatewayFactory {
    override val backend: BackendKind = BackendKind.Codex

    /**
     * Validate → authenticated `initialize` → close. A probe without a trusted credential never dials: Codex upgrades
     * need the token (B-SEC-124).
     */
    override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> {
        val endpoint = CodexEndpoint.parse(target.origin.toString())
            ?: return Outcome.Failed(Failure.Refused(RefusalReason.InvalidAddress))
        val secret = target.credentials?.secret?.takeIf(CodexProfileRules::tokenAcceptable)
            ?: return Outcome.Failed(Failure.SignIn(SignInWhy.TokenMissing))
        return coroutineScope {
            val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
            val transport = CodexTransport(endpoint, secret, sockets, scope, dispatchers.serial("codex-probe"), requestTimeoutMs)
            try {
                codexAttempt {
                    transport.connect()
                    ProbeResult(BackendKind.Codex, serverVersion = null, capabilities = CodexCapabilities.Base)
                }
            } finally {
                transport.close()
                scope.coroutineContext[Job]?.cancel()
            }
        }
    }

    /**
     * Opens the socket and runs the handshake (so a refused token fails here), then starts listening. The pair's
     * operations surface is all defaults (Failure.Unavailable): Codex offers no Project tab tools (B-AGENT-068).
     */
    override suspend fun connect(spec: GatewaySpec): Outcome<GatewayPair> {
        val endpoint = CodexEndpoint.parse(spec.profile.origin.toString())
            ?: return Outcome.Failed(Failure.Refused(RefusalReason.InvalidAddress))
        val secret = spec.credentials.secret?.takeIf(CodexProfileRules::tokenAcceptable)
            ?: return Outcome.Failed(Failure.SignIn(SignInWhy.TokenMissing))
        val gateway = open(endpoint, secret, spec.location.directory, spec.scope, "codex:${spec.profile.id.raw}")
        val connected = codexAttempt { gateway.transport.connect() }
        if (connected is Outcome.Failed) {
            gateway.close()
            return connected
        }
        gateway.startListening()
        return Outcome.Ok(GatewayPair(gateway, object : ServerOperationsGateway {}))
    }

    /** One gateway with no I/O until first use. */
    public fun open(endpoint: CodexEndpoint, secret: String, directory: String?, scope: CoroutineScope, lane: String = "codex"): CodexGateway {
        val serial = dispatchers.serial(lane)
        val transport = CodexTransport(endpoint, secret, sockets, scope, serial, requestTimeoutMs)
        return CodexGateway(transport, scope, clock, directory)
    }

    /** The pairing check (Dart `probeCodexConnection`): nothing is saved; everything opened is closed. */
    public suspend fun checkConnection(baseUrl: String, secret: String, directory: String): CodexProbeResult =
        coroutineScope {
            val scope = ConnectionCoroutineScope(SupervisorJob(coroutineContext[Job]) + dispatchers.default)
            try {
                probeCodexConnection(baseUrl, secret, directory) { url, token, dir ->
                    open(CodexEndpoint.parse(url) ?: error("validated"), token, dir, scope, "codex-check")
                }
            } finally {
                scope.coroutineContext[Job]?.cancel()
            }
        }
}
