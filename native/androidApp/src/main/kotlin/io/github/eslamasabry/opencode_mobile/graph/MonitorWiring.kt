package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.servers.presentation.ServerMonitorUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServerMonitoring
import kotlinx.coroutines.flow.StateFlow

/**
 * The Servers-list opt-in (ANDR-2) on the app's ONE attention monitor, [NativeProfileMonitoring].
 * A second reader would poll every enrolled server twice and post every alert twice, so this port
 * owns no engine, no transport and no keys of its own: enrollment, consent, deletion admission,
 * opaque notification taps (NativeMonitorInbox) and the shared Wi-Fi / check-in rules
 * (Settings › Notifications, as Flutter's `SharedNotifyRules`) all live there.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ServerMonitoring>())
@Inject
class NativeServerMonitoring(private val monitoring: NativeProfileMonitoring) : ServerMonitoring {
    override val rows: StateFlow<Map<ProfileId, ServerMonitorUi>>
        get() = monitoring.serverRows

    override suspend fun enabled(profile: ProfileId, enabled: Boolean): Outcome<Unit> =
        monitoring.setEnabled(profile, enabled)
}
