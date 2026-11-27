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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted

/**
 * B-473 360 dp renders (dark & light, text 1.0 & 1.3) of ChatScreen showing the "Finishing undo…" status line.
 * Writes PNGs into `native/feature/chat/build/renders/undo-revert-direct/`.
 */
@OptIn(ExperimentalTestApi::class)
class UndoFinishingRenders {
    private val out =
        (System.getenv("UNDO_DIRECT_OUT")?.let(::File) ?: File("build/renders/undo-revert-direct"))
            .also(File::mkdirs)

    private val composerActions =
        object : BasicComposerActions {
            override fun draftChanged(text: String) = Unit

            override suspend fun send(text: String) = true

            override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
        }

    private val host =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                BasicComposer(
                    BasicComposerUi(
                        available = true,
                        sending = false,
                        steers = true,
                        targetKey = "undo-finishing",
                        models = ComposerModels(usesDefault = true),
                        filesAvailable = true,
                    ),
                    composerActions,
                    chrome,
                    modifier,
                )
            }
        }

    private fun shoot(mode: AppearanceMode, fontScale: Float, name: String) {
        Locale.setDefault(Locale.ENGLISH)
        val world =
            object : FakeChatWorld() {
                override val undoing: MutableStateFlow<Boolean> = MutableStateFlow(true)
            }
        world.title.value = TITLE
        world.link.value = ChatLink.Live
        world.transcript.value =
            stateOf(
                prompt("u1", "Make the header blue"),
                block("u1", "a1", "p1", 0, "Updated the header color to blue."),
                footer("u1"),
            )
        val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val states = presenter.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
        kitUiTest(WIDTH_PX, HEIGHT_PX) {
            setContent {
                val state by states.collectAsState()
                CompositionLocalProvider(LocalDensity provides Density(DENSITY, fontScale)) {
                    KitTheme(
                        appearance = KitAppearance(mode = mode, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            KitSheetHost {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    ChatScreen(state.copy(title = TITLE), presenter, host)
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Finishing undo", substring = true).assertIsDisplayed()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
    }

    @Test
    fun `chat finishing undo status line renders`() {
        if (System.getenv("GITHUB_ACTIONS") == "true") return
        val modes = listOf("dark" to AppearanceMode.Dark, "light" to AppearanceMode.Light)
        val scales = listOf("t10" to 1.0f, "t13" to 1.3f)
        for ((modeName, mode) in modes) {
            for ((scaleName, scale) in scales) {
                shoot(mode, scale, "after-chat-finishing-undo-$modeName-$scaleName")
            }
        }
    }

    private companion object {
        const val DENSITY: Float = 2.625f
        const val WIDTH_PX: Int = 945
        const val HEIGHT_PX: Int = 2100
        const val TITLE: String = "Blue app"
    }
}
