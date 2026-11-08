package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.ThoughtRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDetailUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerMic
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerVoice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceField
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Capture only (no comparison): the chat while a reply runs, on the real screen with the real composer, in the states
 * Flutter's running-turn goldens cover — just started, mid-commands (steps hidden behind the live line, then shown),
 * waiting for approval, a failed step — under Glass and Solid, dark. Writes PNGs (and, for the motion record, numbered
 * frames) to `CHAT_RUNNING_OUT`; does nothing when that is unset, so CI never writes.
 */
@OptIn(ExperimentalTestApi::class)
class ChatRunningRenders {
    private val out = System.getenv("CHAT_RUNNING_OUT")?.let { File(it).also(File::mkdirs) }
    private val sid = Ref.sessionId.raw

    private fun info(id: String, role: Role, at: Long, done: Long? = null) =
        MessageInfo(id, sid, role, providerId = "anthropic", modelId = "claude-sonnet-4", time = MsgTime(at, done))

    private fun text(id: String, mid: String, words: String) = Part(id, mid, sid, PartType.Text, "text", text = words)

    private fun reasoning(id: String, mid: String, words: String) =
        Part(id, mid, sid, PartType.Reasoning, "reasoning", text = words, ended = true)

    private fun tool(
        id: String,
        name: String,
        status: ToolStatus,
        input: Map<String, String>,
        start: Long,
        end: Long? = null,
        output: String? = null,
        ran: Boolean = true,
    ) =
        Part(
            id, "m2", sid, PartType.Tool, "tool",
            callId = "call-$id",
            toolName = name,
            tool =
                ToolState(
                    status,
                    input = JsonObject(input.mapValues { JsonPrimitive(it.value) }),
                    output = output,
                    startedAtMs = if (ran) start else null,
                    completedAtMs = end,
                ),
        )

    private val prompt =
        MessageWithParts(info("m1", Role.User, NOW - 41_000), persistentListOf(text("p1", "m1", PROMPT)))

    private fun bash(id: String, command: String, start: Long, end: Long?, output: String = "ok", status: ToolStatus? = null) =
        tool(id, "bash", status ?: if (end == null) ToolStatus.Running else ToolStatus.Completed, mapOf("command" to command), start, end, output)

    private fun fetch(id: String, url: String, start: Long, end: Long, failed: Boolean = false) =
        tool(id, "webfetch", if (failed) ToolStatus.Error else ToolStatus.Completed, mapOf("url" to url), start, end, if (failed) "404" else "ok")

    /** Four commands and two fetches done, as on the owner's phone. */
    private fun doneSteps(): List<Part> =
        listOf(
            bash("t1", "git status --short", NOW - 38_000, NOW - 37_600),
            bash("t2", "npm install", NOW - 37_000, NOW - 29_000),
            fetch("t3", "https://docs.example.test/checkout", NOW - 28_500, NOW - 27_900),
            fetch("t4", "https://docs.example.test/coupons", NOW - 27_500, NOW - 26_800),
            bash("t5", "npm run lint", NOW - 26_000, NOW - 21_000),
            bash("t6", "npm run build", NOW - 20_500, NOW - 13_000),
        )

    private enum class Scene { Started, Commands, CommandsSteps, Waiting, Failed }

    private fun messages(scene: Scene): List<MessageWithParts> =
        when (scene) {
            Scene.Started -> listOf(MessageWithParts(info("m1", Role.User, NOW - 2_000), prompt.parts))
            Scene.Commands,
            Scene.CommandsSteps ->
                listOf(
                    prompt,
                    MessageWithParts(
                        info("m2", Role.Assistant, NOW - 39_000),
                        (doneSteps() +
                                bash("t7", "npm test -- --watch=false", NOW - 12_000, null, "  ✓ cart total settles\n  ✓ coupon applies once\n"))
                            .toImmutableList(),
                    ),
                )
            Scene.Waiting ->
                listOf(
                    prompt,
                    MessageWithParts(
                        info("m2", Role.Assistant, NOW - 39_000),
                        (doneSteps() +
                                tool("t7", "bash", ToolStatus.Pending, mapOf("command" to "rm -rf node_modules/.cache"), NOW - 9_000, ran = false))
                            .toImmutableList(),
                    ),
                )
            Scene.Failed ->
                listOf(
                    prompt,
                    MessageWithParts(
                        info("m2", Role.Assistant, NOW - 39_000),
                        (doneSteps() +
                                fetch("t7", "https://registry.example.test/coupon-kit", NOW - 12_000, NOW - 11_200, failed = true) +
                                reasoning("r1", "m2", THOUGHT))
                            .toImmutableList(),
                    ),
                )
        }

    private fun facts(scene: Scene, now: Long): TurnFactsUi {
        val keeper = TurnFeedKeeper(sid)
        keeper.history(messages(scene))
        // Just started: the run began two seconds ago (the line says only what it does, no clock yet).
        keeper.event(GatewayEvent.RunStarted(SessionId(sid)), if (scene == Scene.Started) NOW - 2_000 else NOW - 41_000)
        val facts = TurnFactsPresenter(FixedClock).present(keeper.feed.value, now, waitingForYou = scene == Scene.Waiting)
        if (scene != Scene.Failed) return facts
        val part = messages(scene).last().parts.last()
        return facts.copy(reading = facts.reading.copy(details = facts.reading.details + (THOUGHT_KEY to TranscriptDetailUi(THOUGHT_KEY, part))))
    }

    private fun rows(scene: Scene): List<TranscriptRow> =
        when (scene) {
            Scene.Started -> listOf(prompt("m1", PROMPT))
            Scene.Failed ->
                listOf(
                    ThoughtRow(THOUGHT_KEY, "m1", THOUGHT.lineSequence().first(), THOUGHT),
                    WorkLineRow("work:m1", "m1", calls = 7, failed = 1, state = WorkState.Done),
                    prompt("m1", PROMPT),
                )
            else -> listOf(WorkLineRow("work:m1", "m1", calls = 7, failed = 0, state = WorkState.Running), prompt("m1", PROMPT))
        }

    private val composerActions =
        object : BasicComposerActions {
            override fun draftChanged(text: String) {}

            override suspend fun send(text: String) = true

            override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
        }

    private val voice =
        object : KitComposerVoice {
            @Composable
            override fun Mic(field: KitVoiceField, modifier: Modifier) {
                KitComposerMic("Talk instead of typing", onClick = {}, modifier = modifier)
            }

            @Composable override fun Panel(field: KitVoiceField, modifier: Modifier) = Unit
        }

    private val host =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                BasicComposer(
                    BasicComposerUi(
                        available = true,
                        sending = false,
                        steers = true,
                        targetKey = "checkout",
                        models = ComposerModels(usesDefault = true),
                        filesAvailable = true,
                    ),
                    composerActions,
                    chrome,
                    modifier,
                    voice = voice,
                )
            }
        }

    @Composable
    private fun Screen(scene: Scene, style: Style, facts: TurnFactsUi, still: Boolean) {
        CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
            KitTheme(
                appearance = KitAppearance(mode = AppearanceMode.Dark, style = style, motion = MotionLevel.Full),
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = still,
            ) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                        val state =
                            ChatUi.loading(Ref).copy(
                                title = "Fix the flaky checkout test",
                                phase = ChatPhase.Live,
                                rows = rows(scene).toPersistentList(),
                                live = LiveTurnUi(stopping = false),
                                reasoningExpanded = scene == Scene.Failed,
                            )
                        ChatScreen(state, ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate), host, facts = facts)
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.save(name: String) {
        val image = onRoot().captureToImage().toAwtImage()
        ImageIO.write(image, "png", File(out, "$name.png"))
    }

    private fun shoot(scene: Scene, style: Style, name: String, setup: ComposeUiTest.() -> Unit = {}) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent { Screen(scene, style, facts(scene, NOW), still = true) }
            waitForIdle()
            setup()
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            save("$name-${style.storageName}-dark")
        }
    }

    @Test
    fun `chat running renders`() {
        if (out == null) return
        for (style in listOf(Style.Glass, Style.Solid)) {
            shoot(Scene.Started, style, "1-just-started")
            shoot(Scene.Commands, style, "2-mid-commands")
            shoot(Scene.CommandsSteps, style, "3-mid-commands-steps-shown") {
                onNodeWithTag("chat-live-line").performClick()
            }
            shoot(Scene.Waiting, style, "4-waiting-for-approval")
            shoot(Scene.Failed, style, "5-failed-step")
        }
    }

    /** Frames of the running animation (30 fps, two seconds: one step-light lap) for the screen recording, steps shown, Solid dark. */
    @Test
    fun `chat running motion frames`() {
        val dir = out?.let { File(it, "frames").also(File::mkdirs) } ?: return
        Locale.setDefault(Locale.forLanguageTag("en"))
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            var facts by mutableStateOf(facts(Scene.CommandsSteps, NOW))
            mainClock.autoAdvance = false
            setContent { Screen(Scene.CommandsSteps, Style.Solid, facts, still = false) }
            mainClock.advanceTimeBy(500)
            onNodeWithTag("chat-live-line").performClick()
            mainClock.advanceTimeBy(600)
            for (frame in 0 until FRAMES) {
                if (frame % 30 == 0) facts = facts(Scene.CommandsSteps, NOW + frame / 30 * 1_000L)
                mainClock.advanceTimeBy(1_000L / 30)
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "f%03d.png".format(frame)))
            }
        }
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(NOW)
    }

    private companion object {
        const val NOW = 1_000_000L
        const val WIDTH = 1081
        const val HEIGHT = 2402
        const val DENSITY = 2.625f
        const val FRAMES = 60
        const val PROMPT = "The checkout test still fails about one run in five. Install, run it ten times and fix it."
        const val THOUGHT_KEY = "thought:m2/r1"
        const val THOUGHT =
            "The coupon kit page is gone, so the registry fetch failed.\n" +
                "I do not need it: the race is in CheckoutBloc, where applyCoupon awaits the network before the total " +
                "settles. I will make the test wait on the settled state and run it ten times."
    }
}
