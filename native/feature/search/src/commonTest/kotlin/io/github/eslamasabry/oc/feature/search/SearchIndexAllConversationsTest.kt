package io.github.eslamasabry.oc.feature.search

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.feature.search.presentation.SearchGoToFacts
import io.github.eslamasabry.oc.feature.search.presentation.SearchIndex
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf

/** The "All conversations" place follows `globalSessionSearch` and opens the All conversations page. */
class SearchIndexAllConversationsTest {
    private val profile = ProfileId("p1")

    private fun destination(across: Boolean) =
        SearchIndex.entries(
                profile,
                SearchGoToFacts(
                    fileBrowsing = false,
                    terminal = false,
                    projectManagement = false,
                    recent = persistentListOf(),
                    capabilities = ServerCapabilities(globalSessionSearch = across),
                ),
            )
            .firstOrNull { it.id == "all-conversations" }
            ?.destination

    @Test
    fun `the place is offered when the server lists across projects, and opens the All conversations page`() {
        assertEquals(ChatsRoute.All(), destination(true))
        assertNull(destination(false))
    }
}
