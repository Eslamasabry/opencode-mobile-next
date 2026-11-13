package io.github.eslamasabry.opencode_mobile

import android.os.Build
import android.view.Window

/**
 * B-270: a language switch calls `recreate()` on the resumed Activity, and Android hands the new Activity the old
 * window's DecorView (a "preserved window"). On Android 15 the new window does not carry over "the decor does not fit
 * the system bars": it starts at the platform default (fit) and pushes that onto the reused view root at once, so the
 * page is laid out between the status and navigation bars with black bands above and below. The edge-to-edge
 * enforcement (target SDK 35+) is only switched on when the decor is first installed, and from then on
 * `setDecorFitsSystemWindows(false)` is ignored: `enableEdgeToEdge()` (which installs the decor first) can no longer
 * undo it, on create or on resume. Only a new window (Back and reopen, or a process restart) cleared it.
 *
 * [release] must run before anything touches `window.decorView` in `onCreate`: the call is still honoured then and
 * clears the reused view root's fitting. On a fresh window it only repeats what edge-to-edge sets anyway.
 */
internal object DecorFit {
    fun release(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(false)
    }
}
