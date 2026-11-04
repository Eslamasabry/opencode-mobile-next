package io.github.eslamasabry.oc.protocol.codex

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.BackendKind
import kotlin.time.Clock
import kotlinx.coroutines.*

public class CodexGatewayFactory(
    private val sockets: CodexSocketFactory,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val requestTimeoutMs: Long = 15_000,
) : GatewayFactory {
    override val backend: BackendKind = BackendKind.Codex

    override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> = codexWireAttempt {
        coroutineScope {
            val transport =
                CodexTransport(
                    target.origin.toString(),
                    target.credentials?.secret.orEmpty(),
                    sockets,
                    this,
                    dispatchers.serial("codex-probe"),
                    requestTimeoutMs,
                )
            try {
                transport.connect()
                ProbeResult(
                    backend,
                    transport.serverVersion,
                    CodexCapabilities.Base.copy(agentAccount = transport.accountSupported),
                )
            } finally {
                transport.close()
            }
        }
    }

    override suspend fun connect(spec: GatewaySpec): Outcome<GatewayPair> {
        val lane = dispatchers.serial("codex:${spec.profile.id.raw}")
        val transport =
            CodexTransport(
                spec.profile.origin.toString(),
                spec.credentials.secret.orEmpty(),
                sockets,
                spec.scope,
                lane,
                requestTimeoutMs,
            )
        val gateway =
            CodexGateway(transport, spec.scope, lane, clock, spec.location, spec.reconnectAllowed)
        return when (val result = gateway.start()) {
            is Outcome.Ok -> Outcome.Ok(GatewayPair(gateway, object : ServerOperationsGateway {}))
            is Outcome.Failed -> {
                gateway.close()
                result
            }
        }
    }

    /** Pairing probe: validates all input before opening a temporary, scoped connection. */
    public suspend fun checkConnection(
        address: String,
        token: String,
        directory: String,
    ): Outcome<Unit> {
        val endpoint = address.trim()
        val dir = directory.trim()
        if (
            !validCodexEndpoint(endpoint) ||
                !dir.startsWith('/') ||
                dir.length > 4096 ||
                dir.any { it.code < 32 || it.code == 127 } ||
                token.isEmpty() ||
                token.length > 8192 ||
                token.any { it.code < 32 || it.code == 127 }
        ) {
            return Outcome.Failed(Failure.Refused(RefusalReason.InvalidAddress))
        }
        return coroutineScope {
            val lane = dispatchers.serial("codex-check")
            val transport = CodexTransport(endpoint, token, sockets, this, lane, requestTimeoutMs)
            val gateway =
                CodexGateway(
                    transport,
                    this,
                    lane,
                    clock,
                    io.github.eslamasabry.oc.core.model.ServerLocation(dir),
                    kotlinx.coroutines.flow.MutableStateFlow(false),
                )
            try {
                when (val health = gateway.health()) {
                    is Outcome.Failed -> health
                    is Outcome.Ok ->
                        if (!health.value.healthy) Outcome.Failed(Failure.ComputerDidNotAnswer())
                        else
                            when (val page = gateway.sessionPage(null, 1)) {
                                is Outcome.Failed -> page
                                is Outcome.Ok -> Outcome.Ok(Unit)
                            }
                }
            } finally {
                gateway.close()
            }
        }
    }

    /**
     * Checks health plus one scoped thread page, always closing the temporary gateway. Saves no
     * profile.
     */
    public suspend fun checkConnection(spec: GatewaySpec): Outcome<Unit> {
        if (
            !validCodexEndpoint(spec.profile.origin.toString()) ||
                spec.location.directory?.startsWith('/') != true ||
                spec.credentials.secret.isNullOrEmpty()
        ) {
            return Outcome.Failed(Failure.Refused(RefusalReason.InvalidAddress))
        }
        val opened = connect(spec)
        if (opened is Outcome.Failed) return opened
        val gateway = (opened as Outcome.Ok).value.live
        return try {
            when (val health = gateway.health()) {
                is Outcome.Failed -> health
                is Outcome.Ok ->
                    if (!health.value.healthy) Outcome.Failed(Failure.ComputerDidNotAnswer())
                    else
                        when (val page = gateway.sessionPage(null, 1)) {
                            is Outcome.Failed -> page
                            is Outcome.Ok -> Outcome.Ok(Unit)
                        }
            }
        } finally {
            gateway.close()
        }
    }
}
