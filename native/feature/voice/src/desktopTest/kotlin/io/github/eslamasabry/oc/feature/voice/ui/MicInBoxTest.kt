package io.github.eslamasabry.oc.feature.voice.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.voice.state.VoiceEnd
import io.github.eslamasabry.oc.engine.voice.state.VoiceMode
import io.github.eslamasabry.oc.engine.voice.state.VoiceState
import io.github.eslamasabry.oc.feature.voice.FakeField
import io.github.eslamasabry.oc.feature.voice.FakeHub
import io.github.eslamasabry.oc.feature.voice.SchedulerClock
import io.github.eslamasabry.oc.feature.voice.presentation.ComposerVoicePresenter
import io.github.eslamasabry.oc.feature.voice.presentation.MicPhase
import io.github.eslamasabry.oc.feature.voice.presentation.ComposerVoiceUi
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceConversationHandle
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceConversations
import io.github.eslamasabry.oc.engine.voice.conversation.VoiceConversationSession
import io.github.eslamasabry.oc.engine.voice.conversation.VoiceConversationSnapshot
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerChip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.composer.LocalKitComposerRecorder
import androidx.compose.foundation.layout.RowScope
import io.github.eslamasabry.oc.ui.kitchat.composer.VOICE_RECORDER_STATUS_TAG
import io.github.eslamasabry.oc.ui.kitchat.composer.VOICE_RECORDER_TAG
import io.github.eslamasabry.oc.ui.kitchat.composer.VOICE_RECORDER_TIME_TAG
import io.github.eslamasabry.oc.ui.kitchat.composer.VOICE_WAVE_TAG
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * The owner on build 10077 (2026-11-17, "Wtf is this ugly microphone flow?"): dictation drew a SECOND box above the
 * message box (dead grey bars, "Listening…", a timer) with Cancel and Send as underlined links beside a boxed Done, and
 * the big mic still in the box. Now the box itself is the recorder: one row inside it, a round ✕ (discard), the live
 * waveform and time, a round ✓ (put the words in the box); the box's own Send keeps its place and means "write down and
 * send". The scene arranges the real presenter and the real recorder in the kit's message box as the chat does.
 */
@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MicInBoxTest {
    private var saved: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() {
        Locale.setDefault(saved)
    }

    private class Rig(scope: TestScope, text: String = "") {
        val hub = FakeHub()
        val field = FakeField(text)
        val presenter = ComposerVoicePresenter(hub, SchedulerClock(scope), scope.backgroundScope) {}
        val dictation get() = hub.dictation

        init {
            presenter.attach(field)
        }

        fun listening(words: String, mode: VoiceMode = VoiceMode.Tap) {
            dictation.state.value = VoiceState.Listening(Instant.fromEpochMilliseconds(0), mode, 0, words)
        }
    }

    @Composable
    private fun Scene(
        rig: Rig,
        fontScale: Float = 1f,
        dir: LayoutDirection = LayoutDirection.Ltr,
        shown: ComposerVoiceUi? = null,
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(LocalDensity.current.density, fontScale),
            LocalLayoutDirection provides dir,
        ) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = if (dir == LayoutDirection.Rtl) "ar" else "en",
                stillFrame = true,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides dir) {
                    val live by rig.presenter.state.collectAsState()
                    val state = shown ?: live
                    val level by rig.presenter.level.collectAsState()
                    val recording = voiceRecording(state, rig.presenter, dictationOnly = false, sendLabel = SEND_NOW)
                    val recorder: (@Composable RowScope.() -> Unit)? =
                        if (recording == null) null
                        else ({ VoiceRecorder(state, { level }, rig.presenter, Modifier.weight(1f)) })
                    // A 360 dp phone: the box keeps the page's 14 dp side margins.
                    Box(Modifier.width(360.dp).padding(horizontal = 14.dp)) {
                        CompositionLocalProvider(LocalKitComposerRecorder provides recorder) {
                        KitComposerBox(
                            running = false,
                            field = { KitText("Whats next", Modifier.testTag("field")) },
                            stacked = true,
                            hasLeading = true,
                            leading = { KitComposerAdd("Attach and more", {}, Modifier.testTag("plus")) },
                            chips = { KitComposerChip("Fable 5.2", {}) },
                            trailing = {
                                KitComposerSend(
                                    label = recording?.sendLabel ?: "Send",
                                    onClick = { recording?.sendNow?.invoke() ?: rig.field.send() },
                                    modifier = Modifier.testTag("send"),
                                )
                            },
                        )
                        }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.bounds(tag: String): Rect =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.button(label: String) =
        onNode(
            hasContentDescription(label) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick),
            useUnmergedTree = true,
        )

    private fun ComposeUiTest.buttonBounds(label: String): Rect = button(label).fetchSemanticsNode().boundsInRoot

    /** The old pill's words: underlined Cancel and Send beside a boxed Done. */
    private fun ComposeUiTest.noOldPill() {
        for (words in listOf("Cancel", "Done", "Send")) {
            onAllNodesWithText(words, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    private fun assertInside(part: Rect, box: Rect, what: String) =
        assertTrue(
            part.left >= box.left - 0.5f && part.right <= box.right + 0.5f &&
                part.top >= box.top - 0.5f && part.bottom <= box.bottom + 0.5f,
            "$what $part lies inside the box $box",
        )

    /** The row's one polite live region, saying [words]. */
    private fun ComposeUiTest.liveRegion(words: String) =
        onNode(
            SemanticsMatcher("polite live region saying $words") {
                val said = it.config.getOrNull(SemanticsProperties.ContentDescription)
                val live = it.config.getOrNull(SemanticsProperties.LiveRegion) != null
                live && said?.any { d -> d.startsWith(words) } == true
            },
            useUnmergedTree = true,
        )

    private fun ComposeUiTest.settle(ts: TestScope) {
        ts.runCurrent()
        waitForIdle()
        ts.runCurrent()
        waitForIdle()
    }

    @Test
    fun `B-MEDIA-060 while dictating the box itself is the recorder - one row inside it, no second box`() = runTest {
        val ts = this
        val rig = Rig(ts)
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        rig.listening("hello")
        runCurrent()
        runComposeUiTest {
            setContent { Scene(rig) }
            settle(ts)
            // No second box: the old pill's text links are gone.
            noOldPill()
            val box = bounds("kit-composer-box").inflate(4f)
            val row = bounds(VOICE_RECORDER_TAG)
            assertInside(row, box, "the recorder")
            assertTrue(row.top >= bounds("field").bottom - 0.5f, "the words stay above the row")
            // Three round actions, no text links: ✕ at the start, ✓ at the end of the row, Send in its own place.
            val discard = buttonBounds("Discard recording")
            val use = buttonBounds("Put the words in the message box")
            val send = bounds("send")
            val wave = bounds(VOICE_WAVE_TAG)
            assertTrue(discard.left < wave.left && wave.right < use.left, "✕ $discard · wave $wave · ✓ $use")
            assertTrue(use.right <= send.left + 0.5f, "✓ $use ends the row, Send $send keeps the box's end")
            for ((what, r) in listOf("✕" to discard, "✓" to use, "Send" to send)) {
                assertTrue(r.width >= 47.5f && r.height >= 47.5f, "$what keeps a 48 dp target: $r")
            }
            // The plus and the chips step aside for the row.
            onAllNodesWithTag("plus", useUnmergedTree = true).assertCountEquals(0)
            onNodeWithTag(VOICE_RECORDER_TIME_TAG, useUnmergedTree = true).assertExists()
            // Spoken: the row is one polite live region that says it is listening.
            liveRegion("Listening").assertExists()
        }
    }

    @Test
    fun `B-MEDIA-061 discard throws the words away, puts the box back as it was and sends nothing`() = runTest {
        val ts = this
        val rig = Rig(ts, text = "Whats next")
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        rig.listening("and the tests")
        runCurrent()
        assertTrue(rig.field.value.contains("and the tests"), "the words heard show in the box while listening")
        runComposeUiTest {
            setContent { Scene(rig) }
            settle(ts)
            button("Discard recording").performClick()
            settle(ts)
        }
        assertEquals(listOf(false), rig.dictation.cancels, "the recording is thrown away, not kept")
        assertEquals("Whats next", rig.field.value, "the box is as it was")
        assertTrue(rig.field.sent.isEmpty(), "nothing is sent on discard")
        assertEquals(MicPhase.Idle, rig.presenter.state.value.phase)
    }

    @Test
    fun `B-MEDIA-062 use the words puts them in the box after the earlier text, editable, sends nothing`() =
        runTest {
        val ts = this
        val rig = Rig(ts, text = "Whats next")
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        rig.listening("and the tests")
        runCurrent()
        runComposeUiTest {
            setContent { Scene(rig) }
            settle(ts)
            button("Put the words in the message box").performClick()
            settle(ts)
            assertEquals(1, rig.dictation.finishes, "✓ ends the recording")
            rig.dictation.state.value = VoiceState.Writing("and the tests")
            settle(ts)
            // Writing it down names the stage; the waveform settles; ✓ is gone, nothing is sent yet.
            onNodeWithTag(VOICE_RECORDER_STATUS_TAG, useUnmergedTree = true).assertExists()
            button("Put the words in the message box").assertDoesNotExist()
            rig.dictation.state.value = VoiceState.Done("and the tests", VoiceEnd.Person)
            settle(ts)
            onAllNodesWithTag(VOICE_RECORDER_TAG, useUnmergedTree = true).assertCountEquals(0)
            onNodeWithTag("plus", useUnmergedTree = true).assertExists()
        }
        assertTrue(rig.field.value.startsWith("Whats next"), "the earlier text stays first: ${rig.field.value}")
        assertTrue(rig.field.value.endsWith("and the tests"), "the words land after it: ${rig.field.value}")
        assertTrue(rig.field.sent.isEmpty(), "✓ never sends")
    }

    @Test
    fun `B-MEDIA-063 Send while listening writes the words down and sends them once, however often pressed`() =
        runTest {
        val ts = this
        val rig = Rig(ts)
        runCurrent()
        rig.presenter.tapMic()
        runCurrent()
        rig.listening("ship it")
        runCurrent()
        runComposeUiTest {
            setContent { Scene(rig) }
            settle(ts)
            // The box's own Send, in its place, now named for what it does.
            onNodeWithTag("send", useUnmergedTree = true).assertExists()
            val send = button(SEND_NOW)
            send.performClick()
            send.performClick()
            settle(ts)
            assertEquals(1, rig.dictation.finishes, "two presses are one request")
            rig.dictation.state.value = VoiceState.Done("ship it", VoiceEnd.Person)
            settle(ts)
        }
        assertEquals(listOf("ship it"), rig.field.sent, "the words go once")
    }

    @Test
    fun `B-MEDIA-064 nothing in the recorder is cut at 360 dp, text 1 and 1_3, and Arabic mirrors it`() = runTest {
        val ts = this
        val looks = listOf(1f to LayoutDirection.Ltr, 1.3f to LayoutDirection.Ltr, 1.3f to LayoutDirection.Rtl)
        for ((scale, dir) in looks) {
            val rig = Rig(ts, text = "Whats next")
            runCurrent()
            rig.presenter.tapMic()
            runCurrent()
            rig.listening("and the tests")
            runCurrent()
            runComposeUiTest {
                setContent { Scene(rig, fontScale = scale, dir = dir) }
                settle(ts)
                val box = bounds("kit-composer-box").inflate(4f)
                val discard = buttonBounds("Discard recording")
                val use = buttonBounds("Put the words in the message box")
                val send = bounds("send")
                val time = bounds(VOICE_RECORDER_TIME_TAG)
                for ((what, r) in listOf("✕" to discard, "✓" to use, "Send" to send, "time" to time)) {
                    assertInside(r, box, "$what at $scale $dir")
                }
                val timeText =
                    onNodeWithTag(VOICE_RECORDER_TIME_TAG, useUnmergedTree = true).fetchSemanticsNode()
                        .config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { get ->
                            mutableListOf<TextLayoutResult>().also { get(it) }.firstOrNull()
                        }
                if (timeText != null) assertTrue(!timeText.hasVisualOverflow, "the time is whole at $scale $dir")
                assertTrue(bounds(VOICE_WAVE_TAG).width >= 24f, "the waveform keeps room at $scale $dir")
                if (dir == LayoutDirection.Rtl) {
                    assertTrue(discard.left > use.right, "Arabic: ✕ at the start (right) $discard, ✓ toward Send $use")
                    assertTrue(send.right <= use.left + 0.5f, "Arabic: Send keeps the box's end (left)")
                } else {
                    assertTrue(discard.right < use.left, "✕ $discard before ✓ $use")
                }
            }
        }
    }

    @Test
    fun `B-MEDIA-065 a held mic shows what letting go does and no buttons, the time and the waveform stay`() = runTest {
        val ts = this
        val rig = Rig(ts)
        runCurrent()
        rig.presenter.holdStart()
        runCurrent()
        rig.listening("hold words", mode = VoiceMode.Hold)
        runCurrent()
        runComposeUiTest {
            setContent { Scene(rig) }
            settle(ts)
            button("Discard recording").assertDoesNotExist()
            button("Put the words in the message box").assertDoesNotExist()
            onNodeWithTag(VOICE_RECORDER_STATUS_TAG, useUnmergedTree = true).assertExists()
            onNodeWithTag(VOICE_WAVE_TAG, useUnmergedTree = true).assertExists()
            noOldPill()
        }
    }

    /** A connected, ready conversation the hands-free mode can talk to. */
    private class Talk : VoiceConversations {
        val snapshot = MutableStateFlow(VoiceConversationSnapshot(connected = true, ready = true))

        override fun open(ref: ConversationRef): VoiceConversationHandle =
            VoiceConversationHandle(
                object : VoiceConversationSession {
                    override val voiceConversation = snapshot

                    override suspend fun sendVoice(text: String): Outcome<Unit> = Unit.ok()

                    override suspend fun readVoiceReply(id: String, automatic: Boolean): Outcome<String> = "".ok()
                },
                release = {},
            )
    }

    @Test
    fun `B-MEDIA-070 hands-free mode listens in the box's row - waveform, time, discard ends it, no check`() =
        runTest {
            val ts = this
            val rig = Rig(ts)
            runCurrent()
            rig.presenter.attachConversation(ConversationRef(ProfileId("p"), SessionId("s")), Talk())
            runCurrent()
            rig.presenter.startConversation()
            runCurrent()
            assertTrue(rig.presenter.state.value.conversation, "the hands-free mode started")
            rig.listening("what changed", mode = VoiceMode.Conversation)
            runCurrent()
            runComposeUiTest {
                setContent { Scene(rig) }
                settle(ts)
                val box = bounds("kit-composer-box").inflate(4f)
                val row = bounds(VOICE_RECORDER_TAG)
                assertInside(row, box, "the hands-free row")
                onNodeWithTag(VOICE_WAVE_TAG, useUnmergedTree = true).assertExists()
                onNodeWithTag(VOICE_RECORDER_TIME_TAG, useUnmergedTree = true).assertExists()
                button("Put the words in the message box").assertDoesNotExist()
                noOldPill()
                button("Leave voice mode").performClick()
                settle(ts)
            }
            assertTrue(!rig.presenter.state.value.conversation, "✕ ends the hands-free mode")
            assertTrue(rig.field.sent.isEmpty(), "leaving sends nothing")
        }

    @Test
    fun `B-MEDIA-071 hands-free mode names its state in the row - listening, reading aloud, waiting`() =
        runTest {
            val ts = this
            val rig = Rig(ts)
            runCurrent()
            val base =
                ComposerVoiceUi(available = true, conversationAvailable = true, conversation = true, canListen = true)
            val cases =
                listOf(
                    base.copy(phase = MicPhase.Listening, mode = VoiceMode.Conversation) to "Listening",
                    base.copy(speakingReply = true) to "Reading the reply aloud",
                    base.copy(waitingReply = true) to "Waiting for the reply",
                )
            for ((state, words) in cases) {
                runComposeUiTest {
                    setContent { Scene(rig, shown = state) }
                    settle(ts)
                    liveRegion(words).assertExists()
                    button("Leave voice mode").assertExists()
                    button("Put the words in the message box").assertDoesNotExist()
                    noOldPill()
                }
            }
        }

    private companion object {
        const val SEND_NOW = "Send what you said"
    }
}
