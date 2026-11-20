package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitRow
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.LocalOpenBeside
import io.github.eslamasabry.oc.ui.routes.LocalRestingBeside
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.test.onAllNodesWithText
import io.github.eslamasabry.oc.ui.routes.RouteMotion
import io.github.eslamasabry.oc.ui.shell.NavShell
import io.github.eslamasabry.oc.ui.shell.ShellDependencies
import io.github.eslamasabry.oc.ui.shell.ShellNavigator
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Big screens, slice 3 (BS-LIST-PANE): from 840 dp wide (and not short) Home's conversation list stays as the start
 * pane,
 * 296 dp, and the open conversation fills the rest; under 840 dp everything is the phone's single page. Run on the real
 * NavShell at real window sizes (one dp per pixel), with a Home and a conversation made of kit parts.
 */
@OptIn(ExperimentalTestApi::class)
class ShellListPaneTest {
    private var locale: Locale = Locale.getDefault()
    private var motion: RouteMotion = ChatRoute.openMotion

    @BeforeTest
    fun english() {
        locale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        motion = ChatRoute.openMotion
        // Stricter than the app ships (SharedAxisX): even a conversation that would grow from its row does not grow
        // between panes.
        ChatRoute.openMotion = RouteMotion.GrowFromRow
    }

    @AfterTest
    fun restore() {
        Locale.setDefault(locale)
        ChatRoute.openMotion = motion
    }

    private class NewModel(private val route: NavKey, private val cleared: MutableList<NavKey>) : ViewModel() {
        override fun onCleared() {
            cleared += route
        }
    }

    private class Panes(window: KitWindowInfo? = null) {
        val navigator = ShellNavigator()
        val gate = RowTransformGate()
        val input = FakeBackInput()
        private val dispatcher = NavigationEventDispatcher().also { it.addInput(input) }
        private val owner =
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
            }

        /** A window that stands in for the measured one (rotation); null: the test window itself. */
        val window: MutableState<KitWindowInfo?> = mutableStateOf(window)
        var listWidth = -1f
        var chatWidth = -1f
        var list: LazyListState? = null
        var focus: FocusManager? = null

        /** What the list saw as the page open beside it ([LocalOpenBeside]), last composition. */
        var openSeen: NavKey? = null

        /** Each New conversation route composed, in order, and the ones whose view model was cleared. */
        val newSeen = mutableListOf<NavKey>()
        val cleared = mutableListOf<NavKey>()

        private val installer = EntryInstaller {
            entry<ChatsRoute.Home> {
                listWidth = LocalKitWindow.current.widthDp
                openSeen = LocalOpenBeside.current
                val state = rememberLazyListState().also { list = it }
                KitScreen(title = "Conversations") {
                    LazyColumn(
                        state = state,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f).testTag(LIST),
                    ) {
                        items((0 until ROWS).toList(), key = { it }) { i ->
                            KitRow(
                                "Row $i",
                                "preview $i",
                                onClick = { navigator.openChat(ref(i)) },
                                modifier = Modifier.testTag("row-$i"),
                                growKey = ref(i),
                            )
                        }
                    }
                    KitButton(NEW, onClick = {}, modifier = Modifier.testTag(PLUS))
                }
            }
            // As New conversation does (NewConversationViewModel): Start opens the started conversation when this page
            // is what the person sees (on top, or resting beside the list); its view model must outlive the hand-over.
            entry<ChatsRoute.New> { route ->
                val resting = LocalRestingBeside.current
                viewModel { NewModel(route, cleared) }
                newSeen += route
                KitScreen(title = "New", backLabel = "Back", onBack = navigator::back) {
                    KitText(NEW_PAGE)
                    val shown = { navigator.top.value == route || resting }
                    KitButton(START, onClick = { if (shown()) navigator.openChat(ref(9)) })
                }
            }
            entry<ChatsRoute.All> {
                KitScreen(title = "Everything", backLabel = "Back", onBack = navigator::back) {
                    KitRow("Every 5", "preview 5", onClick = { navigator.openChat(ref(5)) })
                }
            }
            entry<ChatRoute> { route ->
                chatWidth = LocalKitWindow.current.widthDp
                var taps by rememberSaveable { mutableIntStateOf(0) }
                val composer = remember { FocusRequester() }
                val field = rememberTextFieldState()
                // As the conversation does (ChatTypingFocus, around the whole page): the shell's steer of a typed
                // letter lands in the message field.
                Column(
                    Modifier.focusProperties {
                            onEnter = {
                                if (requestedFocusDirection == FocusDirection.Enter && composer.requestFocus())
                                    cancelFocusChange()
                            }
                        }
                        .focusGroup()
                ) {
                    KitGrowPage(growKey = route.ref) {
                        val title = "Chat ${route.ref.sessionId.raw}"
                        KitScreen(title = title, backLabel = "Back", onBack = navigator::back) {
                            KitText("transcript ${route.ref.sessionId.raw}")
                            KitButton("Tapped $taps", onClick = { taps++ }, modifier = Modifier.testTag(SEND))
                            BasicTextField(
                                field,
                                Modifier.size(200.dp, 48.dp).testTag(COMPOSER).focusRequester(composer),
                            )
                        }
                    }
                }
            }
        }
        val deps = ShellDependencies(navigator, setOf(installer), FakeSystemMotion(1f))

        @Composable
        fun Content() {
            val platform = remember { DesktopKitPlatform() }
            CompositionLocalProvider(
                LocalKitPlatform provides platform,
                LocalNavigationEventDispatcherOwner provides owner,
                LocalDensity provides Density(1f),
            ) {
                val light = KitAppearance(mode = AppearanceMode.Light)
                KitTheme(appearance = light, platform = platform, localeTag = "en") {
                    CompositionLocalProvider(LocalKitWindow provides (window.value ?: LocalKitWindow.current)) {
                        focus = LocalFocusManager.current
                        NavShell(start = ChatsRoute.Home, deps = deps, gate = gate)
                    }
                }
            }
        }

        fun back() {
            input.start(NavigationEvent.EDGE_LEFT)
            input.complete()
        }
    }

    private fun at(w: Int, h: Int, window: KitWindowInfo? = null, block: ComposeUiTest.(Panes) -> Unit) =
        runDesktopComposeUiTest(w, h) {
            val p = Panes(window)
            setContent { p.Content() }
            waitForIdle()
            block(p)
        }

    private fun ComposeUiTest.open(i: Int) {
        onNodeWithTag("row-$i").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.focusedNode(): String =
        onAllNodes(isFocused()).fetchSemanticsNodes().joinToString { "${it.config} @ ${it.boundsInRoot}" }

    /** The left edge of the node holding focus, or null when no node with focus semantics has it. */
    private fun ComposeUiTest.focusedLeft(): Float? =
        onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.left

    @Test
    fun `B-NAV-210 list beside conversation from 840 dp`() {
        for ((w, h) in listOf(840 to 800, 1024 to 768, 1280 to 800, 900 to 1200)) at(w, h) { p ->
            open(3)
            onNodeWithText("transcript s3").assertIsDisplayed()
            onNodeWithText("Row 0").assertIsDisplayed()
            assertEquals(listOf(ChatsRoute.Home, ChatRoute(ref(3))), p.navigator.snapshot(), "$w x $h")
        }
        for ((w, h) in listOf(360 to 780, 600 to 800, 839 to 800, 1280 to 400)) at(w, h) { _ ->
            open(3)
            onNodeWithText("transcript s3").assertIsDisplayed()
            onNodeWithText("Row 0").assertDoesNotExist()
        }
    }

    @Test
    fun `B-NAV-211 tapping a row replaces the conversation`() = at(1280, 800) { p ->
        onNodeWithTag(LIST).performScrollToIndex(10)
        waitForIdle()
        val first = p.list!!.firstVisibleItemIndex
        open(11)
        open(12)
        onNodeWithText("transcript s12").assertIsDisplayed()
        onNodeWithText("transcript s11").assertDoesNotExist()
        val stack = p.navigator.snapshot()
        assertEquals(listOf(ChatsRoute.Home, ChatRoute(ref(12))), stack, "the back stack does not grow per tap")
        assertEquals(first, p.list!!.firstVisibleItemIndex, "the list keeps its scroll")
        val settled = onNodeWithTag(SEND).getBoundsInRoot()
        // The next conversation is whole and in place on its first frame: nothing grows out of the row between panes.
        mainClock.autoAdvance = false
        onNodeWithTag("row-13").performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithText("transcript s13").assertIsDisplayed()
        assertEquals(settled, onNodeWithTag(SEND).getBoundsInRoot(), "the conversation is in place on its first frame")
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(GateState.Idle, p.gate.state)
    }

    @Test
    fun `B-NAV-212 Back from a conversation in two panes shows the list with a new conversation beside it`() =
        at(1280, 800) { p ->
        onNodeWithText(NEW_PAGE).assertIsDisplayed()
        open(2)
        onNodeWithText(NEW_PAGE).assertDoesNotExist()
        runOnIdle { p.back() }
        waitForIdle()
        assertEquals(listOf(ChatsRoute.Home), p.navigator.snapshot())
        onNodeWithText("Row 0").assertIsDisplayed()
        onNodeWithText(NEW_PAGE).assertIsDisplayed()
        onNodeWithText("transcript s2").assertDoesNotExist()
        // A second Back is not the app's: no handler of the app takes it, so it leaves as Home does today.
        val commits = p.gate.trace.count { it == GateEvent.GestureCommitted }
        runOnIdle { p.back() }
        waitForIdle()
        assertEquals(listOf(ChatsRoute.Home), p.navigator.snapshot())
        onNodeWithText("Row 0").assertIsDisplayed()
        val after = p.gate.trace.count { it == GateEvent.GestureCommitted }
        assertEquals(commits, after, "the app does not take the second Back")
    }

    @Test
    fun `B-NAV-213 rotation keeps the open conversation`() =
        at(1280, 1280, window = KitWindowInfo(1280f, 800f)) { p ->
            open(4)
            onNodeWithTag(SEND).performClick()
            waitForIdle()
            onNodeWithText("Tapped 1").assertIsDisplayed()
            onNodeWithText("Row 0").assertIsDisplayed()
            runOnIdle { p.window.value = KitWindowInfo(800f, 1280f) }
            waitForIdle()
            onNodeWithText("transcript s4").assertIsDisplayed()
            onNodeWithText("Tapped 1").assertIsDisplayed()
            onNodeWithText("Row 0").assertDoesNotExist()
            runOnIdle { p.window.value = KitWindowInfo(1280f, 800f) }
            waitForIdle()
            onNodeWithText("transcript s4").assertIsDisplayed()
            onNodeWithText("Tapped 1").assertIsDisplayed()
            onNodeWithText("Row 0").assertIsDisplayed()
            assertEquals(listOf(ChatsRoute.Home, ChatRoute(ref(4))), p.navigator.snapshot())
        }

    @Test
    fun `B-NAV-214 each pane sizes by its own width`() {
        at(1280, 800) { p ->
            open(1)
            assertEquals(LIST_PANE, p.listWidth, "the list pane's parts see a 360 dp window, as the smallest phone")
            assertEquals(1280f - LIST_PANE, p.chatWidth, "the conversation pane's parts see its own width")
            assertTrue(onNodeWithTag(SEND).getBoundsInRoot().left.value >= LIST_PANE)
        }
        at(412, 800) { p ->
            open(1)
            assertEquals(412f, p.chatWidth, "a phone's conversation sees the whole window")
        }
    }

    @Test
    fun `B-NAV-215 the plus is reachable at 840 and wider in both orientations`() {
        for ((w, h) in listOf(1280 to 800, 900 to 1200, 840 to 800)) at(w, h) { _ ->
            open(1)
            onNodeWithText("Row 0").assertIsDisplayed()
            onNodeWithTag(PLUS).assertIsDisplayed()
            val plus = onNodeWithTag(PLUS).getBoundsInRoot()
            val atFoot = plus.bottom.value <= h && plus.right.value <= LIST_PANE
            assertTrue(atFoot, "$w x $h: the + sits at the list pane's foot ($plus)")
        }
    }

    @Test
    fun `B-NAV-216 keyboard reaches both panes`() = at(1280, 800) { p ->
        open(1)
        onNodeWithText("Row 0").assertIsDisplayed()
        // Tab from the list goes on into the conversation…
        onNodeWithTag("row-0").requestFocus()
        waitForIdle()
        var presses = 0
        while ((focusedLeft() ?: 0f) < LIST_PANE && presses < MAX_PRESSES) {
            onRoot().performKeyInput { pressKey(Key.Tab) }
            waitForIdle()
            presses++
        }
        assertTrue((focusedLeft() ?: 0f) >= LIST_PANE, "Tab reached the conversation after $presses presses")
        // …and Shift+Tab comes back into the list.
        presses = 0
        while ((focusedLeft() ?: LIST_PANE) >= LIST_PANE && presses < MAX_PRESSES) {
            onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
            waitForIdle()
            presses++
        }
        assertTrue((focusedLeft() ?: LIST_PANE) < LIST_PANE, "Shift+Tab reached the list after $presses presses")
        // Arrows cross the panes too. Android turns an arrow or D-pad key into this move; the desktop test window maps
        // only Tab, so the move is asked of the focus manager directly.
        onNodeWithTag(LIST).performScrollToIndex(0)
        waitForIdle()
        onNodeWithTag("row-0").requestFocus()
        waitForIdle()
        runOnIdle { p.focus!!.moveFocus(FocusDirection.Right) }
        waitForIdle()
        assertTrue((focusedLeft() ?: 0f) >= LIST_PANE, "Right from a row reaches the conversation")
        // Left goes through the conversation's own controls on its way (its Back sits left of the message area).
        presses = 0
        while ((focusedLeft() ?: LIST_PANE) >= LIST_PANE && presses < ARROWS) {
            runOnIdle { p.focus!!.moveFocus(FocusDirection.Left) }
            waitForIdle()
            presses++
        }
        val left = focusedLeft() ?: LIST_PANE
        assertTrue(left < LIST_PANE, "Left from the conversation reaches the list (now ${focusedNode()})")
    }

    @Test
    fun `B-NAV-218 with no conversation open the pages side is a new conversation, never an empty pane`() {
        at(1280, 800) { p ->
            onNodeWithText("Row 0").assertIsDisplayed()
            onNodeWithText(NEW_PAGE).assertIsDisplayed()
            assertEquals(listOf(ChatsRoute.Home), p.navigator.snapshot(), "the resting page is not on the back stack")
            open(5)
            onNodeWithText(NEW_PAGE).assertDoesNotExist()
            runOnIdle { p.back() }
            waitForIdle()
            onNodeWithText(NEW_PAGE).assertIsDisplayed()
        }
        // After a process death the app puts the last stack back: the conversation the person was in shows again.
        at(1280, 800) { p ->
            runOnIdle { p.navigator.restoreStack(listOf(ChatsRoute.Home, ChatRoute(ref(7)))) }
            waitForIdle()
            onNodeWithText("transcript s7").assertIsDisplayed()
            onNodeWithText("Row 0").assertIsDisplayed()
        }
        // A phone's Home is Home alone.
        at(412, 800) { _ -> onNodeWithText(NEW_PAGE).assertDoesNotExist() }
    }

    @Test
    fun `B-NAV-219 the list knows which conversation is open beside it`() {
        at(1280, 800) { p ->
            assertTrue(p.openSeen is ChatsRoute.New, "the resting new conversation is what is open beside the list")
            open(3)
            assertEquals(ChatRoute(ref(3)), p.openSeen)
            open(4)
            assertEquals(ChatRoute(ref(4)), p.openSeen, "the choice follows the open conversation")
        }
        at(412, 800) { p -> assertEquals(null, p.openSeen, "a phone's list chooses nothing") }
    }

    @Test
    fun `B-NAV-230 the panes use the whole width at every window, the list grows before margins appear`() {
        for (w in SWEEP_FROM..SWEEP_TO step SWEEP_STEP) {
            val list = ShellPanes.listWidth(w.toFloat(), 1f)
            assertTrue(list in 320f..420f, "$w dp: the list is 320 to 420 dp ($list)")
            if (w - list > PAGE_CAP) assertEquals(420f, list, "$w dp: before any margin the list is at its widest")
        }
        assertEquals(LIST_PANE, ShellPanes.listWidth(1280f, 1f), "360 dp on a 1280 dp window")
        assertEquals(320f, ShellPanes.listWidth(840f, 1f), "320 dp on an 840 dp window")
        // The conversation pane takes all the rest; its page is centred only past its cap.
        for ((w, h) in listOf(840 to 800, 1024 to 768, 1280 to 800, 1600 to 900)) at(w, h) { p ->
            open(1)
            val pane = w - p.listWidth
            assertEquals(minOf(pane, PAGE_CAP), p.chatWidth, "$w dp: the conversation's page fills its pane to the cap")
            val send = onNodeWithTag(SEND).getBoundsInRoot()
            if (pane > PAGE_CAP) {
                val left = send.left.value - p.listWidth
                assertTrue(left >= (pane - PAGE_CAP) / 2, "$w dp: two equal margins past the cap ($left)")
            }
        }
    }

    @Test
    fun `B-NAV-226 Start on the resting New conversation opens the started conversation beside the list`() =
        at(1280, 800) { p ->
            onNodeWithText(NEW_PAGE).assertIsDisplayed()
            val first = p.newSeen.last()
            mainClock.autoAdvance = false
            onNodeWithText(START).performClick()
            // No frame shows a second New conversation beside the started conversation.
            repeat(FRAMES) {
                mainClock.advanceTimeByFrame()
                val newShown = onAllNodesWithText(NEW_PAGE).fetchSemanticsNodes().isNotEmpty()
                val chatShown = onAllNodesWithText("transcript s9").fetchSemanticsNodes().isNotEmpty()
                assertTrue(!(newShown && chatShown), "frame $it: a New conversation beside the started one")
            }
            mainClock.autoAdvance = true
            waitForIdle()
            onNodeWithText("transcript s9").assertIsDisplayed()
            onNodeWithText(NEW_PAGE).assertDoesNotExist()
            assertEquals(listOf(ChatsRoute.Home, ChatRoute(ref(9))), p.navigator.snapshot())
            assertEquals(ChatRoute(ref(9)), p.openSeen, "its row is the chosen one")
            assertTrue(first !in p.cleared, "the starting page keeps its view model while the first message settles")
            // Back: a fresh New conversation rests beside the list again.
            runOnIdle { p.back() }
            waitForIdle()
            onNodeWithText(NEW_PAGE).assertIsDisplayed()
            assertTrue(p.newSeen.last() != first, "a fresh page, not the one that started the conversation")
        }

    @Test
    fun `B-NAV-227 every way into a conversation lands beside the list`() = at(1280, 800) { p ->
        runOnIdle { p.navigator.open(ChatsRoute.All()) }
        waitForIdle()
        onNodeWithText("Every 5").performClick()
        waitForIdle()
        assertEquals(listOf(ChatsRoute.Home, ChatRoute(ref(5))), p.navigator.snapshot(), "from All conversations")
        onNodeWithText("transcript s5").assertIsDisplayed()
        onNodeWithText("Row 0").assertIsDisplayed()
        // A notification or the edge light (switchTo) and a page's link back to a conversation (backToChat) too.
        runOnIdle { p.navigator.switchTo(ref(6)) }
        waitForIdle()
        assertEquals(listOf(ChatsRoute.Home, ChatRoute(ref(6))), p.navigator.snapshot(), "switchTo")
        runOnIdle { p.navigator.backToChat(ref(7)) }
        waitForIdle()
        assertEquals(listOf(ChatsRoute.Home, ChatRoute(ref(7))), p.navigator.snapshot(), "backToChat")
        onNodeWithText("Row 0").assertIsDisplayed()
    }

    @Test
    fun `B-NAV-216 a letter typed with nothing focused lands in the conversation beside the list`() =
        at(1280, 800) { p ->
        // A tap on a touch screen focuses nothing (a desktop click may focus the row): open it as a tap does.
        runOnIdle { p.navigator.openChat(ref(1)) }
        waitForIdle()
        onNodeWithText("Row 0").assertIsDisplayed()
        onRoot().performKeyInput { pressKey(Key.H) }
        waitForIdle()
        onNodeWithTag(COMPOSER).assertIsFocused()
    }

    private companion object {
        const val ROWS = 40
        const val LIST = "pane-list"
        const val PLUS = "pane-plus"
        const val SEND = "pane-send"
        const val COMPOSER = "pane-composer"
        const val NEW = "New conversation"
        const val NEW_PAGE = "a new conversation, ready to type"
        const val START = "Start"
        const val FRAMES = 4
        const val SWEEP_FROM = 840
        const val SWEEP_TO = 1700
        const val SWEEP_STEP = 40
        const val PAGE_CAP = 1000f
        const val LIST_PANE = 360f
        const val MAX_PRESSES = 120
        const val ARROWS = 4
    }
}
