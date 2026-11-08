package io.github.eslamasabry.oc.feature.servers.links

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.servers.ServersTestTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LinkNoticeHostTest {
    private var before: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        before = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(before)

    private fun androidx.compose.ui.test.ComposeUiTest.show(notice: LinkNotice, calls: MutableList<String>) =
        setContent {
            ServersTestTheme {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                    KitSheetHost(Modifier.fillMaxSize()) {
                        LinkNoticeHost(notice, onAddServer = { calls += "add" }, onDismiss = { calls += "dismiss" })
                    }
                }
            }
        }

    @Test
    fun `B-ENTRY-140 an unknown server asks Add this server and Add server opens the add flow`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        show(LinkNotice.ServerMissing, calls)
        onNodeWithText("Add this server?").assertExists()
        onNodeWithText("The conversation is on a server this phone has not saved", substring = true).assertExists()
        onNodeWithText("Add server").performClick()
        assertEquals(listOf("dismiss", "add"), calls)
    }

    @Test
    fun `B-ENTRY-141 re-entry says to enter the password again`() = runComposeUiTest {
        show(LinkNotice.Reentry, mutableListOf())
        onNodeWithText("Enter this server’s password again", substring = true).assertExists()
    }

    @Test
    fun `B-ENTRY-146 a failed connection says to check it under Servers`() = runComposeUiTest {
        show(LinkNotice.ConnectionFailed, mutableListOf())
        onNodeWithText("Could not connect to the saved server", substring = true).assertExists()
    }

    @Test
    fun `B-ENTRY-142 the address sheet says links with an address are not available yet`() = runComposeUiTest {
        show(LinkNotice.AddressLink(SessionAddressFailure.Unavailable), mutableListOf())
        onNodeWithText("Open a shared conversation").assertExists()
        onNodeWithTag("session-address-failure").assertExists()
        onNodeWithText("Conversation links with a server address are not available yet.", substring = true).assertExists()
    }
}
