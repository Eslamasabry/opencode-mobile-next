package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.FakeChatWorld
import io.github.eslamasabry.oc.feature.chat.Immediate
import io.github.eslamasabry.oc.feature.chat.Ref
import io.github.eslamasabry.oc.feature.chat.RecordingNavigator
import io.github.eslamasabry.oc.feature.chat.block
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ChatPromptsDoor
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.footer
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChipActions
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChipUi
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChoice
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderActions
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderUi
import io.github.eslamasabry.oc.feature.chat.header.presentation.ContinueChoice
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderAction
import io.github.eslamasabry.oc.feature.chat.header.presentation.TranscriptWords
import io.github.eslamasabry.oc.feature.chat.header.ui.ApprovalChipSlot
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptReadingUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnMetadataUi
import io.github.eslamasabry.oc.feature.chat.prompt
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import io.github.eslamasabry.oc.ui.kitchat.composer.ComposerInbox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerMic
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerVoice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceField
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The chat scene of Flutter's chat goldens (test/goldens/chat_states_golden_test.dart and tool/capture/fixtures.dart),
 * on the real native screen: "Fix flaky checkout test" in the project "shopfront", one prompt and one reply.
 *
 * The reply is chat_states_golden_test.dart:275-306's: three reads and one edit, then the two-paragraph intro. Its
 * work line, changed file and footer come from those parts through the screen's own [TurnFeedKeeper] and
 * [TurnFactsPresenter], as in the app. The capture server offers approvals (answered by asking first), uses the
 * server's default model and has voice, so the composer shows "Ask first", "Server default" and the mic.
 */
internal class ChatScene(
    val nav: RecordingNavigator = RecordingNavigator(),
    val world: FakeChatWorld = FakeChatWorld(),
) {
    val actions = ChatPresenter(world, nav, Immediate)
    private val promptsDoor = ChatPromptsDoor(Ref, ComposerInbox(), nav)
    val headerActions = object : ChatHeaderActions {
        override fun openMenu() {}
        override fun closeMenu() {}
        override fun choose(action: HeaderAction) {}
        override fun copyTranscript(words: TranscriptWords, mask: (String) -> String) {}
        override fun renameRequested() {}
        override fun renameDone(title: String?) {}
        override fun closeSheet() {}
        override fun continueWith(choice: ContinueChoice) {}
        override fun retryContinueWith() {}
        override fun confirm() {}
        override fun dismissConfirm() {}
        override fun dismissFailure() {}
        override fun clipDone(id: Long) {}
    }
    private val approvalActions = object : ApprovalChipActions {
        override fun openApprovalMenu() = Unit
        override fun closeApprovalMenu() = Unit
        override fun chooseApproval(choice: ApprovalChoice) = Unit
        override fun confirmEverything() = Unit
        override fun cancelEverything() = Unit
        override fun dismissApprovalFailure() = Unit
    }
    private val composerActions = object : BasicComposerActions {
        override fun draftChanged(text: String) {}
        override suspend fun send(text: String) = true
        override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
    }

    /** Flutter's capture has voice, so the empty box shows the mic (kit_composer.dart:434 `_Trailing.mic`). */
    private val voice = object : KitComposerVoice {
        @Composable
        override fun Mic(field: KitVoiceField, modifier: Modifier) {
            KitComposerMic("Talk instead of typing", onClick = {}, modifier = modifier)
        }

        @Composable
        override fun Panel(field: KitVoiceField, modifier: Modifier) = Unit
    }
    /** The composer as the capture server has it; a gate case may put words in its draft. */
    var composer: BasicComposerUi =
        BasicComposerUi(
            available = true,
            sending = false,
            steers = false,
            targetKey = "checkout",
            models = ComposerModels(usesDefault = true),
            filesAvailable = true,
        )
    val host = object : ComposerHost {
        @Composable override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
            BasicComposer(
                composer,
                composerActions,
                chrome,
                modifier,
                voice = voice,
            )
        }
    }

    fun live(): ChatUi = ChatUi.loading(Ref).copy(
        title = "Fix flaky checkout test", phase = ChatPhase.Live,
        // Newest first, as the engine publishes them: the footer, the reply's two paragraphs, the work line, the prompt.
        rows = persistentListOf(
            footer(TURN),
            block(TURN, ASSISTANT, "part_intro", PARAGRAPH_TWO, REPLY.substring(PARAGRAPH_TWO)),
            block(TURN, ASSISTANT, "part_intro", 0, REPLY.substring(0, PARAGRAPH_TWO).trimEnd()),
            WorkLineRow("work:$TURN", TURN, calls = 4, failed = 0, state = WorkState.Done),
            prompt(TURN, "Fix the flaky checkout test"),
        ),
    )

    /** The turn's facts as the screen derives them from the messages, plus Flutter's footer words. */
    fun facts(): TurnFactsUi {
        val keeper = TurnFeedKeeper(SESSION)
        keeper.history(messages())
        return TurnFactsPresenter(FixedClock).present(keeper.feed.value, NOW).copy(
            reading = TranscriptReadingUi(
                metadata = mapOf(TURN to TurnMetadataUi(listOf(MODEL), 0, 0.0, latest = true, footerModel = "Sonnet 4")),
            ),
        )
    }

    /** The screen as Flutter's golden has it: the chat at its window's root (no Back), in "shopfront". */
    @Composable
    fun Screen(
        state: ChatUi,
        canGoBack: Boolean = false,
        branch: String? = null,
        approval: ApprovalChipUi? = ApprovalChipUi(offered = true, choice = ApprovalChoice.Ask),
        facts: TurnFactsUi = facts(),
        projectName: String? = "shopfront",
    ) {
        ChatScreen(
            state, actions, host,
            header = ChatHeaderSlot(
                ChatHeaderUi(title = state.title, projectName = projectName, branch = branch),
                headerActions,
                approval = approval?.let { ApprovalChipSlot(it, approvalActions) },
            ),
            facts = facts,
            canGoBack = canGoBack,
            // The capture app keeps saved prompts, so the "+" sheet folds them under "Prompts".
            prompts = promptsDoor,
        )
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(NOW)
    }

    companion object {
        const val REPLY: String =
            "The flakiness comes from `CheckoutBloc` racing the price refresh: `applyCoupon()` awaits the network " +
                "before the cart total has settled, so the assertion sometimes reads the stale total.\n\n" +
                "I made the test wait on the settled state instead of a fixed delay:"
        private val PARAGRAPH_TWO = REPLY.indexOf("I made")
        private const val TURN = "msg_user"
        private const val ASSISTANT = "msg_assistant"
        private const val MODEL = "anthropic/claude-sonnet-4"
        private val SESSION = Ref.sessionId.raw
        private const val NOW = 100_000L

        private fun tool(id: String, name: String, path: String) =
            Part(
                id, ASSISTANT, SESSION, PartType.Tool, "tool",
                callId = "call_$id",
                toolName = name,
                tool = ToolState(
                    ToolStatus.Completed,
                    input = JsonObject(mapOf("filePath" to JsonPrimitive(path))),
                    output = "ok",
                ),
            )

        /** chat_states_golden_test.dart:286-304: the prompt, then three reads, one edit and the intro. */
        fun messages(): List<MessageWithParts> = listOf(
            MessageWithParts(
                MessageInfo(TURN, SESSION, Role.User, time = MsgTime(NOW - 95_000, NOW - 95_000)),
                persistentListOf(Part("part_user", TURN, SESSION, PartType.Text, "text", text = "Fix the flaky checkout test")),
            ),
            MessageWithParts(
                MessageInfo(
                    ASSISTANT, SESSION, Role.Assistant,
                    providerId = "anthropic", modelId = "claude-sonnet-4",
                    time = MsgTime(NOW - 80_000, NOW - 4_000), finish = "stop",
                ),
                listOf(
                    tool("t1", "read", "test/checkout_test.dart"),
                    tool("t2", "read", "lib/checkout_bloc.dart"),
                    tool("t3", "read", "lib/coupon.dart"),
                    tool("t4", "edit", "test/checkout_test.dart"),
                    Part("part_intro", ASSISTANT, SESSION, PartType.Text, "text", text = REPLY),
                ).toImmutableList(),
            ),
        )
    }
}
