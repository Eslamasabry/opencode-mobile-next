package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class Kita1FrameInsetsTest {
    @Test
    fun `B-KIT-029 finding 2 frame consumes safe and keyboard insets with or without optional slots`() =
        runComposeUiTest {
            val slots = mutableStateOf(false to false)
            val keyboard = mutableStateOf(false)
            setContent {
                val insets =
                    WindowInsets(
                        left = 16,
                        top = 28,
                        right = 20,
                        bottom = if (keyboard.value) 240 else 24,
                    )
                CompositionLocalProvider(
                    LocalDensity provides Density(1f),
                    LocalKitScreenInsets provides insets,
                ) {
                    KitTheme(
                        KitAppearance(motion = MotionLevel.Off),
                        FakeKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        InWindow(412, 915) {
                            KitScreen(
                                page = true,
                                topBar =
                                    if (slots.value.first)
                                        ({ Box(Modifier.height(48.dp).testTag("bar")) })
                                    else null,
                                bottom =
                                    if (slots.value.second)
                                        ({ Box(Modifier.height(48.dp).testTag("bottom")) })
                                    else null,
                            ) {
                                Box(Modifier.fillMaxSize().testTag("body")) {
                                    // A nested inset-aware child must not pay for the same insets
                                    // again.
                                    Box(
                                        Modifier.fillMaxSize()
                                            .windowInsetsPadding(insets)
                                            .testTag("child")
                                    )
                                }
                            }
                        }
                    }
                }
            }
            for (bar in listOf(false, true)) for (bottom in listOf(false, true)) {
                slots.value = bar to bottom
                for (ime in listOf(false, true, false)) {
                    keyboard.value = ime
                    waitForIdle()
                    val window = onNodeWithTag("window").getBoundsInRoot()
                    val body = onNodeWithTag("body").getBoundsInRoot()
                    assertEquals(window.left + 16.dp, body.left)
                    assertEquals(window.right - 20.dp, body.right)
                    assertTrue(body.top >= window.top + 28.dp)
                    assertTrue(body.bottom <= window.bottom - (if (ime) 240 else 24).dp)
                    assertEquals(body, onNodeWithTag("child").getBoundsInRoot())
                    if (!bar && !bottom) {
                        assertEquals(window.top + 28.dp, body.top)
                        assertEquals(window.bottom - (if (ime) 240 else 24).dp, body.bottom)
                    }
                }
            }
        }
}
