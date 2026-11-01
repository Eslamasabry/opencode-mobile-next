package io.github.eslamasabry.oc.ui.kit

import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform

/** Shared Robolectric settings for the kit's host tests (plan 01 §11 P7: SDK 37, JDK 21). */
object KitRobolectric {
    /** compileSdk 37 (ADR §8). Robolectric 4.17 runs it on the `android-all-instrumented` 17 jar. */
    const val SDK: Int = 37

    /** Plan 06 §15.3: 412 × 915 dp at 420 dpi. */
    const val PHONE: String = "w412dp-h915dp-420dpi"

    fun platform(view: View = View(ApplicationProvider.getApplicationContext())): AndroidKitPlatform =
        AndroidKitPlatform(view)
}
