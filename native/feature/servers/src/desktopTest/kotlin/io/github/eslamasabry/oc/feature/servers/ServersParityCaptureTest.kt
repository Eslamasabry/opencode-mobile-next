package io.github.eslamasabry.oc.feature.servers

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.feature.servers.presentation.ServerIssue
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.feature.servers.ui.ServersScreen
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * UI parity (P12 UI-SERVERS-SETTINGS): the Servers screen states the Flutter goldens show, in the five parity frames,
 * with the behaviour each must keep. Captures: `./gradlew :feature:servers:desktopTest
 * --tests '*ServersParityCaptureTest*' -Poc.parityOut=<dir>`.
 */
@OptIn(ExperimentalTestApi::class)
class ServersParityCaptureTest {
    private val calls = mutableListOf<String>()
    private val actions =
        object : ServersActions {
            override fun back() { calls += "back" }

            override fun allowCleartext(allowed: Boolean, address: String) {}

            override fun save(name: String, address: String, username: String, password: String) { calls += "save" }

            override fun connect(id: ProfileId) { calls += "connect:${id.raw}" }

            override fun create(id: ProfileId) { calls += "create:${id.raw}" }

            override fun remove(id: ProfileId) { calls += "remove:${id.raw}" }
        }
    private val saved =
        persistentListOf(
            SavedServer(ProfileId("laptop"), "Laptop", "https://laptop.example:4096", connected = true),
            SavedServer(ProfileId("studio"), "Studio Mac", "https://studio.example:4096", connected = false),
            SavedServer(ProfileId("box"), "Codex box", "https://box.example:4096", connected = false),
        )

    @Test
    fun `B-NAV-037 parity the servers list is one row per server with its state and Add server`() =
        ParityCapture.capture(
            "servers-list",
            check = { v ->
                if (v.locale == "en") {
                    onNodeWithText("Laptop").assertExists()
                    onNodeWithText("Connected · https://laptop.example:4096").assertExists()
                    onNodeWithText("Not connected · https://studio.example:4096").assertExists()
                    onNodeWithText("Add server").assertExists()
                    // The form is closed until it is asked for.
                    onNodeWithText("Server address").assertDoesNotExist()
                }
            },
        ) { ServersScreen(ServersUi(servers = saved), actions) }

    @Test
    fun `B-NAV-037 parity a row's tap is its obvious act and Add server opens the form`() =
        ParityCapture.capture(
            "servers-add",
            only = ParityCapture.variants.filter { it.locale == "en" && it.fontScale == 1f && it.width == 412 },
            arrange = {
                onNodeWithText("Laptop").performClick()
                onNodeWithText("Studio Mac").performClick()
                onNodeWithText("Add server").performClick()
                waitForIdle()
            },
            check = {
                assertEquals(listOf("create:laptop", "connect:studio"), calls.take(2))
                onNodeWithText("Server address").performScrollTo().assertExists()
                onNodeWithText("Scan code").assertExists()
            },
        ) { ServersScreen(ServersUi(servers = saved), actions) }

    @Test
    fun `B-NAV-037 parity Back from the add form returns to the list and stays on Servers`() =
        ParityCapture.capture(
            "servers-add-back",
            only = ParityCapture.variants.take(1),
            arrange = {
                onNodeWithText("Add server").performClick()
                waitForIdle()
                onNodeWithContentDescription("Back").performClick()
                waitForIdle()
            },
            check = {
                onNodeWithText("Laptop").assertExists()
                onNodeWithText("Server address").assertDoesNotExist()
                assertEquals(false, "back" in calls)
            },
        ) { ServersScreen(ServersUi(servers = saved), actions) }

    @Test
    fun `B-STATE-005 parity removing a server asks first and names it`() =
        ParityCapture.capture(
            "servers-remove",
            only = ParityCapture.variants.filter { it.width == 412 && it.fontScale == 1f },
            arrange = { v ->
                onNodeWithContentDescription(if (v.locale == "ar") "إجراءات Studio Mac" else "Actions for Studio Mac", substring = true)
                    .performClick()
                waitForIdle()
                onAllNodesWithText(if (v.locale == "ar") "إزالة" else "Remove", substring = true)[0].performClick()
                waitForIdle()
            },
            check = { v ->
                if (v.locale == "en") {
                    onNodeWithText("Remove Studio Mac?").assertExists()
                    onNodeWithText("Nothing is deleted on the server or at your AI providers").assertExists()
                    assertEquals(false, calls.any { it.startsWith("remove:") })
                }
            },
        ) { ServersScreen(ServersUi(servers = saved), actions) }

    @Test
    fun `B-NAV-037 parity with nothing saved the add form is the screen`() =
        ParityCapture.capture(
            "servers-empty",
            check = { v -> if (v.locale == "en") onNodeWithText("Add an OpenCode 2 server to start a conversation.").assertExists() },
        ) { ServersScreen(ServersUi(), actions) }

    @Test
    fun `B-NET-020 parity a failed connection is one plain line with Details`() =
        ParityCapture.capture(
            "servers-add-failed",
            only = ParityCapture.variants.filter { it.width == 412 && it.fontScale == 1f },
            check = { v -> if (v.locale == "en") onNodeWithText("Details").assertExists() },
        ) {
            ServersScreen(
                ServersUi(issue = ServerIssue.ConnectFailed, failure = Failure.Network(NetworkWhy.Dns, parityDetails("OpenGateway · Dns"))),
                actions,
            )
        }
}

@OptIn(RedactorApi::class) private fun parityDetails(text: String): Redacted = Redacted(text)
