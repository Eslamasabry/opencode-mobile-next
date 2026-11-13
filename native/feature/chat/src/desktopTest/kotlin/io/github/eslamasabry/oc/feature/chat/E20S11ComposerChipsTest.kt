package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.E20Goldens.compareGolden
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import kotlin.test.Test
import kotlin.math.abs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * E20-S11 (owner's phone, 11-07): the phrase chips sat centred while the rest of the composer starts on its edge, and
 * the model chip read "Crouton …" beside "Approve everything". The phrases start on the message box's edge; the model's
 * name has priority and the approval chip shortens first. 360 and 600 dp, left to right and right to left.
 */
@OptIn(ExperimentalTestApi::class)
class E20S11ComposerChipsTest {
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

    private val model = ComposerModel("crouton", "crouton-fast-2", "Crouton Fast 2", true)
    // What the real chip says for either automatic mode (Flutter `chatChipAutoApproveOn`).
    private val approve = "Auto-approve on"

    private fun ui() =
        BasicComposerUi(
            available = true,
            sending = false,
            steers = false,
            draft = DraftFacts(),
            targetKey = "p/s",
            models = ComposerModels(choices = listOf(model), selected = model),
            pickingFile = false,
            attachmentFailure = null,
            filesAvailable = true,
            phrases = listOf("Continue", "Run the tests"),
        )

    private val chrome =
        ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false }, rowChips = {
            // The real approval chip's part (header/ui/ApprovalChip.kt, Flutter `_AutoApprovalIndicator`).
            io.github.eslamasabry.oc.ui.kit.chip.KitChip(
                approve,
                kind = io.github.eslamasabry.oc.ui.kit.chip.KitChipKind.Action,
                selected = true,
                onClick = {},
                modifier = Modifier.testTag("approval-chip"),
            )
        })

    private fun scene(width: Int, locale: String, check: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(width, 360) {
            setContent {
                E20Scene(locale = locale) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        BasicComposer(ui(), Actions(), chrome)
                    }
                }
            }
            waitForIdle()
            check()
        }

    // Owner 2026-11-14 ("no new bar or row level"): the chips ride the box's own controls row in every style. Was:
    // "...the approval chip leads the strip above the box".
    @Test
    fun `E20-S11 F1 the model's name shows whole, the chips ride inside the box`() {
        for (width in listOf(360, 600)) for (locale in listOf("en", "ar")) scene(width, locale) {
            val where = "$width dp, $locale"
            if (width >= 600) assertFalse(isCut("Crouton Fast 2"), "$where: the model's name is whole")
            val modelChip = boundsOfTag("chat-model-button")
            val chip = boundsOfTag("approval-chip")
            // The tag sits inside the face's S1 padding.
            val box = boundsOfTag("kit-composer-box").inflate(4f)
            for ((what, r) in listOf("approval" to chip, "model" to modelChip))
                assertTrue(r.top >= box.top - 0.5f && r.bottom <= box.bottom + 0.5f, "$where: the $what chip $r lies inside the box $box")
            assertTrue(abs(chip.center.y - modelChip.center.y) < 1f, "$where: both chips share one row, $chip, $modelChip")
        }
    }

    @Test
    fun `E20-S11 the phrase chips start on the message box's edge`() {
        for (width in listOf(360, 600)) for (locale in listOf("en", "ar")) scene(width, locale) {
            val where = "$width dp, $locale"
            val box = boundsOfTag("kit-composer-box")
            val continueChip = boundsOfText("Continue")
            val second = boundsOfText("Run the tests")
            if (locale == "en") {
                assertTrue(continueChip.left < second.left, "$where: the first phrase leads")
                // The chip's words sit inside its own padding; the chip itself starts on the box's edge.
                assertTrue(continueChip.left - box.left in 0f..24f, "$where: first phrase $continueChip on box $box")
            } else {
                assertTrue(continueChip.right > second.right, "$where: right to left the first phrase leads")
                assertTrue(box.right - continueChip.right in 0f..24f, "$where: first phrase $continueChip on box $box")
            }
            // Never centred: the far side keeps the free room.
            val freeStart = if (locale == "en") continueChip.left - box.left else box.right - continueChip.right
            val freeEnd = if (locale == "en") box.right - second.right else second.left - box.left
            assertTrue(freeEnd > freeStart + 8f, "$where: the free room is at the end ($freeStart vs $freeEnd)")
        }
    }

    @Test
    fun `E20-S11 goldens at 360 and 600 dp, left to right and right to left`() {
        for (width in listOf(360, 600)) for (locale in listOf("en", "ar")) scene(width, locale) {
            compareGolden("e20-s11-composer-$width-$locale")
        }
    }
}
