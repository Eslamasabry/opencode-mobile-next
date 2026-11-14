package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.KitRow
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen as KitFrameScreen
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.shell.NavShell
import io.github.eslamasabry.oc.ui.shell.ShellDependencies
import io.github.eslamasabry.oc.ui.shell.ShellNavigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The owner's phone crashed twice on 2026-11-14 with a NullPointerException in
 * `LookaheadPassDelegate.forEachChildAlignmentLinesOwner`: a lookahead pass over the open conversation's message box
 * (KitScreen's bottom block → the composer's BoxWithConstraints → its box) met a child without its lookahead delegate,
 * right after the conversation went from loading to live and KitScreen moved its bottom block (with the composer's own
 * subcomposition) from its stacked layout to its overlaid one. Every page is in that pass: the navigation sits in a
 * `SharedTransitionLayout` for the row → conversation grow, the title's flight and the control → sheet morph.
 *
 * With the transform on, as the app runs it, a conversation-shaped page that grows from its row goes through loading →
 * live → loading (and a status line coming and going) over many frames, with the message box drawn and typed in: no
 * exception, the page really is measured in lookahead passes, the frame never moves its parts to another layout (its
 * nodes keep their identity), and the box keeps its words and focus. On a build where KitScreen still swaps layouts
 * on loading, the identity check fails.
 */
@OptIn(ExperimentalTestApi::class)
class ShellLookaheadTest {
    @Test
    fun `B-NAV-121 a conversation grown from its row goes from loading to live and back under the lookahead pass`() =
        runDesktopComposeUiTest(W, H) {
            val platform = DesktopKitPlatform()
            val navigator = ShellNavigator()
            val page = ProbedPages(navigator)
            val deps = ShellDependencies(navigator, setOf(page.installer), FakeSystemMotion())
            setContent {
                CompositionLocalProvider(LocalKitPlatform provides platform, LocalShellRowTransform provides true) {
                    KitTheme(
                        appearance = KitAppearance(mode = AppearanceMode.Light),
                        platform = platform,
                        localeTag = "en",
                    ) {
                        NavShell(start = ChatsRoute.Home, deps = deps)
                    }
                }
            }
            waitForIdle()
            // Tap the row: the conversation grows out of it, still loading, its message box already drawn.
            onNodeWithText("Row 1").performClick()
            frames()
            waitForIdle()
            onNodeWithText("skeleton line").assertExists()
            onNodeWithTag("kit-screen-loading", useUnmergedTree = true).assertExists()
            val frame = frameIds()
            onNode(hasSetTextAction()).performTouchInput { click() }
            onNode(hasSetTextAction()).performTextInput("he")

            // A status line arrives while it loads, then the load ends: the box gains a tray and a subcomposed row.
            runOnIdle { page.line = KitStatus(KitStatusKind.Connection, "Reconnecting…", id = "link") }
            frames()
            runOnIdle { page.live = true }
            frames()
            onNodeWithText("transcript line").assertExists()
            onNodeWithText("tray line").assertExists()
            assertEquals(frame, frameIds(), "loading to live: the frame's parts stay where they are (nothing moved)")
            runOnIdle { page.line = null }
            frames()
            assertEquals(frame, frameIds(), "the status line leaves: nothing moved")
            onNode(hasSetTextAction()).assertIsFocused()
            onNode(hasSetTextAction()).performTextInput("llo")

            // A reload: back to loading, the box loses those children again.
            runOnIdle { page.live = false }
            frames()
            onNodeWithText("tray line").assertDoesNotExist()
            assertEquals(frame, frameIds(), "live to loading: nothing moved")
            runOnIdle { assertEquals("hello", page.words.text.toString(), "the message box keeps its words") }

            // Live again, then back to the row: the conversation shrinks into it.
            runOnIdle { page.live = true }
            frames()
            runOnIdle { navigator.back() }
            frames()
            waitForIdle()
            onNodeWithText("Row 1").assertExists()

            assertTrue(page.passes > 0, "the probes were measured")
            assertTrue(page.lookaheadPasses > 0, "the pages were measured in lookahead passes (the transform is on)")
        }

    /** Many frames, one at a time, so every lookahead and approach pass in between runs. */
    private fun ComposeUiTest.frames() {
        repeat(FRAMES) { mainClock.advanceTimeByFrame() }
    }

    /** The conversation frame's box and its bottom block's box: new nodes whenever the frame changes layout. */
    private fun ComposeUiTest.frameIds(): List<Int> =
        listOf("kit-screen", "kit-screen-bottom").map {
            onNode(hasTestTag(it) and hasAnyAncestor(hasTestTag("page-frame")), useUnmergedTree = true).fetchSemanticsNode().id
        }

    private companion object {
        const val W = 412
        const val H = 860
        const val FRAMES = 90
    }
}

/** A home with a row and a conversation page whose parts count their measure passes, and the lookahead ones among them. */
private class ProbedPages(private val navigator: ShellNavigator) {
    var live by mutableStateOf(false)
    var line by mutableStateOf<KitStatus?>(null)
    val words = TextFieldState()
    var lookaheadPasses = 0
    var passes = 0

    private val probe =
        Modifier.layout { measurable, constraints ->
            passes++
            if (isLookingAhead) lookaheadPasses++
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }

    val installer = EntryInstaller {
        entry<ChatsRoute.Home> {
            KitScreen(title = "List") {
                Box(probe) { KitRow("Row 1", "preview 1", onClick = { navigator.openChat(ref(1)) }, growKey = ref(1)) }
            }
        }
        // The conversation's shape: a page that grows from a row, with the chat's frame (overlaid chrome over a recorded
        // body, a status line, the loading line) and a message box that is a BoxWithConstraints holding its own
        // subcomposition, drawn while the conversation loads.
        entry<ChatRoute> { route ->
            KitGrowPage(growKey = route.ref) {
                val frost = rememberKitFrostState()
                Box(Modifier.fillMaxSize().testTag("page-frame")) {
                    KitFrameScreen(
                        page = true,
                        overlayChrome = true,
                        frost = frost,
                        topBar = { KitText("Chat") },
                        status = line,
                        loading = !live,
                        loadingLabel = "Loading the conversation",
                        bottom = { MessageBox() },
                    ) {
                        Box(Modifier.fillMaxSize().kitFrostSource(frost).then(probe)) {
                            KitText(if (live) "transcript line" else "skeleton line")
                        }
                    }
                }
            }
        }
    }

    /** The composer's nesting: its BoxWithConstraints, the box, the words column that gains a tray and chips. */
    @Composable
    private fun MessageBox() {
        BoxWithConstraints(probe.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
                Box {
                    Column(probe) {
                        if (live) KitText("tray line")
                        KitField(
                            state = words,
                            label = "Message",
                            kind = KitFieldKind.Multiline,
                            labelShown = false,
                            framed = false,
                            composer = true,
                            multilineLines = 1..5,
                        )
                        if (live) BoxWithConstraints { KitText("chips ${maxWidth.value.toInt()}") }
                    }
                }
            }
        }
    }
}
