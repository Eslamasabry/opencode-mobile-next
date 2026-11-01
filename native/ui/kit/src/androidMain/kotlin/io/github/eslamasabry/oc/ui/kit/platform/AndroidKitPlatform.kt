package io.github.eslamasabry.oc.ui.kit.platform

import android.view.HapticFeedbackConstants
import android.view.View

/** Built once by :androidApp's MainActivity and provided through LocalKitPlatform. */
public class AndroidKitPlatform(private val view: View) : KitPlatform {
    override val name: String = "android"

    override fun haptic(kind: KitHaptic) {
        val constant =
            when (kind) {
                KitHaptic.Send, KitHaptic.Commit, KitHaptic.Done -> HapticFeedbackConstants.CONFIRM
                KitHaptic.LongPress -> HapticFeedbackConstants.LONG_PRESS
                KitHaptic.Select, KitHaptic.ToggleOn, KitHaptic.ToggleOff -> HapticFeedbackConstants.CLOCK_TICK
            }
        view.performHapticFeedback(constant) // respects the system haptics setting
    }
}
