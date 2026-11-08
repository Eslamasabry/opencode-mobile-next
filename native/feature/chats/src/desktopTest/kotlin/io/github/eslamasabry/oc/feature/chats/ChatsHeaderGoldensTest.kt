package io.github.eslamasabry.oc.feature.chats

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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeConnectionState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import kotlinx.collections.immutable.persistentListOf
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The Conversations top (Flutter conv-header): the title row with plain Search and Settings icons, then the fixed
 * connection pill, a divider and the filters. Dark and light, a long server name, not connected, and Arabic (right to
 * left), at 360 x 640 dp. Record: `./gradlew :feature:chats:desktopTest -Poc.recordGoldens=true --tests
 * '*ChatsHeaderGoldensTest*'`. Files: `src/desktopTest/goldens/chats-header-<variant>.png`. Look at them after recording.
 */
@OptIn(ExperimentalTestApi::class)
class ChatsHeaderGoldensTest {
    private data class Variant(
        val name: String,
        val dark: Boolean,
        val locale: String,
        val server: String,
        val state: HomeConnectionState,
    )

    private fun withConnection(base: ChatsUi, server: String, state: HomeConnectionState): ChatsUi =
        base.copy(
            home =
                base.home.copy(
                    sources =
                        persistentListOf(
                            ChatsListSourceUi(
                                profile = ProfileId("conv-header"),
                                name = server,
                                shown = true,
                                main = true,
                                onThisPhone = false,
                                unreachable = state == HomeConnectionState.NotAnswering,
                            ),
                        ),
                ),
        )

    private val variants =
        listOf(
            Variant("dark-en", true, "en", "This phone", HomeConnectionState.Connected),
            Variant("light-en", false, "en", "This phone", HomeConnectionState.Connected),
            Variant("dark-en-long", true, "en", "Eslam's workstation at the office (secure):4443", HomeConnectionState.Connected),
            Variant("light-en-long", false, "en", "Eslam's workstation at the office (secure):4443", HomeConnectionState.Connected),
            Variant("dark-en-offline", true, "en", "This phone", HomeConnectionState.NotAnswering),
            Variant("light-en-offline", false, "en", "Laptop", HomeConnectionState.NotAnswering),
            Variant("dark-ar", true, "ar", "هذا الهاتف", HomeConnectionState.Connected),
            Variant("light-ar", false, "ar", "هذا الهاتف", HomeConnectionState.Connected),
        )
    private val savedLocale: Locale = Locale.getDefault()
    private val dir = File(System.getProperty("oc.goldens.dir") ?: "src/desktopTest/goldens")
    private val record = System.getProperty("oc.goldens.record") == "true"

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    @Test
    fun `the top shows the title row and one chip row`() {
        val failures = variants.mapNotNull { variant ->
            Locale.setDefault(Locale.forLanguageTag(variant.locale))
            val state: ChatsUi = withConnection(mixed(claude = false), variant.server, variant.state)
            var failure: String? = null
            runDesktopComposeUiTest(360, 640) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                        ChatEnvironment {
                            KitTheme(
                                appearance =
                                    KitAppearance(
                                        mode = if (variant.dark) AppearanceMode.Dark else AppearanceMode.Light,
                                        motion = MotionLevel.Off,
                                    ),
                                platform = DesktopKitPlatform(),
                                localeTag = variant.locale,
                                stillFrame = true,
                            ) {
                                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                        ChatsScreen(state, RecordingChatsActions())
                                    }
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                failure = compare("chats-header-${variant.name}")
            }
            failure
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    private fun ComposeUiTest.compare(name: String): String? {
        val image = onRoot().captureToImage().toAwtImage()
        val file = File(dir, "$name.png")
        val golden = if (record || !file.isFile) null else ImageIO.read(file)
        if (golden == null) {
            file.parentFile.mkdirs()
            ImageIO.write(image, "png", file)
        } else {
            assertTrue(
                golden.width == image.width && golden.height == image.height,
                "$name: size ${image.width}x${image.height} vs ${golden.width}x${golden.height}",
            )
        }
        val share = golden?.let { differingPixels(it, image).toDouble() / (image.width * image.height) } ?: 0.0
        val actual = File("build/golden-actual/$name.png")
        if (share > MAX_SHARE) {
            actual.parentFile.mkdirs()
            ImageIO.write(image, "png", actual)
        }
        return when {
            golden == null && !record -> "$name: no golden yet; recorded ${file.name}. Look at it, then run again."
            share > MAX_SHARE -> "$name: ${"%.3f".format(share * 100)} % of pixels differ; actual: $actual"
            else -> null
        }
    }

    private fun differingPixels(a: BufferedImage, b: BufferedImage): Int {
        var count = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val p = a.getRGB(x, y)
            val q = b.getRGB(x, y)
            if (p == q) continue
            val apart =
                (0..24 step 8).any { shift -> abs((p shr shift and 0xFF) - (q shr shift and 0xFF)) > CHANNEL_SLACK }
            if (apart) count++
        }
        return count
    }

    private companion object {
        const val MAX_SHARE = 0.002
        const val CHANNEL_SLACK = 8
    }
}
