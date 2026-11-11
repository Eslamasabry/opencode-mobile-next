package io.github.eslamasabry.oc.feature.search

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.search.presentation.SearchGoToFacts
import io.github.eslamasabry.oc.feature.search.presentation.SearchIndex
import io.github.eslamasabry.oc.ui.routes.DemoRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import io.github.eslamasabry.oc.ui.routes.UsageRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** S6: Search reaches the Settings rows and the pages inside them, not only a handful. */
class SearchIndexSettingsTest {
    private val facts =
        SearchGoToFacts(
            fileBrowsing = false,
            terminal = false,
            projectManagement = false,
            recent = persistentListOf(),
        )

    private fun destination(id: String) =
        SearchIndex.entries(ProfileId("p1"), facts).firstOrNull { it.id == id }?.destination

    @Test
    fun `S6 every platform has Privacy, About, Guide, Report a problem, Usage, Add server, Disconnect and the demo`() {
        assertEquals(SettingsRoute.Target("settings-privacy"), destination("settings-privacy"))
        assertEquals(SettingsRoute.Target("settings-about"), destination("settings-about"))
        assertEquals(SettingsRoute.Target("settings-guide"), destination("settings-guide"))
        assertEquals(SettingsRoute.ReportProblem, destination("settings-report-problem"))
        assertEquals(UsageRoute.Hub(), destination("settings-usage"))
        assertEquals(ServersRoute.Edit(), destination("settings-servers-add"))
        assertEquals(ServersRoute.Home, destination("inside-server-disconnect"))
        assertEquals(DemoRoute, destination("settings-demo"))
    }

    @Test
    fun `S6 ids are unique so no row shows twice`() {
        val ids = SearchIndex.entries(ProfileId("p1"), facts).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
