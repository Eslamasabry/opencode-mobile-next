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
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeUsageLimit
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
 * E20-S24, B-161, B-162: the home with mixed rows (live, done, needs you, git and no git, two agents) and the Usage
 * row, at 360 × 1000 dp: light and dark, text 1.0 and 1.3, English and Arabic (right to left). Record:
 * `./gradlew :feature:chats:desktopTest -Poc.recordGoldens=true --tests '*S10HomeGoldensTest*'`. Files:
 * `src/desktopTest/goldens/s10-home-<variant>.png`. Look at them after recording.
 */
@OptIn(ExperimentalTestApi::class)
class S10HomeGoldensTest {
    private data class Variant(val name: String, val dark: Boolean, val locale: String, val fontScale: Float)

    private val variants =
        listOf(
            Variant("light-en-100", false, "en", 1f),
            Variant("dark-en-100", true, "en", 1f),
            Variant("light-en-130", false, "en", 1.3f),
            Variant("dark-ar-100", true, "ar", 1f),
            Variant("light-ar-130", false, "ar", 1.3f),
        )
    private val savedLocale: Locale = Locale.getDefault()
    private val dir = File(System.getProperty("oc.goldens.dir") ?: "src/desktopTest/goldens")
    private val record = System.getProperty("oc.goldens.record") == "true"

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    @Test
    fun `B-CHATS-161 E20-S24 B-CHATS-162 home with mixed rows`() {
        val state: ChatsUi = mixed(claude = true).copy(usage = HomeUsageLimit("Claude Code", 34))
        val failures = variants.mapNotNull { variant ->
            Locale.setDefault(Locale.forLanguageTag(variant.locale))
            var failure: String? = null
            runDesktopComposeUiTest(360, 1000) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, variant.fontScale)) {
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
                failure = compare("s10-home-${variant.name}")
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
