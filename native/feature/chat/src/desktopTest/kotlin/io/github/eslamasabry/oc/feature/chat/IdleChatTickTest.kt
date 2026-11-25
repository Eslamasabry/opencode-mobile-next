package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.ui.TranscriptProbe
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.FrameMeter
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiTest
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.turn.SWEEP_TAG
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * B-CHAT-340 / B-CHAT-341: an idle chat with a command an ended turn left running only updates its time labels on
 * each 1-second clock tick, never recomposing transcript rows or looping the step light.
 */
@OptIn(ExperimentalTestApi::class)
class IdleChatTickTest {
    private val sid = Ref.sessionId.raw

    private val composerSlot =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    @Composable
    private fun Root(appearance: KitAppearance, stillFrame: Boolean, content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = appearance,
            localeTag = "en",
            stillFrame = stillFrame,
        ) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor(), content = content)
        }
    }

    private fun idleUi(vararg rows: TranscriptRow) =
        ChatUi.loading(Ref).copy(
            title = "Native chat",
            phase = ChatPhase.Live,
            rows = persistentListOf(*rows),
            live = null,
        )

    private fun bash(): Part =
        Part(
            id = "p-c1",
            messageId = "m2",
            sessionId = sid,
            type = PartType.Tool,
            rawType = "tool",
            callId = "c1",
            toolName = "bash",
            tool =
                ToolState(
                    ToolStatus.Running,
                    input = JsonObject(mapOf("command" to JsonPrimitive("sleep 20 && uname -a"))),
                    output = null,
                    metadata = JsonObject(mapOf("background" to JsonPrimitive(true))),
                    startedAtMs = 2_000,
                    completedAtMs = null,
                ),
        )

    private val words =
        Part(
            "p-words",
            "m2",
            sid,
            PartType.Text,
            "text",
            text = "I started it in the background and will report the output once it finishes.",
        )

    private fun leftRunningFeed(): TurnFeed {
        val promptMsg =
            MessageWithParts(
                MessageInfo("m1", sid, Role.User, time = MsgTime(1_000, 1_000)),
                listOf(Part("p1", "m1", sid, PartType.Text, "text", text = "what kernel is this?")).toImmutableList(),
            )
        val replyMsg =
            MessageWithParts(
                MessageInfo(
                    "m2",
                    sid,
                    Role.Assistant,
                    providerId = "antigravity",
                    modelId = "m",
                    time = MsgTime(2_000, 12_000),
                ),
                listOf(bash(), words).toImmutableList(),
            )
        val keeper = TurnFeedKeeper(sid)
        keeper.history(listOf(promptMsg, replyMsg))
        keeper.link(ChatLink.Live)
        keeper.event(GatewayEvent.RunStarted(SessionId(sid)), 2_000)
        keeper.event(GatewayEvent.RunEnded(SessionId(sid), RunOutcome.Succeeded), 12_000)
        return keeper.feed.value
    }

    private val trackedTag =
        SemanticsMatcher("tag starts with chat- or kit-step-") { node ->
            val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: return@SemanticsMatcher false
            tag.startsWith("chat-") || tag.startsWith("kit-step-")
        }

    private fun KitUiTest.taggedBounds(): Map<Int, Pair<String, Rect>> =
        runWithoutImplicitWait {
            onAllNodes(trackedTag, useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .associate { node ->
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
                    node.id to (tag to node.boundsInRoot)
                }
        }

    private fun assertBoundsHeld(frame: Int, base: Map<Int, Pair<String, Rect>>, now: Map<Int, Pair<String, Rect>>) {
        assertEquals(base.keys, now.keys, "frame $frame changed tracked node set: $base vs $now")
        for ((id, expected) in base) {
            val actual = now.getValue(id).second
            val rect = expected.second
            val drift =
                maxOf(
                    abs(actual.left - rect.left),
                    abs(actual.top - rect.top),
                    abs(actual.right - rect.right),
                    abs(actual.bottom - rect.bottom),
                )
            assertTrue(drift <= 0.5f, "frame $frame moved ${expected.first} by $drift px: $rect -> $actual")
        }
    }

    private fun KitUiTest.runMeasuredTicks(meter: FrameMeter, onTick: (Int) -> Unit) {
        val frame0 = taggedBounds()
        var frameNo = 0
        for (s in 1..5) {
            repeat(62) {
                meter.frame()
                frameNo++
                assertBoundsHeld(frameNo, frame0, taggedBounds())
            }
            onTick(s)
        }
        repeat(10) {
            meter.frame()
            frameNo++
            assertBoundsHeld(frameNo, frame0, taggedBounds())
        }
    }

    private fun checkIdleTick(width: Int, height: Int) {
        val prevLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("en"))
            kitUiTest(width, height) {
                mainClock.autoAdvance = false
                val feed = leftRunningFeed()
                val presenter = TurnFactsPresenter(FixedClock)
                var facts by mutableStateOf(presenter.present(feed, 30_000))
                val turnId = facts.work.keys.single()
                assertTrue(facts.work.values.single().state != WorkLineState.Running)
                val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
                val state =
                    idleUi(
                        WorkLineRow("work:$turnId", turnId, calls = 1, failed = 0, state = WorkState.Done),
                        prompt(turnId, "what kernel is this?"),
                    )
                val meter = FrameMeter.attach(this)
                try {
                    setContent {
                        Root(KitAppearance(motion = MotionLevel.Off), stillFrame = true) {
                            ChatScreen(state, actions, composerSlot, facts = facts)
                        }
                    }
                    meter.settle()
                    // A finished turn's work collapses behind its summary row until opened.
                    onNodeWithTag("chat-work-summary").performTouchInput { down(center); up() }
                    meter.settle()
                    onNodeWithTag("chat-step-p-c1", useUnmergedTree = true).assertExists()

                    TranscriptProbe.rows.clear()
                    TranscriptProbe.steps.clear()
                    TranscriptProbe.on = true
                    runMeasuredTicks(meter) { s -> facts = presenter.present(feed, 30_000L + s * 1_000L) }
                    assertTrue(TranscriptProbe.rows.isEmpty(), "idle ticks recomposed rows: ${TranscriptProbe.rows}")
                    assertTrue(
                        TranscriptProbe.steps.keys.all { it == "p-c1" },
                        "idle ticks recomposed other steps: ${TranscriptProbe.steps}",
                    )
                    onNodeWithText("33.0s", substring = true, useUnmergedTree = true).assertExists()
                } finally {
                    TranscriptProbe.on = false
                    meter.close()
                }
            }
        } finally {
            Locale.setDefault(prevLocale)
        }
    }

    @Test
    fun `B-CHAT-340 an idle chat's clock tick recomposes no transcript row and moves nothing`() {
        checkIdleTick(360, 800)
        checkIdleTick(840, 800)
    }

    @Suppress("MaxLineLength")
    @Test
    fun `B-CHAT-341 a command a finished turn left running keeps a still light, so an idle chat asks for no frames between ticks`() {
        val prevLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("en"))
            kitUiTest(360, 800) {
                mainClock.autoAdvance = false
                val feed = leftRunningFeed()
                val presenter = TurnFactsPresenter(FixedClock)
                var facts by mutableStateOf(presenter.present(feed, 30_000))
                val turnId = facts.work.keys.single()
                val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
                val state =
                    idleUi(
                        WorkLineRow("work:$turnId", turnId, calls = 1, failed = 0, state = WorkState.Done),
                        prompt(turnId, "what kernel is this?"),
                    )
                val meter = FrameMeter.attach(this)
                try {
                    setContent {
                        Root(KitAppearance(), stillFrame = false) {
                            ChatScreen(state, actions, composerSlot, facts = facts)
                        }
                    }
                    meter.settle()
                    onNodeWithTag("chat-work-summary").performTouchInput { down(center); up() }
                    meter.settle(max = 80)
                    onNodeWithTag("chat-step-p-c1", useUnmergedTree = true).assertExists()
                    val stepRunning =
                        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Running") and
                            hasAnyAncestor(hasTestTag("chat-step-p-c1"))
                    onNode(stepRunning, useUnmergedTree = true).assertExists()
                    onNodeWithText("Running uname", useUnmergedTree = true).assertExists()
                    onNodeWithText("28.0s", substring = true, useUnmergedTree = true).assertExists()
                    onNodeWithTag(SWEEP_TAG, useUnmergedTree = true).assertDoesNotExist()

                    var busy = 0
                    for (s in 1..5) {
                        repeat(62) { if (meter.frame().busy) busy++ }
                        facts = presenter.present(feed, 30_000L + s * 1_000L)
                    }
                    repeat(10) { if (meter.frame().busy) busy++ }
                    assertTrue(busy <= 15, "idle chat had $busy busy frames out of 320")
                    onNode(
                        hasTestTag("kit-step-node") and hasAnyAncestor(hasTestTag("chat-step-p-c1")),
                        useUnmergedTree = true,
                    ).assertExists()
                    onNode(stepRunning, useUnmergedTree = true).assertExists()
                    onNodeWithText("Running uname", useUnmergedTree = true).assertExists()
                    onNodeWithText("33.0s", substring = true, useUnmergedTree = true).assertExists()
                    onNodeWithTag(SWEEP_TAG, useUnmergedTree = true).assertDoesNotExist()
                } finally {
                    meter.close()
                }
            }
        } finally {
            Locale.setDefault(prevLocale)
        }
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(30_000)
    }
}
