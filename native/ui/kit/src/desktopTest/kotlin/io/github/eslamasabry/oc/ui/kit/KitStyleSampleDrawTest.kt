package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.choice.KitStyleOptionCard
import kotlin.test.Test

/**
 * N-29: the Style chooser drew its Glass sample through a backdrop recorded in the node's own draw scope, so the
 * sample's `toPx()` recursed until the stack overflowed. Every style's sample card is composed and actually drawn
 * (captured to an image) under every active style, light and dark, and the chooser still selects.
 */
@OptIn(ExperimentalTestApi::class)
class KitStyleSampleDrawTest {
    @Test
    fun `N-29 every style sample draws under every active style without recursing`() = runComposeUiTest {
        var active by mutableStateOf(Style.Solid)
        var mode by mutableStateOf(AppearanceMode.Dark)
        var chosen by mutableStateOf(Style.Solid)
        setContent {
            KitTheme(
                KitAppearance(mode = mode, motion = MotionLevel.Off, style = active),
                FakeKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Column {
                    Style.entries.forEach { style ->
                        KitStyleOptionCard(style, style.name, chosen == style, { chosen = style })
                    }
                }
            }
        }
        for (m in listOf(AppearanceMode.Dark, AppearanceMode.Light)) {
            for (s in Style.entries) {
                runOnIdle {
                    mode = m
                    active = s
                }
                // Forces a real draw pass: the recursion only happens while the backdrop is recorded.
                onRoot().captureToImage()
            }
        }
        onNodeWithContentDescription(Style.Glass.name).performClick().assertIsSelected()
        onRoot().captureToImage()
    }
}
