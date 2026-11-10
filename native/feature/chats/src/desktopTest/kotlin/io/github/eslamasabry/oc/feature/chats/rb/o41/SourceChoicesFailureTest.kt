package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.chats.RecordingChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeSheet
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsHomeSheets
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/** "Connections in this list": a server that does not answer is said on its row; a real failure is one compact notice. */
@OptIn(ExperimentalTestApi::class)
class SourceChoicesFailureTest {
    private val sources =
        persistentListOf(
            ChatsListSourceUi(ProfileId("main"), "This phone", shown = true, main = true, onThisPhone = true, unreachable = false),
            ChatsListSourceUi(ProfileId("termux"), "This device (Termux)", shown = true, main = false, onThisPhone = true, unreachable = true),
        )

    @Test fun `an unreachable server is said on its own row only, with no sheet-level error`() = runComposeUiTest {
        val actions = RecordingChatsActions()
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                KitSheetHost { ChatsHomeSheets(ChatsUi(sheet = ChatsHomeSheet.Sources, home = ChatsHomeExtraState(sources = sources)), actions) }
            }
        }
        onNodeWithText("Isn't answering").assertExists()
        onNodeWithText("Try again").performClick()
        onNodeWithText("Could not change this list’s connections. Try again.").assertDoesNotExist()
        onNodeWithText("Copy details").assertDoesNotExist()
        onNodeWithTag("kit-notice-error").assertDoesNotExist()
    }

    @Test fun `a failure to save is one compact notice with its details folded and one copy action`() = runComposeUiTest {
        val failure = Failure.StorageRefused(Redactor().details("OpenGateway · Storage · Refused"))
        setContent {
            KitTheme(platform = DesktopKitPlatform()) {
                KitSheetHost {
                    ChatsHomeSheets(
                        ChatsUi(sheet = ChatsHomeSheet.Sources, home = ChatsHomeExtraState(sources = sources, failure = failure)),
                        RecordingChatsActions(),
                    )
                }
            }
        }
        onNodeWithText("Could not change this list’s connections. Try again.").assertExists()
        onNodeWithTag("kit-notice-error").assertExists()
        // The technical line stays folded, and there is no copy button on the notice itself.
        onNodeWithText("OpenGateway · Storage · Refused").assertDoesNotExist()
        onAllNodesWithText("Copy details").assertCountEquals(0)
        onNodeWithTag("kit-notice-copy-details").assertDoesNotExist()
        onNodeWithTag("kit-details-toggle").performSemanticsAction(SemanticsActions.Expand)
        waitForIdle()
        onNodeWithText("OpenGateway · Storage · Refused").assertExists()
        onAllNodesWithText("Copy details").assertCountEquals(1)
    }
}
