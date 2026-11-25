package io.github.eslamasabry.oc.feature.chat

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
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class B605ChatBeforeRendersTest {
    private val out = File("build/b605-renders").also { it.mkdirs() }
    private val savedLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    @Test
    fun `B-605 before pictures of chat prompt at 360dp light and dark text 1_0 and 1_3`() {
        Locale.setDefault(Locale.ENGLISH)
        val variants =
            listOf(
                Triple("light-10", false, 1.0f),
                Triple("dark-10", true, 1.0f),
                Triple("light-13", false, 1.3f),
                Triple("dark-13", true, 1.3f),
            )
        for ((name, dark, scale) in variants) {
            kitUiTest(360, 800) {
                val scene = ChatScene()
                val rows =
                    persistentListOf(
                        footer("t2"),
                        block("t2", "m2", "text-0", 0, "Checked CI: all 14 checks are green."),
                        prompt("t2", "Check CI and summarize any failures"),
                        footer("t1"),
                        block("t1", "m1", "text-0", 0, "Sure, I will look at the checkout test."),
                        prompt("t1", "Can you inspect the checkout test?"),
                    )
                val state = scene.live().copy(status = null, rows = rows, live = null)
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                        KitTheme(
                            appearance =
                                KitAppearance(
                                    mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                                    motion = MotionLevel.Off,
                                ),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    scene.Screen(state, projectName = "shopfront")
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "before-chat-$name.png"))
            }
        }
    }
}
