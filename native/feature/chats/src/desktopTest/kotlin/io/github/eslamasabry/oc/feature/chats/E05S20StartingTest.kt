package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.ui.OpeningStatus
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** E05-S20: while the app boots its own server, home says it is starting and offers no failure's ways out. */
@OptIn(ExperimentalTestApi::class)
class E05S20StartingTest {
    @Test
    fun `E05-S20 a starting phone server reads Starting this phone, never isn't answering`() = runComposeUiTest {
        // The connection already failed once (the server was still booting); the phase decides the words.
        val opening = ChatsOpeningUi(ChatsOpeningPhase.Starting, "This phone", failure = Failure.Network())
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                Column(Modifier.width(412.dp)) { OpeningStatus(opening, RecordingChatsActions()) }
            }
        }
        onNodeWithText("Starting this phone…", useUnmergedTree = true).assertExists()
        onNodeWithText("Your conversations show here once it answers.", useUnmergedTree = true).assertExists()
        for (gone in listOf("This phone isn’t answering", "Try again", "Change server", "Copy details")) {
            assertEquals(0, onAllNodesWithText(gone, useUnmergedTree = true).fetchSemanticsNodes().size, gone)
        }
    }
}
