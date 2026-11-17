package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerMic
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerVoice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitRecorderStage
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceField
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceRecorderRow
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceRecording
import io.github.eslamasabry.oc.ui.kitchat.composer.VOICE_RECORDER_TAG
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The chat's message box while words are dictated (owner 2026-11-17 on build 10077, "Wtf is this ugly microphone
 * flow?"): the box itself is the recorder. The voice slot is a stand-in here (the real one is `:feature:voice`, tested
 * in its MicInBoxTest); this proves what the chat's box does with it: the recorder row inside the box in place of the
 * plus and the chips, Send kept in its place and meaning "write down and send" (once, through the slot, never the
 * composer's own send of a half-heard box), the note after dictation inside the box, and no second box above it.
 */
@OptIn(ExperimentalTestApi::class)
class MicInBoxComposerTest {
    private class Actions : BasicComposerActions {
        var sends = 0

        override fun draftChanged(text: String) = Unit

        override suspend fun send(text: String): Boolean {
            sends++
            return true
        }

        override suspend fun selectModel(choice: ComposerModel?, variant: String): Boolean = true
    }

    /** A voice slot whose dictation the test switches on and off. */
    private class Voice(
        val on: MutableState<Boolean>,
        val note: MutableState<Boolean> = mutableStateOf(false),
        val streaming: String? = null,
    ) : KitComposerVoice {
        var sendNows = 0

        /** The theme's quieter text colour, as the box sees it. */
        var quiet = androidx.compose.ui.graphics.Color.Unspecified

        @Composable
        override fun unconfirmed(field: KitVoiceField): IntRange? {
            quiet = io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current.text2
            val words = streaming ?: return null
            // A streaming engine's words: the confirmed part, then the forming part (the last word) quieter.
            androidx.compose.runtime.LaunchedEffect(words) { field.replace(words) }
            val forming = words.substringAfterLast(' ')
            return (words.length - forming.length) until words.length
        }

        @Composable
        override fun Mic(field: KitVoiceField, modifier: Modifier) {
            KitComposerMic("Talk instead of typing", onClick = { on.value = true }, modifier = modifier.testTag("mic"))
        }

        @Composable override fun isActive(field: KitVoiceField): Boolean = on.value

        @Composable override fun Panel(field: KitVoiceField, modifier: Modifier) = Unit

        @Composable
        override fun recording(field: KitVoiceField): KitVoiceRecording? =
            if (on.value) KitVoiceRecording(held = false, sendNow = { sendNows++ }, sendLabel = "Send what you said") else null

        @Composable
        override fun Recorder(field: KitVoiceField, modifier: Modifier) {
            if (!on.value) return
            KitVoiceRecorderRow(
                stage = KitRecorderStage.Listening,
                status = "Listening…",
                modifier = modifier,
                elapsed = "0:05",
                level = { 0.5f },
                cancel = KitLineAction("Discard recording", { on.value = false }),
                accept = KitLineAction("Put the words in the message box", { on.value = false }),
            )
        }

        @Composable
        override fun Note(field: KitVoiceField, modifier: Modifier) {
            if (note.value) KitText("The microphone is off for this app", modifier.testTag("voice-note"))
        }
    }

    private val chrome = ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false })

    private fun ui() =
        BasicComposerUi(
            available = true,
            sending = false,
            steers = false,
            targetKey = "p/s",
            models = ComposerModels(usesDefault = true),
            filesAvailable = true,
        )

    @Composable
    private fun Scene(actions: Actions, voice: Voice, fontScale: Float = 1f) {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                stillFrame = true,
                localeTag = "en",
            ) {
                // A 360 dp phone with the page's 14 dp side margins.
                Box(Modifier.width(360.dp).padding(horizontal = 14.dp)) { BasicComposer(ui(), actions, chrome, voice = voice) }
            }
        }
    }

    private fun ComposeUiTest.bounds(tag: String): Rect =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun `B-MEDIA-067 the chat box hosts the recorder in place of the plus and chips, and Send keeps its place`() = runComposeUiTest {
        val on = mutableStateOf(false)
        val actions = Actions()
        val voice = Voice(on)
        setContent { Scene(actions, voice) }
        waitForIdle()
        val before = bounds("kit-composer-box")
        // The empty box shows the mic where Send goes.
        val micAt = bounds("mic")
        on.value = true
        waitForIdle()
        val box = bounds("kit-composer-box").inflate(4f)
        val row = bounds(VOICE_RECORDER_TAG)
        assertTrue(row.top >= box.top - 0.5f && row.bottom <= box.bottom + 0.5f, "the recorder $row lies inside the box $box")
        assertTrue(row.top >= bounds("chat-composer-field").bottom - 0.5f, "the words stay above the row")
        // No new level: the box does not grow a row for the recorder; the plus and the chips step aside.
        assertTrue(abs(bounds("kit-composer-box").height - before.height) <= 1f, "the box keeps its height: $before vs ${bounds("kit-composer-box")}")
        onAllNodesWithTag("chat-attach-button", useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithTag("chat-model-button", useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithTag("mic", useUnmergedTree = true).assertCountEquals(0)
        // Send keeps the mic's place at the box's end, enabled, named for what it does now.
        val send = bounds("chat-send-button")
        assertTrue(abs(send.center.x - micAt.center.x) <= 1f && abs(send.center.y - micAt.center.y) <= 1f, "Send $send in the mic's place $micAt")
        assertTrue(row.right <= send.left + 0.5f, "the recorder ends before Send")
        val sendNow = onNode(hasContentDescription("Send what you said") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true)
        sendNow.assertExists()
        onNode(
            hasContentDescription("Send what you said") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled),
            useUnmergedTree = true,
        ).assertDoesNotExist()
        sendNow.performClick()
        waitForIdle()
        assertEquals(1, voice.sendNows, "Send while listening goes through the recording, once")
        assertEquals(0, actions.sends, "the composer's own send never sends the half-heard box")
    }

    @Test
    fun `B-MEDIA-068 the note after dictation sits inside the box above the words`() = runComposeUiTest {
        val on = mutableStateOf(false)
        val note = mutableStateOf(true)
        setContent { Scene(Actions(), Voice(on, note)) }
        waitForIdle()
        val box = bounds("kit-composer-box").inflate(4f)
        val shown = bounds("voice-note")
        assertTrue(shown.top >= box.top - 0.5f && shown.bottom <= box.bottom + 0.5f, "the note $shown lies inside the box $box")
        assertTrue(shown.bottom <= bounds("chat-composer-field").top + 0.5f, "above the words")
    }

    @Test
    fun `B-MEDIA-069 at 360 dp and text 1_3 the recorder row and Send fit inside the box`() = runComposeUiTest {
        val on = mutableStateOf(true)
        setContent { Scene(Actions(), Voice(on), fontScale = 1.3f) }
        waitForIdle()
        val box = bounds("kit-composer-box").inflate(4f)
        for (tag in listOf(VOICE_RECORDER_TAG, "chat-send-button")) {
            val r = bounds(tag)
            assertTrue(r.left >= box.left - 0.5f && r.right <= box.right + 0.5f, "$tag $r inside the box $box")
        }
        val discard = onNode(hasContentDescription("Discard recording") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(discard.width >= 47.5f, "✕ keeps 48 dp at text 1.3: $discard")
    }

    @Test
    fun `B-MEDIA-074 words still forming are drawn quieter in the message field, the confirmed ones in the normal colour`() =
        runComposeUiTest {
            val on = mutableStateOf(true)
            val voice = Voice(on, streaming = "Whats next hello wor")
            setContent { Scene(Actions(), voice) }
            waitForIdle()
            val node =
                onNode(
                    androidx.compose.ui.test.hasSetTextAction() and
                        androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("chat-composer-field")),
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
            val layout =
                mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    .also { node.config[SemanticsActions.GetTextLayoutResult].action!!(it) }
                    .first()
            val shown = layout.layoutInput.text
            assertEquals("Whats next hello wor", shown.text)
            val spans = shown.spanStyles.filter { it.item.color == voice.quiet }
            assertEquals(listOf(17 to 20), spans.map { it.start to it.end }, "only the forming word is quieter: ${shown.spanStyles}")
        }
}
