package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Inside a Glass modal sheet, KitRowGroup renders flat on the sheet's own glass surface (zero card-inside-card) and
 * aligns rows directly with the sheet's section rail, while page-level Glass row groups and non-Glass styles keep
 * their framed panels unchanged.
 */
@OptIn(ExperimentalTestApi::class)
class KitRowGroupGlassSheetTest {
    @Test
    fun `B-KIT-032 KitRowGroup inside a Glass KitSheet renders flat on the sheet rail without a nested card`() =
        runDesktopComposeUiTest(360, 760) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    KitTheme(
                        appearance =
                            KitAppearance(
                                style = Style.Glass,
                                mode = AppearanceMode.Dark,
                                motion = MotionLevel.Off,
                            ),
                        platform = FakeKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        InWindow(360, 760) {
                            KitSheetHost(Modifier.fillMaxSize()) {
                                KitSheet(
                                    visible = true,
                                    onDismissRequest = {},
                                    title = "Updates for your agents",
                                ) {
                                    KitRowGroup(label = "Optional") {
                                        KitRow(title = "Claude Code", supporting = "2.1.327 -> 2.1.330")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            onAllNodesWithTag("kit-row-group-panel", useUnmergedTree = true).assertCountEquals(0)
            val labelBounds = onNodeWithText("Optional").getUnclippedBoundsInRoot()
            val rowBounds = onNodeWithText("Claude Code").getUnclippedBoundsInRoot()
            assertTrue(
                abs((labelBounds.left - rowBounds.left).value) < 0.5f,
                "row starts at ${rowBounds.left}, expected flush with section label at ${labelBounds.left}",
            )
        }

    @Test
    fun `B-KIT-032 KitRowGroup on a Glass page outside a sheet keeps its panel`() =
        runDesktopComposeUiTest(360, 760) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    KitTheme(
                        appearance =
                            KitAppearance(
                                style = Style.Glass,
                                mode = AppearanceMode.Dark,
                                motion = MotionLevel.Off,
                            ),
                        platform = FakeKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        InWindow(360, 760) {
                            Column {
                                KitRowGroup(label = "General") {
                                    KitRow(title = "Appearance")
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            onAllNodesWithTag("kit-row-group-panel", useUnmergedTree = true).assertCountEquals(1)
        }

    @Test
    fun `B-KIT-032 KitRowGroup inside a Soft KitSheet keeps its panel`() =
        runDesktopComposeUiTest(360, 760) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    KitTheme(
                        appearance =
                            KitAppearance(
                                style = Style.Soft,
                                mode = AppearanceMode.Dark,
                                motion = MotionLevel.Off,
                            ),
                        platform = FakeKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        InWindow(360, 760) {
                            KitSheetHost(Modifier.fillMaxSize()) {
                                KitSheet(
                                    visible = true,
                                    onDismissRequest = {},
                                    title = "Updates for your agents",
                                ) {
                                    KitRowGroup(label = "Optional") {
                                        KitRow(title = "Claude Code")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            onAllNodesWithTag("kit-row-group-panel", useUnmergedTree = true).assertCountEquals(1)
        }
}
