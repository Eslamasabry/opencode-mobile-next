package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An empty list shows only its empty state: a group with no rows draws no panel, label or note. */
@OptIn(ExperimentalTestApi::class)
class KitRowGroupEmptyTest {
    @Test
    fun `a row group with no rows takes no room`() =
        runDesktopComposeUiTest(412, 600) {
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    androidx.compose.foundation.layout.Column {
                        KitRowGroup(label = "Servers", note = "Note") { listOf<String>().forEach { KitRow(it) } }
                    }
                }
            }
            waitForIdle()
            onAllNodesWithTag("kit-row-group").fetchSemanticsNodes().forEach {
                assertEquals(0, it.size.height, "an empty group drew ${it.size}")
            }
        }

    @Test
    fun `a row group with rows still draws`() =
        runDesktopComposeUiTest(412, 600) {
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    androidx.compose.foundation.layout.Column { KitRowGroup(label = "Servers") { KitRow("one") } }
                }
            }
            waitForIdle()
            val nodes = onAllNodesWithTag("kit-row-group").fetchSemanticsNodes()
            assertTrue(nodes.isNotEmpty() && nodes.all { it.size.height > 0 })
        }
}
