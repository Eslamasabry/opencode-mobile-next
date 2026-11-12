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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderCompiler
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** The three scenes of the dock follow-ups' pictures (Glass dark, a page of text with bright bands, 412 dp). */
internal object DockFollowUpScenes {
    private val longName = "Quarterly budget review for the phone app and the release notes for the next beta"
    private val rings =
        listOf(
            KitDockRing("one", "name two animals", null, KitDockRingState.Working),
            KitDockRing("two", "count from one to eighty", null, KitDockRingState.Idle),
            KitDockRing("three", longName, null, KitDockRingState.Idle),
            KitDockRing("four", "create a file hello.txt", null, KitDockRingState.Idle),
            KitDockRing("five", "Alpha", null, KitDockRingState.Idle),
            KitDockRing("six", "tell a short joke", null, KitDockRingState.Idle),
            KitDockRing("seven", "name three colours", null, KitDockRingState.Idle),
        )

    private fun lens(): FakeKitPlatform =
        FakeKitPlatform(
            liquidGlass = true,
            liveShaders = LiveShaderCompiler(DesktopLiveShader::compile),
            joinEffect = { p, b -> DesktopKitPlatform().liquidJoinEffect(p, b) },
        )

    @Composable
    private fun Scene(state: KitEdgeDockState, bottom: Float, current: String?) {
        val source = rememberKitFrostState()
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().kitFrostSource(source).background(Color(0xFF15171C)).padding(16.dp)) {
                repeat(26) { i ->
                    if (i % 7 == 3) Box(Modifier.fillMaxWidth().height(28.dp).background(Color(0xFFE8E2D0)))
                    else KitText("Pressure and ink opacity come from local speed; each sample is stamped as soft circles.", role = TypeRole.Body, maxLines = 1)
                }
            }
            CompositionLocalProvider(LocalKitDockGlass provides source) {
                KitEdgeDock(
                    KitDockModel(rings = rings, current = current, placement = KitDockPlacement(KitDockSide.End, 0.5f)),
                    object : KitDockIntents {},
                    TestDockWords,
                    insets = KitDockInsets(top = 24.dp, bottom = bottom.dp),
                    state = state,
                )
            }
        }
    }

    /** [item]: "1" (the keyboard up, the column caught between two rings), "3" (renaming a long name), "4" (the list). */
    fun png(item: String): ByteArray {
        var bytes = ByteArray(0)
        val state = KitEdgeDockState()
        runDesktopComposeUiTest(412, 915) {
            mainClock.autoAdvance = false
            val bottom = if (item == "1") 560f else if (item == "3") 428f + 80f else 48f
            setContent { KitTheme(KitAppearance(style = Style.Glass, mode = AppearanceMode.Dark), lens(), "en", stillFrame = true) { Scene(state, bottom, if (item == "1") "one" else null) } }
            mainClock.advanceTimeBy(500)
            runOnIdle {
                when (item) {
                    "1" -> runBlocking { state.scroll.snapTo(30f) }
                    "3" -> state.startRename(2)
                    else -> state.openPanel()
                }
            }
            mainClock.advanceTimeBy(1_500)
            val out = ByteArrayOutputStream()
            ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", out)
            bytes = out.toByteArray()
        }
        return bytes
    }
}

/** Writes the follow-ups' pictures to docs/designs/glass-in-the-kit/ (render-dock3-<item>-after.png). */
class KitEdgeDockFollowUpPicturesTest {
    private val docs = File(System.getProperty("user.dir"), "../../../docs/designs/glass-in-the-kit").canonicalFile

    private fun save(item: String, name: String) {
        val f = File(docs, name)
        f.parentFile.mkdirs()
        f.writeBytes(DockFollowUpScenes.png(item))
        assertTrue(f.length() > 0)
    }

    @Test
    fun `dock follow-ups - picture, the keyboard up and the column between two rings`() = save("1", "render-dock3-1-after.png")

    @Test
    fun `dock follow-ups - picture, renaming a long name`() = save("3", "render-dock3-3-after.png")

    @Test
    fun `dock follow-ups - picture, the list over a page of rows`() = save("4", "render-dock3-4-after.png")
}
