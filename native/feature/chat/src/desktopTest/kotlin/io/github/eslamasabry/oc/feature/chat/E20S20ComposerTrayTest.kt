package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTrayRules
import io.github.eslamasabry.oc.feature.chat.composer.TrayItem
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** E20-S20: the composer's "+" tray and its More list copy Flutter (composer_tray.dart, composer_tools.dart). */
@OptIn(ExperimentalTestApi::class)
class E20S20ComposerTrayTest {
    private val all = setOf(AttachKind.Photos, AttachKind.Camera, AttachKind.Clipboard)

    private class Actions : BasicComposerActions {
        val calls = mutableListOf<String>()

        override fun draftChanged(text: String) {
            calls += "draftChanged <$text>"
        }

        override suspend fun send(text: String): Boolean = true

        override fun attachFile() {
            calls += "attachFile"
        }

        override fun attach(kind: AttachKind) {
            calls += "attach $kind"
        }
    }

    private fun chrome(commands: (() -> Unit)? = null, saved: (() -> Unit)? = null) =
        ComposerChrome(
            { false }, { false }, {}, { null }, {}, { null }, {}, {}, { false },
            openCommands = commands,
            openSavedPrompts = saved,
        )

    @Composable
    private fun Theme(content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            stillFrame = true,
            localeTag = "en",
        ) {
            KitSheetHost { content() }
        }
    }

    private fun ui(
        kinds: Set<AttachKind> = all,
        attachments: Boolean = true,
        imagesOnly: Boolean = false,
        sending: Boolean = false,
        draft: DraftFacts = DraftFacts(),
    ) = BasicComposerUi(
        available = true,
        sending = sending,
        steers = false,
        draft = draft,
        targetKey = "p/s",
        filesAvailable = true,
        attachKinds = kinds,
        attachmentsSupported = attachments,
        imagesOnly = imagesOnly,
    )

    @Test
    fun `E20-S20 the tray is Photos, Camera, File, More in Flutter's order`() {
        assertEquals(
            listOf(TrayItem.Photos, TrayItem.Camera, TrayItem.File, TrayItem.More),
            ComposerTrayRules.items(true, false, true, all),
        )
        assertEquals(
            listOf(TrayItem.Photos, TrayItem.Camera, TrayItem.More),
            ComposerTrayRules.items(true, imagesOnly = true, filesAvailable = true, kinds = all),
        )
        assertEquals(emptyList(), ComposerTrayRules.items(false, false, true, all), "no files, no tray")
        assertEquals(emptyList(), ComposerTrayRules.items(true, true, true, emptySet()), "nothing to attach, no tray")
    }

    @Test
    fun `E20-S20 plus opens one row of four, without paste, reuse, commands, send later, clear or voice`() =
        runComposeUiTest {
            setContent { Theme { BasicComposer(ui(), Actions(), chrome(commands = {})) } }
            onNodeWithTag("kit-composer-tray").assertDoesNotExist()
            onNodeWithContentDescription("Attach and more").performClick()
            onNodeWithTag("kit-composer-tray").assertIsDisplayed()
            onNodeWithContentDescription("Close").assertExists()
            for (word in listOf("Photos", "Camera", "File", "More")) onNodeWithText(word).assertIsDisplayed()
            for (gone in
                listOf(
                    "Paste image from clipboard",
                    "Reuse a prompt",
                    "Commands",
                    "Send later or repeat",
                    "Clear draft text",
                    "Voice conversation",
                ))
                onNodeWithText(gone).assertDoesNotExist()
        }

    @Test
    fun `E20-S20 Camera in the tray runs the camera capture and closes the tray`() = runComposeUiTest {
        val actions = Actions()
        setContent { Theme { BasicComposer(ui(), actions, chrome()) } }
        onNodeWithTag("chat-attach-button").performClick()
        onNodeWithText("Camera").performClick()
        waitForIdle()
        assertEquals(listOf("attach Camera"), actions.calls)
        onNodeWithTag("kit-composer-tray").assertDoesNotExist()
    }

    @Test
    fun `E20-S20 while a send runs Photos, Camera and File are off, More still opens`() = runComposeUiTest {
        val actions = Actions()
        setContent { Theme { BasicComposer(ui(sending = true), actions, chrome()) } }
        onNodeWithTag("chat-attach-button").performClick()
        onNodeWithTag("composer-tool-gallery").assertIsNotEnabled()
        onNodeWithTag("composer-tool-camera").assertIsNotEnabled()
        onNodeWithTag("composer-tool-attach").assertIsNotEnabled()
        onNodeWithText("More").performClick()
        onNodeWithTag("composer-tools-sheet").assertExists()
        assertTrue(actions.calls.none { it.startsWith("attach") })
    }

    @Test
    fun `E20-S20 a server that takes no files has no tray, plus opens the tools list and says why`() =
        runComposeUiTest {
            setContent { Theme { BasicComposer(ui(attachments = false), Actions(), chrome(commands = {})) } }
            onNodeWithTag("chat-attach-button").performClick()
            onNodeWithTag("kit-composer-tray").assertDoesNotExist()
            onNodeWithText("Prompt tools").assertIsDisplayed()
            onNodeWithText("This server takes text only").assertIsDisplayed()
            onNodeWithText("Commands and agents").assertIsDisplayed()
        }

    @Test
    fun `E20-S20 More lists commands, then the prompt shelf, and does not repeat the tray`() = runComposeUiTest {
        var commands = 0
        val actions = Actions()
        setContent {
            Theme {
                BasicComposer(
                    ui(draft = DraftFacts(text = "draft words", saved = "draft words")),
                    actions,
                    chrome(commands = { commands += 1 }, saved = {}),
                )
            }
        }
        onNodeWithTag("chat-attach-button").performClick()
        onNodeWithText("More").performClick()
        onNodeWithText("Attach file").assertDoesNotExist()
        onNodeWithText("Photo library").assertDoesNotExist()
        val commandsTop = onNodeWithText("Commands and agents").getBoundsInRoot().top
        val promptsTop = onNodeWithText("Prompts").getBoundsInRoot().top
        assertTrue(commandsTop < promptsTop, "commands come before the prompt shelf")
        onNodeWithText("Prompts").performClick()
        onNodeWithText("Saved prompts").assertIsDisplayed()
        onNodeWithText("Clear draft text").performClick()
        waitForIdle()
        onNodeWithTag("composer-tools-sheet").assertDoesNotExist()
        assertTrue("draftChanged <>" in actions.calls)
        assertEquals(0, commands)
    }
}
