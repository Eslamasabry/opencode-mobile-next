package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.choice.KitSliderStep
import io.github.eslamasabry.oc.ui.kit.choice.KitStepSlider
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitStepSliderTest {
    @Test
    fun `B-KIT-017 step slider adjusts one named choice by accessibility and keys`() =
        runComposeUiTest {
            val selected = mutableStateOf(1)
            val calls = mutableListOf<Int>()
            setContent {
                KitTheme(
                    KitAppearance(motion = MotionLevel.Off),
                    DesktopKitPlatform(),
                    stillFrame = true,
                ) {
                    KitStepSlider(
                        "Text size",
                        listOf(
                            KitSliderStep(0, "Small"),
                            KitSliderStep(1, "Normal"),
                            KitSliderStep(2, "Large"),
                        ),
                        selected.value,
                        {
                            calls += it
                            selected.value = it
                        },
                        Modifier.width(340.dp),
                    )
                }
            }
            val slider = onNodeWithContentDescription("Text size")
            slider.performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
            slider.assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Large")
            )
            slider.performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
            assertEquals(listOf(2), calls)
            slider.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            slider.performKeyInput { pressKey(Key.DirectionLeft) }
            slider.assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Normal")
            )
            assertEquals(listOf(2, 1), calls)
        }

    @Test
    fun `B-KIT-017 step slider reverses horizontal keys in RTL`() = runComposeUiTest {
        val selected = mutableStateOf(1)
        setContent {
            KitTheme(
                KitAppearance(motion = MotionLevel.Off),
                DesktopKitPlatform(),
                stillFrame = true,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    KitStepSlider(
                        "Text size",
                        listOf(
                            KitSliderStep(0, "Small"),
                            KitSliderStep(1, "Normal"),
                            KitSliderStep(2, "Large"),
                        ),
                        selected.value,
                        { selected.value = it },
                        Modifier.width(340.dp),
                    )
                }
            }
        }
        val slider = onNodeWithContentDescription("Text size")
        slider.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Small"))
    }

    @Test
    fun `B-KIT-017 disabled step slider exposes its value without a mutation action`() =
        runComposeUiTest {
            setContent {
                KitTheme(
                    KitAppearance(motion = MotionLevel.Off),
                    DesktopKitPlatform(),
                    stillFrame = true,
                ) {
                    KitStepSlider(
                        "Text size",
                        listOf(KitSliderStep(0, "Small"), KitSliderStep(1, "Normal")),
                        1,
                        null,
                    )
                }
            }
            onNodeWithContentDescription("Text size")
                .assertIsNotEnabled()
                .assert(
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Normal")
                )
                .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetProgress))
        }
}
