package io.github.eslamasabry.oc.feature.servers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.engine.lifecycle.PhoneOwnServers
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.feature.servers.ui.ServersScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/** The real Servers screen marks only the phone's own servers as This phone (phone_own_server_icon_test.dart). */
@OptIn(ExperimentalTestApi::class)
class PhoneOwnServerMarkTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private val actions =
        object : ServersActions {
            override fun back() {}

            override fun allowCleartext(allowed: Boolean, address: String) {}

            override fun save(name: String, address: String, username: String, password: String) {}

            override fun connect(id: ProfileId) {}

            override fun create(id: ProfileId) {}

            override fun remove(id: ProfileId) {}
        }

    private val markedThisPhone = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "This phone")

    private fun server(name: String, address: String) = SavedServer(ProfileId(name), name, address, connected = false)

    @Test
    fun `B-NAV-040 the Servers screen marks only the phone's own server as This phone`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    Box(Modifier.width(412.dp)) {
                        ServersScreen(
                            ServersUi(
                                servers =
                                    persistentListOf(
                                        // The app's own server has its own port (isolated from Termux's 4097).
                                        server("in-app", PhoneOwnServers.IN_APP_URL),
                                        server("termux", "http://127.0.0.1:4097"),
                                        server("forwarded", "http://127.0.0.1:4123"),
                                        server("local", "http://localhost:4096"),
                                        server("lan", "https://laptop.example"),
                                    ),
                            ),
                            actions,
                            onThisPhone = {},
                        )
                    }
                }
            }
        }
        // The phone glyph is decorative; the row of the phone's own server is the one that says "This phone".
        onAllNodes(markedThisPhone).assertCountEquals(1)
    }

    @Test
    fun `B-NAV-040 without a built-in Linux no saved server is marked as This phone`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    Box(Modifier.width(412.dp)) {
                        ServersScreen(
                            ServersUi(servers = persistentListOf(server("in-app", PhoneOwnServers.IN_APP_URL))),
                            actions,
                        )
                    }
                }
            }
        }
        onAllNodes(markedThisPhone).assertCountEquals(0)
    }
}
