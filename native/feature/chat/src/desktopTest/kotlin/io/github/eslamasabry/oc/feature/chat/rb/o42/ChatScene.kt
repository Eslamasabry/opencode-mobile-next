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
import io.github.eslamasabry.oc.feature.chat.presentation.ChatCapabilitiesUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnMetadataUi
import io.github.eslamasabry.oc.feature.chat.prompt
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
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
 * Flutter's capture controller (fixtures.dart `captureController`) offers approvals (the strip's "Ask first"), says
 * the model is the server's default ("Server default"), has voice (the empty box's mic) and a server with Project
 * Files ("All files" under a turn's changed files).
 */
internal class ChatScene(val nav: RecordingNavigator = RecordingNavigator()) {
    val actions = ChatPresenter(FakeChatWorld(), nav, Immediate)
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
    val host = object : ComposerHost {
        @Composable override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
            BasicComposer(
                BasicComposerUi(
                    available = true,
                    sending = false,
                    steers = false,
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

    fun live(): ChatUi = ChatUi.loading(Ref).copy(
        title = "Fix flaky checkout test", phase = ChatPhase.Live,
        rows = persistentListOf(footer("msg_user"), *reply(), prompt("msg_user", "Fix the flaky checkout test")),
        capabilities = ChatCapabilitiesUi(files = true),
    )

    /**
     * chat-1 of chat_states_golden_test.dart (`chat · transcript`): the reply read three files and edited one before
     * it answered, so the turn has a work line above the words and the changed file under them.
     */
    fun liveWithWork(): ChatUi = live().copy(
        rows = persistentListOf(
            footer("msg_user"),
            *reply(),
            WorkLineRow("work:msg_user", "msg_user", calls = 4, failed = 0, state = WorkState.Done),
            prompt("msg_user", "Fix the flaky checkout test"),
        ),
    )

    /**
     * The reply as the engine's block splitter gives it (StreamingBlocks: a blank line closes a block), one row per
     * paragraph, newest first.
     */
    private fun reply() = REPLY.split("\n\n").let { paragraphs ->
        paragraphs.map { words -> block("msg_user", "msg_assistant", "part_intro", REPLY.indexOf(words), words) }.reversed()
    }.toTypedArray()

    /** The turn facts of [liveWithWork], from the same messages through the real feed and presenter. */
    fun workFacts(): TurnFactsUi {
        val keeper = TurnFeedKeeper(Ref.sessionId.raw)
        keeper.history(workTurn())
        val facts = TurnFactsPresenter(FixedClock).present(keeper.feed.value, NOW_MS)
        return facts.copy(reading = facts.reading.copy(metadata = METADATA))
    }

    /** The screen as Flutter's golden has it: the chat at its window's root (no Back), in "shopfront". */
    @Composable
    fun Screen(
        state: ChatUi,
        canGoBack: Boolean = false,
        branch: String? = null,
        facts: TurnFactsUi = TurnFactsUi(reading = io.github.eslamasabry.oc.feature.chat.presentation.TranscriptReadingUi(metadata = METADATA)),
    ) {
        ChatScreen(
            state, actions, host,
            header = ChatHeaderSlot(
                ChatHeaderUi(title = state.title, projectName = "shopfront", branch = branch),
                headerActions,
                approval = ApprovalChipSlot(ApprovalChipUi(offered = true, choice = ApprovalChoice.Ask), approvalActions),
            ),
            facts = facts,
            canGoBack = canGoBack,
        )
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(NOW_MS)
    }

    companion object {
        const val REPLY: String =
            "The flakiness comes from `CheckoutBloc` racing the price refresh: `applyCoupon()` awaits the network " +
                "before the cart total has settled, so the assertion sometimes reads the stale total.\n\n" +
                "I made the test wait on the settled state instead of a fixed delay:"

        private const val NOW_MS = 1_000_000L
        private val METADATA = mapOf("msg_user" to TurnMetadataUi(listOf("Sonnet 4"), 0, 0.0, true))

        private fun info(id: String, role: Role, created: Long, done: Long) =
            MessageInfo(id, Ref.sessionId.raw, role, providerId = "anthropic", modelId = "claude-sonnet-4", time = MsgTime(created, done),
                finish = if (role == Role.Assistant) "stop" else null, parentId = if (role == Role.Assistant) "msg_user" else null)

        private fun tool(id: String, name: String, path: String) =
            Part(
                id, "msg_assistant", Ref.sessionId.raw, PartType.Tool, "tool",
                callId = "call_$id",
                toolName = name,
                tool = ToolState(ToolStatus.Completed, input = JsonObject(mapOf("filePath" to JsonPrimitive(path))), output = "ok"),
            )

        /** chat_states_golden_test.dart:262-297: three reads, one edit, then the reply's words. */
        private fun workTurn(): List<MessageWithParts> = listOf(
            MessageWithParts(
                info("msg_user", Role.User, NOW_MS - 95_000, NOW_MS - 95_000),
                persistentListOf(Part("part_user", "msg_user", Ref.sessionId.raw, PartType.Text, "text", text = "Fix the flaky checkout test")),
            ),
            MessageWithParts(
                info("msg_assistant", Role.Assistant, NOW_MS - 80_000, NOW_MS - 4_000),
                listOf(
                    tool("t1", "read", "test/checkout_test.dart"),
                    tool("t2", "read", "lib/checkout_bloc.dart"),
                    tool("t3", "read", "lib/coupon.dart"),
                    tool("t4", "edit", "test/checkout_test.dart"),
                    Part("part_intro", "msg_assistant", Ref.sessionId.raw, PartType.Text, "text", text = REPLY),
                ).toImmutableList(),
            ),
        )
    }
}
