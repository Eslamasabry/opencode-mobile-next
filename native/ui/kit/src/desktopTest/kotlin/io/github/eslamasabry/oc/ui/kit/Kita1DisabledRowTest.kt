package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class Kita1DisabledRowTest {
    @Test
    fun `B-KIT-051 finding 4 disabled row blocks inline and stacked action activation`() =
        runComposeUiTest {
            val scale = mutableStateOf(1f)
            var calls = 0
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, scale.value)) {
                    KitTheme(
                        KitAppearance(motion = MotionLevel.Off),
                        FakeKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        InWindow(320, 600) {
                            KitRow(
                                "Service",
                                enabled = false,
                                disabledReason = "Unavailable here",
                                onClick = { calls++ },
                                trailing =
                                    KitRowTrailing.Action(
                                        KitLineAction("Start", { calls++ }, testTag = "row-action")
                                    ),
                            )
                        }
                    }
                }
            }
            for (fontScale in listOf(1f, 1.5f)) {
                scale.value = fontScale
                waitForIdle()
                val action = onNodeWithTag("row-action", useUnmergedTree = true)
                action.assertIsNotEnabled()
                action.assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.StateDescription,
                        "Unavailable here",
                    )
                )
                action.performTouchInput { click() }
                action.performSemanticsAction(SemanticsActions.OnClick) { it() }
                assertEquals(0, calls)
            }
        }
}
