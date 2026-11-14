package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen as KitFrameScreen
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
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
 * right after the conversation went from loading to live and KitScreen moved its bottom block between its two layouts.
 * Every page was in that pass because the navigation sat in a `SharedTransitionLayout`. As the app composes the shell,
 * no page (a list, or a conversation that grows from a row) is ever measured in a lookahead pass, through that same
 * change, so that crash cannot happen.
 */
@OptIn(ExperimentalTestApi::class)
class ShellLookaheadTest {
    @Test
    fun `B-NAV-121 no page is measured in a lookahead pass while its subcomposed children change`() =
        runDesktopComposeUiTest(W, H) {
            val platform = DesktopKitPlatform()
            val navigator = ShellNavigator()
            val page = ProbedPages()
            val deps = ShellDependencies(navigator, setOf(page.installer), FakeSystemMotion())
            setContent {
                CompositionLocalProvider(LocalKitPlatform provides platform) {
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
            onNodeWithText("home line").assertExists()
            runOnIdle { navigator.openChat(ref(1)) }
            waitForIdle()
            onNodeWithText("words line").assertExists()
            onNodeWithText("skeleton line").assertExists()
            // Loading → live: the screen changes layout, the message box moves and gains subcomposed children.
            runOnIdle { page.live = true }
            waitForIdle()
            onNodeWithText("transcript line").assertExists()
            onNodeWithText("tray line").assertExists()
            // And back (a reload): the box moves again and loses them.
            runOnIdle { page.live = false }
            waitForIdle()
            onNodeWithText("tray line").assertDoesNotExist()
            onNodeWithText("words line").assertExists()
            assertTrue(page.passes > 0, "the probes were measured")
            assertEquals(0, page.lookaheadPasses, "no page is measured in a lookahead pass")
        }

    private companion object {
        const val W = 412
        const val H = 860
    }
}

/** A home page and a conversation page whose parts count their measure passes, and the lookahead ones among them. */
private class ProbedPages {
    var live by mutableStateOf(false)
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
                Box(probe) { KitText("home line") }
            }
        }
        // The conversation's shape at the crash: a page that grows from a row; while it loads the screen pins its
        // loading line (the stacked layout) with the message box already drawn, and when it comes alive the screen
        // switches to its overlaid layout, moving the box (a BoxWithConstraints holding its own subcomposition) to its
        // new place, and the box gains children.
        entry<ChatRoute> { route ->
            KitGrowPage(growKey = route.ref) {
                KitFrameScreen(
                    topBar = { KitText("Chat") },
                    loading = !live,
                    loadingLabel = "Loading the conversation",
                    overlayChrome = true,
                    bottom = { MessageBox() },
                ) {
                    Box(probe) { KitText(if (live) "transcript line" else "skeleton line") }
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
                        KitText("words line")
                        if (live) BoxWithConstraints { KitText("chips ${maxWidth.value.toInt()}") }
                    }
                }
            }
        }
    }
}
