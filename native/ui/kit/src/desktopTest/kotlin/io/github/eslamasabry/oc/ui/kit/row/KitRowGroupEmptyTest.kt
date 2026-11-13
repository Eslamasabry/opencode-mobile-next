package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An empty list shows only its empty state: a group with no rows draws no panel, label or note. */
@OptIn(ExperimentalTestApi::class)
class KitRowGroupEmptyTest {
    @Test
    fun `a row group with no rows takes no room in its parent`() =
        runDesktopComposeUiTest(412, 600) {
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    Column(Modifier.testTag("host")) {
                        KitRowGroup(label = "Servers", note = "Note") { listOf<String>().forEach { KitRow(it) } }
                    }
                }
            }
            waitForIdle()
            assertEquals(0.dp, onNodeWithTag("host").getBoundsInRoot().height)
        }

    @Test
    fun `a row group with rows still draws`() =
        runDesktopComposeUiTest(412, 600) {
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    Column(Modifier.testTag("host")) { KitRowGroup(label = "Servers") { KitRow("one") } }
                }
            }
            waitForIdle()
            assertTrue(onNodeWithTag("host").getBoundsInRoot().height > 0.dp)
        }
}
