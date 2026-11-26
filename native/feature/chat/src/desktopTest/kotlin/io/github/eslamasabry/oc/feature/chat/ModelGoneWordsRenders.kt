package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.ReplacedModel
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow

/**
 * Renders the two 360 dp owner review pictures for B-523:
 * (1) the error card naming the gone model and provider (`model-gone-card-360.png`),
 * (2) the stale-choice replacement note in the conversation status slot (`model-replaced-note-360.png`).
 * Writes to `MODEL_GONE_OUT`, `~/render-out/model-gone` on the picture server, or `build/model-gone-render`.
 */
@OptIn(ExperimentalTestApi::class)
class ModelGoneWordsRenders {
    private val out: File =
        (
            System.getenv("MODEL_GONE_OUT")?.let(::File)
                ?: File(System.getProperty("user.home"), "render-out")
                    .takeIf { it.isDirectory }
                    ?.resolve("model-gone")
                ?: File("build/model-gone-render")
        ).also(File::mkdirs)

    private val composerActions =
        object : BasicComposerActions {
            override fun draftChanged(text: String) = Unit

            override suspend fun send(text: String) = true

            override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
        }

    private fun composerHost(models: ComposerModels): ComposerHost =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                BasicComposer(
                    BasicComposerUi(
                        available = true,
                        sending = false,
                        steers = true,
                        targetKey = "model-gone",
                        models = models,
                        filesAvailable = true,
                    ),
                    composerActions,
                    chrome,
                    modifier,
                )
            }
        }

    private fun shoot(world: FakeChatWorld, models: ComposerModels, name: String) {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("en"))
            val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val states = presenter.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
            kitUiTest(WIDTH_PX, HEIGHT_PX) {
                setContent {
                    val state by states.collectAsState()
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY, 1.3f)) {
                        KitTheme(
                            appearance =
                                KitAppearance(
                                    mode = AppearanceMode.Dark,
                                    style = Style.Solid,
                                    motion = MotionLevel.Off,
                                ),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                                KitSheetHost {
                                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                        ChatScreen(
                                            state.copy(title = TITLE),
                                            presenter,
                                            composerHost(models),
                                            modelChoices = models.choices,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                val image = onRoot().captureToImage().toAwtImage()
                val file = File(out, "$name.png")
                ImageIO.write(image, "png", file)
                assertEquals(WIDTH_PX, image.width)
                assertTrue(file.length() > 0L)
            }
        } finally {
            Locale.setDefault(before)
        }
    }

    private fun cardWorld(models: ComposerModels): FakeChatWorld =
        object : FakeChatWorld() {
            override val models: StateFlow<ComposerModels> = MutableStateFlow(models)
        }.apply {
            title.value = TITLE
            transcript.value =
                stateOf(
                    prompt("u1", "Check the checkout race"),
                    block("u1", "a1", "p1", 0, "The race happens in the retry path."),
                    FooterRow(
                        "footer:u1",
                        "u1",
                        failed = false,
                        createdMs = 100L,
                        providerId = "anthropic",
                        modelId = "claude-sonnet-5",
                    ),
                    prompt("u2", "Write the regression test"),
                    FooterRow(
                        "footer:u2",
                        "u2",
                        failed = true,
                        reason = "Model not found",
                        errorKind = MessageErrorKind.ModelNotFound,
                        createdMs = 200L,
                        providerId = "anthropic",
                        modelId = "alpha-beta-gamma-delta-epsilon-zeta-40ch",
                    ),
                )
        }

    private fun noteWorld(models: ComposerModels): FakeChatWorld =
        object : FakeChatWorld() {
            override val models: StateFlow<ComposerModels> = MutableStateFlow(models)
        }.apply {
            title.value = TITLE
            transcript.value =
                stateOf(
                    prompt("u1", "Check the checkout race"),
                    block("u1", "a1", "p1", 0, "The race happens in the retry path."),
                    footer("u1"),
                )
        }

    @Test
    fun `B-523 render error card and stale-choice replacement note at 360 dp`() {
        val sonnet =
            ComposerModel(
                "anthropic",
                "claude-sonnet-5",
                "Sonnet 5",
                supportsAttachments = false,
                providerName = "Anthropic",
                enabled = true,
            )
        val cardModels = ComposerModels(choices = listOf(sonnet), selected = sonnet)
        shoot(cardWorld(cardModels), cardModels, "model-gone-card-360")

        val noteModels =
            ComposerModels(
                choices = listOf(sonnet),
                selected = sonnet,
                replaced = ReplacedModel("Alpha Beta Gamma Delta Epsilon Zeta 40ch", "Sonnet 5"),
            )
        shoot(noteWorld(noteModels), noteModels, "model-replaced-note-360")
    }

    private companion object {
        const val WIDTH_PX = 1080
        const val HEIGHT_PX = 1920
        const val DENSITY = 3f
        const val TITLE = "Checkout race"
    }
}
