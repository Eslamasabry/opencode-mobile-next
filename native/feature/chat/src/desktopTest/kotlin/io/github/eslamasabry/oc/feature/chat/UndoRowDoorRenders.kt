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
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ChatUndone
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted

/**
 * Capture only (no comparison, nothing asserted): a conversation right after a send refused while an undo waits
 * (build 10110). The refused message's row offers Review, the door to the waiting undo, instead of Try again, which
 * the server would refuse the same way. Dark, 360 dp, text 1.3. Writes PNGs to `UNDO_ROW_DOOR_OUT` (or the picture
 * server's render-out folder); does nothing when neither exists, so CI never writes.
 */
@OptIn(ExperimentalTestApi::class)
class UndoRowDoorRenders {
    // The environment variable, or (on the picture server, which gets no variables) its render-out folder.
    private val out =
        (
            System.getenv("UNDO_ROW_DOOR_OUT")?.let(::File)
                ?: File(System.getProperty("user.home"), "render-out")
                    .takeIf { it.isDirectory }
                    ?.resolve("undo-row-door")
        )?.also(File::mkdirs)

    private class UndoWorld(staged: Boolean) : FakeChatWorld() {
        override val undone = MutableStateFlow(if (staged) ChatUndone.Staged else ChatUndone.None)
    }

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
                        targetKey = "undo",
                        models = ComposerModels(usesDefault = true),
                        filesAvailable = true,
                    ),
                    composerActions,
                    chrome,
                    modifier,
                )
            }
        }

    private fun shoot(staged: Boolean, name: String) {
        val o = out ?: return
        Locale.setDefault(Locale.forLanguageTag("en"))
        val world = UndoWorld(staged)
        world.pending.value =
            persistentListOf(
                PendingSend(7, PROMPT, PendingState.Failed, Failure.Refused(RefusalReason.UndoUnresolved))
            )
        val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val states = presenter.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent {
                val state by states.collectAsState()
                CompositionLocalProvider(LocalDensity provides Density(DENSITY, FONT_SCALE)) {
                    KitTheme(
                        appearance =
                            KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                ChatScreen(state.copy(title = TITLE), presenter, host)
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
    fun `undo row door renders`() {
        if (out == null) return
        shoot(staged = true, name = "01-refused-send-review-dark-360-1.3")
        shoot(staged = false, name = "02-undo-cleared-try-again-dark-360-1.3")
    }

    private companion object {
        const val DENSITY = 2.625f
        const val FONT_SCALE = 1.3f
        const val WIDTH = 945 // 360 dp
        const val HEIGHT = 2100 // 800 dp
        const val TITLE = "Create a file alpha.txt"
        const val PROMPT = "Reply with exactly the word: staged"
    }
}
