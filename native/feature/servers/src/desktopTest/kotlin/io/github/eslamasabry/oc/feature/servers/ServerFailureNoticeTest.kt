package io.github.eslamasabry.oc.feature.servers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.servers.presentation.ServerIssue
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
import kotlin.test.assertTrue

/** Real ServersScreen regressions; no test-only rendering or message selection. */
@OptIn(ExperimentalTestApi::class)
class ServerFailureNoticeTest {
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

    @Composable
    private fun Screen(failure: Failure, locale: String = "en") {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = locale,
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp)) {
                    ServersScreen(
                        ServersUi(issue = ServerIssue.ConnectFailed, failure = failure),
                        actions,
                    )
                }
            }
        }
    }

    @Test
    fun `B-PROTO-095 real Servers screen identifies unreadable replies without blaming the network`() =
        runComposeUiTest {
            val diagnostic = "OpenGateway · UnreadableReply"
            setContent { Screen(Failure.UnreadableReply(testDetails(diagnostic))) }
            onNodeWithText("The server replied, but this app could not read its response.")
                .assertExists()
            onNodeWithText(
                    "Could not connect. Check the server address and network, then try again."
                )
                .assertDoesNotExist()
            onNodeWithTag("kit-details-toggle").assertExists()
            onNodeWithText(diagnostic).assertDoesNotExist()
        }

    @Test
    fun `B-SEC-014 real Servers screen keeps Arabic Details collapsed then reveals only safe stage text`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            val diagnostic = "OpenGateway · Network · Dns"
            setContent { Screen(Failure.Network(NetworkWhy.Dns, testDetails(diagnostic)), "ar") }
            onNodeWithText("التفاصيل").assertExists()
            onNodeWithTag("kit-details-body").assertDoesNotExist()
            onNodeWithText(diagnostic).assertDoesNotExist()
            onNodeWithTag("kit-details-toggle").performClick()
            onNodeWithText(diagnostic).assertExists()
            onNodeWithTag("kit-details-copy-all").assertExists()
            onNodeWithText("نسخ التفاصيل").assertExists()
        }

    @Test
    fun `B-SEC-130 real Servers screen keeps HTTP status out of body and inside Details`() =
        runComposeUiTest {
            val diagnostic = "LoadConversations · Server · HTTP 502"
            setContent { Screen(Failure.Server(502, details = testDetails(diagnostic))) }
            onNodeWithText("The server had a problem. Try again later.").assertExists()
            assertTrue(onAllNodesWithText("502", substring = true).fetchSemanticsNodes().isEmpty())
            onNodeWithTag("kit-details-toggle").performClick()
            onNodeWithText(diagnostic).assertExists()
        }

    @Test
    fun `B-PROTO-095 unknown failures never invent a network cause`() = runComposeUiTest {
        setContent { Screen(Failure.Unknown(testDetails("OpenGateway · Unknown"))) }
        onNodeWithText("The app could not complete this connection. Try again.").assertExists()
        onNodeWithText("Could not connect. Check the server address and network, then try again.")
            .assertDoesNotExist()
    }
}

@OptIn(RedactorApi::class) private fun testDetails(text: String): Redacted = Redacted(text)
