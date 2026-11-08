package io.github.eslamasabry.oc.feature.search

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.feature.search.presentation.SearchGoToFacts
import io.github.eslamasabry.oc.feature.search.presentation.SearchIndex
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** s26: Search › AI Team opens the server's team page when it has a team (Flutter search_index/places.dart:206). */
class SearchIndexTeamTest {
    private val profile = ProfileId("p1")

    private fun facts(hasTeam: Boolean) =
        SearchGoToFacts(
            fileBrowsing = false,
            terminal = false,
            projectManagement = false,
            recent = persistentListOf(),
            capabilities = ServerCapabilities(),
            hasTeam = hasTeam,
        )

    private fun destination(facts: SearchGoToFacts) = SearchIndex.entries(profile, facts).firstOrNull { it.id == "ai-team" }?.destination

    @Test
    fun `B-NAV-090 a server with a team lists AI Team, which opens its team page`() {
        assertEquals(TeamRoute.Home(profile), destination(facts(hasTeam = true)))
    }

    @Test
    fun `B-NAV-090 a server without a team has no AI Team entry`() {
        assertEquals(null, destination(facts(hasTeam = false)))
    }
}
