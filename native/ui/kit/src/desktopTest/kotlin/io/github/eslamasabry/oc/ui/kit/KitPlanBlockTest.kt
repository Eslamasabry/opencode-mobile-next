package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.state.KitPlanBlock
import io.github.eslamasabry.oc.ui.kit.state.KitWindowRow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitPlanBlockTest {
    @Test
    fun `B-STATE-usage inline window has one spoken value and a whole-block open target`() =
        runComposeUiTest {
            var opened = 0
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    stillFrame = true,
                ) {
                    Box(Modifier.width(412.dp)) {
                        KitPlanBlock(
                            "Codex",
                            { opened++ },
                            Modifier.testTag("plan"),
                            "Plus",
                            "12% left",
                        ) {
                            KitWindowRow(
                                "5 hours",
                                .12f,
                                "12% · 14:10",
                                "12% left · 14:10",
                                modifier = Modifier.testTag("window"),
                                inline = true,
                            )
                        }
                    }
                }
            }
            onNodeWithTag("window", useUnmergedTree = true)
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.ProgressBarRangeInfo,
                        ProgressBarRangeInfo(.12f, 0f..1f),
                    )
                )
            onNodeWithTag("plan").performClick()
            assertEquals(1, opened)
        }

    @Test
    fun `B-KIT-009 large plan text keeps the full name tier and summary visible`() =
        runComposeUiTest {
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    stillFrame = true,
                ) {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                        Box(Modifier.width(360.dp)) {
                            KitPlanBlock(
                                "Claude",
                                {},
                                titleNote = "Max · Workstation",
                                summary = "Back at 14:10",
                            ) {
                                KitWindowRow(
                                    "5 hours",
                                    0f,
                                    "0% · 14:10",
                                    "0% left · 14:10",
                                    inline = true,
                                )
                            }
                        }
                    }
                }
            }
            onNodeWithText("Claude Max · Workstation", useUnmergedTree = true).assertExists()
            onNodeWithText("Back at 14:10", useUnmergedTree = true).assertExists()
            onNodeWithContentDescription("5 hours, 0% left · 14:10", useUnmergedTree = true)
                .assertExists()
        }
}
