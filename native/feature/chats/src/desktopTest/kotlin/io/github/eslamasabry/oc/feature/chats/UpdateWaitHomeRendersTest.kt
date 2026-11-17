package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadFact
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadPhase
import io.github.eslamasabry.oc.feature.chats.presentation.HomeStage
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pictures of Home with an OpenCode update waiting, at 360 dp wide, text 1.0 and 1.3, light and dark, with and without
 * the helper starting. Writes `build/update-wait-home/<name>.png` to look at; nothing is compared.
 */
@OptIn(ExperimentalTestApi::class)
class UpdateWaitHomeRendersTest {
    private val out = File("build/update-wait-home").also { it.mkdirs() }

    private fun shoot(name: String, state: ChatsUi, fontScale: Float, dark: Boolean) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        runDesktopComposeUiTest(900, 2000) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.5f, fontScale)) {
                    ChatEnvironment {
                        KitTheme(
                            appearance = KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { ChatsScreen(state, RecordingChatsActions()) }
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
    }

    @Test
    fun `B-HOME-212 renders of Home with the update waiting, at 360 dp`() {
        val waiting = HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Waiting)
        val base = mixed()
        for ((tag, stage) in listOf("waiting" to null, "waiting-helper" to HomeStage.Helper))
            for (scale in listOf(1f, 1.3f))
                for (dark in listOf(false, true))
                    shoot(
                        "$tag-${if (scale == 1f) "1.0" else "1.3"}-${if (dark) "dark" else "light"}",
                        base.copy(download = waiting, home = base.home.copy(stage = stage)),
                        scale,
                        dark,
                    )
        assertTrue(out.listFiles().orEmpty().size >= 8)
    }
}
