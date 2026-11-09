package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * B-A11Y-ATF gap 6: the Android Accessibility Test Framework (ATF) run over the kit parts the key screens are made of
 * (home tabs and search, filter chips, the motion parts), light and dark. ATF checks touch-target size, text contrast,
 * missing labels and duplicate clickable bounds on the real semantics tree. A failure names the node and the check.
 * Run: `:ui:kit:testAndroidHostTest --tests '*KitAccessibilityChecksTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [KitRobolectric.SDK], qualifiers = KitRobolectric.PHONE)
class KitAccessibilityChecksTest {
    @get:Rule val compose = createComposeRule()

    private val words = KitbWords.of("en")

    private fun check(dark: Boolean, content: @Composable () -> Unit) {
        compose.enableAccessibilityChecks()
        compose.setContent {
            KitTheme(
                appearance = KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off),
                platform = KitRobolectric.platform(),
                localeTag = "en",
                stillFrame = true,
                content = content,
            )
        }
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test fun `B-A11Y-ATF tabs pass ATF in light`() = check(false) { TabsScene(words, index = 1) }

    @Test fun `B-A11Y-ATF tabs pass ATF in dark`() = check(true) { TabsScene(words, index = 1) }

    @Test fun `B-A11Y-ATF search passes ATF in light`() = check(false) { SearchScene(words) }

    @Test fun `B-A11Y-ATF search passes ATF in dark`() = check(true) { SearchScene(words) }

    @Test fun `B-A11Y-ATF filter chips pass ATF in light`() = check(false) { FilterChipsScene(words) }

    @Test fun `B-A11Y-ATF filter chips pass ATF in dark`() = check(true) { FilterChipsScene(words) }

    @Test fun `B-A11Y-ATF motion parts pass ATF in light`() = check(false) { MotionPartsScene(words) }
}
