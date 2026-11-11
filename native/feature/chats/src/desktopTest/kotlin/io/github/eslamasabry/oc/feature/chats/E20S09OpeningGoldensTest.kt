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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * E20-S09: the home's "isn't answering" page, with and without saved titles, as desktop goldens (412 × 915 dp,
 * light en and dark ar). Record: `./gradlew :feature:chats:desktopTest -Poc.recordGoldens=true --tests
 * '*E20S09OpeningGoldensTest*'`. Files: `src/desktopTest/goldens/<scene>-<variant>.png`. Look at them after recording.
 */
@OptIn(ExperimentalTestApi::class)
class E20S09OpeningGoldensTest {
    private data class Variant(val name: String, val dark: Boolean, val locale: String)

    private val variants = listOf(Variant("light-en", false, "en"), Variant("dark-ar", true, "ar"))
    private val savedLocale: Locale = Locale.getDefault()
    private val dir = File(System.getProperty("oc.goldens.dir") ?: "src/desktopTest/goldens")
    private val record = System.getProperty("oc.goldens.record") == "true"

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private fun failed() =
        ChatsUi(
            phase = ChatsPhase.Opening,
            // C18: a failed opening is never a dead end - Settings stays in the bar, like the real home.
            settingsAvailable = true,
            home =
                ChatsHomeExtraState(
                    opening =
                        ChatsOpeningUi(
                            ChatsOpeningPhase.Failed,
                            "This phone",
                            failure = Failure.Network(),
                            profile = ProfileId("phone"),
                        )
                ),
        )

    private fun scene(name: String, state: ChatsUi, check: ComposeUiTest.(Variant) -> Unit) {
        for (variant in variants) {
            Locale.setDefault(Locale.forLanguageTag(variant.locale))
            runDesktopComposeUiTest(412, 915) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
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
                check(variant)
                compare("$name-${variant.name}")
            }
        }
    }

    @Test
    fun `E20-S09 with no saved titles a failure shows no placeholder rows and does not mention saved titles`() =
        scene("home-opening-failed-empty", failed()) { variant ->
            assertEquals(0, onAllNodesWithTag("kit-skeleton-rows").fetchSemanticsNodes().size)
            // C18: the failed page keeps a way out beside Try again and Change server - the Settings gear.
            if (variant.locale == "en") {
                onNodeWithContentDescription("Settings").assertExists()
                assertEquals(0, onAllNodesWithText("saved last time", substring = true).fetchSemanticsNodes().size)
                // 2b4451ecbd: the card names the cause (Flutter ConnectionFailure.diagnose: nothing answered).
                onNodeWithText("Server not reachable").assertExists()
                onNodeWithText("Nothing answered", substring = true).assertExists()
            }
        }

    private fun ComposeUiTest.compare(name: String) {
        val image = onRoot().captureToImage().toAwtImage()
        val file = File(dir, "$name.png")
        if (record || !file.isFile) {
            file.parentFile.mkdirs()
            ImageIO.write(image, "png", file)
            if (!record) fail("$name: no golden yet; recorded ${file.name}. Look at it, then run again.")
            return
        }
        val golden = ImageIO.read(file)
        assertTrue(
            golden.width == image.width && golden.height == image.height,
            "$name: size ${image.width}x${image.height} vs ${golden.width}x${golden.height}",
        )
        val share = differingPixels(golden, image).toDouble() / (image.width * image.height)
        if (share > MAX_SHARE) {
            val actual = File("build/golden-actual/$name.png").also { it.parentFile.mkdirs() }
            ImageIO.write(image, "png", actual)
            fail("$name: ${"%.3f".format(share * 100)} % of pixels differ (limit ${MAX_SHARE * 100} %); actual: $actual")
        }
    }

    private fun differingPixels(a: BufferedImage, b: BufferedImage): Int {
        var count = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val p = a.getRGB(x, y)
            val q = b.getRGB(x, y)
            if (p == q) continue
            if ((0..24 step 8).any { shift -> abs((p shr shift and 0xFF) - (q shr shift and 0xFF)) > CHANNEL_SLACK }) count++
        }
        return count
    }

    private companion object {
        const val MAX_SHARE = 0.002
        const val CHANNEL_SLACK = 8
    }
}
