package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.frame.KitPaneScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class Kita1PaneSheetTest {
    @Test
    fun `B-KIT-032 finding 6 list sheet covers window and blocks sibling input`() =
        runComposeUiTest {
            var calls = 0
            val owner =
                object : NavigationEventDispatcherOwner {
                    override val navigationEventDispatcher = NavigationEventDispatcher()
                }
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f),
                    LocalNavigationEventDispatcherOwner provides owner,
                ) {
                    KitTheme(
                        KitAppearance(motion = MotionLevel.Off),
                        FakeKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        InWindow(1280, 800) {
                            KitPaneScreen(
                                list = {
                                    KitSheet(
                                        visible = true,
                                        onDismissRequest = {},
                                        title = "Choose",
                                        dismissible = false,
                                    ) {
                                        KitText("Options")
                                    }
                                },
                                detail = {
                                    KitScreen(page = true) {
                                        KitTappable(
                                            { calls++ },
                                            Modifier.testTag("detail-button"),
                                        ) {
                                            KitText("Detail action")
                                        }
                                    }
                                },
                                side = {
                                    KitTappable({ calls++ }, Modifier.testTag("side-button")) {
                                        KitText("Side action")
                                    }
                                },
                                emptyDetail = {},
                            )
                        }
                    }
                }
            }
            val window = onNodeWithTag("window").getBoundsInRoot()
            assertEquals(window, onNodeWithTag("kit-sheet-scrim").getBoundsInRoot())
            assertEquals(560.dp, onNodeWithTag("kit-sheet").getBoundsInRoot().width)
            onNodeWithTag("detail-button").performTouchInput { click() }
            onNodeWithTag("side-button").performTouchInput { click() }
            assertEquals(0, calls)
            owner.navigationEventDispatcher.dispose()
        }
}
