package io.github.eslamasabry.oc.android.widgets

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/** What lets a widget setup save: the server it was opened for, not the connection's scope token. */
class WidgetPanelSaveTest {
    private fun prefs(profile: String, scope: String, project: String = "", projects: List<String> = listOf("api", "web")) =
        WidgetPanelPreferences(project = project, projects = projects, scope = scope, profile = profile)

    @Test
    fun `B-WIDGET-302 a save after a reconnect keeps the chosen projects`() {
        val opened = prefs("p1", scope = "token-before", project = "api")
        val afterReconnect = prefs("p1", scope = "token-after")
        assertNull(WidgetPanels.refusal(opened, afterReconnect))
    }

    @Test
    fun `B-WIDGET-303 a setup opened for another server is refused as changed`() {
        assertEquals(
            WidgetPanelSave.SetupChanged,
            WidgetPanels.refusal(prefs("p1", "s", "api"), prefs("p2", "s")),
        )
    }

    @Test
    fun `B-WIDGET-304 a project the widget no longer lists is refused, All projects never is`() {
        assertEquals(
            WidgetPanelSave.ProjectGone,
            WidgetPanels.refusal(prefs("p1", "s", "gone"), prefs("p1", "s")),
        )
        assertNull(WidgetPanels.refusal(prefs("p1", "s", ""), prefs("p1", "s", projects = emptyList())))
    }

    @Test
    fun `B-WIDGET-305 a setup opened before the app published anything saves once it did`() {
        assertNull(WidgetPanels.refusal(prefs("", ""), prefs("p1", "s")))
    }
}
