package io.github.eslamasabry.oc.feature.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.COMPUTERS
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.EVERY_STATE
import io.github.eslamasabry.oc.feature.agents.AgentsBentoTest.Companion.MARKS
import io.github.eslamasabry.oc.feature.agents.hub.AgentsUi
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

private const val PHASE = "before"
private const val WIDTH_DP = 360
private const val HEIGHT_DP = 800
private const val DENSITY = 2.625f

/** Before/after polish review renders for the Agents page (en and longest translation fr, t10 and t13). */
@OptIn(ExperimentalTestApi::class)
class AgentsPolishRendersTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private data class Shot(
        val files: List<File>,
        val style: Style,
        val mode: AppearanceMode,
        val state: AgentsUi,
        val fontScale: Float,
        val locale: String = "en",
    )

    @Test
    fun renders() {
        val out = File("build/polish-render")
        out.mkdirs()
        val scales = listOf(1f to "t10", 1.3f to "t13")
        shootMainScenes(out, scales)
        shootExtraScenes(out, scales)
    }

    private fun shootMainScenes(out: File, scales: List<Pair<Float, String>>) {
        for (locale in listOf("en", "fr")) {
            val langPrefix = if (locale == "en") "" else "$locale-"
            val phoneFindings = if (locale == "en") listOf(3, 17, 31) else listOf(3, 17)
            val compFindings = if (locale == "en") listOf(3, 17, 34) else listOf(3, 17)
            for (mode in listOf(AppearanceMode.Light, AppearanceMode.Dark)) {
                val look = "${Style.Solid.storageName}-${mode.name.lowercase()}"
                for ((scale, tag) in scales) {
                    shoot(
                        Shot(
                            files = phoneFindings.map { File(out, "$it-$PHASE-${langPrefix}phone-$look-$tag.png") },
                            style = Style.Solid,
                            mode = mode,
                            state = EVERY_STATE,
                            fontScale = scale,
                            locale = locale,
                        )
                    )
                    shoot(
                        Shot(
                            files = compFindings.map { File(out, "$it-$PHASE-${langPrefix}computers-$look-$tag.png") },
                            style = Style.Solid,
                            mode = mode,
                            state = COMPUTERS,
                            fontScale = scale,
                            locale = locale,
                        )
                    )
                }
            }
        }
    }

    private fun shootExtraScenes(out: File, scales: List<Pair<Float, String>>) {
        val connectFailed =
            EVERY_STATE.copy(
                helperConnected = false,
                helperConnectFailed = true,
                helperConnectFailure = Failure.TimedOut(),
            )
        for ((scale, tag) in scales) {
            shoot(
                Shot(
                    files = listOf(File(out, "31-$PHASE-connect-failed-solid-light-$tag.png")),
                    style = Style.Solid,
                    mode = AppearanceMode.Light,
                    state = connectFailed,
                    fontScale = scale,
                )
            )
            shoot(
                Shot(
                    files = listOf(File(out, "32-$PHASE-menu-solid-dark-$tag.png")),
                    style = Style.Solid,
                    mode = AppearanceMode.Dark,
                    state = EVERY_STATE,
                    fontScale = scale,
                )
            ) {
                onNode(hasContentDescription("More actions") and hasClickAction()).performClick()
            }
            shoot(
                Shot(
                    files = listOf(File(out, "33-$PHASE-sheet-installing-glass-light-$tag.png")),
                    style = Style.Glass,
                    mode = AppearanceMode.Light,
                    state = EVERY_STATE.copy(sheetAgent = "kilo"),
                    fontScale = scale,
                )
            )
        }
    }

    private fun shoot(shot: Shot, then: ComposeUiTest.() -> Unit = {}) {
        Locale.setDefault(Locale.forLanguageTag(shot.locale))
        try {
            runDesktopComposeUiTest((WIDTH_DP * DENSITY).toInt(), (HEIGHT_DP * DENSITY).toInt()) {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(DENSITY, shot.fontScale),
                        LocalKitSecretSink provides KitSecretSink {},
                        LocalKitLogoLoader provides MARKS,
                    ) {
                        KitTheme(
                            appearance = KitAppearance(mode = shot.mode, style = shot.style, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = shot.locale,
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                AgentsScreen(shot.state, AgentsBentoTest.Recorder())
                            }
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                then()
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                val image = onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage()
                for (file in shot.files) {
                    ImageIO.write(image, "png", file)
                    assertTrue(file.isFile, "expected render file ${file.name}")
                }
            }
        } finally {
            Locale.setDefault(Locale.ENGLISH)
        }
    }
}
