package io.github.eslamasabry.oc.feature.onboarding

import androidx.compose.foundation.Indication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeActions
import io.github.eslamasabry.oc.feature.onboarding.ui.WelcomeScreen
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatformFamily
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

/** QA wave 1 B15: the first-run buttons touched the screen edges. */
@OptIn(ExperimentalTestApi::class)
class WelcomeScreenTest {
    private class Platform : KitPlatform {
        override val name: String = "test"
        override val family: KitPlatformFamily = KitPlatformFamily.Desktop
        override val signals: MutableStateFlow<KitSystemSignals> = MutableStateFlow(KitSystemSignals())
        override val isDebug: Boolean = true

        override fun pressIndication(ink: Color): Indication = KitFillIndication(ink)

        override fun haptic(kind: KitHaptic) {}

        override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? = null
    }

    @Test
    fun `B15 QA wave1 first-run buttons keep a side margin on a phone`() = runComposeUiTest {
        setContent {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = Platform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.width(360.dp)) {
                    WelcomeScreen(
                        object : WelcomeActions {
                            override fun connect() {}

                            override fun openSettings() {}
                        }
                    )
                }
            }
        }
        val bounds = onNodeWithText("Connect to my computer").getBoundsInRoot()
        assertTrue(bounds.left >= 12.dp, "left edge at ${bounds.left}")
        assertTrue(bounds.right <= 348.dp, "right edge at ${bounds.right}")
    }
}
