package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationProject
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** E20-S08: the New conversation project sheet shows each folder once. */
class NewConversationProjectsTest {
    private fun ui(directory: String?, vararg projects: NewConversationProject) =
        NewConversationUi(
            directory = directory,
            setup = NewConversationSetup(directory, projects.toList(), emptyList(), emptyList()),
        )

    @Test
    fun `E20-S08 a folder the server lists twice is one row in the project sheet`() {
        val state =
            ui(
                "/root/projects/shopfront",
                NewConversationProject("/root/projects/shopfront", "shopfront", separateCopy = false),
                NewConversationProject("/root/projects/api", "api", separateCopy = false),
                NewConversationProject("/root/projects/shopfront/", "shopfront", separateCopy = true),
            )
        assertEquals(listOf("/root/projects/shopfront", "/root/projects/api"), state.projectChoices.map { it.directory })
        // Any listing that offers a separate copy keeps offering it on the one row.
        assertTrue(state.projectChoices.first().separateCopy)
    }

    @Test
    fun `E20-S08 the chosen folder is marked however its path is spelled`() {
        val shop = NewConversationProject("/root/projects/shopfront", "shopfront", separateCopy = false)
        val api = NewConversationProject("/root/projects/api", "api", separateCopy = false)
        val state = ui("/root/projects/shopfront/", shop, api)
        assertTrue(state.isChosen(shop))
        assertFalse(state.isChosen(api))
        assertFalse(ui(null, shop).isChosen(shop))
    }
}
