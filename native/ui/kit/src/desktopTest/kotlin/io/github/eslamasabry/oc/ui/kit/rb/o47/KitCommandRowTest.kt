package io.github.eslamasabry.oc.ui.kit.rb.o47

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitCommandRow
import io.github.eslamasabry.oc.ui.kit.state.KitInlineState
import io.github.eslamasabry.oc.ui.kit.state.KitInlineStateLayout
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitCommandRowTest {
    @Test
    fun `B-KIT-051 command below stays with the row and trailing act does not activate the row`() =
        runComposeUiTest {
            val calls = mutableListOf<String>()
            setContent {
                KitTheme(platform = FakeKitPlatform()) {
                    KitCommandRow(
                        "Preview",
                        "Running command",
                        KitIcons.processor,
                        { calls += "logs" },
                        below = { KitText("http://localhost:5173") },
                        trailing = {
                            KitIconButton(KitIcons.stop, "Stop Preview", { calls += "stop" })
                        },
                    )
                }
            }
            onNodeWithText("http://localhost:5173").assertExists()
            onNodeWithContentDescription("Stop Preview").performClick()
            assertEquals(listOf("stop"), calls)
            onNodeWithText("Preview").performClick()
            assertEquals(listOf("stop", "logs"), calls)
        }

    @Test
    fun `B-KIT-034 inline state has one visible way forward`() = runComposeUiTest {
        var added = 0
        setContent {
            KitTheme(platform = FakeKitPlatform()) {
                KitInlineState(
                    "No dev commands yet",
                    KitIcons.processor,
                    layout = KitInlineStateLayout.List,
                    body = "Keep commands together",
                    primary = KitAction("Register service", { added++ }),
                )
            }
        }
        onNodeWithText("Keep commands together").assertExists()
        onNodeWithText("Register service").performClick()
        assertEquals(1, added)
    }
}
