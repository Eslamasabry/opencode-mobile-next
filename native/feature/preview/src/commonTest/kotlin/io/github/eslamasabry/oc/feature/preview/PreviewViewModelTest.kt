package io.github.eslamasabry.oc.feature.preview

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.security.ApprovedLink
import io.github.eslamasabry.oc.core.security.ExternalLinkOpener
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.feature.preview.presentation.DevPreviewTarget
import io.github.eslamasabry.oc.feature.preview.presentation.DevPreviewViewModel
import io.github.eslamasabry.oc.feature.preview.presentation.PagePhase
import io.github.eslamasabry.oc.feature.preview.presentation.PortRow
import io.github.eslamasabry.oc.feature.preview.presentation.PortsViewModel
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewBlock
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewNotice
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.PreviewPort
import io.github.eslamasabry.oc.ui.routes.PreviewRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModelTest {
    private val remote = SavedServer(ProfileId("remote"), "Box", "https://box.tail.ts.net:4096", connected = true)
    private val phone = SavedServer(ProfileId("phone"), "Phone", "http://127.0.0.1:4096", connected = false)
    private val ref = ConversationRef(ProfileId("phone"), SessionId("s"))

    private class Connections(vararg saved: SavedServer) : ServerConnections {
        override val servers = MutableStateFlow<ImmutableList<SavedServer>>(persistentListOf(*saved))
        override val busy = MutableStateFlow(false)

        override suspend fun save(name: String, address: String, username: String, password: String, allowCleartext: Boolean) =
            error("not used")

        override suspend fun connect(id: ProfileId): Outcome<Unit> = error("not used")

        override suspend fun createConversation(profile: ProfileId?): Outcome<ConversationRef> = error("not used")

        override suspend fun remove(id: ProfileId): Outcome<Unit> = error("not used")
    }

    private class Navigator : AppNavigator {
        override val top = MutableStateFlow<AppRoute?>(null)
        var backs = 0

        override fun open(route: AppRoute) {
            top.value = route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) = Unit

        override fun land() = Unit

        override fun replaceRoot(root: AppRoute) = Unit

        override fun back() {
            backs++
        }
    }

    private class Opener(var result: Outcome<Unit> = Outcome.Ok(Unit)) : ExternalLinkOpener {
        val opened = mutableListOf<ApprovedLink>()

        override fun open(link: ApprovedLink): Outcome<Unit> {
            opened += link
            return result
        }
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun page(route: PreviewRoute.Page, vararg saved: SavedServer, opener: Opener = Opener()) =
        DevPreviewViewModel(route, Connections(*saved), Navigator(), ExternalLinkPolicy(), opener)

    @Test
    fun `B-CHAT-027 the page opens the conversation's server, else the connected one`() {
        val own = page(PreviewRoute.Page(5173, ref = ref), remote, phone)
        assertEquals(DevPreviewTarget.Phone("http://127.0.0.1:5173/"), own.state.value.target)
        val connected = page(PreviewRoute.Page(5173), phone, remote)
        assertEquals(DevPreviewTarget.Remote("http://box.tail.ts.net:5173/", "box.tail.ts.net"), connected.state.value.target)
        assertEquals("box.tail.ts.net", connected.state.value.remoteHost)
        assertEquals("http://127.0.0.1:5173/", own.state.value.shown)
    }

    @Test
    fun `B-CHAT-027 with no server the page is blocked with a reason and offers nothing to load`() {
        val vm = page(PreviewRoute.Page(3000))
        assertEquals(PreviewBlock.NoServer, vm.state.value.blocked)
        assertNull(vm.state.value.origin)
        assertNull(vm.pageEgress)
    }

    @Test
    fun `B-SEC-058 the address bar only follows addresses the preview may load`() {
        val vm = page(PreviewRoute.Page(5173, ref = ref), phone)
        vm.pageAddress("http://127.0.0.1:5173/inner")
        assertEquals("http://127.0.0.1:5173/inner", vm.state.value.shown)
        vm.pageAddress("https://evil.example/")
        assertEquals("http://127.0.0.1:5173/inner", vm.state.value.shown)
        assertTrue(vm.pageEgress!!.mayLoad("http://127.0.0.1:5173/x"))
    }

    @Test
    fun `B-SEC-058 Open in browser opens the app's own address through the link policy`() {
        val opener = Opener()
        val vm = page(PreviewRoute.Page(5173, ref = ref), phone, opener = opener)
        vm.openInBrowser()
        assertEquals(listOf("http://127.0.0.1:5173/"), opener.opened.map { it.uri })
        assertEquals(LinkOrigin.AppAuthored, opener.opened.single().origin)
    }

    @Test
    fun `B-SEC-058 a page's link to elsewhere never loads and asks before the browser opens it`() {
        val opener = Opener()
        val vm = page(PreviewRoute.Page(5173, ref = ref), phone, opener = opener)
        vm.pageLeave("https://docs.example.com/guide")
        assertEquals("docs.example.com", vm.state.value.leaveHost)
        assertTrue(opener.opened.isEmpty())
        vm.cancelLeave()
        assertNull(vm.state.value.leaveHost)
        assertTrue(opener.opened.isEmpty())
        vm.pageLeave("https://docs.example.com/guide")
        vm.confirmLeave()
        assertEquals(listOf("https://docs.example.com/guide"), opener.opened.map { it.uri })
        assertEquals(LinkOrigin.ServerContent, opener.opened.single().origin)
    }

    @Test
    fun `B-SEC-058 an unsafe link is refused plainly and a failed browser says so`() {
        val opener = Opener(Outcome.Failed(Failure.Device()))
        val vm = page(PreviewRoute.Page(5173, ref = ref), phone, opener = opener)
        vm.pageLeave("intent://evil#Intent;end")
        assertEquals(PreviewNotice.LinkBlocked, vm.state.value.notice)
        assertNull(vm.state.value.leaveHost)
        vm.noticeShown()
        vm.openInBrowser()
        assertEquals(PreviewNotice.OpenFailed, vm.state.value.notice)
    }

    @Test
    fun `B-CHAT-027 the page reports loading, ready and not answering`() {
        val vm = page(PreviewRoute.Page(5173, ref = ref), phone)
        assertEquals(PagePhase.Loading, vm.state.value.phase)
        vm.pagePhase(PagePhase.Failed)
        assertEquals(PagePhase.Failed, vm.state.value.phase)
        vm.pagePhase(PagePhase.Ready)
        vm.pageCanGoBack(true)
        assertEquals(PagePhase.Ready, vm.state.value.phase)
        assertTrue(vm.state.value.canGoBack)
    }

    @Test
    fun `B-CHAT-026 the ports page lists mentioned ports with where they open and opens one`() {
        val nav = Navigator()
        val route = PreviewRoute.Ports(ref, listOf(PreviewPort(5173), PreviewPort(8443, https = true)))
        val onPhone = PortsViewModel(route, Connections(phone, remote), nav)
        assertEquals(listOf(PortRow(5173, false, null), PortRow(8443, true, null)), onPhone.state.value.rows)
        val onRemote = PortsViewModel(PreviewRoute.Ports(null, route.mentioned), Connections(remote), nav)
        assertEquals("box.tail.ts.net", onRemote.state.value.rows.first().host)
        onPhone.open(PreviewPort(8443, https = true))
        assertEquals(PreviewRoute.Page(8443, https = true, ref = ref), nav.top.value)
    }

    @Test
    fun `B-CHAT-025 a typed port opens only when it is a real port`() {
        val nav = Navigator()
        val vm = PortsViewModel(PreviewRoute.Ports(null), Connections(remote), nav)
        listOf("", "0", "65536", "80a", "123456", "-1").forEach {
            vm.openTyped(it)
            assertTrue(vm.state.value.invalidPort, it)
        }
        assertNull(nav.top.value)
        vm.openTyped(" 3000 ")
        assertEquals(PreviewRoute.Page(3000), nav.top.value)
        assertEquals(false, vm.state.value.invalidPort)
    }

    @Test
    fun `B-CHAT-027 the ports page says when there is no server to point at`() {
        val vm = PortsViewModel(PreviewRoute.Ports(null, listOf(PreviewPort(3000))), Connections(), Navigator())
        assertEquals(PreviewBlock.NoServer, vm.state.value.blocked)
    }
}
