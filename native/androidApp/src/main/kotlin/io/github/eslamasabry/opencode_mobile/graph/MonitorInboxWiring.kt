package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.chats.monitor.MonitorInboxFacts
import io.github.eslamasabry.oc.feature.chats.monitor.MonitorInboxSource
import io.github.eslamasabry.oc.feature.chats.monitor.MonitoredOpen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The monitored-servers inbox page (screens-3 port) is not wired to a monitor yet. The app keeps ONE
 * attention monitor ([AndroidProfileMonitoring], MonitorWiring.kt); the port's own monitor engine was a
 * second copy and was dropped in the P12 merge. Until the inbox reads that monitor, the page states it is
 * unavailable instead of running a second monitor (double notifications, double polling).
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<MonitorInboxSource>())
@Inject
class NativeMonitorInbox : MonitorInboxSource {
    private val unavailable = Failure.Unavailable(FeatureId("monitor-inbox"))
    private val local = MutableStateFlow(MonitorInboxFacts(loaded = true, failure = unavailable))
    override val facts: StateFlow<MonitorInboxFacts> = local.asStateFlow()

    override suspend fun refresh(): Outcome<Unit> = Outcome.Failed(unavailable)

    override suspend fun open(key: String, allowSwitch: Boolean): Outcome<MonitoredOpen> =
        Outcome.Failed(unavailable)

    override suspend fun token(profileId: String, sessionId: String, token: String): Outcome<String> =
        Outcome.Failed(unavailable)
}
