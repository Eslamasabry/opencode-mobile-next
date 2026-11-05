package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.choice.KitPlainOptionCard
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitPlainOptionCardTest {
    @Test
    fun `B-KIT-017 captioned option is one named radio choice and a held card never selects`() =
        runComposeUiTest {
            var chosen by mutableStateOf(false)
            var held by mutableStateOf(false)
            var calls = 0
            setContent {
                KitTheme(
                    KitAppearance(motion = MotionLevel.Off),
                    DesktopKitPlatform(),
                    stillFrame = true,
                ) {
                    KitPlainOptionCard(
                        "Calm",
                        "Quiet transitions",
                        KitIcons.waveform,
                        chosen,
                        {
                            calls++
                            chosen = true
                        },
                        enabled =
                            if (held) KitEnabled.Disabled("Held by Saver") else KitEnabled.Enabled,
                    )
                }
            }
            onNodeWithContentDescription("Calm").performClick().assertIsSelected()
            assertEquals(1, calls)
            runOnIdle { held = true }
            onNodeWithContentDescription("Calm").assertIsNotEnabled().performClick()
            assertEquals(1, calls)
        }
}
