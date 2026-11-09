@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import io.github.eslamasabry.oc.ui.design.color.PackId
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Phone-size shots of every offered look (bg-looks 2026-11-10): 360×800 dp at density 3 (1080×2400 px), Normal strength,
 * light and dark, on the Opencode and Nord packs, behind a page of chat text and a card, through the real page ground
 * ([KitLiveBackground]: half-resolution layer, the host's uniforms). One full phone screen per PNG, named
 * `<look>-<pack>-<mode>.png`. Runs only with BG_LOOKS_OUT set; BG_LOOKS_ONLY=id,id limits the looks, BG_LOOKS_PACKS the
 * packs (storage names), BG_LOOKS_TIME the clock second (default: the still frame's).
 */
class LiveLookPhoneShots {
    private class ShaderPlatform(base: KitPlatform = FakeKitPlatform()) : KitPlatform by base {
        override val liveShaders: LiveShaderCompiler = LiveShaderCompiler { DesktopLiveShader.compile(it) }

        override fun liveFinish(ground: androidx.compose.ui.graphics.Color): RenderEffect? = DesktopLiveShader.finish(ground)
    }

    @Test
    fun `phone shots`() {
        val out = System.getenv("BG_LOOKS_OUT")?.let(::File) ?: return
        val only = System.getenv("BG_LOOKS_ONLY")?.split(',')?.filter { it.isNotBlank() }?.toSet()
        val packs =
            System.getenv("BG_LOOKS_PACKS")?.split(',')?.map { PackId.fromStorage(it) }
                ?: listOf(PackId.Opencode, PackId.Nord)
        out.mkdirs()
        val ids = LiveLooks.offered.filter { only == null || it in only }
        for (pack in packs) for (dark in listOf(true, false)) for (id in ids) {
            val name = "$id-${pack.storageName}-${if (dark) "dark" else "light"}.png"
            // The painted aurora is not drawn under a still-frame host, so it is shot on a held clock instead.
            val painted = LiveLooks.shader(id) == null
            runDesktopComposeUiTest(WIDTH, HEIGHT) {
                if (painted) mainClock.autoAdvance = false
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                        KitTheme(
                            KitAppearance(
                                mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                                pack = pack,
                                liveBackground = true,
                                liveStyle = id,
                                liveStrength = 1f,
                            ),
                            ShaderPlatform(),
                            "en",
                            stillFrame = !painted,
                        ) {
                            KitLiveBackground(Modifier.fillMaxSize()) { Page() }
                        }
                    }
                }
                if (painted) mainClock.advanceTimeBy(800) else waitForIdle()
                ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(out, name))
            }
        }
    }

    /** Desktop with Skia's blur, so Glass panes frost what lies behind them as on Android 12+. */
    private class GlassPlatform(base: KitPlatform = FakeKitPlatform()) : KitPlatform by base {
        override val liveShaders: LiveShaderCompiler = LiveShaderCompiler { DesktopLiveShader.compile(it) }

        override fun liveFinish(ground: androidx.compose.ui.graphics.Color): RenderEffect? = DesktopLiveShader.finish(ground)

        override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect {
            val r = FrostPolicy.blurRadiusPx(spec.sigmaDp, density) * .57735f + .5f
            return BlurEffect(r, r, TileMode.Clamp)
        }
    }

    /**
     * The owner's case (2026-11-10, "the glow ends in a very teary way"): a Settings page in Glass, dark, a glass card of
     * rows, over the look (glowSpill by default; BG_LOOKS_GLASS=id,id for others). Named `<look>-glass-settings-<pack>-<mode>.png`.
     */
    @Test
    fun `glass settings shots`() {
        val out = System.getenv("BG_LOOKS_OUT")?.let(::File) ?: return
        val ids = System.getenv("BG_LOOKS_GLASS")?.split(',')?.filter { it.isNotBlank() } ?: listOf("glowSpill")
        val packs =
            System.getenv("BG_LOOKS_PACKS")?.split(',')?.map { PackId.fromStorage(it) }
                ?: listOf(PackId.Opencode, PackId.Nord)
        out.mkdirs()
        for (pack in packs) for (dark in listOf(true, false)) for (id in ids) {
            val name = "$id-glass-settings-${pack.storageName}-${if (dark) "dark" else "light"}.png"
            runDesktopComposeUiTest(WIDTH, HEIGHT) {
                mainClock.autoAdvance = false
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                        KitTheme(
                            KitAppearance(
                                mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                                pack = pack,
                                style = Style.Glass,
                                liveBackground = true,
                                liveStyle = id,
                                liveStrength = 1f,
                            ),
                            GlassPlatform(),
                            "en",
                        ) {
                            KitLiveBackground(Modifier.fillMaxSize()) { SettingsPage() }
                        }
                    }
                }
                repeat(3) { mainClock.advanceTimeByFrame() }
                ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(out, name))
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun SettingsPage() {
        Column(Modifier.fillMaxSize().padding(top = 52.dp)) {
            KitText("Settings", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), role = TypeRole.LargeTitle)
            for (group in listOf(listOf("Appearance", "Notifications", "Voice"), listOf("Language", "Privacy and data", "Updates", "Storage"))) {
                KitSurface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), SurfaceLevel.Surface1, KitShapeId.Card) {
                    Column {
                        group.forEach { KitText(it, Modifier.padding(vertical = 14.dp), role = TypeRole.RowTitle) }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Page() {
        val side = Modifier.padding(horizontal = 20.dp)
        Column(Modifier.fillMaxSize().padding(top = 52.dp), verticalArrangement = Arrangement.Top) {
            KitText("Fix the login redirect", side.padding(bottom = 4.dp), role = TypeRole.LargeTitle)
            KitText("web-app · main · laptop", side.padding(bottom = 20.dp), role = TypeRole.Secondary, tone = TextTone.Secondary)
            KitText(
                "On slow networks the login page sends people back to the start after they sign in. The redirect " +
                    "runs before the session cookie is written, so the guard sees no session and bounces them.",
                side.padding(bottom = 14.dp),
            )
            KitText(
                "I moved the redirect after the cookie write and added a short wait on the first request. " +
                    "Here is what changed:",
                side.padding(bottom = 16.dp),
            )
            KitSurface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), SurfaceLevel.Surface1, KitShapeId.Card) {
                Column {
                    KitText("auth/redirect.ts", role = TypeRole.Title)
                    Spacer(Modifier.height(6.dp))
                    KitText("Waits for the session before it leaves the sign-in page.", role = TypeRole.Secondary, tone = TextTone.Secondary)
                    Spacer(Modifier.height(10.dp))
                    KitText("+ 12 lines   − 4 lines", role = TypeRole.Secondary, tone = TextTone.Tertiary)
                }
            }
            Spacer(Modifier.height(18.dp))
            KitText(
                "The tests pass on a throttled connection now. Do you want me to open a pull request, or keep " +
                    "going on the second bug you mentioned?",
                side.padding(bottom = 14.dp),
            )
            KitText("2 minutes ago", side, role = TypeRole.Secondary, tone = TextTone.Tertiary)
        }
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 2400
        const val DENSITY = 3f
    }
}
