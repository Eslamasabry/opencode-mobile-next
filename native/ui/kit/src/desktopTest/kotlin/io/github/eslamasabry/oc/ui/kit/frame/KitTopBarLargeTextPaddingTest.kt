package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** Audit 2 (conversation at 1.3x text): the title's first line sat 15 px under the compact bar's top rim. */
@OptIn(ExperimentalTestApi::class)
class KitTopBarLargeTextPaddingTest {
    @Test
    fun `W2-027 at large text a taller compact bar keeps a step of 8 dp above its title`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    Box(Modifier.width(360.dp)) {
                        KitTopBar(
                            "Explain this folder in detail: a heading, a numbered list of five items and more words",
                            subtitle = "projects · This phone",
                            exit = KitTopBarExit.Back,
                            exitLabel = "Back",
                            onExit = {},
                            backBesideTitle = true,
                        )
                    }
                }
            }
        }
        val bar = onNodeWithTag("kit-top-bar").getUnclippedBoundsInRoot()
        val title = onNodeWithTag("kit-top-bar-title", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue((title.top - bar.top).value >= 8f - 0.5f, "the title starts ${title.top - bar.top} under the bar's top")
    }
}
