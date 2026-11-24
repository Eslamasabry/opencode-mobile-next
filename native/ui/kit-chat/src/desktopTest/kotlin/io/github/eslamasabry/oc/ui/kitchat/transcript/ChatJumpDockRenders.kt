@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarScope
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.turn.KitTurnLiveLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KitTurnLiveTone
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Not a check: pictures of the docked "Jump to latest" row (owner decision O2 = D, 2026-11-24) for a person to look at,
 * at 360 dp in Solid. Three states: `rest` (a reply runs, following), `scrolled` (a reply runs, the person dragged the
 * list up the conversation) and `scrolled-idle` (no reply, dragged up); each floating and not, dark and light.
 * `scrolled` also at text 1.3 and in right-to-left. Written as `dock-<state>-<look>-<mode>-<scale>-<dir>.png` under
 * `DOCK_RENDER_DIR` (default: build/dock-renders).
 */
class ChatJumpDockRenders {
    private data class Picture(
        val name: String,
        val floating: Boolean,
        val dark: Boolean,
        /** A reply runs. */
        val live: Boolean,
        /** The person dragged the list up (else: at rest). */
        val dragged: Boolean,
        val scale: Float = 1f,
        val rtl: Boolean = false,
    )

    private val rows =
        (0 until 40).map { i -> "Row $i: the build finished and every check passed, then the notes were written." }

    /** The page's list state, hoisted out of the composition. */
    private lateinit var list: TranscriptListState

    @Test
    fun renders() {
        val out = File(System.getenv("DOCK_RENDER_DIR") ?: "build/dock-renders").apply { mkdirs() }
        for (picture in pictures()) shot(File(out, "${picture.name}.png"), picture)
    }

    private fun pictures(): List<Picture> = buildList {
        for (state in listOf("rest", "scrolled", "scrolled-idle"))
            for (look in listOf("floating", "solid"))
                for (mode in listOf("dark", "light"))
                    add(
                        Picture(
                            name = "dock-$state-$look-$mode-1.0-ltr",
                            floating = look == "floating",
                            dark = mode == "dark",
                            live = state != "scrolled-idle",
                            dragged = state != "rest",
                        )
                    )
        for (look in listOf("floating", "solid"))
            for (mode in listOf("dark", "light")) {
                val floating = look == "floating"
                val dark = mode == "dark"
                add(
                    Picture(
                        name = "dock-scrolled-$look-$mode-1.3-ltr",
                        floating = floating,
                        dark = dark,
                        live = true,
                        dragged = true,
                        scale = 1.3f,
                    )
                )
                add(
                    Picture(
                        name = "dock-scrolled-$look-$mode-1.0-rtl",
                        floating = floating,
                        dark = dark,
                        live = true,
                        dragged = true,
                        rtl = true,
                    )
                )
            }
    }

    private fun shot(file: File, picture: Picture) =
        runDesktopComposeUiTest(W, H) {
            setContent { Themed(picture) { Page(picture) } }
            waitForIdle()
            if (picture.dragged) dragTowardOlder()
            mainClock.advanceTimeBy(800)
            waitForIdle()
            ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", file)
        }

    /**
     * A person's slow drag that reads older rows (the finger moves down, the list goes 120 dp up the conversation); it
     * rests before the finger lifts, so nothing flings.
     */
    private fun ComposeUiTest.dragTowardOlder() {
        onNodeWithTag("transcript-list").performTouchInput {
            down(center)
            moveBy(Offset(0f, viewConfiguration.touchSlop))
            repeat(12) { moveBy(Offset(0f, 10f)) }
            advanceEventTime(200)
            up()
        }
    }

    @Composable
    private fun Themed(picture: Picture, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalDensity provides Density(1f, picture.scale),
            LocalLayoutDirection provides if (picture.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            KitTheme(
                KitAppearance(
                    mode = if (picture.dark) AppearanceMode.Dark else AppearanceMode.Light,
                    style = Style.Solid,
                    motion = MotionLevel.Full,
                ),
                DesktopKitPlatform(),
                if (picture.rtl) "ar" else "en",
                stillFrame = true,
            ) {
                Inseted(content)
            }
        }
    }

    /** The scene's 360 by 780 box and the phone's status and gesture bars the harness stands in for. */
    @Composable
    private fun Inseted(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalKitScreenInsets provides WindowInsets(top = STATUS_DP.dp, bottom = NAV_DP.dp),
        ) {
            Box(Modifier.size(W.dp, H.dp).testTag("scene")) { content() }
        }
    }

    /** The page: a header capsule over the transcript, the message box over it. */
    @Composable
    private fun Page(picture: Picture) {
        val source = rememberKitFrostState()
        list = rememberTranscriptListState()
        KitScreen(
            page = true,
            overlayChrome = true,
            frost = source,
            floating = picture.floating,
            topBar = { Bar() },
            bottom = { Composer() },
            edges = KitScreenEdges.Bleed,
        ) {
            Box(Modifier.fillMaxSize().kitFrostSource(source)) { Rows(picture) }
        }
    }

    /** The transcript: a running reply's live line above 40 rows (newest first). */
    @Composable
    private fun Rows(picture: Picture) {
        KitTranscriptList(
            state = list,
            newestKey = if (picture.live) LIVE_KEY else rows.first(),
            jumpLabel = "Jump to latest",
            motion = KitMotionLevel.Off,
            dockLive = if (picture.live) DOCK_LIVE else null,
        ) {
            if (picture.live) item(key = LIVE_KEY, contentType = LIVE_KEY) {
                Box(kitTranscriptItemMotion()) { KitTurnLiveLine("Working…", Modifier.padding(horizontal = 16.dp)) }
            }
            items(rows, key = { it }, contentType = { "row" }) { text ->
                KitText(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), role = TypeRole.Body)
            }
        }
    }

    @Composable
    private fun Bar() {
        KitTopBar(
            "Tidy the README",
            scope = {
                KitTopBarScope(
                    "demo-shop",
                    "This phone",
                    "Files of demo-shop",
                    onProject = {},
                    branch = "Git",
                    onBranch = {},
                )
            },
            exit = KitTopBarExit.Back,
            exitLabel = "Back",
            onExit = {},
            backBesideTitle = true,
        )
    }

    @Composable
    private fun Composer() {
        KitComposerBox(
            running = false,
            stacked = true,
            hasLeading = true,
            field = {
                KitText("Ask OpenCode…", role = TypeRole.Body, tone = TextTone.Tertiary, kind = KitTextKind.Frame)
            },
            leading = { KitComposerAdd("Tools", onClick = {}, open = false) },
            trailing = { KitComposerSend("Send", onClick = {}) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }

    private companion object {
        const val W = 360
        const val H = 780
        const val STATUS_DP = 28
        const val NAV_DP = 20
        const val LIVE_KEY = "live"

        val DOCK_LIVE = KitTranscriptDockLive("Working…", KitTurnLiveTone.Working, "Working…", false)
    }
}
