package io.github.eslamasabry.oc.feature.chats

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationModel
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationProject
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Picture only, asserts nothing: New conversation's "Choose a model" sheet with a model that has thinking levels
 * chosen, so the Thinking block stands first under the title, at 360 dp wide, dark and light. Writes PNGs to
 * `THINKING_FIRST_OUT` (or the picture server's render-out folder); does nothing when neither exists.
 */
@OptIn(ExperimentalTestApi::class)
class NewConversationThinkingRenders {
    private val out =
        (System.getenv("THINKING_FIRST_OUT")?.let(::File)
                ?: File(System.getProperty("user.home"), "render-out").takeIf { it.isDirectory }?.resolve("thinking-first"))
            ?.also(File::mkdirs)

    private object Actions : NewConversationActions {
        override fun back() {}
        override fun addServer() {}
        override fun chooseServer(profile: ProfileId) {}
        override fun draftChanged(text: String) {}
        override fun chooseDirectory(directory: String) {}
        override fun chooseModel(model: ModelRef?) {}
        override fun chooseAgent(agent: String?) {}
        override fun separateCopy(on: Boolean) {}
        override fun sheet(sheet: NewConversationSheet?) {}
        override fun start() {}
        override fun import(conversation: ImportableConversation) {}
        override fun retryLoad() {}
    }

    private val p1 = ProfileId("p1")
    private val deepseek =
        NewConversationModel(
            ModelRef("opencode", "deepseek-v4.1-flash-free"),
            "DeepSeek V4.1 Flash Free",
            providerName = "OpenCode Zen",
            variants = listOf("low", "medium", "high"),
        )
    private val models =
        listOf(
            deepseek,
            NewConversationModel(ModelRef("anthropic", "sonnet"), "Sonnet", providerName = "Anthropic"),
            NewConversationModel(ModelRef("anthropic", "opus"), "Opus", providerName = "Anthropic"),
        )
    private val ui =
        NewConversationUi(
            servers = listOf(NewConversationServer(p1, "This phone", connected = true)),
            profile = p1,
            setup =
                NewConversationSetup(
                    "/root/projects/shop",
                    listOf(NewConversationProject("/root/projects/shop", "shop", false)),
                    models,
                    emptyList(),
                    model = deepseek.model,
                ),
            directory = "/root/projects/shop",
            model = deepseek.model,
            sheet = NewConversationSheet.Models,
            text = "hello",
        )

    private fun shoot(mode: AppearanceMode, name: String) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        runDesktopComposeUiTest(945, 2100) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.625f)) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        appearance = KitAppearance(mode = mode, style = Style.Solid, motion = MotionLevel.Off),
                    ) {
                        NewConversationScreen(ui, Actions)
                    }
                }
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
    }

    @Test
    fun `new conversation thinking renders`() {
        if (out == null) return
        shoot(AppearanceMode.Dark, "1-new-conversation-model-sheet-dark")
        shoot(AppearanceMode.Light, "2-new-conversation-model-sheet-light")
    }
}
