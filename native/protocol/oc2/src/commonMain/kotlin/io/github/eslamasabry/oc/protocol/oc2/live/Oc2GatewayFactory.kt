package io.github.eslamasabry.oc.protocol.oc2.live

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
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
    private val networks: Set<NetworkStatus> = emptySet(),
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
            Oc2Gateway(
                net,
                origin,
                spec.credentials,
                clock,
                redactor,
                spec.location,
                random,
                requireDispatchAdmission = loopback && origin.port in setOf(4097, 4099),
            )
        // An unexpected health shape is a re-detection signal: the read-only probe recognises
        // unsupported/legacy servers before a connection graph or event stream is admitted.
        return when (val health = gateway.probe()) {
            is Outcome.Failed -> {
                gateway.close()
                health
            }
            is Outcome.Ok -> {
                if (!health.value.healthy) {
                    gateway.close()
                    Failure.UnreadableReply().failed()
                } else {
                    gateway.start(
                        spec,
                        dispatchers,
                        networks.singleOrNull() ?: UnknownNetworkStatus,
                    )
                    GatewayPair(gateway, gateway).ok()
                }
            }
        }
    }

    private fun decodePin(value: String): ByteArray? {
        val hex = value.replace(":", "")
        if (hex.length != 64 || hex.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}

/** Android contributes its process-owned network monitor; desktop can keep the unknown fallback. */
@ContributesTo(AppScope::class)
public interface Oc2NetworkBindings {
    @Multibinds(allowEmpty = true) public val oc2Networks: Set<NetworkStatus>
}
