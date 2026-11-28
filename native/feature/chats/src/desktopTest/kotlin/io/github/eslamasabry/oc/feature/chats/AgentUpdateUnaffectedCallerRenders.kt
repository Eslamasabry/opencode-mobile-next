package io.github.eslamasabry.oc.feature.chats

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationProject
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Pictures only, nothing asserted: a screen the agents' update sheet's kit changes must leave alone. New conversation
 * with a message whose delivery is not confirmed places a KitActionBlock with a secondary ("Refresh") and a tertiary
 * line ("Discard"), and neither button opts in to the label cross-fade. At 360 dp, dark, text 1.0 and 1.3. Writes
 * `build/agent-update-sheet-redraw/<name>.png`, beside the sheet's own pictures.
 */
@OptIn(ExperimentalTestApi::class)
class AgentUpdateUnaffectedCallerRenders {
    private val out = File("build/agent-update-sheet-redraw").also { it.mkdirs() }

    private object Actions : NewConversationActions {
        override fun back() = Unit
        override fun addServer() = Unit
        override fun chooseServer(profile: ProfileId) = Unit
        override fun draftChanged(text: String) = Unit
        override fun chooseDirectory(directory: String) = Unit
        override fun chooseModel(model: ModelRef?) = Unit
        override fun chooseAgent(agent: String?) = Unit
        override fun separateCopy(on: Boolean) = Unit
        override fun sheet(sheet: NewConversationSheet?) = Unit
        override fun start() = Unit
        override fun import(conversation: ImportableConversation) = Unit
        override fun retryLoad() = Unit
    }

    private val phone = ProfileId("p1")
    private val notConfirmed =
        NewConversationUi(
            servers = listOf(NewConversationServer(phone, "This phone", connected = true)),
            profile = phone,
            setup =
                NewConversationSetup(
                    "/root/projects/shop",
                    listOf(NewConversationProject("/root/projects/shop", "shop", false)),
                    emptyList(),
                    emptyList(),
                ),
            directory = "/root/projects/shop",
            text = "hello",
            attempted = true,
            deliveryUnconfirmed = true,
        )

    private fun shoot(name: String, fontScale: Float) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("en"))
            kitUiTest(900, 2000) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(2.5f, fontScale)) {
                        KitTheme(
                            appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            NewConversationScreen(notConfirmed, Actions)
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `unaffected caller new conversation not confirmed text100 dark`() {
        shoot("unaffected-new-conversation-not-confirmed-text100-dark", fontScale = 1f)
    }

    @Test
    fun `unaffected caller new conversation not confirmed text130 dark`() {
        shoot("unaffected-new-conversation-not-confirmed-text130-dark", fontScale = 1.3f)
    }
}
