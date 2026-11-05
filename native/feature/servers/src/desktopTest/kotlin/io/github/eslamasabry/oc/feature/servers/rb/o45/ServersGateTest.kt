@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.feature.servers.rb.o45

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.feature.servers.ui.ServersScreen
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.collections.immutable.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ServersGateTest {
    private class Actions : ServersActions {
        var opened: ProfileId? = null
        var removed: ProfileId? = null
        override fun back() {}
        override fun allowCleartext(allowed: Boolean, address: String) {}
        override fun save(name: String, address: String, username: String, password: String) {}
        override fun connect(id: ProfileId) {}
        override fun create(id: ProfileId) {}
        override fun remove(id: ProfileId) { removed = id }
        override fun settings(id: ProfileId) { opened = id }
    }
    private val servers = persistentListOf(
        SavedServer(ProfileId("laptop"), "Laptop", "https://laptop.example", false),
        SavedServer(ProfileId("studio"), "Studio Mac", "https://studio.example", false),
        SavedServer(ProfileId("codex"), "Codex box", "wss://codex.example", false),
    )
    private val state = ServersUi(servers = servers, rows = ServerRowsState(facts = servers.associate {
        it.id to ServerRowFacts(if (it.id.raw == "codex") BackendKind.Codex else BackendKind.OpenCode2, null, "", false, 0, 0, 0)
    }.toPersistentMap()))
    @Test fun saved_servers() {
        FlutterGoldenGate.check("servers_servers_loaded", frame = FlutterGoldenGate.Frame.Screen) { ServersScreen(state, Actions()) }
    }
    @Test fun saved_servers_wide() {
        FlutterGoldenGate.check("servers_servers_loaded_1280x800", frame = FlutterGoldenGate.Frame.Screen) { ServersScreen(state, Actions()) }
    }
    @Test fun `server tap opens its settings while removal stays a named menu action`() = runComposeUiTest {
        val actions = Actions()
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) { ServersScreen(state, actions) } }
        onNodeWithText("Laptop").performClick()
        assertEquals(ProfileId("laptop"), actions.opened)
        assertEquals(null, actions.removed)
        onNodeWithContentDescription("Laptop").performClick()
        onNodeWithText("Remove").performClick()
        assertEquals(ProfileId("laptop"), actions.removed)
    }
}
