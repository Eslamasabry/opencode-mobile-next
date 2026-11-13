package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import io.github.eslamasabry.oc.ui.shell.NavShell
import io.github.eslamasabry.oc.ui.shell.ShellDependencies
import io.github.eslamasabry.oc.ui.shell.ShellNavigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

/**
 * Edge light plan v3.1, N0 and N1 on the real NavShell (the app's NavDisplay with its saved-state and view-model
 * decorators). Each page keeps a `rememberSaveable` number, a plain `remember` number and a view model, so a test can
 * tell "kept" (same saved number, same view model, new plain number: the page left composition and came back) from
 * "recreated" (new saved number, new view model).
 */
@OptIn(ExperimentalTestApi::class)
class LampBackStackStateTest {
    private fun conv(name: String) = ConversationRef(ProfileId("p"), SessionId("ses_$name"))

    private val a = conv("A")
    private val b = conv("B")
    private val c = conv("C")

    private class Seen(val saved: Int, val plain: Int, val model: PageModel)

    private class PageModel(private val name: String, private val cleared: MutableList<String>) : ViewModel() {
        override fun onCleared() {
            cleared += name
        }
    }

    private class Pages {
        val navigator = ShellNavigator()
        val seen = mutableMapOf<String, Seen>()
        val cleared = mutableListOf<String>()
        val shown = mutableListOf<String>()
        var counter = 0
        val input = FakeBackInput()
        private val dispatcher = NavigationEventDispatcher().also { it.addInput(input) }
        private val dispatcherOwner =
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
            }
        private val storeOwner =
            object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = ViewModelStore()
            }

        @Composable
        fun Page(name: String, ref: ConversationRef?) {
            val saved = rememberSaveable { ++counter }
            val plain = remember { ++counter }
            val model = viewModel { PageModel(name, cleared) }
            SideEffect { seen[name] = Seen(saved, plain, model) }
            if (ref != null) {
                // What the chat's consumer (slice C1) does: take this conversation's request, show it, consume it.
                LaunchedEffect(ref) {
                    navigator.requestFocus.filterNotNull().filter { it.ref == ref }.collect {
                        shown += "$name:${it.requestId}"
                        navigator.consumeRequestFocus(it.seq)
                    }
                }
            }
            KitText("Page $name")
        }

        private val installer = EntryInstaller {
            entry<ChatsRoute.Home> { KitText("Page Home") }
            entry<ChatRoute> { route -> Page(route.ref.sessionId.raw.removePrefix("ses_"), route.ref) }
            entry<FilesRoute.Folder> { route -> Page(route.from.sessionId.raw.removePrefix("ses_") + "›Files", null) }
        }
        private val deps = ShellDependencies(navigator, setOf(installer), FakeSystemMotion(1f))

        @Composable
        fun Content() {
            val platform = DesktopKitPlatform()
            CompositionLocalProvider(
                LocalKitPlatform provides platform,
                LocalNavigationEventDispatcherOwner provides dispatcherOwner,
                LocalViewModelStoreOwner provides storeOwner,
            ) {
                KitTheme(appearance = KitAppearance(mode = AppearanceMode.Light), platform = platform, localeTag = "en") {
                    NavShell(start = ChatsRoute.Home, deps = deps)
                }
            }
        }
    }

    private fun ComposeUiTest.pages(): Pages = Pages().also { p -> setContent { p.Content() } }

    private fun ComposeUiTest.act(pages: Pages, step: ShellNavigator.() -> Unit) {
        runOnIdle { pages.navigator.step() }
        waitForIdle()
    }

    private fun assertKept(before: Seen?, after: Seen?, what: String) {
        requireNotNull(before) { "$what was never composed" }
        requireNotNull(after)
        assertEquals(before.saved, after.saved, "$what keeps its saved state")
        assertSame(before.model, after.model, "$what keeps its view model")
        assertNotEquals(before.plain, after.plain, "$what really left composition and came back")
    }

    @Test
    fun `B-LAMP-N0 nav3_moveEntry_keepsSaveableAndViewModel`() = runComposeUiTest {
        val p = pages()
        act(p) { openChat(a) }
        act(p) { openChat(b) }
        val a1 = p.seen["A"]
        val b1 = p.seen["B"]
        // Reorder in place: Home, A, B becomes Home, B, A. Nothing is popped and pushed again.
        act(p) { restoreStack(listOf<AppRoute>(ChatsRoute.Home, ChatRoute(b), ChatRoute(a))) }
        onNodeWithText("Page A").assertExists()
        assertKept(a1, p.seen["A"], "A")
        act(p) { restoreStack(listOf<AppRoute>(ChatsRoute.Home, ChatRoute(a), ChatRoute(b))) }
        onNodeWithText("Page B").assertExists()
        assertKept(b1, p.seen["B"], "B")
        assertTrue(p.cleared.isEmpty(), "no view model cleared by a move: ${p.cleared}")
    }

    @Test
    fun `B-LAMP-N1 switchTo_targetIsKept_swapsBothKeepState`() = runComposeUiTest {
        val p = pages()
        act(p) { openChat(a) }
        val a1 = p.seen["A"]
        act(p) { switchTo(b) }
        val b1 = p.seen["B"]
        act(p) { switchTo(a) }
        onNodeWithText("Page A").assertExists()
        assertKept(a1, p.seen["A"], "A after switching back")
        assertEquals(emptyList(), p.cleared, "a swap clears nothing")
        act(p) { back() }
        onNodeWithText("Page B").assertExists()
        assertKept(b1, p.seen["B"], "B after Back")
        assertEquals(listOf("A"), p.cleared, "Back pops A, so only A's view model is cleared")
    }

    @Test
    fun `B-LAMP-N1 switchTo_targetLowerInStack_movesItsRun and keeps its state`() = runComposeUiTest {
        val p = pages()
        act(p) { openChat(a) }
        val a1 = p.seen["A"]
        act(p) { openChat(b) }
        act(p) { openChat(c) }
        act(p) { switchTo(a) }
        onNodeWithText("Page A").assertExists()
        assertKept(a1, p.seen["A"], "A moved up from under B and C")
        assertTrue(p.cleared.isEmpty(), "nothing cleared: ${p.cleared}")
    }

    @Test
    fun `B-LAMP-N1 switchTo_keptIsChatAndFiles_backLandsOnFiles with its state`() = runComposeUiTest {
        val p = pages()
        act(p) { openChat(a) }
        act(p) { open(FilesRoute.Folder(a)) }
        val files1 = p.seen["A›Files"]
        act(p) { switchTo(b) }
        act(p) { back() }
        onNodeWithText("Page A›Files").assertExists()
        assertKept(files1, p.seen["A›Files"], "A's Files page")
    }

    @Test
    fun `B-LAMP-N1 switchTo_thirdSwitch_dropsOldKept_andClearsItsViewModel`() = runComposeUiTest {
        val p = pages()
        act(p) { openChat(a) }
        val a1 = p.seen["A"]!!
        act(p) { switchTo(b) }
        act(p) { switchTo(c) }
        val stack = listOf<AppRoute>(ChatsRoute.Home, ChatRoute(b), ChatRoute(c))
        assertEquals(stack, p.navigator.snapshot(), "A's run left the stack")
        assertEquals(listOf("A"), p.cleared, "the dropped run's view model is cleared")
        act(p) { openChat(a) }
        val a2 = p.seen["A"]!!
        assertTrue(a1.model !== a2.model, "A comes back with a new view model")
        assertNotEquals(a1.saved, a2.saved, "its saved state was cleared too: A comes back fresh")
    }

    @Test
    fun `B-LAMP-N1 requestFocus_freshEntry_receivesAfterEmit`() = runComposeUiTest {
        val p = pages()
        act(p) { openChat(a) }
        act(p) { switchTo(b, request = "per_1") }
        assertEquals(listOf("B:per_1"), p.shown, "B's page subscribed after the emit and still got it")
        assertNull(p.navigator.requestFocus.value, "consumed")
    }

    @Test
    fun `B-LAMP-N1 requestFocus_keptEntry_receivesOnReturn`() = runComposeUiTest {
        val p = pages()
        act(p) { openChat(a) }
        act(p) { switchTo(b) }
        act(p) { switchTo(a, request = "que_2") }
        assertEquals(listOf("A:que_2"), p.shown, "the kept page, composed again, got it once")
        act(p) { back() }
        act(p) { switchTo(a) }
        assertEquals(listOf("A:que_2"), p.shown, "never shown twice")
    }

    @Test
    fun `B-LAMP-N1 a cancelled back gesture leaves the stack and the kept run untouched`() = runComposeUiTest {
        val p = pages()
        act(p) { openChat(a) }
        act(p) { switchTo(b) }
        val before = p.navigator.snapshot()
        runOnIdle {
            p.input.start(NavigationEvent.EDGE_LEFT)
            p.input.progress(NavigationEvent.EDGE_LEFT, 0.4f)
        }
        waitForIdle()
        runOnIdle { p.input.cancel() }
        waitForIdle()
        assertEquals(before, p.navigator.snapshot(), "the stack is as it was")
        onNodeWithText("Page B").assertExists()
        act(p) { switchTo(a) }
        val swapped = listOf<AppRoute>(ChatsRoute.Home, ChatRoute(b), ChatRoute(a))
        assertEquals(swapped, p.navigator.snapshot(), "A is still kept: a swap")
    }

    @Test
    fun `B-LAMP-N1 a committed back gesture after a switch returns to the left conversation as it was`() =
        runComposeUiTest {
            val p = pages()
            act(p) { openChat(a) }
            val a1 = p.seen["A"]
            act(p) { switchTo(b) }
            runOnIdle { p.input.start(NavigationEvent.EDGE_LEFT) }
            waitForIdle()
            runOnIdle { p.input.complete() }
            waitForIdle()
            onNodeWithText("Page A").assertExists()
            assertKept(a1, p.seen["A"], "A after the gesture")
        }
}
