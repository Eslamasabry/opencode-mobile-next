package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import io.github.eslamasabry.oc.feature.chats.dock.HomeEdgeDock
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse

/** B-LAMP-004: Home never composes the Rings dock, whether online or still connecting. */
@OptIn(ExperimentalTestApi::class)
class HomeNoEdgeDockTest {
    private val savedLocale: Locale = Locale.getDefault()

    @AfterTest fun restoreLocale() = Locale.setDefault(savedLocale)

    @Test
    fun `B-LAMP-004 Home never composes HomeEdgeDock or KitEdgeDock while connecting or live`() {
        val base = mixed(extra = 2)
        val connecting = ChatsOpeningUi(ChatsOpeningPhase.Connecting, "This phone")
        val states =
            listOf(
                "connecting" to base.copy(home = base.home.copy(opening = connecting)),
                "live" to base,
            )
        for ((label, state) in states) {
            kitUiTest(360, 640) {
                Locale.setDefault(Locale.forLanguageTag("en"))
                var dockComposed = false
                val spy =
                    object : HomeEdgeDock {
                        @Composable
                        override fun Content(clearBottom: Dp) {
                            dockComposed = true
                        }
                    }
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        ChatEnvironment {
                            val look =
                                KitAppearance(
                                    mode = AppearanceMode.Dark,
                                    motion = MotionLevel.Off,
                                    style = Style.Solid,
                                )
                            KitTheme(
                                appearance = look,
                                platform = DesktopKitPlatform(),
                                localeTag = "en",
                                stillFrame = true,
                            ) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    ChatsScreen(state, RecordingChatsActions(), edgeDock = spy)
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                assertFalse(dockComposed, "$label: Home must never compose HomeEdgeDock / KitEdgeDock")
            }
        }
    }
}
