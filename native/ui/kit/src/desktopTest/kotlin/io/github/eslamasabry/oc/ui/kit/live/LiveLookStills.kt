@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitConversation
import io.github.eslamasabry.oc.ui.kit.row.KitConversationRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * The owner's stills (E20-S28): every shader look through the real page ground ([KitLiveBackground], half-resolution
 * layer, the host's uniforms, the still-frame moment) behind a home list of real kit conversation rows, dark and light,
 * at 1080×2400 (density 2.625). Runs only with LIVE_STILLS_OUT set to the output folder; LIVE_STILLS_ONLY=id,id limits
 * it. Tool: docs/design/live-backgrounds-native/make-stills.sh.
 */
class LiveLookStills {
    private val needsYou = KitRowState("Needs you", KitRowStateTone.NeedsYou)

    private val rows =
        listOf(
            KitConversation("Fix the login redirect on slow networks", "web-app", "2m", null, "main", "laptop"),
            KitConversation("Why does the build cache miss?", "build-tools", "14m", needsYou, server = "laptop"),
            KitConversation("Write release notes for 2.2", "docs", "1h", null, branch = "release/2.2"),
            KitConversation("Move settings into one screen", "mobile", "3h", null, branch = "settings", server = "vps"),
            KitConversation("Find the leak in the image cache", "mobile", "Yesterday", null, server = "vps"),
            KitConversation("Tidy the onboarding words", "mobile", "Mon", null),
            KitConversation("Speed up search on big projects", "search", "Sun", null, branch = "fts"),
            KitConversation("Plan the next sprint", "notes", "Sat", null),
        )

    private class ShaderPlatform(base: KitPlatform = FakeKitPlatform()) : KitPlatform by base {
        override val liveShaders: LiveShaderCompiler = LiveShaderCompiler { DesktopLiveShader.compile(it) }
    }

    @Test
    fun `owner stills`() {
        val out = System.getenv("LIVE_STILLS_OUT")?.let(::File) ?: return
        val only = System.getenv("LIVE_STILLS_ONLY")?.split(',')?.toSet()
        out.mkdirs()
        val ids = LiveLooks.ids.filter { LiveLooks.shader(it) != null && (only == null || it in only) }
        for (dark in listOf(true, false)) {
            for (id in ids) {
                val name = "$id-${if (dark) "dark" else "light"}.png"
                runDesktopComposeUiTest(WIDTH, HEIGHT) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                            KitTheme(
                                KitAppearance(
                                    mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                                    liveBackground = true,
                                    liveStyle = id,
                                ),
                                ShaderPlatform(),
                                "en",
                                stillFrame = true,
                            ) {
                                KitLiveBackground(Modifier.fillMaxSize()) {
                                    Column(Modifier.padding(top = 56.dp)) {
                                        val title = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        KitText("Conversations", title, role = TypeRole.LargeTitle)
                                        rows.forEach { KitConversationRow(it, onClick = {}) }
                                    }
                                }
                            }
                        }
                    }
                    waitForIdle()
                    ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(out, name))
                }
            }
        }
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 2400
        const val DENSITY = 2.625f
    }
}
