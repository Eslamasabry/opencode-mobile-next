package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.*
import io.github.eslamasabry.oc.feature.chats.ui.ClaudeImportBody
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class ClaudeImportScreenTest {
    private class Actions : NewConversationActions {
        var imported: ImportableConversation? = null
        var sheet: NewConversationSheet? = null
        override fun back() {}
        override fun addServer() {}
        override fun chooseServer(profile: ProfileId) {}
        override fun draftChanged(text: String) {}
        override fun chooseDirectory(directory: String) {}
        override fun chooseModel(model: ModelRef?) {}
        override fun chooseAgent(agent: String?) {}
        override fun separateCopy(on: Boolean) {}
        override fun sheet(sheet: NewConversationSheet?) { this.sheet = sheet }
        override fun start() {}
        override fun import(conversation: ImportableConversation) { imported = conversation }
        override fun retryLoad() {}
    }

    @Test fun `B11 empty import list shows its state without a selection introduction`() = runComposeUiTest {
        setContent { KitTheme(platform = DesktopKitPlatform()) { Column {
            ClaudeImportBody(NewConversationUi(imports = ImportableConversations(persistentListOf())), Actions())
        } } }
        onNodeWithText("Nothing to import").assertExists()
        onNodeWithText("Pick a conversation", substring = true).assertDoesNotExist()
    }

    @Test fun `B11 load failure retries listing without attempting an import`() = runComposeUiTest {
        val actions = Actions()
        setContent { KitTheme(platform = DesktopKitPlatform()) { Column {
            ClaudeImportBody(NewConversationUi(importFailed = true), actions)
        } } }
        // Flutter claude_import_screen.dart:105 labels it commonRetry, which reads "Try again".
        onNodeWithText("Try again").performClick()
        assertEquals(NewConversationSheet.Import, actions.sheet)
        assertEquals(null, actions.imported)
    }

    @Test fun `B11 import row shows its folder and dispatches the displayed item`() = runComposeUiTest {
        val item = ImportableConversation("claude", "opaque-handle", "/work/shop/", Instant.fromEpochMilliseconds(0), title = "Fix checkout")
        val actions = Actions()
        setContent { KitTheme(platform = DesktopKitPlatform()) { Column {
            ClaudeImportBody(NewConversationUi(directory = "/work/shop/", imports = ImportableConversations(persistentListOf(item)), importsReadAtMs = 60_000), actions)
        } } }
        onNodeWithText("shop · 1 min ago").assertExists()
        onNodeWithText("opaque-handle", substring = true).assertDoesNotExist()
        onNodeWithText("Fix checkout").performClick()
        assertEquals(item, actions.imported)
    }
}
