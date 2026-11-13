@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Proof pictures for the owner's broken field corners (2026-11-13): a focused one-line field and a focused multiline
 * field, in Glass dark and Glass light, at the phone's 2.625 px/dp, once on the plain ground and once over the painted
 * aurora (frosted, as on a screen with a live background). Not a test: it runs only when `FIELD_CORNER_RENDER_OUT`
 * names a folder, and writes `field-corners-glass-{dark,light}-{one-line,multiline}-{ground,aurora}.png` there.
 */
class FieldCornerRenders {
    @Test
    fun `field corner renders`() {
        val out = System.getenv("FIELD_CORNER_RENDER_OUT")?.let(::File) ?: return
        out.mkdirs()
        for (dark in listOf(true, false)) for (multiline in listOf(false, true)) for (aurora in listOf(false, true)) {
            val name =
                "field-corners-glass-${if (dark) "dark" else "light"}-${if (multiline) "multiline" else "one-line"}-" +
                    "${if (aurora) "aurora" else "ground"}.png"
            runDesktopComposeUiTest(WIDTH, HEIGHT) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                        KitTheme(
                            KitAppearance(
                                mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                                motion = MotionLevel.Off,
                                style = Style.Glass,
                                liveBackground = aurora,
                                liveStyle = "auroraImage",
                            ),
                            FakeKitPlatform(frost = aurora),
                            "en",
                        ) {
                            val field = @androidx.compose.runtime.Composable {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    val focus = remember { FocusRequester() }
                                    if (multiline) {
                                        KitField(
                                            rememberTextFieldState("first line\nsecond line\nthird line"),
                                            "Notes",
                                            kind = KitFieldKind.Multiline,
                                            focusRequester = focus,
                                        )
                                    } else {
                                        KitField(rememberTextFieldState("example.org"), "Address", focusRequester = focus)
                                    }
                                    LaunchedEffect(Unit) { focus.requestFocus() }
                                }
                            }
                            if (aurora) KitLiveBackground(Modifier.fillMaxSize()) { field() }
                            else Column(Modifier.fillMaxSize().background(LocalKitColors.current.ground.copy(alpha = 1f))) { field() }
                        }
                    }
                }
                waitForIdle()
                ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(out, name))
            }
        }
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 720
        const val DENSITY = 2.625f
    }
}
