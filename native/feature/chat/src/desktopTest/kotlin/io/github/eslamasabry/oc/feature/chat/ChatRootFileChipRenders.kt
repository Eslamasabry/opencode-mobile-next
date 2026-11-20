package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
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
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList

/**
 * Picture only, asserts nothing: a reply that names files at the project root in inline code, drawn the way the chat
 * draws it once those names are confirmed as files (the root listing holds notes.txt and README.md; docs.md is not
 * there). Writes PNGs to `ROOT_FILE_CHIP_OUT` (or the server's render-out folder); does nothing when neither exists.
 */
@OptIn(ExperimentalTestApi::class)
class ChatRootFileChipRenders {
    // The environment variable, or (on the picture server, which gets no variables) its render-out folder; nowhere else.
    private val out =
        (System.getenv("ROOT_FILE_CHIP_OUT")?.let(::File)
                ?: File(System.getProperty("user.home"), "render-out").takeIf { it.isDirectory }?.resolve("root-file-chip"))
            ?.also(File::mkdirs)

    private val composerActions =
        object : BasicComposerActions {
            override fun draftChanged(text: String) {}

            override suspend fun send(text: String) = true

            override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
        }

    private val host =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                BasicComposer(
                    BasicComposerUi(available = true, sending = false, steers = true, targetKey = "notes", models = ComposerModels(usesDefault = true)),
                    composerActions,
                    chrome,
                    modifier,
                )
            }
        }

    private fun shoot(mode: AppearanceMode, name: String) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                    KitTheme(
                        appearance = KitAppearance(mode = mode, style = Style.Solid, motion = MotionLevel.Full),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                val state =
                                    ChatUi.loading(Ref).copy(
                                        title = "Notes",
                                        phase = ChatPhase.Live,
                                        rows =
                                            listOf(
                                                footer("u1"),
                                                block("u1", "a1", "p", 0, REPLY),
                                                prompt("u1", "Where did you put the notes?"),
                                            ).toPersistentList(),
                                        confirmedPaths = persistentSetOf("notes.txt", "README.md"),
                                    )
                                ChatScreen(state, ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate), host)
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
    }

    @Test
    fun `root file chip renders`() {
        if (out == null) return
        shoot(AppearanceMode.Dark, "1-notes-chip-dark")
        shoot(AppearanceMode.Light, "2-notes-chip-light")
    }

    private companion object {
        const val WIDTH = 1081
        const val HEIGHT = 2402
        const val DENSITY = 2.625f
        const val REPLY = "The notes are in `notes.txt`, and the short guide is in `README.md`. `docs.md` is not there yet."
    }
}
