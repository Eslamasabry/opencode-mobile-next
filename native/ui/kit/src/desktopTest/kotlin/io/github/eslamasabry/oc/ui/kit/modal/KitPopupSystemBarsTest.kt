package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B-544: every kit Popup calls PopupSystemBars with the active theme; desktop is a no-op. */
@OptIn(ExperimentalTestApi::class)
class KitPopupSystemBarsTest {
    private class RecordingPopupPlatform(
        base: KitPlatform = FakeKitPlatform(),
    ) : KitPlatform by base {
        val calls = mutableListOf<Pair<Boolean, Color>>()

        @Composable
        override fun PopupSystemBars(darkContent: Boolean, ground: Color) {
            calls += darkContent to ground
        }
    }

    @Test
    fun `B-544 KitPageOverlay calls PopupSystemBars with dark and light theme`() =
        runDesktopComposeUiTest(400, 800) {
            val platform = RecordingPopupPlatform()
            var mode by mutableStateOf(AppearanceMode.Dark)
            var ground = Color.Unspecified
            setContent {
                KitTheme(
                    appearance = KitAppearance(mode = mode, motion = MotionLevel.Off),
                    platform = platform,
                    localeTag = "en",
                ) {
                    ground = LocalKitColors.current.ground
                    KitPageOverlay(visible = true, title = "Overlay", onDismissRequest = {}) {
                        KitText("Overlay body")
                    }
                }
            }
            waitForIdle()
            assertTrue(platform.calls.isNotEmpty(), "KitPageOverlay must call PopupSystemBars")
            assertEquals(false to ground, platform.calls.last())

            mode = AppearanceMode.Light
            waitForIdle()
            assertEquals(true to ground, platform.calls.last())
        }

    @Test
    fun `B-544 KitMenu popup calls PopupSystemBars with dark and light theme`() =
        runDesktopComposeUiTest(400, 800) {
            val platform = RecordingPopupPlatform()
            var mode by mutableStateOf(AppearanceMode.Dark)
            var ground = Color.Unspecified
            setContent {
                KitTheme(
                    appearance = KitAppearance(mode = mode, motion = MotionLevel.Off),
                    platform = platform,
                    localeTag = "en",
                ) {
                    ground = LocalKitColors.current.ground
                    KitMenu(
                        expanded = true,
                        items = listOf(KitMenuItem("Copy", onSelect = {})),
                        onDismissRequest = {},
                    ) {
                        KitText("More")
                    }
                }
            }
            waitForIdle()
            assertTrue(platform.calls.isNotEmpty(), "KitMenu popup must call PopupSystemBars")
            assertEquals(false to ground, platform.calls.last())

            mode = AppearanceMode.Light
            waitForIdle()
            assertEquals(true to ground, platform.calls.last())
        }

    @Test
    fun `B-544 KitTip popup calls PopupSystemBars with dark and light theme`() =
        runDesktopComposeUiTest(400, 800) {
            val platform = RecordingPopupPlatform()
            var mode by mutableStateOf(AppearanceMode.Dark)
            var ground = Color.Unspecified
            setContent {
                KitTheme(
                    appearance = KitAppearance(mode = mode, motion = MotionLevel.Off),
                    platform = platform,
                    localeTag = "en",
                ) {
                    ground = LocalKitColors.current.ground
                    KitTip(message = "Hold to send", visible = true, onDismissed = {}) {
                        KitText("Send")
                    }
                }
            }
            waitForIdle()
            assertTrue(platform.calls.isNotEmpty(), "KitTip popup must call PopupSystemBars")
            assertEquals(false to ground, platform.calls.last())

            mode = AppearanceMode.Light
            waitForIdle()
            assertEquals(true to ground, platform.calls.last())
        }

    @Test
    fun `B-544 DesktopKitPlatform PopupSystemBars is a no-op`() =
        runDesktopComposeUiTest(400, 800) {
            setContent {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                ) {
                    KitPageOverlay(visible = true, title = "Overlay", onDismissRequest = {}) {
                        KitText("Desktop overlay")
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Desktop overlay").assertExists()
        }
}
