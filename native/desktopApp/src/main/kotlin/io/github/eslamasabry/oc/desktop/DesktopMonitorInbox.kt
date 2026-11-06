package io.github.eslamasabry.oc.desktop

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
 * The desktop app has no attention monitor, so the monitored-servers inbox says it is unavailable (the
 * same honest answer as the Android app's NativeMonitorInbox until that page reads the one monitor).
 * Without this binding the desktop graph does not compile.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<MonitorInboxSource>())
@Inject
internal class DesktopMonitorInbox : MonitorInboxSource {
    private val unavailable = Failure.Unavailable(FeatureId("monitor-inbox"))
    private val local = MutableStateFlow(MonitorInboxFacts(loaded = true, failure = unavailable))
    override val facts: StateFlow<MonitorInboxFacts> = local.asStateFlow()

    override suspend fun refresh(): Outcome<Unit> = Outcome.Failed(unavailable)

    override suspend fun open(key: String, allowSwitch: Boolean): Outcome<MonitoredOpen> =
        Outcome.Failed(unavailable)

    override suspend fun token(profileId: String, sessionId: String, token: String): Outcome<String> =
        Outcome.Failed(unavailable)
}
