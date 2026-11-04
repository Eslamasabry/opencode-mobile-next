package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.engine.connection.ConnectionCredentialSource
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionCheckFacts
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionCheckStream
import io.github.eslamasabry.oc.feature.servers.presentation.ServerConnectionCheck
import kotlin.time.TimeSource
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Read-only manual check; it never connects a server, replaces its graph or opens another event
 * stream.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ServerConnectionCheck>())
class NativeConnectionCheck(
    private val stores: PreviewStores,
    private val credentials: Set<ConnectionCredentialSource>,
    private val factories: Set<GatewayFactory>,
    private val registry: ConnectionRegistry,
) : ServerConnectionCheck {
    override suspend fun inspect(id: ProfileId): Outcome<ConnectionCheckFacts> {
        when (val warm = stores.warm()) {
            is Outcome.Failed -> return warm
            is Outcome.Ok -> Unit
        }
        val loaded =
            stores.profiles.profile(id)
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
        val profile =
            loaded.toServerProfile()
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.InvalidAddress))
        val source =
            credentials.singleOrNull() ?: return Outcome.Failed(Failure.SecretUnavailable())
        val credential =
            when (val read = source.resolve(profile)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        val factory =
            factories.singleOrNull { it.backend == profile.backend }
                ?: return Outcome.Failed(Failure.Unavailable(FeatureId("connectionCheck")))
        if (stores.profiles.profile(id) !== loaded)
            return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        // Monotonic, like Flutter's Stopwatch: a wall-clock jump must not fake a slow reply.
        val started = TimeSource.Monotonic.markNow()
        val result =
            withTimeoutOrNull(15_000) {
                factory.probe(ProbeTarget(profile.origin, credential, profile.trust))
            } ?: Outcome.Failed(Failure.TimedOut())
        val elapsed = started.elapsedNow().inWholeMilliseconds.coerceAtLeast(0)
        if (stores.profiles.profile(id) !== loaded)
            return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        val live = registry.live.value[id]?.takeIf { it.session.profile == profile }
        val stream =
            if (live == null) ConnectionCheckStream.NotConnected
            else
                when (live.session.server.events.status.value) {
                    StreamStatus.Connected -> ConnectionCheckStream.Live
                    StreamStatus.Connecting,
                    is StreamStatus.Reconnecting -> ConnectionCheckStream.Connecting
                    else -> ConnectionCheckStream.Down
                }
        return Outcome.Ok(ConnectionCheckFacts(result, stream, elapsed))
    }
}
