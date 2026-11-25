package io.github.eslamasabry.oc.feature.onboarding

import androidx.compose.foundation.Indication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.onboarding.presentation.PhoneWelcomeUi
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeActions
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomePlace
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeUi
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
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun recording(): Pair<WelcomeActions, MutableList<String>> {
        val calls = mutableListOf<String>()
        val actions =
            object : WelcomeActions {
                override fun connect() {
                    calls += "connect"
                }

                override fun openSettings() {
                    calls += "settings"
                }
            }
        return actions to calls
    }

    @Test
    fun `B-545 first-launch bar has no Report a problem action`() = runComposeUiTest {
        val (actions, _) = recording()
        setContent {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = Platform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.width(360.dp)) {
                    CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(360f, 800f)) {
                        WelcomeScreen(
                            WelcomeUi(phone = PhoneWelcomeUi(available = true), place = WelcomePlace.Phone),
                            actions,
                        )
                    }
                }
            }
        }
        onAllNodesWithContentDescription("Report a problem", useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithText("Report a problem", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `B-545 first-launch bar keeps Settings without phone setup`() = runComposeUiTest {
        val (actions, calls) = recording()
        setContent {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = Platform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.width(360.dp)) {
                    CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(360f, 800f)) {
                        WelcomeScreen(
                            WelcomeUi(phone = PhoneWelcomeUi(available = false)),
                            actions,
                        )
                    }
                }
            }
        }
        onAllNodesWithContentDescription("Report a problem", useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithText("Report a problem", useUnmergedTree = true).assertCountEquals(0)
        val settings = onAllNodesWithContentDescription("Settings", useUnmergedTree = true)
        assertTrue(settings.fetchSemanticsNodes().isNotEmpty(), "Settings action missing from setup bar")
        settings.onFirst().performClick()
        assertEquals(listOf("settings"), calls)
    }
}
