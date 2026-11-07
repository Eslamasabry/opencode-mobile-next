package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningRow
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.feature.chats.ui.OpeningRow
import io.github.eslamasabry.oc.feature.chats.ui.OpeningStatus
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** E20-S08: the home's "Connecting" words start on the edge of the saved titles listed under them. */
@OptIn(ExperimentalTestApi::class)
class E20S08HomeOpeningTest {
    @Test
    fun `E20-S08 Connecting starts on the list's edge instead of centring over it`() = runComposeUiTest {
        val row = ChatsOpeningRow("a", RowTitle.Text("Fix checkout"))
        val opening = ChatsOpeningUi(ChatsOpeningPhase.Connecting, "Laptop")
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    Column(Modifier.width(412.dp)) {
                        OpeningStatus(opening, RecordingChatsActions())
                        OpeningRow(row)
                    }
                }
            }
        }
        val connecting = onNodeWithText("Connecting to Laptop", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = onNodeWithText("Fix checkout", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // A centred state view put the words near the middle (x ≈ 130); now they start on the gutter side, left of
        // the middle, not centred over a list that starts at the start.
        assertTrue(connecting.left < 80f, "Connecting starts at ${connecting.left}, the list's title at ${title.left}")
        assertTrue(abs(connecting.left - title.left) < 60f, "one reading edge: $connecting vs $title")
    }
}
