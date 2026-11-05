package io.github.eslamasabry.oc.feature.desktopupdates.rb.o51

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.desktopupdates.presentation.*
import io.github.eslamasabry.oc.feature.desktopupdates.ui.DesktopUpdatesScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopReleaseConfirmTest {
    @Test
    fun `B-NAV-068 release destination asks inside the screen host before confirmation`() =
        runComposeUiTest {
            val state =
                mutableStateOf(
                    DesktopUpdatesUi(
                        "2.1.1",
                        phase = DesktopCheckPhase.Available,
                        releaseTag = "2.1.2",
                        releasePage = "https://github.com/example/app/releases/tag/2.1.2",
                    )
                )
            val calls = mutableListOf<String>()
            val actions =
                object : DesktopUpdatesActions {
                    override fun onCheck() {
                        calls += "check"
                    }

                    override fun onOpenRelease() {
                        calls += "ask"
                        state.value = state.value.copy(askingToOpen = true)
                    }

                    override fun onConfirmOpen() {
                        calls += "confirm"
                        state.value = state.value.copy(askingToOpen = false)
                    }

                    override fun onCancelOpen() {
                        calls += "cancel"
                        state.value = state.value.copy(askingToOpen = false)
                    }

                    override fun onClose() {
                        calls += "close"
                    }
                }
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    DesktopUpdatesScreen(state.value, actions)
                }
            }
            assertEquals(emptyList(), calls)
            onNodeWithTag("desktop-updates-open-release").performClick()
            assertEquals(listOf("ask"), calls)
            onNodeWithText("Open the release on GitHub?", useUnmergedTree = true).assertExists()
            onNode(
                    hasText("Open on GitHub") and
                        (hasAnyAncestor(hasTestTag("kit-dialog")) or
                            hasAnyAncestor(hasTestTag("kit-sheet")))
                )
                .performClick()
            assertEquals(listOf("ask", "confirm"), calls)
            onNodeWithText("Open the release on GitHub?", useUnmergedTree = true)
                .assertDoesNotExist()
        }
}
