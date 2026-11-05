package io.github.eslamasabry.oc.ui.kit.rb.o44

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.guide.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitGuideTest {
    @Test
    fun `B-KIT-017 guide opens the named group with its current answer`() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent {
            KitTheme(platform = FakeKitPlatform()) {
                KitGuideRail(
                    listOf(KitGuideItem("colours", "Colours", "Dark, Graphite", next = true)),
                    opened::add,
                )
            }
        }
        onNodeWithText("Dark, Graphite").assertExists()
        onNodeWithTag("guide-item-colours").performClick()
        assertEquals(listOf("colours"), opened)
    }

    @Test
    fun `B-KIT-017 next bar preserves its count and invokes one action`() = runComposeUiTest {
        var next = 0
        setContent {
            KitTheme(platform = FakeKitPlatform()) {
                KitGuideNextBar("2 of 10", "Next: Accent", { next++ })
            }
        }
        onNodeWithText("2 of 10").assertExists()
        onNodeWithText("Next: Accent").performClick()
        assertEquals(1, next)
    }
}
