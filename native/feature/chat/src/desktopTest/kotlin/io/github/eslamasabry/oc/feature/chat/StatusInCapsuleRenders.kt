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
import io.github.eslamasabry.oc.core.security.Redactor
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted

/**
 * Capture only (nothing asserted): F18 (build 10113, J16-kill), the chat while it connects to OpenCode: the status line
 * and the header. Dark, 360 dp, text 1.0, Solid and Glass. Writes PNGs to `STATUS_CAPSULE_OUT` (or the picture
 * server's render-out folder); does nothing when neither exists, so CI never writes.
 */
@OptIn(ExperimentalTestApi::class)
class StatusInCapsuleRenders {
    private val out =
        (
            System.getenv("STATUS_CAPSULE_OUT")?.let(::File)
                ?: File(System.getProperty("user.home"), "render-out")
                    .takeIf { it.isDirectory }
                    ?.resolve("status-capsule")
        )?.also(File::mkdirs)

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
                        targetKey = "status-capsule",
                        models = ComposerModels(usesDefault = true),
                        filesAvailable = true,
                    ),
                    composerActions,
                    chrome,
                    modifier,
                )
            }
        }

    private fun shoot(style: Style, name: String) {
        val o = out ?: return
        Locale.setDefault(Locale.forLanguageTag("en"))
        val world = FakeChatWorld()
        world.title.value = TITLE
        world.link.value = io.github.eslamasabry.oc.feature.chat.world.ChatLink.Connecting
        world.transcript.value =
            stateOf(
                prompt("u1", "Reply with exactly the word: base-two"),
                block("u1", "a1", "p1", 0, "base-two"),
                footer("u1"),
                prompt("u2", "Write a 4000 word story about a lighthouse. No tools."),
            )
        val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val states = presenter.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
        kitUiTest(WIDTH, HEIGHT) {
            setContent {
                val state by states.collectAsState()
                CompositionLocalProvider(LocalDensity provides Density(DENSITY, 1.0f)) {
                    KitTheme(
                        appearance = KitAppearance(mode = AppearanceMode.Dark, style = style, motion = MotionLevel.Off),
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
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(o, "$name.png"))
        }
    }

    @Test
    fun `status in capsule renders`() {
        if (out == null) return
        shoot(Style.Solid, "connecting-solid-dark-360")
        shoot(Style.Glass, "connecting-glass-dark-360")
    }

    private companion object {
        const val DENSITY = 2.625f
        const val WIDTH = 945 // 360 dp
        const val HEIGHT = 2100 // 800 dp
        const val TITLE = "Reply with exactly the word: base-three"
    }
}
