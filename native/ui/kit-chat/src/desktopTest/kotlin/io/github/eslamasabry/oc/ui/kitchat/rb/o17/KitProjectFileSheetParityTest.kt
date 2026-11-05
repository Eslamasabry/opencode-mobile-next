package io.github.eslamasabry.oc.ui.kitchat.rb.o17

import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kitchat.files.KitProjectFileSheetSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/chat/kit_project_file_sheet.dart. */
class KitProjectFileSheetParityTest {
    @Test fun the_sheet_is_full_height() = assertEquals(KitSheetHeight.Full, KitProjectFileSheetSpec.height)

    @Test fun the_key_and_the_title() {
        assertEquals("project-file-sheet", KitProjectFileSheetSpec.SHEET_KEY)
        assertEquals("mentionSheetTitle", KitProjectFileSheetSpec.TITLE_KEY)
    }

    @Test fun a_folder_ends_in_a_slash_and_a_file_does_not() {
        assertTrue(KitProjectFileSheetSpec.isFolder("lib/"))
        assertFalse(KitProjectFileSheetSpec.isFolder("lib/a.dart"))
    }
}
