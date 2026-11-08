package io.github.eslamasabry.oc.feature.search

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.feature.search.presentation.SearchGoToFacts
import io.github.eslamasabry.oc.feature.search.presentation.SearchIndex
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** Go to › Project files reaches the project's files even with no conversation open (Flutter's Files tab). */
class SearchIndexFilesTest {
    private val profile = ProfileId("p1")

    private fun facts(ref: ConversationRef?, directory: String?) =
        SearchGoToFacts(
            fileBrowsing = true,
            terminal = false,
            projectManagement = false,
            recent = persistentListOf(),
            capabilities = ServerCapabilities(fileBrowsing = true, textSearch = true),
            projectDirectory = directory,
            projectRef = ref,
        )

    private fun destination(id: String, facts: SearchGoToFacts) =
        SearchIndex.entries(profile, facts).firstOrNull { it.id == id }?.destination

    @Test
    fun `B-NAV-058 Project files opens the project's own files when no conversation is open`() {
        val none = facts(ref = null, directory = "/work/app")
        assertEquals(FilesRoute.ProjectFolder(profile, "/work/app"), destination("project-files", none))
        assertEquals(FilesRoute.ProjectFolder(profile, "/work/app"), destination("project-search", none))
    }

    @Test
    fun `B-NAV-058 with a conversation open Project files stays that conversation's files, and with neither it is absent`() {
        val ref = ConversationRef(profile, SessionId("s1"))
        assertEquals(FilesRoute.Folder(ref), destination("project-files", facts(ref, "/work/app")))
        assertEquals(null, destination("project-files", facts(ref = null, directory = null)))
    }
}
