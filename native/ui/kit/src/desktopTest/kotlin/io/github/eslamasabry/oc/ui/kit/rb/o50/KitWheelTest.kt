package io.github.eslamasabry.oc.ui.kit.rb.o50

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.choice.KitWheel
import io.github.eslamasabry.oc.ui.kit.choice.KitWheelColumn
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitWheelTest {
    @Test
    fun `B-KIT-014 wheel dispatches one chosen value and external selection never echoes`() =
        runComposeUiTest {
            val chosen = mutableStateOf(2)
            val calls = mutableListOf<Int>()
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    stillFrame = true,
                ) {
                    KitWheel(
                        listOf(
                            KitWheelColumn(
                                (1..5).toList(),
                                chosen.value,
                                { "Value $it" },
                                {
                                    calls += it
                                    chosen.value = it
                                },
                                "Interval",
                                testTag = "wheel",
                            )
                        )
                    )
                }
            }
            onNodeWithTag("wheel")
                .assert(
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Value 2")
                )
            assertEquals(emptyList(), calls)
            onNodeWithTag("wheel").performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
            waitForIdle()
            assertEquals(listOf(4), calls)
            runOnIdle { chosen.value = 1 }
            onNodeWithTag("wheel")
                .assert(
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Value 1")
                )
            assertEquals(listOf(4), calls)
        }

    @Test
    fun `B-KIT-014 wheel caps large text at three readable rows and holds during saves`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), stillFrame = true) {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                        KitWheel(
                            listOf(
                                KitWheelColumn(
                                    listOf("Today", "Tomorrow"),
                                    "Today",
                                    { it },
                                    { error("Held wheel changed") },
                                    "Day",
                                    testTag = "wheel",
                                )
                            ),
                            enabled = KitEnabled.Disabled("Saving"),
                        )
                    }
                }
            }
            onNodeWithTag("wheel").assertHeightIsEqualTo(216.dp).assertIsNotEnabled()
            onNodeWithTag("wheel")
                .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetProgress))
        }
}
