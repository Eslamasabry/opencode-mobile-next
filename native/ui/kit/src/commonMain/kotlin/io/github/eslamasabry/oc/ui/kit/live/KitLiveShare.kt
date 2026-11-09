package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlassPictures

/**
 * One live background for the whole app (smoothness round 2): the theme root's one clock and its live pictures, shared
 * by every framed page under it ([KitLiveBackground] with `shared`). Before, each page ran its own clock, drew its own
 * shader and recorded and blurred its own picture, so a push or a pop drew two backgrounds at once, and the arriving
 * page's look started from its own moment. Now both pages show the one picture at the one time; each page still draws
 * it in its own frame, so a page sliding in covers the one beneath exactly as before.
 */
@Stable
internal class KitLiveShare {
    val clock: LiveFrameClock = LiveFrameClock()
    val pictures: KitGlassPictures = KitGlassPictures()
}

/** The theme root's live share; null outside a [io.github.eslamasabry.oc.ui.kit.KitTheme]. */
internal val LocalKitLiveShare: ProvidableCompositionLocal<KitLiveShare?> = staticCompositionLocalOf { null }

/** Made once per theme root, its clock running while the root is composed (it sleeps while no page animates). */
@Composable
internal fun rememberKitLiveShare(): KitLiveShare {
    val share = remember { KitLiveShare() }
    RunLiveFrameClock(share.clock)
    return share
}
