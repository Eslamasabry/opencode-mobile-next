@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBodyDrawn
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderCompiler
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The edge dock's glass face (owner 2026-11-11, "Why isn't it glass?"). Under Glass with a lens and a recording of what
 * lies behind it in scope, the dock is one body of liquid glass drawn by one node; without a recording, under Solid and
 * under reduced motion it keeps today's face. The platform is desktop's own join shader and lens pass (the same shader
 * text Android runs as AGSL), so the pictures show the real bend, rim light and frost, at desktop's blur.
 */
class KitEdgeDockGlassTest {
    private val drawn = SemanticsMatcher.keyIsDefined(KitLiquidBodyDrawn)

    private fun lens(): FakeKitPlatform =
        FakeKitPlatform(
            liquidGlass = true,
            liveShaders = LiveShaderCompiler(DesktopLiveShader::compile),
            joinEffect = { p, b -> DesktopKitPlatform().liquidJoinEffect(p, b) },
        )

    private val rings =
        listOf(
            KitDockRing("explain", "Explain this project", null, KitDockRingState.Thinking, lastLine = "What else is there?"),
            KitDockRing("bank", "Bank API", "claude", KitDockRingState.WorkingWithTasks(3, 7), lastLine = "Tests pass. Next is the migration."),
            KitDockRing("docs", "Docs", null, KitDockRingState.Finished, lastLine = "Paused after the install section."),
            KitDockRing("idle", "Notes", null, KitDockRingState.Idle),
        )

    private fun model(tucked: Boolean = false) =
        KitDockModel(rings = rings, current = "explain", tucked = tucked, placement = KitDockPlacement(KitDockSide.End, 0.5f))

    /** A page of reading text, recorded for the dock (as the chat's transcript is), and the dock beside the recording. */
    @Composable
    private fun Scene(state: KitEdgeDockState, recorded: Boolean, tucked: Boolean) {
        val source = rememberKitFrostState()
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().kitFrostSource(source).background(Color(0xFF15171C)).padding(16.dp)) {
                repeat(26) { i ->
                    if (i % 7 == 3) Box(Modifier.fillMaxWidth().height(28.dp).background(Color(0xFFE8E2D0)))
                    else KitText("Pressure and ink opacity come from local speed; each sample is stamped as soft circles.", role = TypeRole.Body, maxLines = 1)
                }
            }
            CompositionLocalProvider(LocalKitDockGlass provides (if (recorded) source else null)) {
                KitEdgeDock(
                    model(tucked),
                    object : KitDockIntents {},
                    TestDockWords,
                    insets = KitDockInsets(top = 24.dp, bottom = 48.dp, keepClear = listOf(DpRect(340.dp, 830.dp, 412.dp, 867.dp))),
                    state = state,
                )
            }
        }
    }

    /** Whether the dock drew its own glass body; [act] opens a part of it first; [png] saves the picture. */
    private fun glassBody(
        appearance: KitAppearance,
        recorded: Boolean = true,
        tucked: Boolean = false,
        png: File? = null,
        act: (KitEdgeDockState) -> Unit = {},
    ): Boolean {
        var found = false
        val state = KitEdgeDockState()
        runDesktopComposeUiTest(412, 915) {
            mainClock.autoAdvance = false
            setContent { KitTheme(appearance, lens(), "en", stillFrame = true) { Scene(state, recorded, tucked) } }
            mainClock.advanceTimeBy(500)
            runOnIdle { act(state) }
            mainClock.advanceTimeBy(1_500)
            found = onAllNodes(drawn, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            if (png != null) {
                png.parentFile.mkdirs()
                ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", png)
                assertTrue(png.length() > 0)
            }
        }
        return found
    }

    private val glass = KitAppearance(style = Style.Glass, mode = AppearanceMode.Dark)

    @Test
    fun `dock glass - under Glass with a lens and a recording the dock draws its own glass body`() {
        assertTrue(glassBody(glass), "at rest")
        assertTrue(glassBody(glass, tucked = true), "tucked to the nub")
    }

    @Test
    fun `dock glass - without a recording, under Solid and under reduced motion it keeps today's face`() {
        assertEquals(false, glassBody(glass, recorded = false), "no recording in scope")
        assertEquals(false, glassBody(glass.copy(style = Style.Solid)), "Solid")
        assertEquals(false, glassBody(glass.copy(motion = MotionLevel.Off)), "reduced motion")
        assertEquals(false, glassBody(glass.copy(glassLook = io.github.eslamasabry.oc.ui.design.style.GlassLookId.Frosted)), "the Frosted look")
    }

    /** The pictures for the glass notes (docs/designs/glass-in-the-kit/), Glass dark over a page of text. */
    private val docs = File(System.getProperty("user.dir"), "../../../docs/designs/glass-in-the-kit").canonicalFile

    @Test
    fun `dock glass - picture at rest`() {
        assertTrue(glassBody(glass, png = File(docs, "render-dock-rest.png")))
    }

    @Test
    fun `dock glass - picture with the swell open`() {
        assertTrue(glassBody(glass, png = File(docs, "render-dock-swell.png")) { it.openSwell(SwellKind.Scrub, 0) })
    }

    @Test
    fun `dock glass - picture with the list out`() {
        assertTrue(glassBody(glass, png = File(docs, "render-dock-list.png")) { it.openPanel() })
    }
}
