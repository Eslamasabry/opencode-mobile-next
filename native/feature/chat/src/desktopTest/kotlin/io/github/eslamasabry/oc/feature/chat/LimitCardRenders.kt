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
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderUi
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Picture only, asserts nothing: failed reply cards for a used-up free daily limit and a country refusal.
 * Writes PNGs to `LIMIT_CARD_OUT` or `build/limit-card` so test runs return them via render-pull.
 */
@OptIn(ExperimentalTestApi::class)
class LimitCardRenders {
    private val out: File =
        (System.getenv("LIMIT_CARD_OUT")?.let(::File) ?: File("build/limit-card")).also(File::mkdirs)

    private class ModelWorld(choices: List<ComposerModel>) : FakeChatWorld() {
        override val models: StateFlow<ComposerModels> = MutableStateFlow(ComposerModels(choices = choices))
    }

    private fun shoot(rows: List<TranscriptRow>, choices: List<ComposerModel>, name: String, fontScale: Float) {
        val beforeLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("en"))
            val world = ModelWorld(choices)
            world.transcript.value = stateOf(*rows.toTypedArray())
            val scene = ChatScene(world = world)
            val state = scene.live().copy(status = null, rows = world.transcript.value.rows)
            kitUiTest(WIDTH_360, HEIGHT) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY, fontScale)) {
                        KitTheme(
                            appearance =
                                KitAppearance(
                                    mode = AppearanceMode.Dark,
                                    style = Style.Solid,
                                    motion = MotionLevel.Full,
                                ),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    ChatScreen(
                                        state,
                                        scene.actions,
                                        scene.host,
                                        header = ChatHeaderSlot(ChatHeaderUi(title = state.title), scene.headerActions),
                                        facts = TurnFactsUi.Empty,
                                        modelChoices = choices,
                                        canGoBack = false,
                                    )
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
        } finally {
            Locale.setDefault(beforeLocale)
        }
    }

    private fun limitFixture(): Pair<List<TranscriptRow>, List<ComposerModel>> =
        listOf(
            prompt("u1", "go"),
            FooterRow(
                "footer:u1",
                "u1",
                failed = true,
                reason = FREE,
                createdMs = 200L,
                providerId = "opencode",
                modelId = "big-pickle",
            ),
        ) to
            listOf(
                ComposerModel(
                    "opencode",
                    "deepseek-v4.1-flash-free",
                    "DeepSeek V4.1 Flash Free",
                    supportsAttachments = false,
                    providerName = "OpenCode",
                    enabled = true,
                ),
            )

    private fun countryFixture(): Pair<List<TranscriptRow>, List<ComposerModel>> =
        listOf(
            prompt("u1", "first"),
            FooterRow(
                "footer:u1",
                "u1",
                failed = true,
                reason = COUNTRY,
                errorKind = MessageErrorKind.ProviderAuth,
                createdMs = 100L,
                providerId = "opencode",
                modelId = "beam-free",
            ),
            prompt("u2", "second"),
            FooterRow(
                "footer:u2",
                "u2",
                failed = true,
                reason = COUNTRY,
                errorKind = MessageErrorKind.ProviderAuth,
                createdMs = 200L,
                providerId = "opencode",
                modelId = "muse-spark-1.3-contributor-free",
            ),
        ) to
            listOf(
                ComposerModel(
                    "opencode",
                    "beam-free",
                    "Beam Free",
                    supportsAttachments = false,
                    providerName = "OpenCode",
                    enabled = true,
                ),
                ComposerModel(
                    "opencode",
                    "big-pickle",
                    "Big Pickle",
                    supportsAttachments = false,
                    providerName = "OpenCode",
                    enabled = true,
                ),
            )

    @Test
    fun `limit card renders`() {
        val (limitRows, limitChoices) = limitFixture()
        val (countryRows, countryChoices) = countryFixture()
        shoot(limitRows, limitChoices, "limit-text1.0", 1.0f)
        shoot(limitRows, limitChoices, "limit-text1.3", 1.3f)
        shoot(countryRows, countryChoices, "country-text1.0", 1.0f)
        shoot(countryRows, countryChoices, "country-text1.3", 1.3f)
    }

    private companion object {
        const val WIDTH_360 = 945
        const val HEIGHT = 2402
        const val DENSITY = 2.625f
        const val FREE =
            "Free usage limit reached. Connect a provider or subscribe to OpenCode Go to keep going, or switch models."
        const val COUNTRY = "This model is not available in your country."
    }
}
