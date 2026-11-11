@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassBlurs
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import io.github.eslamasabry.oc.ui.kit.row.KitFeedAgent
import io.github.eslamasabry.oc.ui.kit.row.KitPanelLinkRow
import io.github.eslamasabry.oc.ui.kit.row.KitRecentRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowPanel
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A home-like page under Glass, dark, 360 dp wide, with the in-flow panes in their clear state over the painted aurora
 * (docs/designs/glass-in-the-kit.md, step 1, check 3): a panel of five recent rows and a short second panel. Desktop
 * has no lens; in-flow panes need none. Motion is Calm, not Off: Off is reduced motion, which holds the frost and draws
 * the thick fallback, and Full never goes idle. Not a test: it runs only when `GLASS_RECIPE_RENDER_OUT` names a folder,
 * and writes `GLASS_RECIPE_RENDER_NAME` (default `render-dark.png`) there.
 */
class GlassRecipeRenders {
    @Test
    fun `glass recipe render`() {
        val out = System.getenv("GLASS_RECIPE_RENDER_OUT")?.let(::File) ?: return
        out.mkdirs()
        val name = System.getenv("GLASS_RECIPE_RENDER_NAME") ?: "render-dark.png"
        var clear = false
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                    KitTheme(
                        KitAppearance(
                            mode = AppearanceMode.Dark,
                            motion = MotionLevel.Calm,
                            style = Style.Glass,
                            liveBackground = true,
                            liveStyle = "auroraImage",
                        ),
                        FakeKitPlatform(frost = true, liquidGlass = true),
                        "en",
                    ) {
                        KitLiveBackground(Modifier.fillMaxSize()) {
                            clear = kitClearPanesOn() && kitGlassBlurs(LocalKitGlassBackdrop.current)
                            Home()
                        }
                    }
                }
            }
            waitForIdle()
            ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(out, name))
        }
        println("glass recipe render: $name clear=$clear")
        assertTrue(clear, "the panes were not in their clear state: the render shows the fallback")
    }

    @Composable
    private fun Home() {
        val agent = KitFeedAgent("build", "Build")
        Column(Modifier.padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            KitText("Conversations", Modifier.padding(horizontal = 16.dp), role = TypeRole.LargeTitle)
            KitRowPanel(label = "Recent", prominentDividers = true) {
                KitRecentRow("What's latest news on DeepSeek harness?", agent, "6h ago", {}, preview = "Made you something. A process is running on port 8150")
                KitRecentRow("Hi", agent, "3d ago", {}, preview = "Created notes/check.txt with one line and ran ls")
                KitRecentRow("You know who you are and where are you working", agent, "4d ago", {}, preview = "Got it, I rebuilt it from scratch. Refresh here")
                KitRecentRow("Hi", agent, "4d ago", {}, preview = "PC Health Check: System, OS Ubuntu 24.04")
                KitRecentRow("Hi", agent, "4d ago", {}, meta = "new · This phone")
            }
            KitRowPanel(prominentDividers = true) {
                KitPanelLinkRow("All conversations", "12", {})
            }
        }
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 2400
        const val DENSITY = 3f
    }
}
