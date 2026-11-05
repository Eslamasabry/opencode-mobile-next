package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitSheetLeadingTest {
    @Test
    fun `B-ANDR-078 sheet leading action navigates without dismissing and holds while disabled`() =
        runComposeUiTest {
            var held by mutableStateOf(false)
            var up = 0
            var closed = 0
            setContent {
                KitTheme(
                    KitAppearance(motion = MotionLevel.Off),
                    DesktopKitPlatform(),
                    stillFrame = true,
                ) {
                    KitSheetHost {
                        KitSheet(
                            true,
                            { closed++ },
                            "app",
                            subtitle = "/projects/app",
                            leading =
                                KitAction(
                                    "Up one folder",
                                    { up++ },
                                    enabled =
                                        if (held) KitEnabled.Disabled("Reading folder")
                                        else KitEnabled.Enabled,
                                ),
                        ) {}
                    }
                }
            }
            onNodeWithContentDescription("Up one folder").performClick()
            onNodeWithContentDescription("Close").assertDoesNotExist()
            onNodeWithText("/projects/app").assertExists()
            assertEquals(1, up)
            assertEquals(0, closed)
            runOnIdle { held = true }
            onNodeWithContentDescription("Up one folder").assertIsNotEnabled().performClick()
            assertEquals(1, up)
        }
}
