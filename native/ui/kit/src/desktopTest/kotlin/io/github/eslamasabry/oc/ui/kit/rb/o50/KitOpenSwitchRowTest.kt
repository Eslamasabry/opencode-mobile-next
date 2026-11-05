package io.github.eslamasabry.oc.ui.kit.rb.o50

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.row.KitOpenSwitchRow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitOpenSwitchRowTest {
    @Test
    fun `B-KIT-027 opening a split switch row never toggles it`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent {
            KitTheme(platform = FakeKitPlatform()) {
                KitOpenSwitchRow(
                    "Daily report",
                    true,
                    "Edit schedule",
                    { calls += "edit" },
                    { calls += "enabled:$it" },
                    supporting = "Every day at 09:00",
                )
            }
        }
        onNodeWithText("Daily report").performClick()
        assertEquals(listOf("edit"), calls)
        onNode(isToggleable()).assertIsOn().performClick()
        assertEquals(listOf("edit", "enabled:false"), calls)
    }
}
