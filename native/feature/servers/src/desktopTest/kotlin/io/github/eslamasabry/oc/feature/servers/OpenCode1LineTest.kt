package io.github.eslamasabry.oc.feature.servers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.IncompatibleKind
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.servers.presentation.ServerIssue
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.feature.servers.presentation.serverRunsOpenCode1
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** S23-3: a saved OpenCode 1 server gets one plain line on connect, never the generic version text or a raw error. */
@OptIn(ExperimentalTestApi::class)
class OpenCode1LineTest {
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

    private val guides = mutableListOf<ProfileId>()

    private val actions =
        object : ServersActions {
            override fun openUpdateGuide(id: ProfileId) {
                guides += id
            }

            override fun back() {}

            override fun allowCleartext(allowed: Boolean, address: String) {}

            override fun save(name: String, address: String, username: String, password: String) {}

            override fun connect(id: ProfileId) {}

            override fun create(id: ProfileId) {}

            override fun remove(id: ProfileId) {}
        }

    @Composable
    private fun Screen(failure: Failure, locale: String, openCode1: ProfileId? = null) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = locale,
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp)) {
                    ServersScreen(ServersUi(issue = ServerIssue.ConnectFailed, failure = failure, openCode1 = openCode1), actions)
                }
            }
        }
    }

    @Test
    fun `S23-OC1-001 only an OpenCode 1 refusal is the OpenCode 1 line`() {
        assertTrue(serverRunsOpenCode1(Failure.Incompatible(IncompatibleKind.LegacyOpenCode1)))
        assertFalse(serverRunsOpenCode1(Failure.Incompatible(IncompatibleKind.UnsupportedServerVersion)))
        assertFalse(serverRunsOpenCode1(Failure.Incompatible(IncompatibleKind.ProtocolMismatch)))
        assertFalse(serverRunsOpenCode1(null))
    }

    @Test
    fun `S23-OC1-002 connecting to an OpenCode 1 server says so in plain words`() = runComposeUiTest {
        setContent { Screen(Failure.Incompatible(IncompatibleKind.LegacyOpenCode1), "en") }
        onNodeWithText("This server runs OpenCode 1. Update it to OpenCode 2 to use it here.").assertExists()
        onNodeWithText("This server runs a version this app cannot work with.").assertDoesNotExist()
    }

    @Test
    fun `S23-OC1-003 the OpenCode 1 line in Arabic`() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        setContent { Screen(Failure.Incompatible(IncompatibleKind.LegacyOpenCode1), "ar") }
        onNodeWithText("يعمل هذا الخادم بـ OpenCode 1. حدّثه إلى OpenCode 2 لتستخدمه هنا.").assertExists()
    }

    @Test
    fun `S23-OC1-004 another unsupported version keeps the general line`() = runComposeUiTest {
        setContent { Screen(Failure.Incompatible(IncompatibleKind.UnsupportedServerVersion), "en") }
        onNodeWithText("This server runs a version this app cannot work with.").assertExists()
    }

    @Test
    fun `B-PROTO-095 the OpenCode 1 line offers Update this server, which opens the guide for that server`() =
        runComposeUiTest {
            val laptop = ProfileId("laptop")
            setContent { Screen(Failure.Incompatible(IncompatibleKind.LegacyOpenCode1), "en", openCode1 = laptop) }
            onNodeWithText("Update this server").performClick()
            kotlin.test.assertEquals(listOf(laptop), guides)
        }
}
