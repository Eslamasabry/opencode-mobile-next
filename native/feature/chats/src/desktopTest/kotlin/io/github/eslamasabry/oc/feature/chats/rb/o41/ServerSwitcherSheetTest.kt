package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.chats.RecordingChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeSheet
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.ServerSwitcherUi
import io.github.eslamasabry.oc.feature.chats.presentation.SwitcherServerState
import io.github.eslamasabry.oc.feature.chats.presentation.SwitcherServerUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsHomeSheets
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** F: lib/ui/widgets/server_switcher_sheet.dart:73-95,353-410: the switcher the home's server pill opens. */
@OptIn(ExperimentalTestApi::class)
class ServerSwitcherSheetTest {
    private val servers =
        ServerSwitcherUi(
            persistentListOf(
                SwitcherServerUi(ProfileId("main"), "Home PC", current = true, onThisPhone = false, SwitcherServerState.Connected, working = 2),
                SwitcherServerUi(ProfileId("work"), "Work laptop", current = false, onThisPhone = false, SwitcherServerState.Saved),
                SwitcherServerUi(ProfileId("down"), "Studio", current = false, onThisPhone = false, SwitcherServerState.NotAnswering),
            )
        )

    @Test fun `PARITY-B-235 the sheet lists every server with its state, a tap switches and Add a server adds`() = runComposeUiTest {
        val actions = RecordingChatsActions()
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                KitSheetHost { ChatsHomeSheets(ChatsUi(sheet = ChatsHomeSheet.Servers, switcher = servers), actions) }
            }
        }
        onNodeWithText("Servers").assertExists()
        onNodeWithText("Connected · 2 working").assertExists()
        onNodeWithText("On another computer").assertExists()
        onNodeWithText("Isn't answering").assertExists()
        onNodeWithText("Work laptop").performClick()
        onNodeWithText("Try again").performClick()
        onNodeWithText("Add a server").performClick()
        assertEquals(listOf("switch work", "retry down", "addServer"), actions.calls)
    }

    @Test fun `PARITY-B-235 while one server opens the others wait and it says Connecting`() = runComposeUiTest {
        val actions = RecordingChatsActions()
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                KitSheetHost {
                    ChatsHomeSheets(
                        ChatsUi(sheet = ChatsHomeSheet.Servers, switcher = servers.copy(switching = ProfileId("work"))),
                        actions,
                    )
                }
            }
        }
        onNodeWithText("Connecting").assertExists()
        onNodeWithText("Studio").performClick()
        assertEquals(emptyList(), actions.calls)
    }
}
