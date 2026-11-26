package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.E20Goldens.picture
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
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiTest
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        kitUiTest {
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
    fun `E20-S20 Camera in the tray runs the camera capture and closes the tray`() = kitUiTest {
        val actions = Actions()
        setContent { Theme { BasicComposer(ui(), actions, chrome()) } }
        onNodeWithTag("chat-attach-button").performClick()
        onNodeWithText("Camera").performClick()
        waitForIdle()
        assertEquals(listOf("attach Camera"), actions.calls)
        onNodeWithTag("kit-composer-tray").assertDoesNotExist()
    }

    @Test
    fun `E20-S20 while a send runs Photos, Camera and File are off, More still opens`() = kitUiTest {
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
        kitUiTest {
            setContent { Theme { BasicComposer(ui(attachments = false), Actions(), chrome(commands = {})) } }
            onNodeWithTag("chat-attach-button").performClick()
            onNodeWithTag("kit-composer-tray").assertDoesNotExist()
            onNodeWithText("Prompt tools").assertIsDisplayed()
            onNodeWithText("This server takes text only").assertIsDisplayed()
            onNodeWithText("Commands and agents").assertIsDisplayed()
        }

    @Test
    fun `E20-S20 More lists commands, then the prompt shelf, and does not repeat the tray`() = kitUiTest {
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

    private data class CompactCase(
        val scale: Float,
        val scaleTag: String,
        val dark: Boolean,
        val modeTag: String,
    )

    private val compactCases =
        listOf(
            CompactCase(1.0f, "1_0", false, "light"),
            CompactCase(1.0f, "1_0", true, "dark"),
            CompactCase(1.3f, "1_3", false, "light"),
            CompactCase(1.3f, "1_3", true, "dark"),
        )

    private val trayTileTags =
        listOf(
            "composer-tool-gallery",
            "composer-tool-camera",
            "composer-tool-attach",
            "composer-tray-more",
        )

    private fun Rect.overlapsRect(other: Rect): Boolean =
        right > other.left && other.right > left && bottom > other.top && other.bottom > top

    private fun forEachCompactCase(
        phrases: List<String>,
        block: KitUiTest.(CompactCase, Actions) -> Unit,
    ) {
        for (case in compactCases) {
            val actions = Actions()
            kitUiTest(360, 480) {
                setContent {
                    E20Scene(scale = case.scale, dark = case.dark, motion = MotionLevel.Full) {
                        KitSheetHost {
                            Box(
                                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                BasicComposer(ui().copy(phrases = phrases), actions, chrome())
                            }
                        }
                    }
                }
                waitForIdle()
                block(case, actions)
            }
        }
    }

    private fun KitUiTest.assertNoPhraseTrayOverlap(phrases: List<String>, where: String) {
        val tiles =
            trayTileTags.map { tag ->
                val rect = boundsOfTag(tag)
                assertTrue(rect.width > 0f && rect.height > 0f, "$where: tray tile $tag empty $rect")
                tag to rect
            }
        val phraseBoxes = buildList {
            onAllNodesWithTag("chat-phrase-chips", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .forEach { add("row" to it.boundsInRoot) }
            phrases.forEach { phrase ->
                onAllNodesWithText(phrase, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .forEach { add(phrase to it.boundsInRoot) }
            }
        }
        for ((phraseLabel, chipRect) in phraseBoxes) {
            for ((tileTag, tileRect) in tiles) {
                assertFalse(
                    chipRect.overlapsRect(tileRect),
                    "$where: phrase '$phraseLabel' $chipRect overlaps tray '$tileTag' $tileRect",
                )
            }
        }
    }

    private fun KitUiTest.settleTrayMotion() {
        mainClock.advanceTimeBy(1_500L)
        waitForIdle()
    }

    @Test
    fun `B-483 plus tray at 360 dp has zero overlap with learned-phrase chips`() {
        val phrases = listOf("Hi", "Continue", "Run the tests")
        forEachCompactCase(phrases) { case, _ ->
            val where = "360dp ${case.modeTag} ${case.scaleTag}"
            onNodeWithTag("chat-phrase-chips", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("kit-composer-tray").assertDoesNotExist()
            onNodeWithTag("chat-attach-button").performClick()
            mainClock.advanceTimeBy(64L)
            assertNoPhraseTrayOverlap(phrases, "$where mid")
            settleTrayMotion()
            onNodeWithTag("kit-composer-tray").assertIsDisplayed()
            val out = File("build/renders/b483-plus-tray-360-${case.modeTag}-${case.scaleTag}.png")
            out.parentFile.mkdirs()
            ImageIO.write(picture(), "png", out)
            assertNoPhraseTrayOverlap(phrases, where)
        }
    }

    @Test
    fun `B-483 tapping Photos with learned-phrase chips invokes Photos and never fires a phrase`() {
        val phrases = listOf("Hi", "Continue")
        forEachCompactCase(phrases) { case, actions ->
            val where = "360dp ${case.modeTag} ${case.scaleTag}"
            onNodeWithTag("chat-attach-button").performClick()
            settleTrayMotion()
            onNodeWithTag("kit-composer-tray").assertIsDisplayed()
            onNodeWithTag("composer-tool-gallery").performClick()
            settleTrayMotion()
            assertEquals(listOf("attach Photos"), actions.calls, "$where: Photos tap must invoke Photos only")
            onNodeWithTag("kit-composer-tray").assertDoesNotExist()
        }
    }

    @Test
    fun `B-483 closing the plus tray restores learned-phrase chips cleanly`() {
        val phrases = listOf("Hi", "Continue")
        forEachCompactCase(phrases) { case, actions ->
            val where = "360dp ${case.modeTag} ${case.scaleTag}"
            onNodeWithText("Hi", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("chat-attach-button").performClick()
            settleTrayMotion()
            onNodeWithTag("kit-composer-tray").assertIsDisplayed()
            onNodeWithTag("chat-attach-button").performClick()
            settleTrayMotion()
            onNodeWithTag("kit-composer-tray").assertDoesNotExist()
            onNodeWithTag("chat-phrase-chips", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Hi", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Continue", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Hi", useUnmergedTree = true).performClick()
            waitForIdle()
            assertEquals(listOf("draftChanged <Hi>"), actions.calls, "$where: restored chip must insert phrase")
        }
    }
}
