package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsTitle
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsUi
import io.github.eslamasabry.oc.ui.routes.ServerSettingsPage
import kotlin.test.Test
import kotlin.test.assertEquals

/** P-114: server settings never share This phone's title. */
class ServerSettingsTitleTest {
    private fun facts(name: String, onPhone: Boolean) =
        ServerSettingsFacts(
            name = name,
            address = "http://127.0.0.1:4096",
            connected = true,
            hasSecret = true,
            lastVersion = null,
            canCheck = true,
            capabilities = null,
            managedOnPhone = onPhone,
        )

    @Test
    fun `P-114 the phone's own server is titled Server on this phone, not This phone`() {
        val ui = ServerSettingsUi(ServerSettingsPage.Home, facts = facts("This phone", onPhone = true))
        assertEquals(ServerSettingsTitle.PhoneServer, ui.homeTitle)
    }

    @Test
    fun `P-114 any other server keeps its saved name, and loading says Server settings`() {
        assertEquals(
            ServerSettingsTitle.Named("laptop"),
            ServerSettingsUi(ServerSettingsPage.Home, facts = facts("laptop", onPhone = false)).homeTitle,
        )
        assertEquals(ServerSettingsTitle.Generic, ServerSettingsUi(ServerSettingsPage.Home).homeTitle)
    }
}
