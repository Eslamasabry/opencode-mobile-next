package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.feature.settings.presentation.OtherConversationsMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-NAV-217, the app's half: the edge light stays hidden while a list stands beside the conversation (big screens: the
 * conversation list is in view), and shows as before when the window shows the conversation alone. The shell's half
 * (`ShellNavigator.listShown`) is ShellListShownTest.
 */
class EdgeLightBesideListTest {
    @Test
    fun `B-NAV-217 edge light hidden while the list shows`() {
        for (mode in OtherConversationsMode.entries) {
            assertFalse(EdgeLightRules.lightShows(mode, listShown = true), "$mode: no light beside the list")
        }
        assertTrue(EdgeLightRules.lightShows(OtherConversationsMode.EdgeLight, listShown = false), "the conversation alone")
        // The conversation's mount asks the rule with the shell's signal (not the light choice alone).
        val path = "io/github/eslamasabry/opencode_mobile/graph/AppChatEdgeDock.class"
        val loader = AppChatEdgeDock::class.java.classLoader!!
        val bytes = loader.getResourceAsStream(path)!!.readBytes().toString(Charsets.ISO_8859_1)
        assertTrue("getListShown" in bytes && "lightShows" in bytes, "AppChatEdgeDock reads the shell's listShown")
    }
}
