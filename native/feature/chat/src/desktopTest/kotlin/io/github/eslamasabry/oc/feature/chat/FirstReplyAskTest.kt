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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatActions
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyAskStage
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyAskUi
import io.github.eslamasabry.oc.feature.chat.starprompt.ReplyAskUse
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.transcript.REPLY_ASK_ACTION_TAG
import io.github.eslamasabry.oc.ui.kitchat.transcript.REPLY_ASK_AFTER_TAG
import io.github.eslamasabry.oc.ui.kitchat.transcript.REPLY_ASK_NO_TAG
import io.github.eslamasabry.oc.ui.kitchat.transcript.REPLY_ASK_TAG
import io.github.eslamasabry.oc.ui.kitchat.transcript.REPLY_ASK_YES_TAG
import io.github.eslamasabry.oc.ui.kitchat.transcript.REPLY_META_TAG
import kotlinx.collections.immutable.toPersistentList
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FirstReplyAskTest {
    private val composerActions =
        object : BasicComposerActions {
            override fun draftChanged(text: String) = Unit

            override suspend fun send(text: String) = true

            override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
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
                        targetKey = "ask",
                        models = ComposerModels(usesDefault = true),
                        filesAvailable = true,
                    ),
                    composerActions,
                    chrome,
                    modifier,
                )
            }
        }

    @Composable
    private fun Screen(ask: ReplyAskUi?, actions: ChatActions) {
        CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
            KitTheme(
                appearance =
                    KitAppearance(mode = AppearanceMode.Dark, style = Style.Solid, motion = MotionLevel.Off),
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                        val rows: List<TranscriptRow> = turn(1, ask = FIRST, answer = ANSWER).asReversed()
                        val state =
                            ChatUi.loading(Ref).copy(
                                title = "Fix the flaky checkout test",
                                phase = ChatPhase.Live,
                                rows = rows.toPersistentList(),
                                replyAsk = ask,
                            )
                        ChatScreen(state, actions, host, facts = completedReplyFacts)
                    }
                }
            }
        }
    }

    @Test
    fun `B-STAR-037 the star card is gone and the ask rides the reply's own footer`() {
        kitUiTest(WIDTH, HEIGHT) {
            var ask by mutableStateOf<ReplyAskUi?>(ReplyAskUi(turnId = "u1"))
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Screen(ask, actions) }
            waitForIdle()
            onNodeWithTag("kit-turn-live-ask").assertDoesNotExist()
            onNodeWithText("A GitHub star helps others find it.").assertDoesNotExist()
            onNodeWithTag(REPLY_META_TAG, useUnmergedTree = true).assertDoesNotExist()
            val footer = onNodeWithTag("chat-turn-footer").getBoundsInRoot()
            val row = onNodeWithTag(REPLY_ASK_TAG).getBoundsInRoot()
            assertTrue(row.top >= footer.top - 0.5.dp && row.bottom <= footer.bottom + 0.5.dp, "ask rides the footer")

            ask = null
            waitForIdle()
            onNodeWithTag(REPLY_ASK_TAG).assertDoesNotExist()
            onNodeWithTag(REPLY_META_TAG, useUnmergedTree = true).assertExists()
        }
    }

    private class Branch(
        val tapTag: String,
        val nextStage: ReplyAskStage,
        val pickText: String,
        val expectedNote: String,
        val expectedAction: String,
        val checkAllUses: Boolean = false,
    )

    private fun runAskBranch(branch: Branch) {
        kitUiTest(WIDTH, HEIGHT) {
            var ask by mutableStateOf<ReplyAskUi?>(ReplyAskUi(turnId = "u1"))
            val base = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            val actions =
                object : ChatActions by base {
                    override fun replyAskYes() {
                        ask = ReplyAskUi("u1", branch.nextStage, menu = true)
                    }

                    override fun replyAskNo() {
                        ask = ReplyAskUi("u1", branch.nextStage, menu = true)
                    }

                    override fun replyAskUse(use: ReplyAskUse) {
                        ask = ask?.copy(menu = false)
                    }

                    override fun replyAskMenuClosed() {
                        ask = ask?.copy(menu = false)
                    }
                }
            setContent { Screen(ask, actions) }
            waitForIdle()
            onNodeWithTag(branch.tapTag).performClick()
            waitForIdle()
            onNodeWithText("What do you use it for?").assertExists()
            if (branch.checkAllUses) {
                for (use in listOf("Everyday tasks", "Writing code", "Both", "Just looking")) {
                    onNodeWithText(use).assertExists()
                }
            }
            onNodeWithText(branch.pickText).performClick()
            waitForIdle()
            onNodeWithTag(REPLY_ASK_AFTER_TAG).assertExists()
            onNodeWithText(branch.expectedNote).assertExists()
            onNodeWithTag(REPLY_ASK_ACTION_TAG).assertExists()
            onNodeWithText(branch.expectedAction).assertExists()
        }
    }

    @Test
    fun `B-STAR-038 Yes shows Thanks with Star on GitHub and the four uses, No shows Sorry with Report a problem`() {
        runAskBranch(
            Branch(
                tapTag = REPLY_ASK_YES_TAG,
                nextStage = ReplyAskStage.Thanks,
                pickText = "Writing code",
                expectedNote = "Thanks.",
                expectedAction = "Star on GitHub",
                checkAllUses = true,
            ),
        )
        runAskBranch(
            Branch(
                tapTag = REPLY_ASK_NO_TAG,
                nextStage = ReplyAskStage.Sorry,
                pickText = "Everyday tasks",
                expectedNote = "Sorry.",
                expectedAction = "Report a problem",
            ),
        )
    }

    private companion object {
        const val WIDTH = 945
        const val HEIGHT = 2100
        const val DENSITY = 2.625f
        const val FIRST = "Why does the checkout test fail one run in five?"
        const val ANSWER =
            "The coupon total is read before the network call settles. Waiting on the settled state fixes it."
    }
}
