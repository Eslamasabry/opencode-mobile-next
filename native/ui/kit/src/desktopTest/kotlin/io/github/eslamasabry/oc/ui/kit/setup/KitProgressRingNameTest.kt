@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test

class KitProgressRingNameTest {
    @Test
    fun `B-322 the setup ring keeps its name while its number moves`() =
        runDesktopComposeUiTest(400, 400) {
            var value by mutableStateOf(.14f)
            setContent {
                KitTheme(KitAppearance(), FakeKitPlatform(), "en") {
                    KitProgressRing("Downloading Node.js", "Setup progress", value = value)
                }
            }
            onNode(
                hasContentDescription("Setup progress") and
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "14%"),
            ).assertExists()
            value = .15f
            waitForIdle()
            onNode(
                hasContentDescription("Setup progress") and
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "15%"),
            ).assertExists()
        }

    @Test
    fun `B-322 the setup ring reads measured percent and caption as state`() =
        runDesktopComposeUiTest(400, 400) {
            setContent {
                KitTheme(KitAppearance(), FakeKitPlatform(), "en") {
                    KitProgressRing(
                        "Downloading Node.js",
                        "Setup progress",
                        value = .6f,
                        caption = "18 of 30 MB",
                    )
                }
            }
            onNode(
                hasContentDescription("Setup progress") and
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "60%, 18 of 30 MB"),
            ).assertExists()
        }

    @Test
    fun `B-322 the setup ring reads step count as state`() =
        runDesktopComposeUiTest(400, 400) {
            setContent {
                KitTheme(KitAppearance(), FakeKitPlatform(), "en") {
                    KitProgressRing("Installing Node.js", "Setup progress", done = 2, of = 6)
                }
            }
            onNode(
                hasContentDescription("Setup progress") and
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 of 6"),
            ).assertExists()
        }

    @Test
    fun `B-322 the setup ring reads finished as state`() =
        runDesktopComposeUiTest(400, 400) {
            setContent {
                KitTheme(KitAppearance(), FakeKitPlatform(), "en") {
                    KitProgressRing("All set", "Setup progress", finished = true)
                }
            }
            onNode(
                hasContentDescription("Setup progress") and
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Finished"),
            ).assertExists()
        }
}
