package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pictures of the edge dock in each state at phone width (360 and 412 dp), dark and light, LTR and RTL, written to
 * `looks-out/dock/` for a person to look at. They compare against nothing. Motion is Off so every spring lands at once;
 * the motion itself (overshoot, shake, pulse, comet) must be judged on a device from a frame-stepped recording.
 */
@OptIn(ExperimentalTestApi::class)
class KitEdgeDockLooksTest {
    private val out = File(System.getProperty("user.dir"), "../../../looks-out/dock").canonicalFile.also { it.mkdirs() }

    private val rings =
        listOf(
            KitDockRing(
                "bank", "Bank API", "claude", KitDockRingState.WorkingWithTasks(3, 7),
                children = listOf(
                    KitDockChild("explore", "explore", KitDockRingState.Working, lastLine = "Reading how transfers are retried"),
                    KitDockChild("tests", "tests", KitDockRingState.WorkingWithTasks(2, 5), lastLine = "Running the transfer suite"),
                    KitDockChild("docs", "docs", KitDockRingState.Finished, lastLine = "Updated the API notes"),
                ),
                lastLine = "Tests pass. Next is the migration.",
                peek = listOf(
                    KitDockLine(KitDockLineRole.Person, "Add idempotency keys to the transfer endpoint."),
                    KitDockLine(KitDockLineRole.Agent, "I will read a key from the request header and store it."),
                    KitDockLine(KitDockLineRole.Tool, "Edited src/transfers/create.ts +6 -1"),
                    KitDockLine(KitDockLineRole.Tool, "Ran npm test transfers: 41 passed"),
                    KitDockLine(KitDockLineRole.Agent, "Tests pass. Next is the migration."),
                ),
            ),
            KitDockRing("pong", "Pong", "opencode", KitDockRingState.Working, lastLine = "Next I need to build it."),
            KitDockRing(
                "iptv", "IPTV", null, KitDockRingState.NeedsYou, causedBy = "thumbs",
                children = listOf(
                    KitDockChild("profile", "profile", KitDockRingState.Working),
                    KitDockChild("trace", "trace", KitDockRingState.WorkingWithTasks(1, 3)),
                    KitDockChild("thumbs", "thumbs", KitDockRingState.NeedsYou),
                    KitDockChild("cache", "cache", KitDockRingState.Failed),
                ),
                childOverflow = 1,
                lastLine = "Wants to write cache/thumbs",
            ),
            KitDockRing("lottie", "Lottie", null, KitDockRingState.Failed, lastLine = "The build stopped."),
            KitDockRing("docs", "Docs", null, KitDockRingState.Finished, lastLine = "Paused after the install section."),
            KitDockRing("idle", "Notes", null, KitDockRingState.Idle),
        )

    private fun model(tucked: Boolean = false, side: KitDockSide = KitDockSide.End, pinned: Boolean = false) =
        KitDockModel(
            rings = if (pinned) rings.mapIndexed { i, r -> if (i == 0) r.copy(pinned = true) else r } else rings,
            hidden = listOf(KitDockRing("hidden", "Old task", null, KitDockRingState.Working)),
            current = "bank",
            tucked = tucked,
            placement = KitDockPlacement(side, 0.5f),
        )

    private class Shot(
        val name: String,
        val width: Int = 412,
        val dark: Boolean = true,
        val rtl: Boolean = false,
        val scale: Float = 1f,
        val tucked: Boolean = false,
        val side: KitDockSide = KitDockSide.End,
        val pinned: Boolean = false,
        val act: (KitEdgeDockState) -> Unit = {},
    )

    @Composable
    private fun Behind() {
        // Something to sit over: lines of reading text across the whole width.
        Column(Modifier.fillMaxSize().background(LocalKitColors.current.ground).padding(16.dp)) {
            repeat(28) { KitText("The channel list stutters when I scroll fast; thumbnails decode on the main thread.", role = TypeRole.Body, maxLines = 1) }
        }
    }

    private fun shoot(shot: Shot) {
        val state = KitEdgeDockState()
        runDesktopComposeUiTest(shot.width, 915) {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, shot.scale),
                    LocalLayoutDirection provides if (shot.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    KitTheme(
                        KitAppearance(mode = if (shot.dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off),
                        FakeKitPlatform(),
                        if (shot.rtl) "ar" else "en",
                        stillFrame = true,
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            Behind()
                            KitEdgeDock(
                                model(shot.tucked, shot.side, shot.pinned),
                                object : KitDockIntents {},
                                TestDockWords,
                                insets = KitDockInsets(top = 24.dp, bottom = 48.dp, keepClear = listOf(DpRect(340.dp, 830.dp, 412.dp, 867.dp))),
                                state = state,
                            )
                        }
                    }
                }
            }
            waitForIdle()
            runOnIdle { shot.act(state) }
            waitForIdle()
            onNodeWithTag("kit-edge-dock").assertExists()
            val image = onAllNodes(isRoot())[0].captureToImage().toAwtImage()
            val suffix = (if (shot.dark) "dark" else "light") + (if (shot.rtl) "-rtl" else "") + "-${shot.width}" + (if (shot.scale != 1f) "-x${shot.scale}" else "")
            val file = File(out, "${shot.name}-$suffix.png")
            ImageIO.write(image, "png", file)
            assertTrue(file.length() > 0)
        }
    }

    @Test
    fun `the dock at rest, tucked, on the start edge and in RTL`() {
        for (w in listOf(360, 412)) {
            shoot(Shot("rest", w))
            shoot(Shot("rest", w, dark = false))
        }
        shoot(Shot("rest", scale = 1.3f))
        shoot(Shot("pinned", pinned = true))
        shoot(Shot("nub", tucked = true))
        shoot(Shot("start-edge", side = KitDockSide.Start))
        shoot(Shot("rest", rtl = true))
    }

    @Test
    fun `the swell while scrubbing - a middle ring, the first and the last, with 3 and 5 sub-agents, a request`() {
        for (w in listOf(360, 412)) {
            shoot(Shot("scrub-middle", w) { it.openSwell(SwellKind.Scrub, 1) })
            shoot(Shot("scrub-first-subs3", w) { it.openSwell(SwellKind.Scrub, 0) })
            shoot(Shot("scrub-last", w) { it.openSwell(SwellKind.Scrub, 5) })
            shoot(Shot("scrub-subs5-needs", w) { it.openSwell(SwellKind.Scrub, 2) })
        }
        shoot(Shot("scrub-subs5-needs", scale = 1.3f) { it.openSwell(SwellKind.Scrub, 2) })
        shoot(Shot("scrub-subs5-needs", rtl = true) { it.openSwell(SwellKind.Scrub, 2) })
        shoot(Shot("scrub-subs5-needs", dark = false) { it.openSwell(SwellKind.Scrub, 2) })
    }

    @Test
    fun `the peek card, rename and the pulled-out list`() {
        for (w in listOf(360, 412)) {
            shoot(Shot("peek", w) { it.openSwell(SwellKind.Peek, 0) })
            shoot(Shot("rename", w) { it.startRename(1) })
            shoot(Shot("list", w) { it.openPanel() })
        }
        shoot(Shot("list", dark = false) { it.openPanel() })
        shoot(Shot("list", rtl = true) { it.openPanel() })
        shoot(Shot("list-pinned", pinned = true) { it.openPanel() })
    }

    @Test
    fun `a ring being reordered`() {
        shoot(
            Shot("reorder") {
                it.carrying = 3
                it.carryTo = 1
                it.carryY = it.slotY(1) + 10f
            },
        )
    }
}
