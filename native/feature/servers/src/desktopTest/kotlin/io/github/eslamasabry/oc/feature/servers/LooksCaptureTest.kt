package io.github.eslamasabry.oc.feature.servers

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRowFacts
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRowsState
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.feature.servers.ui.ServersScreen
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LooksCaptureTest {
    private val actions = object : ServersActions {
        override fun back() {}
        override fun allowCleartext(allowed: Boolean, address: String) {}
        override fun save(name: String, address: String, username: String, password: String) {}
        override fun connect(id: ProfileId) {}
        override fun create(id: ProfileId) {}
        override fun remove(id: ProfileId) {}
    }

    @Test
    fun servers() {
        val a = SavedServer(ProfileId("home"), "Home laptop", "https://laptop.tail1234.ts.net:4096", connected = true)
        val b = SavedServer(ProfileId("vps"), "Build VPS", "https://build.example.net:4096", connected = false)
        val c = SavedServer(ProfileId("phone"), "This phone", "http://127.0.0.1:4097", connected = false)
        val ui = ServersUi(
            servers = persistentListOf(a, b, c),
            rows = ServerRowsState(
                persistentMapOf(
                    a.id to ServerRowFacts(BackendKind.entries.first(), "2.1.1", "/home/dev/projects", false, 1, 2, 0),
                    b.id to ServerRowFacts(BackendKind.entries.first(), "2.1.1", "/srv/build", false, null, null, 0),
                    c.id to ServerRowFacts(BackendKind.entries.first(), "2.0.28", "/root/work", false, 0, 0, 0),
                ),
            ),
        )
        Looks.shoot("native-06-servers") {
            CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) { ServersScreen(ui, actions, onThisPhone = {}) }
        }
    }
}
