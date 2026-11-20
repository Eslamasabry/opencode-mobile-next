package io.github.eslamasabry.oc.android.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Why a widget setup is refused: each refusal has its own name, so the log says which check said no. */
class WidgetPanelSaveTest {
    private val listed = listOf("api", "web")

    private fun prefs(scope: String, project: String = "", projects: List<String> = listed) =
        WidgetPanelPreferences(project = project, projects = projects, scope = scope)

    @Test
    fun `B-WIDGET-302 the same scope and a listed project is not refused`() {
        assertNull(WidgetPanels.refusal(prefs("s1", "api"), prefs("s1")))
    }

    @Test
    fun `B-WIDGET-303 a changed scope token is refused as ScopeChanged`() {
        assertEquals(WidgetPanelSave.ScopeChanged, WidgetPanels.refusal(prefs("s1", "api"), prefs("s2")))
    }

    @Test
    fun `B-WIDGET-304 a project the widget no longer lists is refused, All projects never is`() {
        assertEquals(WidgetPanelSave.ProjectGone, WidgetPanels.refusal(prefs("s", "gone"), prefs("s")))
        assertNull(WidgetPanels.refusal(prefs("s", ""), prefs("s", projects = emptyList())))
    }

    @Test
    fun `B-WIDGET-305 before the app published anything both scopes are blank and agree`() {
        assertNull(WidgetPanels.refusal(prefs(""), prefs("")))
    }
}
