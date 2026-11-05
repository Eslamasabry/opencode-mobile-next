package io.github.eslamasabry.oc.ui.kit.rb.o47

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.nav.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitSessionTabsTest {
    @Test
    fun `B-KIT-050 session pills expose selection and announce an ended session`() =
        runComposeUiTest {
            var opened = ""
            setContent {
                KitTheme(platform = FakeKitPlatform()) {
                    KitSessionTabs(
                        listOf(
                            KitSessionTab("Shell 1", true, { opened = "one" }),
                            KitSessionTab("Shell 2", false, { opened = "two" }, ended = true),
                        ),
                        "Terminal sessions",
                        "ended",
                    )
                }
            }
            onNodeWithContentDescription("Shell 1").assertIsSelected()
            onNodeWithContentDescription("Shell 2, ended").performClick()
            assertEquals("two", opened)
        }
}
