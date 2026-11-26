package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.model.NetworkStatus
import io.github.eslamasabry.oc.engine.connection.ConnectionVisibilitySource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Effective event-stream visibility: Foreground while shown, HiddenWatching while hidden and
 * ReplyWatch or Live Mode is actively watching for alerts, and HiddenIdle only when hidden and
 * nothing is watching.
 */
internal fun effectiveStreamVisibility(
    appVisibility: Visibility,
    replyWatchWatching: Boolean,
    liveModeActive: Boolean,
): Visibility =
    when {
        appVisibility == Visibility.Foreground -> Visibility.Foreground
        replyWatchWatching || liveModeActive -> Visibility.HiddenWatching
        else -> Visibility.HiddenIdle
    }

/** App-scoped event-stream visibility combining activity visibility with background watchers. */
@SingleIn(AppScope::class)
@Inject
class AppStreamVisibility(
    visibility: AppVisibility,
    replyWatch: ReplyWatchFlag,
    background: BackgroundHub,
    appScope: AppCoroutineScope,
) {
    val state: StateFlow<Visibility> =
        combine(visibility.state, replyWatch.watching, background.live) { app, watching, live ->
                effectiveStreamVisibility(app, watching, live.enabled && live.active)
            }
            .stateIn(
                appScope,
                SharingStarted.Eagerly,
                effectiveStreamVisibility(
                    visibility.state.value,
                    replyWatch.watching.value,
                    background.live.value.let { it.enabled && it.active },
                ),
            )
}

/** The same process observer drives registry offline state and connection network changes. */
@BindingContainer
@ContributesTo(AppScope::class)
object ProtoNetworkWiring {
    @Provides @IntoSet fun network(source: SetupNetwork): NetworkStatus = source.monitor

    @Provides
    @IntoSet
    fun visibility(stream: AppStreamVisibility): ConnectionVisibilitySource =
        ConnectionVisibilitySource { stream.state }
}
