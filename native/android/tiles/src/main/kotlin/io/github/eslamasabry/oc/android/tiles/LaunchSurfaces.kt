package io.github.eslamasabry.oc.android.tiles

import android.content.Context

/**
 * What the launch surfaces are told about the connection (B-ANDR-030): the connected profile, whether its link is
 * live right now, its pinned sessions in pin order and the Activity count (requests that need the person).
 */
data class LaunchSurfaceState(
    val profileId: String?,
    val connected: Boolean,
    val pins: List<PinnedSession>,
    val pendingCount: Int,
)

/**
 * Keeps the two launch surfaces in step with connection truth (B-ANDR-030), the native half of the Flutter
 * `ConnectionController` launch-surfaces hook: the launcher's pinned-session shortcuts ([PinnedShortcuts]) and the
 * Quick Settings tile's cached Activity count ([AttentionTileCache]).
 *
 * - [onState] publishes only while connected; an unchanged set is not republished (the shortcut record and the tile
 *   snapshot both compare before writing), so calling it on every feed change is cheap.
 * - [onExplicitDisconnect] withdraws both surfaces.
 * - A lifecycle suspension (the app went to the background and the link was parked) calls nothing: the last
 *   published state stays.
 *
 * Android only by construction: no desktop module depends on `:android:tiles`, so nothing is written off Android.
 */
class LaunchSurfaces(private val context: Context, private val nowMs: () -> Long) {
    /** Returns true when the state was published (connected with a profile), false when it was ignored. */
    fun onState(state: LaunchSurfaceState): Boolean {
        val profile = state.profileId?.takeIf { it.isNotBlank() } ?: return false
        if (!state.connected) return false
        PinnedShortcuts.publish(context, profile, state.pins)
        AttentionTileCache.write(context, state.pendingCount, profile, nowMs())
        return true
    }

    /** The person disconnected on purpose: no shortcut and no tile count of that server is left behind. */
    fun onExplicitDisconnect() {
        PinnedShortcuts.withdrawAll(context)
        AttentionTileCache.clear(context)
    }
}
