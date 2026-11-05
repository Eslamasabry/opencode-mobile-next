package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chat.notes.*
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NoteScreenTest {
    private class Actions : SessionNoteActions {
        var removed = 0
        override fun openNote() {}
        override fun closeNote() {}
        override fun discardNote() {}
        override fun keepEditing() {}
        override fun changeNote(value: String) {}
        override fun saveNote() {}
        override fun removeNote() { removed++ }
        override fun reloadNote() {}
    }
    @Test fun unchangedSavedNoteHasDeleteButNoSave() = runComposeUiTest {
        val actions = Actions()
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { SessionNoteScreen(SessionNoteUi(visible = true, ready = true, draft = "Run the checks", saved = "Run the checks"), actions) } }
        onNodeWithTag("kit-page-overlay").assertExists()
        onNodeWithText("Save note").assertDoesNotExist()
        onNodeWithText("Delete saved note").performClick()
        assertEquals(1, actions.removed)
    }
    @Test fun hiddenEditorDoesNotLeaveAModalInTheTree() = runComposeUiTest {
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { SessionNoteScreen(SessionNoteUi(), Actions()) } }
        onNodeWithTag("kit-page-overlay").assertDoesNotExist()
    }
}
