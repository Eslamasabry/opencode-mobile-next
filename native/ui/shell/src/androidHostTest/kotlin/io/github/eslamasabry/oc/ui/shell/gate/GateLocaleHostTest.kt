package io.github.eslamasabry.oc.ui.shell.gate

import android.view.View
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shell half of plan 05 §10's `GateLocaleTest`: with the phone in English, an in-app Arabic choice reads the
 * failure screen in Arabic, right to left; no in-app choice follows the system.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], qualifiers = "en-rUS-w412dp-h915dp-420dpi")
class GateLocaleHostTest {
    @get:Rule val compose = createComposeRule()

    private val failed =
        GateUi.Failed(GateFailureScreen.Failed, GatePresenter.rowsFor(GateBlockReason.PrefsUnreadable, false))

    private var previous: String? = null

    @After
    fun restoreLocale() {
        previous?.let { applyGateLocale(it) }
    }

    private fun show(appLocale: String?, direction: (LayoutDirection) -> Unit = {}) {
        previous = applyGateLocale(appLocale)
        compose.setContent {
            run {
                KitTheme(
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    platform = AndroidKitPlatform(View(ApplicationProvider.getApplicationContext())),
                    localeTag = appLocale ?: "en-US",
                    stillFrame = true,
                ) {
                    direction(LocalLayoutDirection.current)
                    GateScreen(failed, {}, {}, {}, {}, {}) {}
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `B-L10N-001 an in-app Arabic choice reads the gate in Arabic, right to left, on an English phone`() {
        var seen: LayoutDirection? = null
        show("ar") { seen = it }
        compose.onNodeWithText("تعذّر على التحديث إكمال نقل بياناتك").assertExists()
        compose.onNodeWithText("البدء من جديد (مع الاحتفاظ بنسخة من بياناتك القديمة)").assertExists()
        assertEquals(LayoutDirection.Rtl, seen)
    }

    @Test
    fun `B-L10N-001 no in-app choice follows the system language`() {
        show(null)
        compose.onNodeWithText("The update couldn't finish moving your data").assertExists()
    }
}
