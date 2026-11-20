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
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/**
 * Pictures of Home after the person's own stop of OpenCode on this phone, at 360 dp wide, dark, Solid and Glass, text
 * 1.0 and 1.3. Writes `build/own-stop-home/<name>.png` to look at; nothing is compared (picture-making, not proof).
 */
@OptIn(ExperimentalTestApi::class)
class OwnStopHomeRenders {
    private val out = File("build/own-stop-home").also { it.mkdirs() }

    private fun shoot(name: String, state: ChatsUi, style: Style, fontScale: Float) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        runDesktopComposeUiTest(900, 2000) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.5f, fontScale)) {
                    ChatEnvironment {
                        KitTheme(
                            appearance =
                                KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off, style = style),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                ChatsScreen(state, RecordingChatsActions())
                            }
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
    fun `renders of Home after the person's own stop`() {
        val phone = ProfileId("phone")
        val base = mixed()
        val card =
            base.copy(
                home =
                    ChatsHomeExtraState(
                        opening =
                            ChatsOpeningUi(
                                ChatsOpeningPhase.Failed,
                                "This phone",
                                profile = phone,
                                onThisPhone = true,
                                phoneOff = true,
                            ),
                    ),
            )
        val notice =
            base.copy(
                home =
                    ChatsHomeExtraState(
                        sources =
                            persistentListOf(
                                ChatsListSourceUi(
                                    phone, "This phone", shown = true, main = true, onThisPhone = true,
                                    unreachable = false, stoppedByPerson = true,
                                ),
                            ),
                    ),
            )
        for (style in listOf(Style.Solid, Style.Glass))
            for (scale in listOf(1f, 1.3f))
                for ((tag, state) in listOf("card" to card, "notice" to notice))
                    shoot("$tag-${style.storageName}-${if (scale == 1f) "1.0" else "1.3"}-dark", state, style, scale)
    }
}
