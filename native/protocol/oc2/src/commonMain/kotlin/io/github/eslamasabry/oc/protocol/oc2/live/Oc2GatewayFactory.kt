package io.github.eslamasabry.oc.protocol.oc2.live

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.net.*
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.random.Random
import kotlin.time.Clock

/** Real OC2 transport, sharing the trust/no-resend enforcement of :core:net. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<GatewayFactory>())
public class Oc2GatewayFactory(
    private val clients: HttpClientFactory,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val redactor: Redactor,
    private val random: Random,
) : GatewayFactory {
    override val backend: BackendKind = BackendKind.OpenCode2

    override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> {
        // A probe has no cleartext-consent record. Never transmit a credential over HTTP here.
        if (target.origin.scheme != "https" && !AddressRules.isLoopback(target.origin.host))
            return Failure.Refused(RefusalReason.CleartextNotConfirmed).failed()
        val client =
            clients.create(
                TransportSpec(target.origin, AddressRules.classify(target.origin.host), true)
            )
        return try {
            val gateway =
                Oc2Gateway(
                    client,
                    target.origin,
                    target.credentials ?: ConnectionCredentials.None,
                    clock,
                    redactor,
                )
            gateway.probe().map { ProbeResult(backend, it.version, Oc2Gateway.Capabilities) }
        } finally {
            client.close()
        }
    }

    /**
     * Disposable editor check with the same origin-bound transport trust as a connection, without
     * an event stream.
     */
    public suspend fun checkConnection(
        origin: ServerOrigin,
        credentials: ConnectionCredentials,
        trust: ProfileTrust,
    ): Outcome<Health> {
        val loopback = AddressRules.isLoopback(origin.host)
        if (origin.scheme != "https" && !loopback && trust.cleartextConsentAtMs == null)
            return Failure.Refused(RefusalReason.CleartextNotConfirmed).failed()
        val pin = trust.pinnedLeafSha256
        val pinBytes = pin?.let { decodePin(it) ?: return Failure.UnreadableReply().failed() }
        if (origin.scheme != "https" && pinBytes != null)
            return Failure.Refused(RefusalReason.PinNeedsHttps).failed()
        val net =
            clients.create(
                TransportSpec(
                    origin,
                    AddressRules.classify(origin.host),
                    loopback || trust.cleartextConsentAtMs != null,
                    pinBytes,
                )
            )
        return try {
            Oc2Gateway(net, origin, credentials, clock, redactor).probe()
        } finally {
            net.close()
        }
    }

    override suspend fun connect(spec: GatewaySpec): Outcome<GatewayPair> {
        val profile = spec.profile
        val origin = profile.origin
        val loopback = AddressRules.isLoopback(origin.host)
        if (origin.scheme != "https" && !loopback && profile.trust.cleartextConsentAtMs == null)
            return Failure.Refused(RefusalReason.CleartextNotConfirmed).failed()
        val pin = profile.trust.pinnedLeafSha256
        val pinBytes = pin?.let { decodePin(it) ?: return Failure.UnreadableReply().failed() }
        if (origin.scheme != "https" && pinBytes != null)
            return Failure.Refused(RefusalReason.PinNeedsHttps).failed()
        val net =
            clients.create(
                TransportSpec(
                    origin,
                    AddressRules.classify(origin.host),
                    loopback || profile.trust.cleartextConsentAtMs != null,
                    pinBytes,
                )
            )
        val gateway =
            Oc2Gateway(net, origin, spec.credentials, clock, redactor, spec.location, random)
        var transferred = false
        try {
            return when (val health = gateway.health()) {
                is Outcome.Failed -> health
                is Outcome.Ok -> {
                    if (!health.value.healthy) Failure.UnreadableReply().failed()
                    else {
                        if (spec.observeEvents) gateway.start(spec, dispatchers)
                        transferred = true
                        GatewayPair(gateway, gateway).ok()
                    }
                }
            }
        } finally {
            if (!transferred) net.close()
        }
    }

    private fun decodePin(value: String): ByteArray? {
        val hex = value.replace(":", "")
        if (hex.length != 64 || hex.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
