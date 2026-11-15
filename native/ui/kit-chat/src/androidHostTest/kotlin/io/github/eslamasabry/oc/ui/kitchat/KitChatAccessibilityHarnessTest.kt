package io.github.eslamasabry.oc.ui.kitchat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * B-A11Y-HARNESS: the proof that the proof can fail. A 20 dp square that can be tapped and says nothing breaks two ATF
 * checks (touch target size, missing label). If this scene ever passes, `tryPerformAccessibilityChecks` has stopped
 * enforcing (an API level without the checker, a changed default) and every green scene in `KitChatAccessibilityChecksTest` means nothing; this test then fails first.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w412dp-h915dp-420dpi")
class KitChatAccessibilityHarnessTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `B-A11Y-HARNESS a tiny unlabelled tap target is caught`() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            KitTheme(
                appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
                platform = AndroidKitPlatform(View(ApplicationProvider.getApplicationContext())),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.size(20.dp).clickable {})
            }
        }
        compose.waitForIdle()
        val caught = runCatching { compose.onRoot().tryPerformAccessibilityChecks() }.exceptionOrNull()
        check(caught != null) { "ATF let a 20 dp unlabelled tap target through: the accessibility scenes prove nothing" }
    }
}
