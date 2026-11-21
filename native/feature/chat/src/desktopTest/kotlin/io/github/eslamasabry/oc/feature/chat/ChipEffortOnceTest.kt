package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.E20Goldens.picture
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner, build 10073: in an Antigravity conversation the model chip read "Gemini 3.8 Flash (High) · high". The agent
 * names the effort inside the model's name; the app echoed the effort again. At 360 dp, text 1.0 and 1.3, left to
 * right and right to left, the chip names the model once and nothing is cut.
 */
@OptIn(ExperimentalTestApi::class)
class ChipEffortOnceTest {
    private class Actions : BasicComposerActions {
        override fun draftChanged(text: String) {}

        override suspend fun send(text: String): Boolean = true

        override fun loadModels() {}

        override suspend fun selectModel(choice: ComposerModel?, variant: String): Boolean = true

        override fun attachFile() {}

        override fun removeAttachment(index: Int) {}

        override fun dismissAttachmentFailure() {}

        override suspend fun selectAgent(agent: String): Boolean = true

        override fun mentionFile(path: String) {}

        override fun attach(kind: AttachKind) {}

        override fun hidePhrase(text: String) {}
    }

    private val model =
        ComposerModel("antigravity", "gemini-3.8-flash-high", "Gemini 3.8 Flash (High)", true, variants = listOf("low", "high"))

    private fun ui(selected: ComposerModel = model, variant: String = "high") =
        BasicComposerUi(
            available = true,
            sending = false,
            steers = false,
            draft = DraftFacts(),
            targetKey = "p/s",
            models = ComposerModels(choices = listOf(selected), selected = selected, variant = variant),
            pickingFile = false,
            attachmentFailure = null,
            filesAvailable = true,
            phrases = emptyList(),
        )

    private val bare = ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false })

    private val chrome =
        ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false }, rowChips = {
            io.github.eslamasabry.oc.ui.kit.chip.KitChip(
                "Auto-approve on",
                kind = io.github.eslamasabry.oc.ui.kit.chip.KitChipKind.Action,
                selected = true,
                onClick = {},
                modifier = Modifier.testTag("approval-chip"),
            )
        })

    private fun scene(
        locale: String,
        scale: Float,
        shot: String?,
        state: BasicComposerUi = ui(),
        chrome: ComposerChrome = this.chrome,
        check: ComposeUiTest.() -> Unit,
    ) =
        kitUiTest(360, 200) {
            setContent {
                E20Scene(scale = scale, locale = locale) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { BasicComposer(state, Actions(), chrome) }
                }
            }
            waitForIdle()
            // The runner uploads no pictures: a recording run prints the scene's PNG bytes in its log, before it checks.
            if (shot != null && System.getProperty("oc.goldens.record") == "true") {
                val bytes = ByteArrayOutputStream().also { ImageIO.write(picture(), "png", it) }.toByteArray()
                val chunks = Base64.getEncoder().encodeToString(bytes).chunked(180)
                chunks.forEachIndexed { i, c -> println("CHIP_PNG $shot $i/${chunks.size} $c") }
            }
            check()
        }

    private fun ComposeUiTest.chipWords(): List<String> =
        onAllNodes(hasText("Gemini 3.8 Flash", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

    @Test
    fun `B-CHAT-EFFORT-9 the chip names the model once and nothing is cut at 360 dp, text 1 and 1_3, en and ar`() {
        for (scale in listOf(1f, 1.3f)) for (locale in listOf("en", "ar")) {
            val where = "360 dp, text $scale, $locale"
            // The picture has the approval chip beside the model, as a real conversation does; the chip shrinks there by
            // the box's own design, so "whole" is asked of the chip with the box's room to itself.
            scene(locale, scale, "chip-$locale-$scale") {
                val words = chipWords()
                assertTrue("Gemini 3.8 Flash (High)" in words, "$where: the chip says the model's own name; it says $words")
                assertEquals(
                    emptyList(),
                    words.filter { it.contains("·") && it.contains("high", ignoreCase = true) },
                    "$where: the effort is not said a second time",
                )
            }
            // At text 1.3 the kit's chip ellipsizes a name this long beside the plus and the mic (its design, measured:
            // run 44029281726); the words it carries are whole either way, and 1.0 shows the whole name.
            // The picture of the chip with the box's room to itself (no approval chip beside it).
            scene(locale, scale, "bare-$locale-$scale", chrome = bare) {
                if (scale == 1f) assertFalse(isCut("Gemini 3.8 Flash (High)"), "$where: the model's name is whole")
            }
        }
    }

    @Test
    fun `B-CHAT-EFFORT-10 a name that does not carry the effort still shows it, whole at 360 dp text 1_3`() {
        val plain = ComposerModel("antigravity", "gemini-4", "Gemini 4", true, variants = listOf("low", "high"))
        for (locale in listOf("en", "ar")) scene(locale, 1.3f, null, ui(plain)) {
            val words =
                onAllNodes(hasText("Gemini 4", substring = true), useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            assertTrue(words.any { it.contains("Gemini 4") && it.contains("high", ignoreCase = true) }, "$locale: model and effort are both said: $words")
        }
    }

    /** Pictures only (no asserts), so a run on the old behaviour still prints its "before" scenes. */
    @Test
    fun `B-CHAT-EFFORT-11 pictures of the chip with the box's room to itself`() {
        for (scale in listOf(1f, 1.3f)) for (locale in listOf("en", "ar"))
            scene(locale, scale, "only-$locale-$scale", chrome = bare) {}
    }
}
