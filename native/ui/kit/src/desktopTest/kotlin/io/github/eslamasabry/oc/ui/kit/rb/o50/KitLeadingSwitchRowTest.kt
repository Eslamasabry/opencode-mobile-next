package io.github.eslamasabry.oc.ui.kit.rb.o50

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.row.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitLeadingSwitchRowTest {
    @Test
    fun `B-KIT-051 leading provider switch toggles once`() = runComposeUiTest {
        val calls = mutableListOf<Boolean>()
        setContent {
            KitTheme(platform = FakeKitPlatform()) {
                KitLeadingSwitchRow(
                    "Claude",
                    KitRowLeading.Logo("claude", "Claude"),
                    true,
                    { calls += it },
                )
            }
        }
        onNodeWithText("Claude").performClick()
        assertEquals(listOf(false), calls)
    }
}
