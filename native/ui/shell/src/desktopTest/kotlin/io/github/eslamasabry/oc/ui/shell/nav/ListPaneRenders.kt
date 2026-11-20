package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitRow
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.RouteMotion
import io.github.eslamasabry.oc.ui.shell.NavShell
import io.github.eslamasabry.oc.ui.shell.ShellDependencies
import io.github.eslamasabry.oc.ui.shell.ShellNavigator
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Pictures only (asserts nothing): the shell at tablet and phone sizes with a Home and a conversation made of kit parts,
 * dark, density 2, into `build/bigscreen/list-pane/`. Big screens slice 3 (BS-LIST-PANE); the phone sizes are the
 * before/after pixel proof.
 */
@OptIn(ExperimentalTestApi::class)
class ListPaneRenders {
    private data class Size(val key: String, val w: Int, val h: Int, val fontScale: Float = 1f)

    private val sizes =
        listOf(
            Size("phone", 360, 780),
            Size("phone412", 412, 915),
            Size("split600", 600, 800),
            Size("w840", 840, 800),
            Size("w1024", 1024, 768),
            Size("tabl", 1280, 800),
            Size("tabl-t13", 1280, 800, 1.3f),
            Size("tabp", 800, 1280),
            Size("tabp900", 900, 1280),
        )

    private val talks =
        listOf(
            "Fix the login redirect loop" to "Claude · oc_app · Waiting for you: allow npm test?",
            "Release notes for 3.0.1" to "Codex · docs · Finished 4 min ago",
            "Why does the widget stay blank on ZTE launchers" to "OpenCode · widgets · Running: reading RemoteViews",
            "Tablet layout: list beside the conversation" to "Claude · native · 12 files changed",
            "Rename the backup folder" to "OpenCode · scripts · Yesterday",
            "Translate the new strings into Arabic" to "Claude · words · Yesterday",
            "Crash on rotation in Files" to "Codex · files · Mon",
            "Speed up the transcript scroll" to "Claude · kit-chat · Mon",
            "Server keeps asking for the password" to "OpenCode · servers · Sun",
            "Plan the voice mode" to "Claude · voice · Sat",
            "Check the Firebase test lab run" to "Codex · ci · Fri",
            "Move the edge light into the dock" to "Claude · shell · Thu",
        )

    private fun ref(i: Int) = ConversationRef(ProfileId("demo"), SessionId("t$i"))

    private inner class Shell {
        val navigator = ShellNavigator()
        private val installer = EntryInstaller {
            entry<ChatsRoute.Home> {
                KitScreen(title = "Conversations") {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items(talks.indices.toList(), key = { it }) { i ->
                            KitRow(
                                talks[i].first,
                                talks[i].second,
                                onClick = { navigator.openChat(ref(i)) },
                                modifier = Modifier.testTag("row-$i"),
                            )
                        }
                    }
                    KitButton("New conversation", onClick = {})
                }
            }
            entry<ChatsRoute.New> {
                KitScreen(title = "New conversation", backLabel = "Back", onBack = navigator::back) {
                    KitText("oc_app · Claude Sonnet 4.5")
                    KitText("What should we work on?")
                    KitButton("Start", onClick = {})
                }
            }
            entry<ChatRoute> { route ->
                val i = route.ref.sessionId.raw.removePrefix("t").toInt()
                KitScreen(title = talks[i].first, backLabel = "Back", onBack = navigator::back) {
                    KitText("You: The login page sends me back to itself after I sign in. Can you find out why?")
                    KitText(
                        "Claude: The redirect target is read before the session cookie is written, so the guard " +
                            "still sees a signed-out visitor and sends the browser back to /login. Moving the cookie " +
                            "write ahead of the redirect fixes it; I changed auth/session.ts and added a test.",
                    )
                    KitText("Claude wants to run npm test in oc_app. Allow it once, or always for this project?")
                    KitButton("Allow once", onClick = {})
                }
            }
        }
        val deps = ShellDependencies(navigator, setOf(installer), FakeSystemMotion(0f))
        private val owner =
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher: NavigationEventDispatcher = NavigationEventDispatcher()
            }

        @Composable
        fun Content() {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                NavShell(start = ChatsRoute.Home, deps = deps, gate = remember { RowTransformGate() })
            }
        }
    }

    private fun shoot(scene: String, setup: ComposeUiTest.(Shell) -> Unit) {
        val out = File("build/bigscreen/list-pane").also { it.mkdirs() }
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("en"))
        ChatRoute.openMotion = RouteMotion.SharedAxisX
        try {
            val platform = DesktopKitPlatform()
            for (size in sizes) {
                runDesktopComposeUiTest(size.w * 2, size.h * 2) {
                    val shell = Shell()
                    setContent {
                        CompositionLocalProvider(
                            LocalDensity provides Density(2f, size.fontScale),
                            LocalKitPlatform provides platform,
                        ) {
                            KitTheme(
                                appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                                platform = platform,
                                localeTag = "en",
                                stillFrame = true,
                            ) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { shell.Content() }
                            }
                        }
                    }
                    waitForIdle()
                    setup(shell)
                    waitForIdle()
                    mainClock.advanceTimeBy(1_000)
                    waitForIdle()
                    val image = onRoot().captureToImage().toAwtImage()
                    ImageIO.write(image, "png", File(out, "listpane-$scene-${size.key}.png"))
                }
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun home() = shoot("home") {}

    @Test
    fun conversation() = shoot("chat") { runOnIdle { it.navigator.openChat(ref(0)) } }

    /** A keyboard on a tablet: focus on a list row (its ring) while a conversation is open. */
    @Test
    fun focus() =
        shoot("focus") {
            runOnIdle { it.navigator.openChat(ref(0)) }
            waitForIdle()
            // On a phone the list is under the conversation: nothing to focus there.
            if (onAllNodesWithTag("row-1").fetchSemanticsNodes().isNotEmpty()) {
                onNodeWithTag("row-1").requestFocus()
                onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            }
        }
}
